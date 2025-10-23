package fourthyear.roadrescue;

// --- ADD THESE IMPORTS ---
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

// Other imports...
import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity {

    private static final String TAG = "NotificationsActivity";

    // --- MODIFIED ---
    private List<NotificationModel> notificationsList;
    private NotificationsAdapter notificationsAdapter;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration notificationListener; // To stop listener later

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);

        // --- NEW ---
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        notificationsList = new ArrayList<>();

        // Setup UI (your existing back button, etc. code is fine)
        setupClickListeners();
        setupRecyclerView();

        // --- MODIFIED ---
        // REMOVE initializeNotifications();
        listenForNotifications();
    }

    // --- NEW METHOD ---
    private void listenForNotifications() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "No user logged in. Cannot fetch notifications.");
            return;
        }

        String userId = currentUser.getUid();

        // This query fetches all service requests made BY the current user
        // and orders them by the most recent first.
        Query requestsQuery = db.collection("service_requests")
                .whereEqualTo("customerId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING);

        notificationListener = requestsQuery.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.w(TAG, "Listen failed.", e);
                return;
            }

            notificationsList.clear(); // Clear the old list on every update
            for (QueryDocumentSnapshot doc : snapshots) {
                // Convert the Firestore document into our NotificationModel object
                NotificationModel notification = doc.toObject(NotificationModel.class);

                // --- Generate user-friendly title and message based on status ---
                switch (notification.getStatus()) {
                    case "pending":
                        notification.setTitle("Request Sent");
                        notification.setMessage("We are searching for a nearby service provider.");
                        break;
                    case "accepted":
                        // Now you can safely use getStatus() thanks to the fix in the Canvas!
                        notification.setTitle("Request Accepted!");
                        notification.setMessage("A service provider is on their way to your location.");
                        break;
                    case "completed":
                        notification.setTitle("Service Completed");
                        notification.setMessage("Your vehicle service is complete. Please rate us!");
                        break;
                    // Add more cases for "cancelled", "en-route", etc.
                }

                notificationsList.add(notification);
            }

            // Tell the adapter that the data has changed
            notificationsAdapter.notifyDataSetChanged();
            Log.d(TAG, "Notifications list updated. Count: " + notificationsList.size());
        });
    }

    private void setupRecyclerView() {
        RecyclerView notificationsRecyclerView = findViewById(R.id.notificationsRecyclerView);
        // --- MODIFIED FIX: Passing the list directly to the adapter ---
        notificationsAdapter = new NotificationsAdapter(Collections.singletonList(notificationsList));
        notificationsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        notificationsRecyclerView.setAdapter(notificationsAdapter);
    }

    // --- DELETED ---
    // private void initializeNotifications() { ... } // REMOVE THIS ENTIRE METHOD

    private void setupClickListeners() {
        // Back button
        ImageView backButton = findViewById(R.id.back_btn);
        backButton.setOnClickListener(v -> finish());
        // ... all your other button listeners ...
    }

    // --- NEW METHOD ---
    // Stop listening when the activity is destroyed to save resources
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (notificationListener != null) {
            notificationListener.remove();
        }
    }
}
