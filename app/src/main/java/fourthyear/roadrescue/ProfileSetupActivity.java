package fourthyear.roadrescue;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class ProfileSetupActivity extends AppCompatActivity {

    private static final String TAG = "ProfileSetupActivity";
    private EditText editTextFirstName;
    private EditText editTextLastName;
    private Button buttonNext;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser; // Added for the listener

    // --- Badge Listener Variables ---
    private ListenerRegistration notificationListener;
    private TextView unreadNotificationBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_setup);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        editTextFirstName = findViewById(R.id.edit_text_first_name);
        editTextLastName = findViewById(R.id.edit_text_last_name);
        buttonNext = findViewById(R.id.button_next);

        // Attempt to find badge (Safe check provided in listener)
        unreadNotificationBadge = findViewById(R.id.unread_notification_badge);

        // Run the listener fix
        setupNotificationListener();

        buttonNext.setOnClickListener(v -> saveUserName());
    }

    // ---------------------------------------------------------
    // FIXED: Notification Badge Logic
    // ---------------------------------------------------------
    private void setupNotificationListener() {
        if (currentUser == null) return;
        String currentUserId = currentUser.getUid();

        // Logic from the fix:
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

    private void saveUserName() {
        String firstName = editTextFirstName.getText().toString().trim();
        String lastName = editTextLastName.getText().toString().trim();

        if (firstName.isEmpty()) {
            editTextFirstName.setError("First name is required");
            editTextFirstName.requestFocus();
            return;
        }

        if (lastName.isEmpty()) {
            editTextLastName.setError("Last name is required");
            editTextLastName.requestFocus();
            return;
        }

        if (currentUser == null) {
            Toast.makeText(this, "No user logged in. Please restart.", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> userData = new HashMap<>();
        userData.put("firstName", firstName);
        userData.put("lastName", lastName);
        userData.put("fullName", firstName + " " + lastName);
        userData.put("name", firstName + " " + lastName);

        String userId = currentUser.getUid();

        db.collection("users").document(userId)
                .set(userData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(ProfileSetupActivity.this, "Profile Saved!", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(ProfileSetupActivity.this, VehicleRegistrationActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving profile", e);
                    Toast.makeText(ProfileSetupActivity.this, "Error saving profile.", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (notificationListener != null) notificationListener.remove();
    }
}