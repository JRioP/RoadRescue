package fourthyear.roadrescue;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NotificationsActivity extends AppCompatActivity {

    private static final String TAG = "NotificationsActivity";

    private List<NotificationModel> notificationsList;
    private NotificationsAdapter notificationsAdapter;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // Listeners
    private ListenerRegistration mainNotificationListener;
    private ListenerRegistration unreadListener;
    private ListenerRegistration badgeNotificationListener;

    // Badges
    private TextView unreadBadge;
    private TextView unreadNotificationBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        notificationsList = new ArrayList<>();

        setupNavbar();
        setupClickListeners();
        setupRecyclerView();
        setupUnreadMessageListener();
        setupNotificationBadgeListener();
        markNotificationsAsRead();
        listenForNotifications();
    }
    public void onNotificationClicked(NotificationModel notification) {
        if (notification.getRequestId() != null) {
            String requestId = notification.getRequestId();

            // 1. Mark as Read
            db.collection("service_requests").document(requestId)
                    .update("isRead", true)
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to mark read", e));

            // 2. Fetch the Request Details to decide where to go
            db.collection("service_requests").document(requestId).get()
                    .addOnSuccessListener(document -> {
                        if (document.exists()) {
                            String status = document.getString("status");

                            if ("completed".equals(status)) {
                                // --- CASE 1: JOB IS DONE -> GO TO RECEIPT ---
                                Intent intent = new Intent(NotificationsActivity.this, PaymentReceiptActivity.class);
                                intent.putExtra("REFERENCE_ID", document.getId());

                                Double amount = document.getDouble("amount");
                                intent.putExtra("AMOUNT_PAID", String.format(Locale.getDefault(), "PHP %.2f", amount != null ? amount : 0.0));

                                com.google.firebase.Timestamp ts = document.getTimestamp("timestamp");
                                if (ts != null) {
                                    intent.putExtra("PAYMENT_DATE", new SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault()).format(ts.toDate()));
                                }

                                intent.putExtra("PAYMENT_METHOD", document.getString("paymentMethod"));
                                intent.putExtra("REQUEST_TYPE", document.getString("requestType"));
                                intent.putExtra("PICKUP_ADDRESS", document.getString("pickupAddress"));
                                intent.putExtra("DESTINATION_ADDRESS", document.getString("destinationAddress"));

                                startActivity(intent);
                            } else {
                                // --- CASE 2: JOB IS ACTIVE -> GO TO MAP ---
                                // Note: Check user type to know which map to open (CustomerMap or ProviderMap)
                                checkUserTypeAndRedirectToMap(requestId);
                            }
                        } else {
                            Toast.makeText(this, "Request details not found.", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void checkUserTypeAndRedirectToMap(String requestId) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid()).get().addOnSuccessListener(userDoc -> {
            if (userDoc.exists()) {
                String type = userDoc.getString("userType");
                boolean isProvider = type != null && (type.trim().equalsIgnoreCase("Service Provider") || type.trim().equalsIgnoreCase("driver"));

                if (isProvider) {
                    Intent intent = new Intent(this, ServiceProviderHomepage.class);
                    startActivity(intent);
                } else {
                    Intent intent = new Intent(this, MapActivity.class);
                    startActivity(intent);
                }
            }
        });
    }
    private void setupRecyclerView() {
        RecyclerView notificationsRecyclerView = findViewById(R.id.notificationsRecyclerView);
        notificationsAdapter = new NotificationsAdapter(notificationsList, this::onNotificationClicked);
        notificationsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        notificationsRecyclerView.setAdapter(notificationsAdapter);
    }

    private void setupNotificationBadgeListener() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;
        String currentUserId = user.getUid();

        db.collection("users").document(currentUserId).get().addOnSuccessListener(userDoc -> {
            if (userDoc.exists()) {
                String type = userDoc.getString("userType");
                boolean isProvider = type != null && (type.trim().equalsIgnoreCase("Service Provider") || type.trim().equalsIgnoreCase("driver"));

                Query badgeQuery;
                if (isProvider) {
                    badgeQuery = db.collection("service_requests")
                            .whereEqualTo("providerId", currentUserId)
                            .whereEqualTo("isRead", false);
                } else {
                    badgeQuery = db.collection("service_requests")
                            .whereEqualTo("customerId", currentUserId)
                            .whereEqualTo("isRead", false);
                }

                if (badgeNotificationListener != null) badgeNotificationListener.remove();
                badgeNotificationListener = badgeQuery.addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;
                    boolean hasUnread = snapshots != null && !snapshots.isEmpty();
                    if (unreadNotificationBadge != null) {
                        unreadNotificationBadge.setVisibility(hasUnread ? View.VISIBLE : View.GONE);
                    }
                });
            }
        });
    }

    private void listenForNotifications() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;
        String userId = currentUser.getUid();

        db.collection("users").document(userId).get().addOnSuccessListener(userDoc -> {
            if (userDoc.exists()) {
                String type = userDoc.getString("userType");
                boolean isProvider = type != null && (type.trim().equalsIgnoreCase("Service Provider") || type.trim().equalsIgnoreCase("driver"));

                Query requestsQuery;
                if (isProvider) {
                    requestsQuery = db.collection("service_requests")
                            .whereEqualTo("providerId", userId)
                            .orderBy("timestamp", Query.Direction.DESCENDING);
                } else {
                    requestsQuery = db.collection("service_requests")
                            .whereEqualTo("customerId", userId)
                            .orderBy("timestamp", Query.Direction.DESCENDING);
                }
                startFirestoreListener(requestsQuery, isProvider);
            }
        });
    }

    private void startFirestoreListener(Query query, boolean isProvider) {
        if (mainNotificationListener != null) mainNotificationListener.remove();

        mainNotificationListener = query.addSnapshotListener((snapshots, e) -> {
            if (e != null) return;
            notificationsList.clear();
            if (snapshots != null) {
                for (QueryDocumentSnapshot doc : snapshots) {
                    NotificationModel notification = doc.toObject(NotificationModel.class);
                    notification.setRequestId(doc.getId());

                    String status = notification.getStatus();
                    if (status == null) status = "unknown";

                    if (isProvider) {
                        switch (status) {
                            case "pending": notification.setTitle("New Job Opportunity"); notification.setMessage("A customer is waiting."); break;
                            case "accepted": notification.setTitle("Job Active"); notification.setMessage("You accepted this request."); break;
                            case "completed": notification.setTitle("Job Completed"); notification.setMessage("Job finished successfully."); break;
                            default: notification.setTitle("Job Update"); notification.setMessage("Status: " + status); break;
                        }
                    } else {
                        switch (status) {
                            case "pending": notification.setTitle("Request Sent"); notification.setMessage("Searching for provider..."); break;
                            case "accepted": notification.setTitle("Request Accepted!"); notification.setMessage("Provider is on the way."); break;
                            case "completed": notification.setTitle("Service Completed"); notification.setMessage("Tap here to view your receipt."); break;
                            default: notification.setTitle("Status Update"); notification.setMessage("Status: " + status); break;
                        }
                    }
                    notificationsList.add(notification);
                }
            }
            notificationsAdapter.notifyDataSetChanged();
        });
    }

    private void setupClickListeners() {
        ImageView backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
    }
    private void setupUnreadMessageListener() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;
        String currentUserId = user.getUid();

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

    private void setupNavbar() {
        unreadBadge = findViewById(R.id.unread_message_badge);
        unreadNotificationBadge = findViewById(R.id.unread_notification_badge);
        ConstraintLayout notificationLayout = findViewById(R.id.nav_notification_layout);
        if (notificationLayout != null) {
            notificationLayout.setClickable(false);
            notificationLayout.setFocusable(false);
            notificationLayout.setBackgroundResource(R.drawable.rounded_white_background);
        }
        ImageView notificationIcon = findViewById(R.id.notification_icon_btn);
        if (notificationIcon != null) {
            notificationIcon.setColorFilter(Color.BLACK);
        }
        TextView notificationText = findViewById(R.id.notification_text);
        if (notificationText != null) {
            notificationText.setTextColor(Color.BLACK);
            notificationText.setTypeface(null, Typeface.BOLD);
        }
        ImageView profileButton = findViewById(R.id.profile_icon_btn);
        profileButton.setOnClickListener(v -> {
            Intent intent = new Intent(NotificationsActivity.this, ProfileActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });
        ImageView homeButton = findViewById(R.id.home_icon_btn);
        homeButton.setOnClickListener(v -> {
            FirebaseUser user = mAuth.getCurrentUser();
            if (user == null) {
                Intent intent = new Intent(NotificationsActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
                return;
            }
            db.collection("users").document(user.getUid()).get().addOnSuccessListener(doc -> {
                Intent intent;
                String type = doc.getString("userType");
                if (type != null && (type.equalsIgnoreCase("Service Provider") || type.equalsIgnoreCase("driver"))) {
                    intent = new Intent(NotificationsActivity.this, ServiceProviderHomepage.class);
                } else {
                    intent = new Intent(NotificationsActivity.this, homepage.class);
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            });
        });

        ImageView messageButton = findViewById(R.id.message_icon_btn);
        messageButton.setOnClickListener(v -> {
            Intent intent = new Intent(NotificationsActivity.this, ChatInboxActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });
    }

    private void markNotificationsAsRead() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("notifications")
                .whereEqualTo("userId", currentUser.getUid())
                .whereEqualTo("read", false)
                .get()
                .addOnSuccessListener(snapshots -> {
                    for (DocumentSnapshot doc : snapshots) {
                        doc.getReference().update("read", true);
                    }
                })
                .addOnFailureListener(e -> Log.e("Notifications", "Error marking as read", e));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mainNotificationListener != null) mainNotificationListener.remove();
        if (unreadListener != null) unreadListener.remove();
        if (badgeNotificationListener != null) badgeNotificationListener.remove();
    }
}