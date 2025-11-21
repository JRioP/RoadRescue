package fourthyear.roadrescue;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class ProfileChangePasswordActivity extends AppCompatActivity {

    private static final String TAG = "ChangePassword";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private TextInputEditText etCurrentPassword;
    private TextInputEditText etNewPassword;
    private TextInputEditText etRetypePassword;
    private Button btnSavePassword;

    // --- Badge Listeners & UI ---
    private ListenerRegistration unreadListener;
    private ListenerRegistration notificationListener;
    private TextView unreadBadge;
    private TextView unreadNotificationBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_change_password);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Init Badge Views
        unreadBadge = findViewById(R.id.unread_message_badge);
        unreadNotificationBadge = findViewById(R.id.unread_notification_badge);

        setupNavbar();

        setupBadgeListeners();
        setupNotificationListener(); // UPDATED: Calls the fixed method

        TextView forgotPasswordTextView = findViewById(R.id.text_forgot_password);
        ImageView backButton = findViewById(R.id.back_button);

        forgotPasswordTextView.setOnClickListener(v -> sendResetEmail());
        backButton.setOnClickListener(v -> finish());

        etCurrentPassword = findViewById(R.id.input_current_password);
        etNewPassword = findViewById(R.id.input_new_password);
        etRetypePassword = findViewById(R.id.input_retype_password);

        btnSavePassword = findViewById(R.id.button_save_password);

        btnSavePassword.setOnClickListener(v -> validateAndChangePassword());
    }

    private void validateAndChangePassword() {
        String currentPass = etCurrentPassword.getText().toString().trim();
        String newPass = etNewPassword.getText().toString().trim();
        String retypePass = etRetypePassword.getText().toString().trim();

        if (currentPass.isEmpty() || newPass.isEmpty() || retypePass.isEmpty()) {
            Toast.makeText(this, "All fields are required.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (newPass.length() < 8) {
            Toast.makeText(this, "Password must be at least 8 characters.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check: New Password != Current Password
        if (newPass.equals(currentPass)) {
            etNewPassword.setError("New password cannot be the same as current password.");
            Toast.makeText(this, "New password must be different.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newPass.equals(retypePass)) {
            etRetypePassword.setError("Passwords do not match");
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(this, "User not logged in or email not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Re-authenticate
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPass);

        user.reauthenticate(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // 2. Check History
                        checkPasswordHistoryAndUpdate(user, newPass);
                    } else {
                        etCurrentPassword.setError("Incorrect current password.");
                        Toast.makeText(ProfileChangePasswordActivity.this, "Authentication failed: Incorrect current password.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    // --- Check History and Update Logic ---
    private void checkPasswordHistoryAndUpdate(FirebaseUser user, String newPassword) {
        String newPasswordHash = hashPassword(newPassword);
        DocumentReference userDoc = db.collection("users").document(user.getUid());

        userDoc.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                List<String> passwordHistory = (List<String>) documentSnapshot.get("passwordHistory");
                if (passwordHistory == null) {
                    passwordHistory = new ArrayList<>();
                }

                if (passwordHistory.contains(newPasswordHash)) {
                    Toast.makeText(this, "You cannot reuse your current or previous 5 passwords.", Toast.LENGTH_LONG).show();
                    etNewPassword.setError("Used recently");
                    return;
                }

                performFirebasePasswordUpdate(user, newPassword, newPasswordHash, passwordHistory);
            } else {
                performFirebasePasswordUpdate(user, newPassword, newPasswordHash, new ArrayList<>());
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to check password history.", Toast.LENGTH_SHORT).show();
        });
    }

    private void performFirebasePasswordUpdate(FirebaseUser user, String newPassword, String newPasswordHash, List<String> history) {
        user.updatePassword(newPassword)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        history.add(newPasswordHash);
                        if (history.size() > 5) {
                            history.remove(0);
                        }

                        db.collection("users").document(user.getUid())
                                .update("passwordHistory", history)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(ProfileChangePasswordActivity.this, "Password updated successfully.", Toast.LENGTH_SHORT).show();
                                    finish();
                                });

                    } else {
                        Toast.makeText(ProfileChangePasswordActivity.this, "Update failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            Log.e(TAG, "Hashing error", e);
            return password;
        }
    }

    private void sendResetEmail() {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "No user is currently logged in.", Toast.LENGTH_SHORT).show();
            return;
        }

        String emailAddress = currentUser.getEmail();

        if (emailAddress == null || emailAddress.isEmpty()) {
            Toast.makeText(this, "User email not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.sendPasswordResetEmail(emailAddress)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(ProfileChangePasswordActivity.this, "Password reset email sent to " + emailAddress, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(ProfileChangePasswordActivity.this, "Failed to send reset email. " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    // --- Badge Logic ---
    private void setupBadgeListeners() {
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
        FirebaseUser currentUser = mAuth.getCurrentUser(); // Fixed: Define currentUser
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
        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        if (notificationButton != null) {
            notificationButton.setOnClickListener(v -> {
                Intent intent = new Intent(ProfileChangePasswordActivity.this, NotificationsActivity.class);
                // --- FIX ADDED HERE ---
                // Prevents creating a new activity if one already exists.
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
            });
        }

        ConstraintLayout profileLayout = findViewById(R.id.nav_profile_layout);
        ImageView profileIcon = findViewById(R.id.profile_icon_btn);
        TextView profileText = findViewById(R.id.profile_text);

        // --- Active State Styling for Profile Icon ---
        if (profileLayout != null) {
            profileLayout.setBackgroundResource(R.drawable.rounded_white_background);
            profileLayout.setOnClickListener(v -> {
                Intent intent = new Intent(ProfileChangePasswordActivity.this, ProfileActivity.class);
                // --- FIX ADDED HERE ---
                // This is important for navigating "up" to the main profile screen.
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
            });
        }
        if (profileIcon != null) profileIcon.setColorFilter(Color.BLACK);
        if (profileText != null) {
            profileText.setTextColor(Color.BLACK);
            profileText.setTypeface(null, Typeface.BOLD);
        }

        // --- HOME BUTTON ---
        // This logic is already correct as it clears the stack to go "home".
        ImageView homeButton = findViewById(R.id.home_icon_btn);
        if (homeButton != null) {
            homeButton.setOnClickListener(v -> {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user == null) {
                    Intent intent = new Intent(ProfileChangePasswordActivity.this, MainActivity.class);
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
                                if (type != null && (type.trim().equalsIgnoreCase("Service Provider") || type.trim().equalsIgnoreCase("driver"))) {
                                    userType = "Service Provider";
                                }
                            }

                            Intent intent;
                            if (userType.equals("Service Provider")) {
                                intent = new Intent(ProfileChangePasswordActivity.this, ServiceProviderHomepage.class);
                            } else {
                                intent = new Intent(ProfileChangePasswordActivity.this, homepage.class);
                            }
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            finish();
                        });
            });
        }

        ImageView messageButton = findViewById(R.id.message_icon_btn);
        if (messageButton != null) {
            messageButton.setOnClickListener(v -> {
                Intent intent = new Intent(ProfileChangePasswordActivity.this, ChatInboxActivity.class);
                // --- FIX ADDED HERE ---
                // Prevents creating a new activity if one already exists.
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (unreadListener != null) unreadListener.remove();
        if (notificationListener != null) notificationListener.remove();
    }
}