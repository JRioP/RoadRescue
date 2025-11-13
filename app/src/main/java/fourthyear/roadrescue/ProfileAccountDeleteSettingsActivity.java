package fourthyear.roadrescue;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileAccountDeleteSettingsActivity extends AppCompatActivity {

    private static final String TAG = "DeleteAccountActivity";

    private ImageView backButton;
    private MaterialButton buttonDelete;
    private Button buttonCancel;
    private ConstraintLayout navNotification, navHome, navMessage, navProfile;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_account_deletion_settings);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        backButton = findViewById(R.id.back_button);
        buttonDelete = findViewById(R.id.button_delete);
        buttonCancel = findViewById(R.id.button_cancel);

        navNotification = findViewById(R.id.nav_notification_layout);
        navHome = findViewById(R.id.nav_home_layout);
        navMessage = findViewById(R.id.nav_message_layout);
        navProfile = findViewById(R.id.nav_profile_layout);

        backButton.setOnClickListener(v -> finish());
        buttonCancel.setOnClickListener(v -> finish());

        buttonDelete.setOnClickListener(v -> showDeleteConfirmationDialog());

        setupNavbar();
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("Are you absolutely sure?\nThis action is permanent and cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteUserAccount();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void deleteUserAccount() {
        final FirebaseUser user = mAuth.getCurrentUser();

        if (user == null) {
            Toast.makeText(this, "No user logged in.", Toast.LENGTH_SHORT).show();
            return;
        }

        final String userId = user.getUid();

        db.collection("users").document(userId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User's Firestore document deleted.");
                    deleteFirebaseAuthRecord(user);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting Firestore document", e);
                    Toast.makeText(ProfileAccountDeleteSettingsActivity.this, "Error deleting data. " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void deleteFirebaseAuthRecord(FirebaseUser user) {
        user.delete()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "User account deleted.");
                        Toast.makeText(ProfileAccountDeleteSettingsActivity.this, "Account deleted successfully.", Toast.LENGTH_LONG).show();

                        Intent intent = new Intent(ProfileAccountDeleteSettingsActivity.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();

                    } else {

                        Log.w(TAG, "Error deleting user account", task.getException());

                        if (task.getException() instanceof FirebaseAuthRecentLoginRequiredException) {
                            Toast.makeText(ProfileAccountDeleteSettingsActivity.this, "Please sign in again to delete your account.", Toast.LENGTH_LONG).show();
                            startActivity(new Intent(ProfileAccountDeleteSettingsActivity.this, MainActivity.class));
                        } else {
                            Toast.makeText(ProfileAccountDeleteSettingsActivity.this, "Error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void setupNavbar() {
        navNotification.setOnClickListener(v -> startActivity(new Intent(ProfileAccountDeleteSettingsActivity.this, NotificationsActivity.class)));
        navMessage.setOnClickListener(v -> startActivity(new Intent(ProfileAccountDeleteSettingsActivity.this, ChatInboxActivity.class)));
        navProfile.setOnClickListener(v -> startActivity(new Intent(ProfileAccountDeleteSettingsActivity.this, ProfileActivity.class)));

        navHome.setOnClickListener(v -> {
            FirebaseUser user = mAuth.getCurrentUser();
            if (user == null) {

                Intent intent = new Intent(ProfileAccountDeleteSettingsActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
                return;
            }

            db.collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        String userType = "Customer"; // Default to customer
                        if (documentSnapshot.exists()) {
                            String type = documentSnapshot.getString("userType");
                            if (type != null && type.equals("Service Provider")) {
                                userType = type;
                            }
                        }

                        if (userType.equals("Service Provider")) {

                            Intent intent = new Intent(ProfileAccountDeleteSettingsActivity.this, ServiceProviderHomepage.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        } else {

                            Intent intent = new Intent(ProfileAccountDeleteSettingsActivity.this, homepage.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to get userType, defaulting to customer homepage", e);
                        Intent intent = new Intent(ProfileAccountDeleteSettingsActivity.this, homepage.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    });
        });
        // --- END OF FIX ---
    }
}