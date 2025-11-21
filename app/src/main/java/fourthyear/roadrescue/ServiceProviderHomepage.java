package fourthyear.roadrescue;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
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

    // UI Elements
    private FloatingActionButton btnGoToMap;
    private TextView titleText;

    private final List<Map<String, Object>> requestsList = new ArrayList<>();
    private final List<Map<String, Object>> activeJobsList = new ArrayList<>();
    private final List<Map<String, Object>> pendingJobsList = new ArrayList<>();
    private List<String> driverServices = new ArrayList<>();

    private ListenerRegistration pendingListener;
    private ListenerRegistration activeJobListener;

    private FusedLocationProviderClient fusedLocationClient;
    private LatLng currentLatLng;
    private ListenerRegistration unreadListener;
    private ListenerRegistration notificationListener;
    private TextView unreadBadge;
    private TextView unreadNotificationBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_service_provider_homepage);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (currentUser == null) {
            finish();
            return;
        }

        setupUIComponents();
        setupViews();
        setupRecyclerView();
        checkLocationPermission();
        updateLocation();

        // Setup Listeners
        setupUnreadMessageListener();
        setupNotificationListener(); // Uses the fix

        fetchDriverServicesAndListen();
    }

    private void setupViews() {
        titleText = findViewById(R.id.title_text);
        requestsRecyclerView = findViewById(R.id.requests_recycler_view);

        btnGoToMap = findViewById(R.id.btn_go_to_map);
        btnGoToMap.setVisibility(View.VISIBLE);

        btnGoToMap.setOnClickListener(v -> {
            if (!activeJobsList.isEmpty()) {
                redirectToActiveJob(activeJobsList.get(0));
            } else {
                Toast.makeText(this, "You have no active jobs right now.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchDriverServicesAndListen() {
        if (currentUser == null) return;
        db.collection("users").document(currentUser.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Object servicesObj = documentSnapshot.get("servicesProvided");
                        if (servicesObj instanceof List) {
                            driverServices = (List<String>) servicesObj;
                        } else {
                            driverServices = new ArrayList<>();
                        }
                        listenForRequests();
                    }
                });
    }

    private void listenForRequests() {
        if (currentUser == null) return;
        String myUserId = currentUser.getUid();

        // 1. Listen for Active Jobs
        if (activeJobListener != null) activeJobListener.remove();
        activeJobListener = db.collection("service_requests")
                .whereEqualTo("providerId", myUserId)
                .whereEqualTo("status", "accepted")
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    activeJobsList.clear();
                    if (value != null) {
                        for (QueryDocumentSnapshot doc : value) {
                            Map<String, Object> data = new HashMap<>(doc.getData());
                            data.put("requestId", doc.getId());
                            data.put("isMyActiveJob", true);
                            activeJobsList.add(data);
                        }
                    }
                    mergeAndDisplayRequests();
                });

        // 2. Listen for Pending Jobs
        if (pendingListener != null) pendingListener.remove();
        pendingListener = db.collection("service_requests")
                .whereEqualTo("status", "pending")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    pendingJobsList.clear();
                    if (value != null) {
                        for (QueryDocumentSnapshot doc : value) {
                            Map<String, Object> data = new HashMap<>(doc.getData());
                            String requestType = (String) data.get("requestType");

                            // Filter based on driver services
                            if (requestType != null && driverServices.contains(requestType)) {
                                data.put("requestId", doc.getId());
                                pendingJobsList.add(data);
                            }
                        }
                    }
                    mergeAndDisplayRequests();
                });
    }

    private void mergeAndDisplayRequests() {
        requestsList.clear();
        requestsList.addAll(activeJobsList);
        requestsList.addAll(pendingJobsList);
        requestsAdapter.notifyDataSetChanged();

        if (!activeJobsList.isEmpty()) {
            titleText.setText("Current Job Active");
            titleText.setTextColor(Color.RED);
        } else {
            titleText.setText(String.format("Service Request (%d New)", pendingJobsList.size()));
            titleText.setTextColor(Color.WHITE);
        }
    }

    @Override
    public void onAcceptClick(String requestId, Map<String, Object> requestData) {
        if (currentUser == null) return;
        if (!activeJobsList.isEmpty()) {
            Toast.makeText(this, "Complete your current job first!", Toast.LENGTH_LONG).show();
            return;
        }

        String spUserId = currentUser.getUid();
        markRequestAsRead(requestId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "accepted");
        updates.put("providerId", spUserId);
        updates.put("acceptedTimestamp", FieldValue.serverTimestamp());
        updates.put("isRead", true);

        db.collection("service_requests").document(requestId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Request accepted!", Toast.LENGTH_SHORT).show();

                    // --- START FIX: SEND NOTIFICATION TO CUSTOMER ---
                    String customerId = (String) requestData.get("customerId");
                    if (customerId != null) {
                        Map<String, Object> notification = new HashMap<>();
                        notification.put("userId", customerId);
                        notification.put("title", "Request Accepted!");
                        notification.put("message", "A provider is on the way.");
                        notification.put("read", false); // Triggers the light!
                        notification.put("requestId", requestId);
                        notification.put("timestamp", FieldValue.serverTimestamp());
                        notification.put("type", "status_update");

                        db.collection("notifications").add(notification);
                    }
                    // --- END FIX ---

                    redirectToActiveJob(requestData);
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onItemClick(Map<String, Object> requestData) {
        String requestId = (String) requestData.get("requestId");
        markRequestAsRead(requestId);
        redirectToActiveJob(requestData);
    }

    private void redirectToActiveJob(Map<String, Object> requestData) {
        if (requestData == null) return;

        String requestId = (String) requestData.get("requestId");
        Double pickupLat = (Double) requestData.get("pickupLat");
        Double pickupLng = (Double) requestData.get("pickupLng");
        String pickupAddress = (String) requestData.get("pickupAddress");
        String requestType = (String) requestData.get("requestType");

        if (pickupLat == null || pickupLng == null || requestId == null) {
            Toast.makeText(this, "Error: Request data incomplete.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, ProviderMapActivity.class);
        intent.putExtra("REQUEST_ID", requestId);
        intent.putExtra("PICKUP_LAT", pickupLat);
        intent.putExtra("PICKUP_LNG", pickupLng);
        intent.putExtra("PICKUP_ADDRESS", pickupAddress);
        intent.putExtra("REQUEST_TYPE", requestType);
        intent.putExtra("CUSTOMER_ID", (String) requestData.get("customerId"));
        startActivity(intent);
    }

    private void markRequestAsRead(String requestId) {
        if (requestId == null) return;
        db.collection("service_requests").document(requestId)
                .update("isRead", true)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to mark read", e));
    }

    private void setupRecyclerView() {
        requestsAdapter = new PendingRequestsAdapter(requestsList, this, this);
        requestsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        requestsRecyclerView.setAdapter(requestsAdapter);
    }

    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    private void updateLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                requestsAdapter.updateProviderLocation(currentLatLng);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            updateLocation();
        }
    }

    private void setupUnreadMessageListener() {
        if (currentUser == null) return;
        String currentUserId = currentUser.getUid();
        unreadListener = db.collection("chats")
                .whereArrayContains("participantIds", currentUserId)
                .whereEqualTo("status", "active")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;
                    int totalUnread = 0;
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            Long count = doc.getLong("unreadCounts." + currentUserId);
                            if (count != null) totalUnread += count;
                        }
                    }
                    if (unreadBadge != null) unreadBadge.setVisibility(totalUnread > 0 ? View.VISIBLE : View.GONE);
                });
    }

    // ---------------------------------------------------------
    // FIXED: Notification Badge Listener
    // ---------------------------------------------------------
    private void setupNotificationListener() {
        if (currentUser == null) return;
        String currentUserId = currentUser.getUid();

        Query badgeQuery = db.collection("notifications")
                .whereEqualTo("userId", currentUserId)
                .whereEqualTo("read", false);

        if (notificationListener != null) {
            notificationListener.remove();
        }

        notificationListener = badgeQuery.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.e(TAG, "Notification listener error", e);
                return;
            }
            boolean hasUnread = snapshots != null && !snapshots.isEmpty();

            if (unreadNotificationBadge != null) {
                if (hasUnread) {
                    unreadNotificationBadge.setVisibility(View.VISIBLE);
                } else {
                    unreadNotificationBadge.setVisibility(View.GONE);
                }
            }
        });
    }

    private void setupUIComponents() {
        unreadBadge = findViewById(R.id.unread_message_badge);
        unreadNotificationBadge = findViewById(R.id.unread_notification_badge);
        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        if (notificationButton != null) {
            notificationButton.setOnClickListener(v -> startActivity(new Intent(ServiceProviderHomepage.this, NotificationsActivity.class)));
        }
        ImageView profileButton = findViewById(R.id.profile_icon_btn);
        if (profileButton != null) {
            profileButton.setOnClickListener(v -> startActivity(new Intent(ServiceProviderHomepage.this, ProfileActivity.class)));
        }
        ConstraintLayout homeLayout = findViewById(R.id.nav_home_layout);
        ImageView homeIcon = findViewById(R.id.home_icon_btn);
        TextView homeText = findViewById(R.id.home_text);
        if (homeLayout != null) {
            homeLayout.setClickable(false);
            homeLayout.setFocusable(false);
            homeLayout.setBackgroundResource(R.drawable.rounded_white_background);
        }
        if (homeIcon != null) homeIcon.setColorFilter(Color.BLACK);
        if (homeText != null) {
            homeText.setTextColor(Color.BLACK);
            homeText.setTypeface(null, Typeface.BOLD);
        }
        ImageView messageButton = findViewById(R.id.message_icon_btn);
        if (messageButton != null) {
            messageButton.setOnClickListener(v -> startActivity(new Intent(ServiceProviderHomepage.this, ChatInboxActivity.class)));
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pendingListener != null) pendingListener.remove();
        if (activeJobListener != null) activeJobListener.remove();
        if (unreadListener != null) unreadListener.remove();
        if (notificationListener != null) notificationListener.remove();
    }
}