package fourthyear.roadrescue;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
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
    private ListenerRegistration notificationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        setupNavbar();

        notificationsList = new ArrayList<>();

        setupClickListeners();
        setupRecyclerView();

        listenForNotifications();
    }


    private void listenForNotifications() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "No user logged in. Cannot fetch notifications.");
            return;
        }

        String userId = currentUser.getUid();

        Query requestsQuery = db.collection("service_requests")
                .whereEqualTo("customerId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING);

        notificationListener = requestsQuery.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.w(TAG, "Listen failed.", e);
                return;
            }

            notificationsList.clear();

            if (snapshots == null) {
                Log.w(TAG, "Snapshots value is null.");
                notificationsAdapter.notifyDataSetChanged();
                return;
            }

            for (QueryDocumentSnapshot doc : snapshots) {
                NotificationModel notification = doc.toObject(NotificationModel.class);

                String status = notification.getStatus();
                if (status == null) {
                    status = "unknown";
                }

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
        if (notificationListener != null) {
            notificationListener.remove();
        }
    }

    private void setupNavbar() {
        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        notificationButton.setOnClickListener(v -> {
        });

        ImageView profileButton = findViewById(R.id.profile_icon_btn);
        profileButton.setOnClickListener(v -> {
            Intent intent = new Intent(NotificationsActivity.this, ProfileActivity.class);
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

            db.collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        String userType = "Customer";
                        if (documentSnapshot.exists()) {
                            String type = documentSnapshot.getString("userType");
                            if (type != null && type.equals("Service Provider")) {
                                userType = type;
                            }
                        }

                        if (userType.equals("Service Provider")) {
                            Intent intent = new Intent(NotificationsActivity.this, ServiceProviderHomepage.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        } else {
                            Intent intent = new Intent(NotificationsActivity.this, homepage.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
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