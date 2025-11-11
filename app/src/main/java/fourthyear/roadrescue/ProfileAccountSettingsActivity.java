package fourthyear.roadrescue;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class ProfileAccountSettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile_account_settings);
        setupUIMainComponents();
        setupBottomNavigationUIComponents();
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

    private void setupBottomNavigationUIComponents() {
        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        if (notificationButton != null) {
            notificationButton.setOnClickListener(v -> {
                Intent intent = new Intent(ProfileAccountSettingsActivity.this, NotificationsActivity.class);
                startActivity(intent);
            });
        }

        ImageView profileButton = findViewById(R.id.profile_icon_btn);
        if (profileButton != null) {
            profileButton.setOnClickListener(v -> {
                Intent intent = new Intent(ProfileAccountSettingsActivity.this, ProfileActivity.class);
                startActivity(intent);
            });
        }

        ImageView homeButton = findViewById(R.id.home_icon_btn);
        if (homeButton != null) {
            homeButton.setOnClickListener(v -> {
                Intent intent = new Intent(ProfileAccountSettingsActivity.this, homepage.class);
                startActivity(intent);
            });
        }

        ImageView messageButton = findViewById(R.id.message_icon_btn);
        if (messageButton != null) {
            messageButton.setOnClickListener(v -> {
                Intent intent = new Intent(ProfileAccountSettingsActivity.this, ChatInboxActivity.class);
                startActivity(intent);
            });
        }
    }
}