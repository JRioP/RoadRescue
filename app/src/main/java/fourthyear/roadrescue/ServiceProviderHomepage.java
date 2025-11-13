package fourthyear.roadrescue;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServiceProviderHomepage extends AppCompatActivity implements PendingRequestsAdapter.OnAcceptClickListener, PendingRequestsAdapter.OnItemClickListener {

    private static final String TAG = "SPHomepageActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private RecyclerView requestsRecyclerView;
    private PendingRequestsAdapter requestsAdapter;

    private final List<Map<String, Object>> requestsList = new ArrayList<>();
    private ListenerRegistration requestsListener;
    private TextView titleText;

    private FusedLocationProviderClient fusedLocationClient;
    private LatLng currentLatLng;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_service_provider_homepage);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (currentUser == null) {
            Toast.makeText(this, "Please log in as a Service Provider.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setupUIComponents();
        setupViews();
        setupNavigationListeners();
        setupRecyclerView();
        checkLocationPermission();
        updateLocation();

        checkProviderForActiveJob();
    }

    private void checkProviderForActiveJob() {
        if (currentUser == null) return;

        db.collection("service_requests")
                .whereEqualTo("providerId", currentUser.getUid())
                .whereEqualTo("status", "accepted") // Look for jobs they've already accepted
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        Log.d(TAG, "Provider has an active job. Redirecting to map.");
                        DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                        Map<String, Object> requestData = new HashMap<>(doc.getData());
                        requestData.put("requestId", doc.getId());

                        redirectToActiveJob(requestData);
                    } else {
                        Log.d(TAG, "No active job found. Loading pending requests.");
                        loadPendingRequests();
                    }
                });
    }

    private void setupViews() {
        titleText = findViewById(R.id.title_text);
        requestsRecyclerView = findViewById(R.id.requests_recycler_view);
    }

    private void setupNavigationListeners() {
        // ... (your existing navigation code)
        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        ImageView profileButton = findViewById(R.id.profile_icon_btn);
        ImageView homeButton = findViewById(R.id.home_icon_btn);
        ImageView messageButton = findViewById(R.id.message_icon_btn);

        notificationButton.setOnClickListener(v ->
                startActivity(new Intent(this, NotificationsActivity.class))
        );

        profileButton.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class))
        );

        homeButton.setOnClickListener(v ->
                Toast.makeText(this, "You’re already on the homepage.", Toast.LENGTH_SHORT).show()
        );

        messageButton.setOnClickListener(v ->
                startActivity(new Intent(this, ChatInboxActivity.class))
        );
    }

    private void setupRecyclerView() {
        requestsAdapter = new PendingRequestsAdapter(requestsList, this, this);
        requestsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        requestsRecyclerView.setAdapter(requestsAdapter);
    }

    private void loadPendingRequests() {
        Log.d(TAG, "Loading pending requests...");
        if (requestsListener != null) requestsListener.remove();

        requestsListener = db.collection("service_requests")
                .whereEqualTo("status", "pending")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Listen failed for requests: ", error);
                        Toast.makeText(this, "Failed to load requests.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    requestsList.clear();
                    if (value != null) {
                        for (QueryDocumentSnapshot doc : value) {
                            Map<String, Object> requestData = new HashMap<>(doc.getData());
                            requestData.put("requestId", doc.getId());
                            requestsList.add(requestData);
                        }
                    }

                    requestsAdapter.notifyDataSetChanged();
                    updateTitle(requestsList.size());

                    if (requestsList.isEmpty()) {
                        Log.d(TAG, "No pending requests found.");
                    }
                });
    }


    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    private void updateLocation() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                requestsAdapter.updateProviderLocation(currentLatLng);
            } else {
                Toast.makeText(this, "Could not get current location for distance calculation.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            updateLocation();
        } else {
            Toast.makeText(this, "Location denied. Distances may be inaccurate.", Toast.LENGTH_LONG).show();
        }
    }


    @Override
    public void onAcceptClick(String requestId, Map<String, Object> requestData) {
        if (currentUser == null) return;
        String spUserId = currentUser.getUid();

        if (requestsListener != null) {
            requestsListener.remove();
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "accepted");
        updates.put("providerId", spUserId);
        updates.put("acceptedTimestamp", FieldValue.serverTimestamp());

        db.collection("service_requests").document(requestId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Request accepted! Loading map...", Toast.LENGTH_LONG).show();
                    redirectToActiveJob(requestData);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to accept request: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    loadPendingRequests();
                });
    }

    @Override
    public void onItemClick(Map<String, Object> requestData) {
        redirectToActiveJob(requestData);
    }

    private void redirectToActiveJob(Map<String, Object> requestData) {
        String requestId = (String) requestData.get("requestId");
        Double pickupLat = (Double) requestData.get("pickupLat");
        Double pickupLng = (Double) requestData.get("pickupLng");
        String pickupAddress = (String) requestData.get("pickupAddress");
        String requestType = (String) requestData.get("requestType");

        if (pickupLat == null || pickupLng == null || requestId == null) {
            Toast.makeText(this, "Error: Request location data is incomplete.", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Opening map for request ID: " + requestId);

        Intent intent = new Intent(this, ProviderMapActivity.class);
        intent.putExtra("REQUEST_ID", requestId);
        intent.putExtra("PICKUP_LAT", pickupLat);
        intent.putExtra("PICKUP_LNG", pickupLng);
        intent.putExtra("PICKUP_ADDRESS", pickupAddress);
        intent.putExtra("REQUEST_TYPE", requestType);

        intent.putExtra("CUSTOMER_ID", (String) requestData.get("customerId"));

        startActivity(intent);

        if (requestsList.size() > 0) {
            finish();
        }
    }

    private void updateTitle(int count) {
        if (titleText != null) {
            titleText.setText(String.format("Service Provider (%d New)", count));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (requestsListener != null) {
            requestsListener.remove();
        }
    }

    private void setupUIComponents() {

        //Navigation buttons
        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        if (notificationButton != null) {
            notificationButton.setOnClickListener(v -> {
                Intent intent = new Intent(ServiceProviderHomepage.this, NotificationsActivity.class);
                startActivity(intent);
            });
        }

        ImageView profileButton = findViewById(R.id.profile_icon_btn);
        if (profileButton != null) {
            profileButton.setOnClickListener(v -> {
                Intent intent = new Intent(ServiceProviderHomepage.this, ProfileActivity.class);
                startActivity(intent);
            });
        }

        ImageView homeButton = findViewById(R.id.home_icon_btn);
        if (homeButton != null) {
            homeButton.setOnClickListener(v -> {
                Intent intent = new Intent(ServiceProviderHomepage.this, homepage.class);
                startActivity(intent);
            });
        }

        ImageView messageButton = findViewById(R.id.message_icon_btn);
        if (messageButton != null) {
            messageButton.setOnClickListener(v -> {
                Intent intent = new Intent(ServiceProviderHomepage.this, ChatInboxActivity.class);
                startActivity(intent);
            });
        }
    }
}