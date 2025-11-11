package fourthyear.roadrescue;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

public class ProfileAccountManagementSettingActivity extends AppCompatActivity {

    private ImageView backButton;
    private RelativeLayout buttonDeleteAccount;

    private ConstraintLayout navNotification, navHome, navMessage, navProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_account_management_setting);

        // Find views
        backButton = findViewById(R.id.back_button);
        buttonDeleteAccount = findViewById(R.id.button_delete_account);

        // Find bottom nav views
        navNotification = findViewById(R.id.nav_notification_layout);
        navHome = findViewById(R.id.nav_home_layout);
        navMessage = findViewById(R.id.nav_message_layout);
        navProfile = findViewById(R.id.nav_profile_layout);

        // Set listeners
        backButton.setOnClickListener(v -> finish());

        buttonDeleteAccount.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileAccountManagementSettingActivity.this, ProfileAccountDeleteSettingsActivity.class);
            startActivity(intent);
            Toast.makeText(this, "Delete Account clicked", Toast.LENGTH_SHORT).show();
        });

        // Bottom Nav Listeners
        navNotification.setOnClickListener(v -> startActivity(new Intent(ProfileAccountManagementSettingActivity.this, NotificationsActivity.class)));
        navHome.setOnClickListener(v -> startActivity(new Intent(ProfileAccountManagementSettingActivity.this, homepage.class)));
        navMessage.setOnClickListener(v -> startActivity(new Intent(ProfileAccountManagementSettingActivity.this, ChatInboxActivity.class)));
        navProfile.setOnClickListener(v -> startActivity(new Intent(ProfileAccountManagementSettingActivity.this, ProfileActivity.class)));
    }
}