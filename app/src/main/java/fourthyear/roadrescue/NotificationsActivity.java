package fourthyear.roadrescue;

// --- ADD THESE IMPORTS ---
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

// Other imports...
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity {

    private static final String TAG = "NotificationsActivity";

    // FIX 1: Change the list type to List<Object> to match the adapter's constructor
    private List<Object> notificationsList;
    private NotificationsAdapter notificationsAdapter;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration notificationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);
        setupUIComponents();

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

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

            // OPTIONAL: Add a header before adding notifications, e.g., "Recent Activity"
            // notificationsList.add("Recent Activity");

            for (QueryDocumentSnapshot doc : snapshots) {

                NotificationModel notification = doc.toObject(NotificationModel.class);

                // Ensure the status field is not null before checking,
                // though toObject should initialize it to null if absent
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
                        // Handle other statuses or unknown status
                        notification.setTitle("Status Update");
                        notification.setMessage("The status of your service request is: " + status);
                        break;
                }

                // Add the NotificationModel object to the List<Object>
                notificationsList.add(notification);
            }

            notificationsAdapter.notifyDataSetChanged();
            Log.d(TAG, "Notifications list updated. Count: " + notificationsList.size());
        });
    }

    private void setupRecyclerView() {
        RecyclerView notificationsRecyclerView = findViewById(R.id.notificationsRecyclerView);

        // FIX 3: Pass the now-correctly-typed notificationsList to the adapter
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

    private void setupUIComponents() {
        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        if (notificationButton != null) {
            notificationButton.setOnClickListener(v -> {
                Intent intent = new Intent(NotificationsActivity.this, NotificationsActivity.class);
                startActivity(intent);
            });
        }

        ImageView profileButton = findViewById(R.id.profile_icon_btn);
        if (profileButton != null) {
            profileButton.setOnClickListener(v -> {
                Intent intent = new Intent(NotificationsActivity.this, homepage.class);
                startActivity(intent);
            });
        }

        ImageView homeButton = findViewById(R.id.home_icon_btn);
        if (homeButton != null) {
            homeButton.setOnClickListener(v -> {
                Intent intent = new Intent(NotificationsActivity.this, homepage.class);
                startActivity(intent);
            });
        }

        ImageView messageButton = findViewById(R.id.message_icon_btn);
        if (messageButton != null) {
            messageButton.setOnClickListener(v -> {
                Intent intent = new Intent(NotificationsActivity.this, ChatInboxActivity.class);
                startActivity(intent);
            });
        }
    }
}