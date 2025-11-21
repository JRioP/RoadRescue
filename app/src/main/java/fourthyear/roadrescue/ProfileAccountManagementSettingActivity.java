package fourthyear.roadrescue;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

public class ProfileAccountManagementSettingActivity extends AppCompatActivity {

    private static final String TAG = "AccountManagement";

    private ImageView backButton;
    private RelativeLayout buttonDeleteAccount;
    private RelativeLayout buttonLogout;
    private ConstraintLayout navNotification, navHome, navMessage, navProfile;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // Badges
    private ListenerRegistration unreadListener;
    private ListenerRegistration notificationListener;
    private TextView unreadBadge;
    private TextView unreadNotificationBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_account_management_setting);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        backButton = findViewById(R.id.back_button);
        buttonDeleteAccount = findViewById(R.id.button_delete_account);
        buttonLogout = findViewById(R.id.button_logout);

        navNotification = findViewById(R.id.nav_notification_layout);
        navHome = findViewById(R.id.nav_home_layout);
        navMessage = findViewById(R.id.nav_message_layout);
        navProfile = findViewById(R.id.nav_profile_layout);

        unreadBadge = findViewById(R.id.unread_message_badge);
        unreadNotificationBadge = findViewById(R.id.unread_notification_badge);

        backButton.setOnClickListener(v -> finish());

        buttonDeleteAccount.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileAccountManagementSettingActivity.this, ProfileAccountDeleteSettingsActivity.class);
            startActivity(intent);
        });

        buttonLogout.setOnClickListener(v -> showLogoutConfirmation());

        // Setup Badge Listeners
        setupUnreadMessageListener();
        setupNotificationListener(); // Uses the fix

        setupNavbar();
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    mAuth.signOut();

                    Toast.makeText(ProfileAccountManagementSettingActivity.this, "Logged out successfully", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(ProfileAccountManagementSettingActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // --- Badge Listener Logic ---

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

    // ---------------------------------------------------------
    // FIXED: Notification Badge Logic (Updated to use 'notifications' collection)
    // ---------------------------------------------------------
    private void setupNotificationListener() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
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

    private void setupNavbar() {
        navNotification.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileAccountManagementSettingActivity.this, NotificationsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });

        navMessage.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileAccountManagementSettingActivity.this, ChatInboxActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });
        navProfile.setBackgroundResource(R.drawable.rounded_white_background);
        ImageView profileIcon = findViewById(R.id.profile_icon_btn);
        TextView profileText = findViewById(R.id.profile_text);

        if (profileIcon != null) {
            profileIcon.setColorFilter(Color.BLACK);
        }
        if (profileText != null) {
            profileText.setTextColor(Color.BLACK);
            profileText.setTypeface(null, Typeface.BOLD);
        }
        navProfile.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileAccountManagementSettingActivity.this, ProfileActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });
        navHome.setOnClickListener(v -> {
            FirebaseUser user = mAuth.getCurrentUser();
            if (user == null) {
                Intent intent = new Intent(ProfileAccountManagementSettingActivity.this, MainActivity.class);
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
                            intent = new Intent(ProfileAccountManagementSettingActivity.this, ServiceProviderHomepage.class);
                        } else {
                            intent = new Intent(ProfileAccountManagementSettingActivity.this, homepage.class);
                        }
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to get userType", e);
                        // Default fallback
                        Intent intent = new Intent(ProfileAccountManagementSettingActivity.this, homepage.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (unreadListener != null) unreadListener.remove();
        if (notificationListener != null) notificationListener.remove();
    }
}