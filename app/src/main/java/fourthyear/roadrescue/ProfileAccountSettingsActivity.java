package fourthyear.roadrescue;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

// --- FIXED IMPORTS ---
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
// ---------------------

public class ProfileAccountSettingsActivity extends AppCompatActivity {

    // --- ADDED MISSING VARIABLES ---
    private static final String TAG = "ProfileAccountSettings";
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    // -------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile_account_settings);

        // --- INITIALIZE FIREBASE (Must happen before setupNavbar) ---
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        // ---------------------------------------------------------

        setupUIMainComponents();
        setupNavbar();
    }

    public void setupUIMainComponents() {
        ImageView backButton = findViewById(R.id.back_button2);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        RelativeLayout passwordSetting = findViewById(R.id.setting_password);
        passwordSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ProfileAccountSettingsActivity.this, ProfileChangePasswordActivity.class);
                startActivity(intent);
            }
        });

        RelativeLayout contactInfoSetting = findViewById(R.id.setting_contact);
        contactInfoSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ProfileAccountSettingsActivity.this, ProfileContactInfoActivity.class);
                startActivity(intent);
            }
        });

        RelativeLayout languageSetting = findViewById(R.id.setting_language);
        languageSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ProfileAccountSettingsActivity.this, ProfileAccountLanguageSettingActivity.class);
                startActivity(intent);
            }
        });

        RelativeLayout accountManagementSetting = findViewById(R.id.setting_account_management);
        accountManagementSetting.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ProfileAccountSettingsActivity.this, ProfileAccountManagementSettingActivity.class);
                startActivity(intent);
            }
        });

    }

    private void setupNavbar() {
        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        notificationButton.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileAccountSettingsActivity.this, NotificationsActivity.class);
            startActivity(intent);
        });

        ImageView profileButton = findViewById(R.id.profile_icon_btn);
        profileButton.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileAccountSettingsActivity.this, ProfileActivity.class);
            startActivity(intent);
        });

        // --- THIS CODE IS NOW FIXED ---
        ImageView homeButton = findViewById(R.id.home_icon_btn);
        homeButton.setOnClickListener(v -> {
            FirebaseUser user = auth.getCurrentUser(); // 'auth' will no longer be null
            if (user == null) {
                // Failsafe, go to login
                Intent intent = new Intent(ProfileAccountSettingsActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
                return;
            }

            // Check the user's type from Firestore
            db.collection("users").document(user.getUid()).get() // 'db' will no longer be null
                    .addOnSuccessListener(documentSnapshot -> {
                        String userType = "Customer"; // Default to customer
                        if (documentSnapshot.exists()) {
                            String type = documentSnapshot.getString("userType");
                            if (type != null && type.equals("Service Provider")) {
                                userType = type;
                            }
                        }

                        if (userType.equals("Service Provider")) {
                            // Go to provider homepage
                            Intent intent = new Intent(ProfileAccountSettingsActivity.this, ServiceProviderHomepage.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        } else {
                            // Go to customer homepage
                            Intent intent = new Intent(ProfileAccountSettingsActivity.this, homepage.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                        finish(); // Finish this activity after navigating home
                    })
                    .addOnFailureListener(e -> {
                        // On failure, just default to the customer homepage
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
}