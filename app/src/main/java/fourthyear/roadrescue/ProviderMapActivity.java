package fourthyear.roadrescue;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.content.Intent;
import android.graphics.Color; // ADDED
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.TextView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline; // ADDED
import com.google.android.gms.maps.model.PolylineOptions; // ADDED

// --- ADDED IMPORTS FOR DIRECTIONS API ---
import com.google.maps.GeoApiContext;
import com.google.maps.DirectionsApi;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.DirectionsLeg;
import com.google.maps.model.DirectionsRoute;
import com.google.maps.android.PolyUtil; // For decoding the polyline
import java.util.concurrent.Executors; // For background threads
// --- END OF ADDED IMPORTS ---

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;

public class ProviderMapActivity extends AppCompatActivity
        implements OnMapReadyCallback, PendingRequestsAdapter.OnAcceptClickListener, PendingRequestsAdapter.OnItemClickListener {

    private static final String TAG = "ProviderMapActivity";
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration pendingRequestListener;
    private ListenerRegistration acceptedJobsListener;
    private TextView statusTextView;
    private GoogleMap MyMap;
    private List<Map<String, Object>> acceptedJobsList = new ArrayList<>();
    private RecyclerView pendingRequestsRecyclerView;
    private PendingRequestsAdapter pendingRequestsAdapter;
    private List<Map<String, Object>> pendingRequestsList = new ArrayList<>();
    private List<Marker> tempPendingRequestMarkers = new ArrayList<>();

    // --- NEW: For Directions API ---
    private GeoApiContext geoApiContext = null;
    private Polyline activeJobPolyline;
    private Polyline tempPendingRequestPolyline;
    // --- END NEW ---

    // UI for Active Job Card
    private View activeJobCard;
    private TextView activeJobPickup;
    private TextView activeJobDestination;
    private Button completeJobButton;

    private static final String FIELD_PICKUP_ADDRESS = "pickupAddress";
    private static final String FIELD_DESTINATION_ADDRESS = "destinationAddress";
    private static final String FIELD_PICKUP_LAT = "pickupLat";
    private static final String FIELD_PICKUP_LNG = "pickupLng";
    private static final String FIELD_DEST_LAT = "destinationLat";
    private static final String FIELD_DEST_LNG = "destinationLng";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_map);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // --- NEW: Initialize the GeoApiContext ---
        if (geoApiContext == null) {
            geoApiContext = new GeoApiContext.Builder()
                    .apiKey(getString(R.string.google_maps_key))
                    .build();
        }
        // --- END NEW ---

        statusTextView = findViewById(R.id.status_text_view);
        statusTextView.setText("Offline");

        // Find Active Job Card UI elements
        activeJobCard = findViewById(R.id.active_job_card);
        activeJobPickup = findViewById(R.id.active_job_pickup_text);
        activeJobDestination = findViewById(R.id.active_job_destination_text);
        completeJobButton = findViewById(R.id.complete_job_button);

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

        SwitchMaterial onlineSwitch = findViewById(R.id.online_switch);
        onlineSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                goOnline();
            } else {
                goOffline();
            }
        });

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            Log.e(TAG, "Map fragment not found in layout.");
        }

        setupPendingRequestsRecyclerView();
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        MyMap = googleMap;
        Toast.makeText(this, "Map is ready.", Toast.LENGTH_SHORT).show();
        if (statusTextView.getText().toString().equals("Online")) {
            listenForAcceptedJobs();
        }
    }

    private void setupPendingRequestsRecyclerView() {
        pendingRequestsRecyclerView = findViewById(R.id.pending_requests_recycler_view);
        pendingRequestsAdapter = new PendingRequestsAdapter(pendingRequestsList, this, this);
        pendingRequestsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        pendingRequestsRecyclerView.setAdapter(pendingRequestsAdapter);
    }

    private void goOnline() {
        Toast.makeText(this, "You are online.", Toast.LENGTH_SHORT).show();
        statusTextView.setText("Online");
        listenForAcceptedJobs();
    }

    private void goOffline() {
        Toast.makeText(this, "You are offline.", Toast.LENGTH_SHORT).show();
        statusTextView.setText("Offline");

        if (pendingRequestListener != null) {
            pendingRequestListener.remove();
            pendingRequestListener = null;
        }
        if (acceptedJobsListener != null) {
            acceptedJobsListener.remove();
            acceptedJobsListener = null;
        }

        acceptedJobsList.clear();
        if (MyMap != null) {
            MyMap.clear();
        }
        clearTempMarkers();
        if (activeJobPolyline != null) { // Make sure to clear active polyline
            activeJobPolyline.remove();
            activeJobPolyline = null;
        }


        pendingRequestsList.clear();
        if (pendingRequestsAdapter != null) {
            pendingRequestsAdapter.notifyDataSetChanged();
        }
        if (pendingRequestsRecyclerView != null) {
            pendingRequestsRecyclerView.setVisibility(View.GONE);
        }
        if (activeJobCard != null) {
            activeJobCard.setVisibility(View.GONE);
        }
    }

    // --- MODIFIED: clearTempMarkers ---
    // Now clears the polyline too
    private void clearTempMarkers() {
        for (Marker marker : tempPendingRequestMarkers) {
            marker.remove();
        }
        tempPendingRequestMarkers.clear();

        if (tempPendingRequestPolyline != null) {
            tempPendingRequestPolyline.remove();
            tempPendingRequestPolyline = null;
        }
    }

    private void listenForPendingRequests() {
        if (mAuth.getCurrentUser() == null) return;
        if (pendingRequestListener != null || !acceptedJobsList.isEmpty()) return;

        Query pendingRequestsQuery = db.collection("service_requests")
                .whereEqualTo("status", "pending");

        pendingRequestListener = pendingRequestsQuery.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.w(TAG, "Pending Request Listener failed.", e);
                return;
            }

            boolean listChanged = false;
            for (DocumentChange dc : snapshots.getDocumentChanges()) {
                String requestId = dc.getDocument().getId();
                Map<String, Object> requestData = dc.getDocument().getData();
                requestData.put("requestId", requestId);

                switch (dc.getType()) {
                    case ADDED:
                        if (!isRequestInList(pendingRequestsList, requestId)) {
                            pendingRequestsList.add(requestData);
                            listChanged = true;
                            Log.d(TAG, "New pending request added to list: " + requestId);
                        }
                        break;
                    case REMOVED:
                        if (removeRequestFromList(pendingRequestsList, requestId)) {
                            listChanged = true;
                            Log.d(TAG, "Pending request removed from list: " + requestId);
                        }
                        break;
                    case MODIFIED:
                        break;
                }
            }

            if (listChanged && pendingRequestsAdapter != null && pendingRequestsRecyclerView != null) {
                if (acceptedJobsList.isEmpty()) {
                    pendingRequestsAdapter.notifyDataSetChanged();
                    pendingRequestsRecyclerView.setVisibility(pendingRequestsList.isEmpty() ? View.GONE : View.VISIBLE);
                } else {
                    pendingRequestsRecyclerView.setVisibility(View.GONE);
                }
            }
        });
    }

    private boolean isRequestInList(List<Map<String, Object>> list, String requestId) {
        for (Map<String, Object> item : list) {
            if (requestId.equals(item.get("requestId"))) {
                return true;
            }
        }
        return false;
    }

    private boolean removeRequestFromList(List<Map<String, Object>> list, String requestId) {
        for (int i = list.size() - 1; i >= 0; i--) {
            if (requestId.equals(list.get(i).get("requestId"))) {
                list.remove(i);
                return true;
            }
        }
        return false;
    }

    private void listenForAcceptedJobs() {
        if (mAuth.getCurrentUser() == null) return;
        String providerId = mAuth.getCurrentUser().getUid();

        Query acceptedJobsQuery = db.collection("service_requests")
                .whereEqualTo("providerId", providerId)
                .whereEqualTo("status", "accepted");

        if (acceptedJobsListener != null) {
            acceptedJobsListener.remove();
        }

        acceptedJobsListener = acceptedJobsQuery.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.w(TAG, "Accepted Jobs Listener failed.", e);
                return;
            }

            acceptedJobsList.clear();
            for (DocumentSnapshot doc : snapshots.getDocuments()) {
                Map<String, Object> jobData = doc.getData();
                jobData.put("requestId", doc.getId());
                acceptedJobsList.add(jobData);
            }

            updateUiBasedOnJobStatus();
            drawAllAcceptedJobs();
        });

        listenForPendingRequests();
    }

    // This method *remains* from File 1.
    // It sets the *initial* straight-line distance on the card.
    // The Directions API call in updateUiWithRoute will *later* update this text.
    private void updateUiBasedOnJobStatus() {
        if (acceptedJobsList.isEmpty()) {
            activeJobCard.setVisibility(View.GONE);
            pendingRequestsRecyclerView.setVisibility(pendingRequestsList.isEmpty() ? View.GONE : View.VISIBLE);
            listenForPendingRequests();
        } else {
            activeJobCard.setVisibility(View.VISIBLE);
            pendingRequestsRecyclerView.setVisibility(View.GONE);

            if (pendingRequestListener != null) {
                pendingRequestListener.remove();
                pendingRequestListener = null;
            }

            Map<String, Object> activeJob = acceptedJobsList.get(0);
            String pickup = (String) activeJob.get(FIELD_PICKUP_ADDRESS);
            String destination = (String) activeJob.get(FIELD_DESTINATION_ADDRESS);
            String requestId = (String) activeJob.get("requestId");

            // --- THIS IS THE STRAIGHT-LINE DISTANCE LOGIC ---
            Double pickupLat = (Double) activeJob.get(FIELD_PICKUP_LAT);
            Double pickupLng = (Double) activeJob.get(FIELD_PICKUP_LNG);
            Double destLat = (Double) activeJob.get(FIELD_DEST_LAT);
            Double destLng = (Double) activeJob.get(FIELD_DEST_LNG);

            String distanceText = "N/A";
            if (pickupLat != null && pickupLng != null && destLat != null && destLng != null) {
                float[] results = new float[1];
                Location.distanceBetween(pickupLat, pickupLng, destLat, destLng, results);
                float distanceInMeters = results[0];

                if (distanceInMeters > 1000) {
                    float distanceInKm = distanceInMeters / 1000;
                    distanceText = String.format(Locale.getDefault(), "%.2f km", distanceInKm);
                } else {
                    distanceText = String.format(Locale.getDefault(), "%.0f m", distanceInMeters);
                }
            }

            // Find the new TextView
            TextView activeJobDistance = findViewById(R.id.active_job_distance_text);

            // Set the text
            activeJobPickup.setText(pickup != null ? pickup : "Not specified");
            activeJobDestination.setText(destination != null ? destination : "Not specified");
            activeJobDistance.setText(distanceText + " (Straight Line)"); // Sets initial text
            // --- END OF STRAIGHT-LINE LOGIC ---

            completeJobButton.setOnClickListener(v -> {
                completeJob(requestId);
            });
        }
    }

    private void completeJob(String requestId) {
        if (requestId == null) return;

        db.collection("service_requests").document(requestId)
                .update("status", "completed")
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Job marked complete. Awaiting status update.", Toast.LENGTH_LONG).show();
                    // Go offline will clear the UI
                    goOffline();
                    // Go back online to listen for new jobs
                    goOnline();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update job status.", Toast.LENGTH_SHORT).show();
                });
    }

    // --- MODIFIED: onItemClick ---
    // This now just gets the locations and calls the new method.
    @Override
    public void onItemClick(Map<String, Object> requestData) {
        if (MyMap == null) return;

        Double pickupLat = (Double) requestData.get(FIELD_PICKUP_LAT);
        Double pickupLng = (Double) requestData.get(FIELD_PICKUP_LNG);
        Double destLat = (Double) requestData.get(FIELD_DEST_LAT);
        Double destLng = (Double) requestData.get(FIELD_DEST_LNG);

        if (pickupLat != null && pickupLng != null && destLat != null && destLng != null) {
            LatLng pickup = new LatLng(pickupLat, pickupLng);
            LatLng destination = new LatLng(destLat, destLng);

            // Call the new method to get the route
            getDirectionsAndDrawRoute(pickup, destination, true); // true = temporary
        } else if (pickupLat != null && pickupLng != null) {
            // No destination, just zoom to pickup
            clearTempMarkers();
            LatLng pickup = new LatLng(pickupLat, pickupLng);
            Marker pickupMarker = MyMap.addMarker(new MarkerOptions()
                    .position(pickup)
                    .title("Pending Pickup")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
            tempPendingRequestMarkers.add(pickupMarker);
            MyMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pickup, 15f));
        } else {
            Log.w(TAG, "Pickup location data is null.");
        }
    }


    private void acceptJob(String requestId, Map<String, Object> requestData) {
        if (mAuth.getCurrentUser() == null) return;
        String providerId = mAuth.getCurrentUser().getUid();

        db.collection("service_requests").document(requestId)
                .update("status", "accepted", "providerId", providerId)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Job accepted! Customer notified.", Toast.LENGTH_LONG).show();
                    Log.d(TAG, "Job " + requestId + " accepted by " + providerId);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Could not accept job.", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error updating document", e);
                });
    }

    // --- MODIFIED: drawAllAcceptedJobs ---
    // This now also just gets locations and calls the new method.
    private void drawAllAcceptedJobs() {
        if (MyMap == null) {
            Log.e(TAG, "GoogleMap instance (MyMap) is null. Cannot draw jobs.");
            return;
        }

        MyMap.clear();
        clearTempMarkers();
        if (activeJobPolyline != null) activeJobPolyline.remove();

        if (acceptedJobsList.isEmpty()) {
            Toast.makeText(this, "No active jobs.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Displaying your active job.", Toast.LENGTH_SHORT).show();

        // We only care about the first (only) active job
        Map<String, Object> jobData = acceptedJobsList.get(0);

        Double pickupLat = (Double) jobData.get(FIELD_PICKUP_LAT);
        Double pickupLng = (Double) jobData.get(FIELD_PICKUP_LNG);
        Double destLat = (Double) jobData.get(FIELD_DEST_LAT);
        Double destLng = (Double) jobData.get(FIELD_DEST_LNG);

        if (pickupLat != null && pickupLng != null && destLat != null && destLng != null) {
            LatLng pickup = new LatLng(pickupLat, pickupLng);
            LatLng destination = new LatLng(destLat, destLng);

            // Call the new method to get the route
            getDirectionsAndDrawRoute(pickup, destination, false); // false = active job
        } else {
            Log.w(TAG, "Missing location data for active job.");
            // Fallback to old marker drawing if no destination
            if (pickupLat != null && pickupLng != null) {
                LatLng pickup = new LatLng(pickupLat, pickupLng);
                MyMap.addMarker(new MarkerOptions()
                        .position(pickup)
                        .title("PICKUP")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
                MyMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pickup, 15f));
            }
        }
    }

    // --- NEW METHOD: Calls the Directions API ---
    private void getDirectionsAndDrawRoute(LatLng pickup, LatLng destination, boolean isTemporary) {
        Log.d(TAG, "Getting directions from " + pickup + " to " + destination);

        // Must run on a background thread
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                DirectionsResult result = DirectionsApi.newRequest(geoApiContext)
                        .origin(new com.google.maps.model.LatLng(pickup.latitude, pickup.longitude))
                        .destination(new com.google.maps.model.LatLng(destination.latitude, destination.longitude))
                        .await();

                // Now, update the UI on the main thread
                runOnUiThread(() -> updateUiWithRoute(result, isTemporary));

            } catch (Exception e) {
                Log.e(TAG, "Directions API failed", e);
                runOnUiThread(() -> Toast.makeText(this, "Could not get directions.", Toast.LENGTH_SHORT).show());
            }
        });
    }

    // --- NEW METHOD: Updates the UI with the result ---
    private void updateUiWithRoute(DirectionsResult result, boolean isTemporary) {
        if (MyMap == null) return;

        // Clear previous temp markers/lines if this is a new temp request
        if (isTemporary) {
            clearTempMarkers();
        }

        if (result.routes != null && result.routes.length > 0) {
            DirectionsRoute route = result.routes[0];
            DirectionsLeg leg = route.legs[0];

            // Get the encoded polyline string
            String encodedPolyline = route.overviewPolyline.getEncodedPath();
            List<LatLng> decodedPath = PolyUtil.decode(encodedPolyline);

            // Add markers
            LatLng pickup = decodedPath.get(0);
            LatLng destination = decodedPath.get(decodedPath.size() - 1);

            LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
            boundsBuilder.include(pickup);
            boundsBuilder.include(destination);

            if (isTemporary) {
                // Add orange/green markers for pending job
                Marker pMarker = MyMap.addMarker(new MarkerOptions()
                        .position(pickup)
                        .title("Pending Pickup")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));

                Marker dMarker = MyMap.addMarker(new MarkerOptions()
                        .position(destination)
                        .title("Pending Destination")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

                tempPendingRequestMarkers.add(pMarker);
                tempPendingRequestMarkers.add(dMarker);

                tempPendingRequestPolyline = MyMap.addPolyline(new PolylineOptions()
                        .addAll(decodedPath)
                        .width(10)
                        .color(0x80FF5722)); // Semi-transparent orange
            } else {
                // Add red/blue markers for active job
                MyMap.addMarker(new MarkerOptions()
                        .position(pickup)
                        .title("PICKUP")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

                MyMap.addMarker(new MarkerOptions()
                        .position(destination)
                        .title("DESTINATION")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));

                activeJobPolyline = MyMap.addPolyline(new PolylineOptions()
                        .addAll(decodedPath)
                        .width(12)
                        .color(0xFFD32F2F)); // Solid red

                // Update the active job card with real distance/time
                String distance = leg.distance.humanReadable;
                String duration = leg.duration.humanReadable;

                TextView activeJobDistance = findViewById(R.id.active_job_distance_text);
                activeJobDistance.setText(distance + " (" + duration + ")");
            }

            // Zoom map
            MyMap.animateCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100));

        } else {
            Toast.makeText(this, "No routes found.", Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        goOffline();
        // --- NEW: Shut down the GeoApiContext ---
        if (geoApiContext != null) {
            geoApiContext.shutdown();
        }
    }

    // THIS IS THE FIX from file 1
    @Override
    public void onAcceptClick(String requestId, Map<String, Object> requestData) {
        // Enforce one job at a time
        if (!acceptedJobsList.isEmpty()) {
            Toast.makeText(this, "You already have an active job. Complete it first.", Toast.LENGTH_LONG).show();
            return;
        }

        Log.d(TAG, "Accept button clicked for request: " + requestId);
        acceptJob(requestId, requestData);
        clearTempMarkers();

        if (removeRequestFromList(pendingRequestsList, requestId) && pendingRequestsAdapter != null && pendingRequestsRecyclerView != null) {
            pendingRequestsAdapter.notifyDataSetChanged();
            pendingRequestsRecyclerView.setVisibility(pendingRequestsList.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }
}