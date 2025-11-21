package fourthyear.roadrescue;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.os.LocaleListCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Locale;

public class ProfileAccountLanguageSettingActivity extends AppCompatActivity {

    private static final String TAG = "LanguageSettings";

    private ImageView backButton;
    private RadioGroup radioGroupLanguage;
    private RadioButton radioEnglish, radioFilipino, radioSpanish, radioJapanese, radioChineseSimplified, radioFrench;

    private ConstraintLayout navNotification, navHome, navMessage, navProfile;

    // Firebase
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
        setContentView(R.layout.activity_profile_account_language_setting);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        backButton = findViewById(R.id.back_button);
        radioGroupLanguage = findViewById(R.id.radio_group_language);
        radioEnglish = findViewById(R.id.radio_english);
        radioFilipino = findViewById(R.id.radio_filipino);
        radioSpanish = findViewById(R.id.radio_spanish);
        radioJapanese = findViewById(R.id.radio_japanese);
        radioChineseSimplified = findViewById(R.id.radio_chinese_simplified);
        radioFrench = findViewById(R.id.radio_french);

        navNotification = findViewById(R.id.nav_notification_layout);
        navHome = findViewById(R.id.nav_home_layout);
        navMessage = findViewById(R.id.nav_message_layout);
        navProfile = findViewById(R.id.nav_profile_layout);

        // Init Badge Views
        unreadBadge = findViewById(R.id.unread_message_badge);
        unreadNotificationBadge = findViewById(R.id.unread_notification_badge);

        loadLanguagePreference();
        setupNavbar();
        setupBadgeListeners();

        backButton.setOnClickListener(v -> finish());

        radioGroupLanguage.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_english) {
                setAppLocale("en");
            } else if (checkedId == R.id.radio_filipino) {
                setAppLocale("fil");
            } else if (checkedId == R.id.radio_spanish) {
                setAppLocale("es");
            } else if (checkedId == R.id.radio_japanese) {
                setAppLocale("ja");
            } else if (checkedId == R.id.radio_chinese_simplified) {
                setAppLocale("zh-CN");
            } else if (checkedId == R.id.radio_french) {
                setAppLocale("fr");
            }
        });
    }

    // --- Badge Logic ---
    private void setupBadgeListeners() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;
        String currentUserId = user.getUid();

        // Messages Badge
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

        // Notifications Badge
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

    private void setupNavbar() {
        navNotification.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileAccountLanguageSettingActivity.this, NotificationsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });

        navMessage.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileAccountLanguageSettingActivity.this, ChatInboxActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });
        navProfile.setBackgroundResource(R.drawable.rounded_white_background);
        ImageView profileIcon = findViewById(R.id.profile_icon_btn);
        TextView profileText = findViewById(R.id.profile_text);
        if (profileIcon != null) profileIcon.setColorFilter(Color.BLACK);
        if (profileText != null) {
            profileText.setTextColor(Color.BLACK);
            profileText.setTypeface(null, Typeface.BOLD);
        }
        navProfile.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileAccountLanguageSettingActivity.this, ProfileActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });

        navHome.setOnClickListener(v -> navigateToCorrectHomepage());
    }

    private void navigateToCorrectHomepage() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Intent intent = new Intent(this, MainActivity.class);
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

                        // Robust Check
                        if (type != null && (type.trim().equalsIgnoreCase("Service Provider") || type.trim().equalsIgnoreCase("driver"))) {
                            userType = "Service Provider";
                        }
                    }

                    Intent intent;
                    if (userType.equals("Service Provider")) {
                        intent = new Intent(this, ServiceProviderHomepage.class);
                    } else {
                        intent = new Intent(this, homepage.class);
                    }

                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get userType", e);
                    Intent intent = new Intent(this, homepage.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                });
    }

    private void loadLanguagePreference() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        String currentLanguage;

        if (locales.isEmpty()) {
            currentLanguage = Locale.getDefault().toLanguageTag();
        } else {
            currentLanguage = locales.get(0).toLanguageTag();
        }

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
            radioEnglish.setChecked(true);
        }
    }

    private void setAppLocale(String languageCode) {
        LocaleListCompat newLocale = LocaleListCompat.forLanguageTags(languageCode);
        AppCompatDelegate.setApplicationLocales(newLocale);

        Toast.makeText(this, "Language set. Restarting app...", Toast.LENGTH_SHORT).show();

        // --- UPDATED: Check user type and redirect appropriately on reset ---
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    String userType = "Customer";
                    if (documentSnapshot.exists()) {
                        String type = documentSnapshot.getString("userType");

                        // Robust Check for Redirect on Reset
                        if (type != null && (type.trim().equalsIgnoreCase("Service Provider") || type.trim().equalsIgnoreCase("driver"))) {
                            userType = "Service Provider";
                        }
                    }

                    Intent intent;
                    if (userType.equals("Service Provider")) {
                        intent = new Intent(this, ServiceProviderHomepage.class);
                    } else {
                        intent = new Intent(this, homepage.class);
                    }

                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .addOnFailureListener(e -> {
                    Intent intent = new Intent(this, homepage.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
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