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

import java.util.ArrayList;
import java.util.List;

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

        // Initialize RecyclerView with the Click Listener
        setupRecyclerView();

        // Badge Listeners
        setupUnreadMessageListener();
        setupNotificationBadgeListener();

        // Load the actual list data
        listenForNotifications();
    }

    // ---------------------------------------------------------
    // NEW: Mark as Read Logic (Fixes the persistent red dot)
    // ---------------------------------------------------------
    public void onNotificationClicked(NotificationModel notification) {
        if (notification.getRequestId() != null) {
            // Update Firestore to set isRead = true
            // This triggers the listener to update the badge automatically
            db.collection("service_requests").document(notification.getRequestId())
                    .update("isRead", true)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "Notification marked as read");
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to mark as read", e));
        }
    }

    private void setupRecyclerView() {
        RecyclerView notificationsRecyclerView = findViewById(R.id.notificationsRecyclerView);

        // Pass 'this::onNotificationClicked' so the adapter knows what to do on click
        notificationsAdapter = new NotificationsAdapter(notificationsList, this::onNotificationClicked);

        notificationsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        notificationsRecyclerView.setAdapter(notificationsAdapter);
    }

    // ---------------------------------------------------------
    // BADGE LISTENERS
    // ---------------------------------------------------------

    // 1. Chat Badge (Unread Messages)
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
                    if (unreadBadge != null) {
                        unreadBadge.setVisibility(totalUnread > 0 ? View.VISIBLE : View.GONE);
                    }
                });
    }

    // 2. Notification Badge (Unread Service Requests)
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
                    // Provider: Count requests assigned to me that are UNREAD
                    badgeQuery = db.collection("service_requests")
                            .whereEqualTo("providerId", currentUserId)
                            .whereEqualTo("isRead", false);
                } else {
                    // Customer: Count my requests that are UNREAD
                    badgeQuery = db.collection("service_requests")
                            .whereEqualTo("customerId", currentUserId)
                            .whereEqualTo("isRead", false);
                }

                if (badgeNotificationListener != null) {
                    badgeNotificationListener.remove();
                }

                badgeNotificationListener = badgeQuery.addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;

                    // Show badge if there are any documents
                    boolean hasUnread = snapshots != null && !snapshots.isEmpty();
                    if (unreadNotificationBadge != null) {
                        unreadNotificationBadge.setVisibility(hasUnread ? View.VISIBLE : View.GONE);
                    }
                });
            }
        });
    }

    // ---------------------------------------------------------
    // POPULATE LIST
    // ---------------------------------------------------------

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
            if (e != null) {
                Log.w(TAG, "Listen failed.", e);
                return;
            }

            notificationsList.clear();

            if (snapshots != null) {
                for (QueryDocumentSnapshot doc : snapshots) {
                    NotificationModel notification = doc.toObject(NotificationModel.class);

                    // Save ID for the click listener
                    notification.setRequestId(doc.getId());

                    String status = notification.getStatus();
                    if (status == null) status = "unknown";

                    // Customize Titles based on status
                    if (isProvider) {
                        switch (status) {
                            case "pending":
                                notification.setTitle("New Job Opportunity");
                                notification.setMessage("A customer is waiting for help.");
                                break;
                            case "accepted":
                                notification.setTitle("Job Active");
                                notification.setMessage("You have accepted this request.");
                                break;
                            case "completed":
                                notification.setTitle("Job Completed");
                                notification.setMessage("You have successfully finished this job.");
                                break;
                            default:
                                notification.setTitle("Job Update");
                                notification.setMessage("Status: " + status);
                                break;
                        }
                    } else {
                        switch (status) {
                            case "pending":
                                notification.setTitle("Request Sent");
                                notification.setMessage("We are searching for a nearby service provider.");
                                break;
                            case "accepted":
                                notification.setTitle("Request Accepted!");
                                notification.setMessage("A service provider is on their way.");
                                break;
                            case "completed":
                                notification.setTitle("Service Completed");
                                notification.setMessage("Your vehicle service is complete.");
                                break;
                            default:
                                notification.setTitle("Status Update");
                                notification.setMessage("Status: " + status);
                                break;
                        }
                    }
                    notificationsList.add(notification);
                }
            }
            notificationsAdapter.notifyDataSetChanged();
        });
    }

    // ---------------------------------------------------------
    // NAV BAR & UI SETUP
    // ---------------------------------------------------------

    private void setupClickListeners() {
        ImageView backButton = findViewById(R.id.back_btn);
        backButton.setOnClickListener(v -> finish());
    }

    private void setupNavbar() {
        unreadBadge = findViewById(R.id.unread_message_badge);
        unreadNotificationBadge = findViewById(R.id.unread_notification_badge);

        // Notification Button (Active State)
        ConstraintLayout notificationLayout = findViewById(R.id.nav_notification_layout);
        ImageView notificationIcon = findViewById(R.id.notification_icon_btn);
        TextView notificationText = findViewById(R.id.notification_text);

        if (notificationLayout != null) {
            notificationLayout.setClickable(false);
            notificationLayout.setFocusable(false);
            notificationLayout.setBackgroundResource(R.drawable.rounded_white_background);
        }
        if (notificationIcon != null) notificationIcon.setColorFilter(Color.BLACK);
        if (notificationText != null) {
            notificationText.setTextColor(Color.BLACK);
            notificationText.setTypeface(null, Typeface.BOLD);
        }

        ImageView profileButton = findViewById(R.id.profile_icon_btn);
        profileButton.setOnClickListener(v -> startActivity(new Intent(NotificationsActivity.this, ProfileActivity.class)));

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
            db.collection("users").document(user.getUid()).get().addOnSuccessListener(documentSnapshot -> {
                String userType = "Customer";
                if (documentSnapshot.exists()) {
                    String type = documentSnapshot.getString("userType");
                    if (type != null && (type.trim().equalsIgnoreCase("Service Provider") || type.trim().equalsIgnoreCase("driver"))) {
                        userType = "Service Provider";
                    }
                }
                Intent intent;
                if (userType.equals("Service Provider")) {
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
        messageButton.setOnClickListener(v -> startActivity(new Intent(NotificationsActivity.this, ChatInboxActivity.class)));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mainNotificationListener != null) mainNotificationListener.remove();
        if (unreadListener != null) unreadListener.remove();
        if (badgeNotificationListener != null) badgeNotificationListener.remove();
    }
}