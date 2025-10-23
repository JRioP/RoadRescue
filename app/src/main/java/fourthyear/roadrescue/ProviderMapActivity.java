package fourthyear.roadrescue;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.TextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.android.material.switchmaterial.SwitchMaterial;

// --- CRITICAL MAP IMPORTS ---
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback; // ADDED: Map Callback Interface
import com.google.android.gms.maps.SupportMapFragment; // ADDED: Fragment management
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
// -----------------------------

import fourthyear.roadrescue.R;

// MODIFIED: Implement the OnMapReadyCallback interface
public class ProviderMapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "ProviderMapActivity";
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration pendingRequestListener;
    private ListenerRegistration acceptedJobsListener;

    private TextView statusTextView;

    // --- ASSIGNED: GoogleMap object ---
    private GoogleMap MyMap;

    private List<Map<String, Object>> acceptedJobsList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_map);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        statusTextView = findViewById(R.id.status_text_view);
        statusTextView.setText("Offline");

        // --- Navigation Buttons (Re-added for completeness) ---
        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        notificationButton.setOnClickListener(v -> {
            Intent intent = new Intent(ProviderMapActivity.this, NotificationsActivity.class);
            startActivity(intent);
        });

        ImageView homeButton = findViewById(R.id.home_icon_btn);
        homeButton.setOnClickListener(v -> {
            Intent intent = new Intent(ProviderMapActivity.this, homepage.class);
            startActivity(intent);
        });

        ImageView messageButton = findViewById(R.id.message_icon_btn);
        messageButton.setOnClickListener(v -> {
            Intent intent = new Intent(ProviderMapActivity.this, ChatInboxActivity.class);
            startActivity(intent);
        });
        // ----------------------------------------------------

        SwitchMaterial onlineSwitch = findViewById(R.id.online_switch);
        onlineSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                goOnline();
            } else {
                goOffline();
            }
        });

        // --- ADDED: Initialize Map Fragment ---
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_fragment); // Assuming the ID of your map fragment is 'map_fragment'
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            Log.e(TAG, "Map fragment not found in layout.");
        }
    }

    // --- ADDED: Map Ready Callback Method ---
    @Override
    public void onMapReady(GoogleMap googleMap) {
        MyMap = googleMap;
        Toast.makeText(this, "Map is ready.", Toast.LENGTH_SHORT).show();

        // Optional: Move camera to a default location or the provider's current location
        // Example: MyMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(14.5995, 120.9842), 10f));

        // If the provider is already online when the map loads, draw the jobs immediately
        if (statusTextView.getText().toString().equals("Online")) {
            drawAllAcceptedJobs();
        }
    }


    private void goOnline() {
        // TODO: Update provider's status in your 'users' collection to 'online: true'
        Toast.makeText(this, "You are online.", Toast.LENGTH_SHORT).show();
        statusTextView.setText("Online");

        listenForPendingRequests();
        listenForAcceptedJobs();
    }

    private void goOffline() {
        // TODO: Update provider's status to 'online: false'
        Toast.makeText(this, "You are offline.", Toast.LENGTH_SHORT).show();
        statusTextView.setText("Offline");

        if (pendingRequestListener != null) {
            pendingRequestListener.remove();
        }
        if (acceptedJobsListener != null) {
            acceptedJobsListener.remove();
        }

        // Clear the map and the jobs list when going offline
        acceptedJobsList.clear();
        if (MyMap != null) {
            MyMap.clear();
        }
    }

    /**
     * Listens for newly created (pending) service requests to show in the dialog.
     */
    private void listenForPendingRequests() {
        if (mAuth.getCurrentUser() == null) return;

        Query pendingRequestsQuery = db.collection("service_requests")
                .whereEqualTo("status", "pending");

        pendingRequestListener = pendingRequestsQuery.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.w(TAG, "Pending Request Listener failed.", e);
                return;
            }

            for (DocumentChange dc : snapshots.getDocumentChanges()) {
                if (dc.getType() == DocumentChange.Type.ADDED) {
                    String requestId = dc.getDocument().getId();
                    Map<String, Object> requestData = dc.getDocument().getData();
                    Log.d(TAG, "New pending request found: " + requestId);

                    showJobOfferDialog(requestId, requestData);
                }
            }
        });
    }

    /**
     * NEW: Listens for all active jobs assigned to this provider.
     */
    private void listenForAcceptedJobs() {
        if (mAuth.getCurrentUser() == null) return;
        String providerId = mAuth.getCurrentUser().getUid();

        // Query for jobs that are 'accepted' AND assigned to the current provider
        Query acceptedJobsQuery = db.collection("service_requests")
                .whereEqualTo("providerId", providerId)
                .whereEqualTo("status", "accepted");
        // TODO: You may want to listen for 'en-route' status too

        acceptedJobsListener = acceptedJobsQuery.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.w(TAG, "Accepted Jobs Listener failed.", e);
                return;
            }

            // Clear and rebuild the list based on the current accepted documents
            acceptedJobsList.clear();
            for (DocumentChange dc : snapshots.getDocumentChanges()) {
                Map<String, Object> jobData = dc.getDocument().getData();
                String jobId = dc.getDocument().getId();

                // Add the job data (including its ID for later reference)
                jobData.put("requestId", jobId);

                switch (dc.getType()) {
                    case ADDED:
                    case MODIFIED:
                        acceptedJobsList.add(jobData);
                        Log.d(TAG, "Job added/modified: " + jobId);
                        break;
                    case REMOVED:
                        // Find and remove the job that was completed/removed
                        // (While acceptedJobsList.clear() handles bulk changes, this is safer for REMOVED type)
                        for (int i = 0; i < acceptedJobsList.size(); i++) {
                            if (acceptedJobsList.get(i).get("requestId").equals(jobId)) {
                                acceptedJobsList.remove(i);
                                break;
                            }
                        }
                        Log.d(TAG, "Job completed/removed: " + jobId);
                        break;
                }
            }

            // Redraw the map with the updated list of jobs
            drawAllAcceptedJobs();
        });
    }

    /**
     * Shows the AlertDialog and passes requestData to the acceptJob method.
     */
    private void showJobOfferDialog(String requestId, Map<String, Object> requestData) {
        // Extract location details for display (optional)
        Double pickupLat = (Double) requestData.get("pickupLat");
        Double pickupLng = (Double) requestData.get("pickupLng");

        String message = "A new towing job is available at ("
                + String.format("%.4f", pickupLat) + ", "
                + String.format("%.4f", pickupLng) + "). Do you want to accept it?";

        new AlertDialog.Builder(this)
                .setTitle("New Service Request!")
                .setMessage(message)
                .setPositiveButton("Accept", (dialog, which) -> {
                    acceptJob(requestId, requestData);
                })
                .setNegativeButton("Decline", (dialog, which) -> {
                    dialog.dismiss();
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Updates Firestore status to 'accepted'. The acceptedJobsListener handles
     * drawing the job on the map when Firestore confirms the update.
     */
    private void acceptJob(String requestId, Map<String, Object> requestData) {
        if (mAuth.getCurrentUser() == null) return;
        String providerId = mAuth.getCurrentUser().getUid();

        db.collection("service_requests").document(requestId)
                .update("status", "accepted", "providerId", providerId)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Job accepted! Customer notified.", Toast.LENGTH_LONG).show();
                    Log.d(TAG, "Job " + requestId + " accepted by " + providerId);
                    // The listener will trigger drawAllAcceptedJobs, so no direct call needed here.
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Could not accept job.", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error updating document", e);
                });
    }

    /**
     * Clears the map and draws all jobs in the acceptedJobsList.
     */
    private void drawAllAcceptedJobs() {
        if (MyMap == null) {
            Log.e(TAG, "GoogleMap instance (MyMap) is null. Cannot draw jobs.");
            return;
        }

        MyMap.clear(); // Clear all old job markers

        if (acceptedJobsList.isEmpty()) {
            Toast.makeText(this, "No active jobs.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, acceptedJobsList.size() + " active job(s) shown on map.", Toast.LENGTH_SHORT).show();

        for (Map<String, Object> jobData : acceptedJobsList) {
            Double pickupLat = (Double) jobData.get("pickupLat");
            Double pickupLng = (Double) jobData.get("pickupLng");
            Double destLat = (Double) jobData.get("destinationLat");
            Double destLng = (Double) jobData.get("destinationLng");
            String requestId = (String) jobData.get("requestId");

            if (pickupLat == null || pickupLng == null) continue;

            LatLng pickupLocation = new LatLng(pickupLat, pickupLng);

            // 1. Add Pickup marker (The job location)
            MyMap.addMarker(new MarkerOptions()
                    .position(pickupLocation)
                    .title("PICKUP: Job " + requestId.substring(0, 4) + "...")
                    .snippet("Status: Accepted")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

            // 2. Add Destination marker (if available)
            if (destLat != null && destLng != null) {
                LatLng destinationLocation = new LatLng(destLat, destLng);
                MyMap.addMarker(new MarkerOptions()
                        .position(destinationLocation)
                        .title("DESTINATION: Job " + requestId.substring(0, 4) + "...")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            }
        }

        // Optionally zoom to the first accepted job or fit all markers
        Map<String, Object> firstJob = acceptedJobsList.get(0);
        LatLng firstPickup = new LatLng((Double)firstJob.get("pickupLat"), (Double)firstJob.get("pickupLng"));
        MyMap.animateCamera(CameraUpdateFactory.newLatLngZoom(firstPickup, 13f));
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        goOffline(); // Calls goOffline which handles detaching both listeners
    }
}
