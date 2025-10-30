package fourthyear.roadrescue;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.content.Intent;
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
import java.util.ArrayList;
import java.util.List;
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

    private void clearTempMarkers() {
        for (Marker marker : tempPendingRequestMarkers) {
            marker.remove();
        }
        tempPendingRequestMarkers.clear();
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
                        if(removeRequestFromList(pendingRequestsList, requestId)) {
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

            activeJobPickup.setText(pickup != null ? pickup : "Not specified");
            activeJobDestination.setText(destination != null ? destination : "Not specified");

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
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update job status.", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onItemClick(Map<String, Object> requestData) {
        if (MyMap == null) {
            Log.w(TAG, "Map is not ready, cannot show pending request.");
            return;
        }

        Double pickupLat = (Double) requestData.get(FIELD_PICKUP_LAT);
        Double pickupLng = (Double) requestData.get(FIELD_PICKUP_LNG);
        Double destLat = (Double) requestData.get(FIELD_DEST_LAT);
        Double destLng = (Double) requestData.get(FIELD_DEST_LNG);
        String pickupName = (String) requestData.get(FIELD_PICKUP_ADDRESS);
        String destinationName = (String) requestData.get(FIELD_DESTINATION_ADDRESS);
        String requestId = (String) requestData.get("requestId");

        String pickupTitle = (pickupName != null && !pickupName.isEmpty())
                ? pickupName
                : "Pending Pickup";
        String destinationTitle = (destinationName != null && !destinationName.isEmpty())
                ? destinationName
                : "Pending Destination";

        if (pickupLat != null && pickupLng != null) {
            LatLng pickupLocation = new LatLng(pickupLat, pickupLng);
            Log.d(TAG, "Showing pending request on map. Pickup: " + pickupLocation);

            clearTempMarkers();

            Marker pickupMarker = MyMap.addMarker(new MarkerOptions()
                    .position(pickupLocation)
                    .title(pickupTitle)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
            tempPendingRequestMarkers.add(pickupMarker);

            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            builder.include(pickupLocation);

            if (destLat != null && destLng != null) {
                LatLng destinationLocation = new LatLng(destLat, destLng);
                Log.d(TAG, "Destination: " + destinationLocation);

                Marker destinationMarker = MyMap.addMarker(new MarkerOptions()
                        .position(destinationLocation)
                        .title(destinationTitle)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
                tempPendingRequestMarkers.add(destinationMarker);
                builder.include(destinationLocation);
            }

            LatLngBounds bounds = builder.build();
            MyMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));

        } else {
            Log.w(TAG, "Pickup location data is null, cannot move camera or add marker.");
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

    private void drawAllAcceptedJobs() {
        if (MyMap == null) {
            Log.e(TAG, "GoogleMap instance (MyMap) is null. Cannot draw jobs.");
            return;
        }

        MyMap.clear();
        clearTempMarkers();

        if (acceptedJobsList.isEmpty()) {
            Toast.makeText(this, "No active jobs.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Displaying your active job.", Toast.LENGTH_SHORT).show();

        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        boolean hasPoints = false;

        for (Map<String, Object> jobData : acceptedJobsList) {
            Double pickupLat = (Double) jobData.get(FIELD_PICKUP_LAT);
            Double pickupLng = (Double) jobData.get(FIELD_PICKUP_LNG);
            Double destLat = (Double) jobData.get(FIELD_DEST_LAT);
            Double destLng = (Double) jobData.get(FIELD_DEST_LNG);

            String pickupName = (String) jobData.get(FIELD_PICKUP_ADDRESS);
            String destinationName = (String) jobData.get(FIELD_DESTINATION_ADDRESS);
            String requestId = (String) jobData.get("requestId");

            if (pickupLat == null || pickupLng == null || requestId == null) continue;

            String pickupTitle = (pickupName != null && !pickupName.isEmpty())
                    ? pickupName
                    : "PICKUP: Job " + requestId.substring(0, Math.min(requestId.length(), 4)) + "...";

            LatLng pickupLocation = new LatLng(pickupLat, pickupLng);

            MyMap.addMarker(new MarkerOptions()
                    .position(pickupLocation)
                    .title(pickupTitle)
                    .snippet("Status: Accepted")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

            boundsBuilder.include(pickupLocation);
            hasPoints = true;

            if (destLat != null && destLng != null) {
                String destinationTitle = (destinationName != null && !destinationName.isEmpty())
                        ? destinationName
                        : "DESTINATION: Job " + requestId.substring(0, Math.min(requestId.length(), 4)) + "...";

                LatLng destinationLocation = new LatLng(destLat, destLng);
                MyMap.addMarker(new MarkerOptions()
                        .position(destinationLocation)
                        .title(destinationTitle)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));

                boundsBuilder.include(destinationLocation);
            }
        }

        if (hasPoints) {
            LatLngBounds bounds = boundsBuilder.build();
            MyMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        goOffline();
    }

    // THIS IS THE FIX
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

        if(removeRequestFromList(pendingRequestsList, requestId) && pendingRequestsAdapter != null && pendingRequestsRecyclerView != null) {
            pendingRequestsAdapter.notifyDataSetChanged();
            pendingRequestsRecyclerView.setVisibility(pendingRequestsList.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }
}