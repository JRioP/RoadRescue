package fourthyear.roadrescue;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

public class ProfileAccountLanguageSettingActivity extends AppCompatActivity {

    private ImageView backButton;
    private RadioGroup radioGroupLanguage;
    private RadioButton radioEnglish;
    private RadioButton radioFilipino;
    private RadioButton radioSpanish; // New
    private RadioButton radioJapanese; // New
    private RadioButton radioChineseSimplified;
    private RadioButton radioFrench;

    private ConstraintLayout navNotification, navHome, navMessage, navProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_account_language_setting);

        backButton = findViewById(R.id.back_button);
        radioGroupLanguage = findViewById(R.id.radio_group_language);
        radioEnglish = findViewById(R.id.radio_english);
        radioFilipino = findViewById(R.id.radio_filipino);
        radioSpanish = findViewById(R.id.radio_spanish); // Find
        radioJapanese = findViewById(R.id.radio_japanese); // Find
        radioChineseSimplified = findViewById(R.id.radio_chinese_simplified);
        radioFrench = findViewById(R.id.radio_french);

        navNotification = findViewById(R.id.nav_notification_layout);
        navHome = findViewById(R.id.nav_home_layout);
        navMessage = findViewById(R.id.nav_message_layout);
        navProfile = findViewById(R.id.nav_profile_layout);

        loadLanguagePreference();

        backButton.setOnClickListener(v -> finish());

        radioGroupLanguage.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_english) {
                setAppLocale("en"); // English
            } else if (checkedId == R.id.radio_filipino) {
                setAppLocale("fil"); // Filipino
            } else if (checkedId == R.id.radio_spanish) {
                setAppLocale("es"); // Spanish
            } else if (checkedId == R.id.radio_japanese) {
                setAppLocale("ja"); // Japanese
            } else if (checkedId == R.id.radio_chinese_simplified) {
                setAppLocale("zh-CN"); // Chinese (Simplified)
            } else if (checkedId == R.id.radio_french) {
                setAppLocale("fr"); // French
            }
        });

        navNotification.setOnClickListener(v -> startActivity(new Intent(ProfileAccountLanguageSettingActivity.this, NotificationsActivity.class)));
        navHome.setOnClickListener(v -> startActivity(new Intent(ProfileAccountLanguageSettingActivity.this, homepage.class)));
        navMessage.setOnClickListener(v -> startActivity(new Intent(ProfileAccountLanguageSettingActivity.this, ChatInboxActivity.class)));
        navProfile.setOnClickListener(v -> startActivity(new Intent(ProfileAccountLanguageSettingActivity.this, ProfileActivity.class)));
    }

    private void loadLanguagePreference() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        String currentLanguage;

        if (locales.isEmpty()) {
            currentLanguage = Locale.getDefault().toLanguageTag();
        } else {
            currentLanguage = locales.get(0).toLanguageTag();
        }

        // Check for base language code
        if (currentLanguage.startsWith("fil")) {
            radioFilipino.setChecked(true);
        } else if (currentLanguage.startsWith("es")) {
            radioSpanish.setChecked(true);
        } else if (currentLanguage.startsWith("ja")) {
            radioJapanese.setChecked(true);
        } else if (currentLanguage.startsWith("zh-CN") || currentLanguage.startsWith("zh-Hans")) {
            radioChineseSimplified.setChecked(true);
        } else if (currentLanguage.startsWith("fr")) {
            radioFrench.setChecked(true);
        } else {
            // Default to English
            radioEnglish.setChecked(true);
        }
    }

    private void setAppLocale(String languageCode) {
        LocaleListCompat newLocale = LocaleListCompat.forLanguageTags(languageCode);

        AppCompatDelegate.setApplicationLocales(newLocale);

        Toast.makeText(this, "Language set. Restarting app...", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, homepage.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}