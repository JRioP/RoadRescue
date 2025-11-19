package fourthyear.roadrescue;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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

        setupClickListeners();
        setupRecyclerView();

        // Setup Badges and Navbar
        setupNavbar();
        setupUnreadMessageListener();
        setupNotificationBadgeListener();

        listenForNotifications();
    }

    // --- Listener for Unread Chat Messages Badge ---
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
                            if (count != null) {
                                totalUnread += count;
                            }
                        }
                    }

                    if (unreadBadge != null) {
                        unreadBadge.setVisibility(totalUnread > 0 ? View.VISIBLE : View.GONE);
                    }
                });
    }

    private void setupNotificationBadgeListener() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;
        String currentUserId = user.getUid();

        badgeNotificationListener = db.collection("notifications")
                .whereEqualTo("userId", currentUserId)
                .whereEqualTo("read", false)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;

                    boolean hasUnread = snapshots != null && !snapshots.isEmpty();
                    if (unreadNotificationBadge != null) {
                        unreadNotificationBadge.setVisibility(hasUnread ? View.VISIBLE : View.GONE);
                    }
                });
    }

    // --- UPDATED: Dynamic Notification Logic ---
    private void listenForNotifications() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "No user logged in. Cannot fetch notifications.");
            return;
        }

        String userId = currentUser.getUid();

        // 1. Check User Type to determine Query
        db.collection("users").document(userId).get().addOnSuccessListener(userDoc -> {
            if (userDoc.exists()) {
                String type = userDoc.getString("userType");
                boolean isProvider = type != null && (type.trim().equalsIgnoreCase("Service Provider") || type.trim().equalsIgnoreCase("driver"));

                Query requestsQuery;

                if (isProvider) {
                    // If Provider: Look for requests where I am the providerId
                    requestsQuery = db.collection("service_requests")
                            .whereEqualTo("providerId", userId)
                            .orderBy("timestamp", Query.Direction.DESCENDING);
                } else {
                    // If Customer: Look for requests where I am the customerId
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

            if (snapshots == null) {
                notificationsAdapter.notifyDataSetChanged();
                return;
            }

            for (QueryDocumentSnapshot doc : snapshots) {
                NotificationModel notification = doc.toObject(NotificationModel.class);
                String status = notification.getStatus();
                if (status == null) status = "unknown";

                if (isProvider) {
                    switch (status) {
                        case "pending":
                            notification.setTitle("New Job Opportunity");
                            notification.setMessage("A customer is waiting for help.");
                            break;
                        case "accepted":
                            notification.setTitle("Job Active");
                            notification.setMessage("You have accepted this request. Go to map to navigate.");
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
                            notification.setMessage("A service provider is on their way to your location.");
                            break;
                        case "completed":
                            notification.setTitle("Service Completed");
                            notification.setMessage("Your vehicle service is complete. Please rate us!");
                            break;
                        default:
                            notification.setTitle("Status Update");
                            notification.setMessage("The status of your service request is: " + status);
                            break;
                    }
                }
                notificationsList.add(notification);
            }

            notificationsAdapter.notifyDataSetChanged();
            Log.d(TAG, "Notifications list updated. Count: " + notificationsList.size());
        });
    }

    private void setupRecyclerView() {
        RecyclerView notificationsRecyclerView = findViewById(R.id.notificationsRecyclerView);
        notificationsAdapter = new NotificationsAdapter(notificationsList);
        notificationsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        notificationsRecyclerView.setAdapter(notificationsAdapter);
    }


    private void setupClickListeners() {
        ImageView backButton = findViewById(R.id.back_btn);
        backButton.setOnClickListener(v -> finish());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mainNotificationListener != null) mainNotificationListener.remove();
        if (unreadListener != null) unreadListener.remove();
        if (badgeNotificationListener != null) badgeNotificationListener.remove();
    }

    private void setupNavbar() {
        // Initialize Badges
        unreadBadge = findViewById(R.id.unread_message_badge);
        unreadNotificationBadge = findViewById(R.id.unread_notification_badge);

        // --- Notification Button (Active State) ---
        ConstraintLayout notificationLayout = findViewById(R.id.nav_notification_layout);
        ImageView notificationIcon = findViewById(R.id.notification_icon_btn);
        TextView notificationText = findViewById(R.id.notification_text);

        if (notificationLayout != null) {
            notificationLayout.setClickable(false);
            notificationLayout.setFocusable(false);
            notificationLayout.setBackgroundResource(R.drawable.rounded_white_background);
        }

        if (notificationIcon != null) {
            notificationIcon.setColorFilter(Color.BLACK);
        }

        if (notificationText != null) {
            notificationText.setTextColor(Color.BLACK);
            notificationText.setTypeface(null, Typeface.BOLD);
        }
        // ------------------------------------------

        ImageView profileButton = findViewById(R.id.profile_icon_btn);
        profileButton.setOnClickListener(v -> {
            Intent intent = new Intent(NotificationsActivity.this, ProfileActivity.class);
            startActivity(intent);
        });

        // --- HOME BUTTON FIX ---
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

            db.collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        String userType = "Customer"; // Default
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
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to get userType, defaulting to customer homepage", e);
                        Intent intent = new Intent(NotificationsActivity.this, homepage.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    });
        });

        ImageView messageButton = findViewById(R.id.message_icon_btn);
        messageButton.setOnClickListener(v -> {
            Intent intent = new Intent(NotificationsActivity.this, ChatInboxActivity.class);
            startActivity(intent);
        });
    }
}