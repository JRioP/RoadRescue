package fourthyear.roadrescue;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class ProfileAccountSettingsActivity extends AppCompatActivity {

    private static final String TAG = "ProfileAccountSettings";
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    // --- Badge Listeners & UI ---
    private ListenerRegistration unreadListener;
    private ListenerRegistration notificationListener;
    private TextView unreadBadge;
    private TextView unreadNotificationBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile_account_settings);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        setupUIMainComponents();

        // --- Setup Badge Listeners ---
        setupUnreadMessageListener();
        setupNotificationListener();

        setupNavbar();
    }

    // --- Badge Listener Logic ---
    private void setupUnreadMessageListener() {
        FirebaseUser user = auth.getCurrentUser();
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

    private void setupNotificationListener() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;
        String currentUserId = user.getUid();

        notificationListener = db.collection("notifications")
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

    public void setupUIMainComponents() {
        ImageView backButton = findViewById(R.id.back_button2);
        backButton.setOnClickListener(v -> finish());

        RelativeLayout passwordSetting = findViewById(R.id.setting_password);
        passwordSetting.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileAccountSettingsActivity.this, ProfileChangePasswordActivity.class);
            startActivity(intent);
        });

        RelativeLayout contactInfoSetting = findViewById(R.id.setting_contact);
        contactInfoSetting.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileAccountSettingsActivity.this, ProfileContactInfoActivity.class);
            startActivity(intent);
        });

        RelativeLayout languageSetting = findViewById(R.id.setting_language);
        languageSetting.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileAccountSettingsActivity.this, ProfileAccountLanguageSettingActivity.class);
            startActivity(intent);
        });

        RelativeLayout accountManagementSetting = findViewById(R.id.setting_account_management);
        accountManagementSetting.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileAccountSettingsActivity.this, ProfileAccountManagementSettingActivity.class);
            startActivity(intent);
        });
    }

    private void setupNavbar() {
        // --- Init Badge Views ---
        unreadBadge = findViewById(R.id.unread_message_badge);
        unreadNotificationBadge = findViewById(R.id.unread_notification_badge);

        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        notificationButton.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileAccountSettingsActivity.this, NotificationsActivity.class);
            startActivity(intent);
        });

        // --- Profile Button (Active State) ---
        ConstraintLayout profileLayout = findViewById(R.id.nav_profile_layout);
        ImageView profileIcon = findViewById(R.id.profile_icon_btn);
        TextView profileText = findViewById(R.id.profile_text);

        if (profileLayout != null) {
            profileLayout.setBackgroundResource(R.drawable.rounded_white_background);
        }
        if (profileIcon != null) {
            profileIcon.setColorFilter(Color.BLACK);
        }
        if (profileText != null) {
            profileText.setTextColor(Color.BLACK);
            profileText.setTypeface(null, Typeface.BOLD);
        }

        // Navigate back to main Profile page
        if (profileLayout != null) {
            profileLayout.setOnClickListener(v -> {
                Intent intent = new Intent(ProfileAccountSettingsActivity.this, ProfileActivity.class);
                startActivity(intent);
            });
        }

        // --- HOME BUTTON FIX ---
        ImageView homeButton = findViewById(R.id.home_icon_btn);
        homeButton.setOnClickListener(v -> {
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) {
                Intent intent = new Intent(ProfileAccountSettingsActivity.this, MainActivity.class);
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

                            // Robust Check: Handles "driver", "Service Provider", etc.
                            if (type != null && (type.trim().equalsIgnoreCase("Service Provider") || type.trim().equalsIgnoreCase("driver"))) {
                                userType = "Service Provider";
                            }
                        }

                        Intent intent;
                        if (userType.equals("Service Provider")) {
                            intent = new Intent(ProfileAccountSettingsActivity.this, ServiceProviderHomepage.class);
                        } else {
                            intent = new Intent(ProfileAccountSettingsActivity.this, homepage.class);
                        }

                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to get userType, defaulting to customer homepage", e);
                        Intent intent = new Intent(ProfileAccountSettingsActivity.this, homepage.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    });
        });

        ImageView messageButton = findViewById(R.id.message_icon_btn);
        messageButton.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileAccountSettingsActivity.this, ChatInboxActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (unreadListener != null) unreadListener.remove();
        if (notificationListener != null) notificationListener.remove();
    }
}