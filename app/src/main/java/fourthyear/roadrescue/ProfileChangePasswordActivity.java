package fourthyear.roadrescue;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button; // Added
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ProfileChangePasswordActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private TextInputEditText etCurrentPassword;
    private TextInputEditText etNewPassword;
    private TextInputEditText etRetypePassword;
    private Button btnSavePassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_change_password);

        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        if (notificationButton != null) {
            notificationButton.setOnClickListener(v -> {
                Intent intent = new Intent(ProfileChangePasswordActivity.this, NotificationsActivity.class);
                startActivity(intent);
            });
        }

        ImageView profileButton = findViewById(R.id.profile_icon_btn);
        if (profileButton != null) {
            profileButton.setOnClickListener(v -> {
                Intent intent = new Intent(ProfileChangePasswordActivity.this, ProfileActivity.class);
                startActivity(intent);
            });
        }

        ImageView homeButton = findViewById(R.id.home_icon_btn);
        if (homeButton != null) {
            homeButton.setOnClickListener(v -> {
                Intent intent = new Intent(ProfileChangePasswordActivity.this, homepage.class);
                startActivity(intent);
            });
        }

        ImageView messageButton = findViewById(R.id.message_icon_btn);
        if (messageButton != null) {
            messageButton.setOnClickListener(v -> {
                Intent intent = new Intent(ProfileChangePasswordActivity.this, ChatInboxActivity.class);
                startActivity(intent);
            });
        }

        mAuth = FirebaseAuth.getInstance();

        TextView forgotPasswordTextView = findViewById(R.id.text_forgot_password);
        ImageView backButton = findViewById(R.id.back_button);

        forgotPasswordTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendResetEmail();
            }
        });

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        etCurrentPassword = findViewById(R.id.input_current_password);
        etNewPassword = findViewById(R.id.input_new_password);
        etRetypePassword = findViewById(R.id.input_retype_password);

        btnSavePassword = findViewById(R.id.button_save_password);

        btnSavePassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                validateAndChangePassword();
            }
        });
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

        if (!newPass.equals(retypePass)) {
            etRetypePassword.setError("Passwords do not match");
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(this, "User not logged in or email not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPass);

        user.reauthenticate(credential)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            user.updatePassword(newPass)
                                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                                        @Override
                                        public void onComplete(@NonNull Task<Void> task) {
                                            if (task.isSuccessful()) {
                                                Toast.makeText(ProfileChangePasswordActivity.this, "Password updated successfully.", Toast.LENGTH_SHORT).show();
                                                finish();
                                            } else {
                                                Toast.makeText(ProfileChangePasswordActivity.this, "Update failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                                            }
                                        }
                                    });
                        } else {

                            etCurrentPassword.setError("Incorrect current password.");
                            Toast.makeText(ProfileChangePasswordActivity.this, "Authentication failed: Incorrect current password.", Toast.LENGTH_LONG).show();
                        }
                    }
                });
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
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            Toast.makeText(ProfileChangePasswordActivity.this,
                                    "Password reset email sent to " + emailAddress,
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(ProfileChangePasswordActivity.this,
                                    "Failed to send reset email. " + task.getException().getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }
}