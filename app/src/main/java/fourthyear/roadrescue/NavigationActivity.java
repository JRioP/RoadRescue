package fourthyear.roadrescue;

import android.content.Context;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

public class NavigationActivity extends AppCompatActivity {

    // --- Fragments ---
    private final Fragment chatFragment = new ChatInboxFragment();
    private final Fragment notificationFragment = new NotificationsFragment();
    private final Fragment profileFragment = new ProfileFragment();

    // --- UI Elements ---
    private ConstraintLayout navNotificationLayout, navHomeLayout, navMessageLayout, navProfileLayout;
    private ImageView notificationIcon, homeIcon, messageIcon, profileIcon;
    private TextView notificationText, homeText, messageText, profileText;
    private TextView unreadNotificationBadge, unreadMessageBadge;
    private boolean isNotificationListenerInitialized = false;
    private boolean isMessageListenerInitialized = false;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private ListenerRegistration unreadListener;
    private ListenerRegistration notificationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initializeViews();
        setupClickListeners();
        setupBadgeListeners();
        setupBackNavigation();

        // Handle Payment Result
        getSupportFragmentManager().setFragmentResultListener("payment_result_key", this, (requestKey, result) -> {
            checkUserTypeAndLoadHome();
            updateNavUI(navHomeLayout);
        });

        // Default Load
        if (savedInstanceState == null) {
            checkUserTypeAndLoadHome();
            updateNavUI(navHomeLayout);
        }
    }

    // --- NEW: Helper to load Detail Fragments (Chat Conversation, Receipt) ---
    public void loadDetailFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null) // This allows the back button to work!
                .commit();
    }

    // --- Standard Navigation ---
    public void navigateToTab(int tabIndex) {
        // Clear back stack when switching main tabs
        getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

        switch (tabIndex) {
            case 0: loadFragment(notificationFragment); updateNavUI(navNotificationLayout); break;
            case 1: checkUserTypeAndLoadHome(); updateNavUI(navHomeLayout); break;
            case 2: loadFragment(chatFragment); updateNavUI(navMessageLayout); break;
            case 3: loadFragment(profileFragment); updateNavUI(navProfileLayout); break;
        }
    }

    private void checkUserTypeAndLoadHome() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid()).get().addOnSuccessListener(doc -> {
            String type = doc.getString("userType");
            if (type != null && (type.equalsIgnoreCase("Service Provider") || type.equalsIgnoreCase("driver"))) {
                // --- UPDATED: Use ServiceProviderHomeFragment ---
                loadFragment(new ServiceProviderHomeFragment());
            } else {
                loadFragment(new HomepageFragment());
            }
        }).addOnFailureListener(e -> loadFragment(new HomepageFragment()));
    }

    private void loadFragment(Fragment fragment) {
        // Clear back stack for main tabs
        getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, fragment);
        transaction.commit();
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                FragmentManager fm = getSupportFragmentManager();

                // 1. If in a detail fragment (like ChatConversation), go back
                if (fm.getBackStackEntryCount() > 0) {
                    fm.popBackStack();
                    return;
                }

                // 2. If not on Home, go Home
                Fragment current = fm.findFragmentById(R.id.fragment_container);
                if (!(current instanceof HomepageFragment) && !(current instanceof ServiceProviderHomeFragment) && !(current instanceof ProviderMapFragment)) {
                    checkUserTypeAndLoadHome();
                    updateNavUI(navHomeLayout);
                } else {
                    finish(); // Exit
                }
            }
        });
    }


    private void initializeViews() {
        navNotificationLayout = findViewById(R.id.nav_notification_layout);
        navHomeLayout = findViewById(R.id.nav_home_layout);
        navMessageLayout = findViewById(R.id.nav_message_layout);
        navProfileLayout = findViewById(R.id.nav_profile_layout);
        notificationIcon = findViewById(R.id.notification_icon_btn);
        homeIcon = findViewById(R.id.home_icon_btn);
        messageIcon = findViewById(R.id.message_icon_btn);
        profileIcon = findViewById(R.id.profile_icon_btn);
        notificationText = findViewById(R.id.notification_text);
        homeText = findViewById(R.id.home_text);
        messageText = findViewById(R.id.message_text);
        profileText = findViewById(R.id.profile_text);
        unreadNotificationBadge = findViewById(R.id.unread_notification_badge);
        unreadMessageBadge = findViewById(R.id.unread_message_badge);
    }

    private void setupClickListeners() {
        navNotificationLayout.setOnClickListener(v -> { loadFragment(notificationFragment); updateNavUI(navNotificationLayout); });
        navHomeLayout.setOnClickListener(v -> { checkUserTypeAndLoadHome(); updateNavUI(navHomeLayout); });
        navMessageLayout.setOnClickListener(v -> { loadFragment(chatFragment); updateNavUI(navMessageLayout); });
        navProfileLayout.setOnClickListener(v -> { loadFragment(profileFragment); updateNavUI(navProfileLayout); });
    }

    private void updateNavUI(ConstraintLayout selectedLayout) {
        int white = Color.WHITE;
        int transparent = Color.TRANSPARENT;
        int black = Color.BLACK;

        navNotificationLayout.setBackgroundColor(transparent); notificationIcon.setColorFilter(white); notificationText.setTextColor(white);
        navHomeLayout.setBackgroundColor(transparent); homeIcon.setColorFilter(white); homeText.setTextColor(white);
        navMessageLayout.setBackgroundColor(transparent); messageIcon.setColorFilter(white); messageText.setTextColor(white);
        navProfileLayout.setBackgroundColor(transparent); profileIcon.setColorFilter(white); profileText.setTextColor(white);

        if (selectedLayout != null) {
            selectedLayout.setBackgroundResource(R.drawable.rounded_white_background);
            if (selectedLayout == navNotificationLayout) { notificationIcon.setColorFilter(black); notificationText.setTextColor(black); }
            if (selectedLayout == navHomeLayout) { homeIcon.setColorFilter(black); homeText.setTextColor(black); }
            if (selectedLayout == navMessageLayout) { messageIcon.setColorFilter(black); messageText.setTextColor(black); }
            if (selectedLayout == navProfileLayout) { profileIcon.setColorFilter(black); profileText.setTextColor(black); }
        }
    }

    private void setupBadgeListeners() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        // Ensure listeners are removed before adding new ones
        if (unreadListener != null) unreadListener.remove();
        if (notificationListener != null) notificationListener.remove();

        // --- CHAT MESSAGE LISTENER ---
        unreadListener = db.collection("chats").whereArrayContains("participantIds", user.getUid()).whereEqualTo("status", "active")
                .addSnapshotListener((snapshots, e) -> {
                    // CRASH GUARD 1: Check if the Activity is still alive
                    if (e != null || isFinishing()) return;

                    int total = 0;
                    if (snapshots != null) for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Long count = doc.getLong("unreadCounts." + user.getUid());
                        if (count != null) total += count;
                    }

                    boolean currentlyVisible = unreadMessageBadge != null && unreadMessageBadge.getVisibility() == View.VISIBLE;
                    boolean shouldBeVisible = total > 0;

                    // Play sound only if the badge should become visible and the listener has been running (not initial load)
                    if (shouldBeVisible && !currentlyVisible && isMessageListenerInitialized) {
                        playNotificationSound();
                    }

                    if (unreadMessageBadge != null) unreadMessageBadge.setVisibility(shouldBeVisible ? View.VISIBLE : View.GONE);
                    isMessageListenerInitialized = true; // Mark as initialized after the first run
                });

        // --- NOTIFICATION LISTENER ---
        notificationListener = db.collection("notifications").whereEqualTo("userId", user.getUid()).whereEqualTo("read", false)
                .addSnapshotListener((snapshots, e) -> {
                    // CRASH GUARD 2: Check if the Activity is still alive
                    if (e != null || isFinishing()) return;

                    boolean currentlyVisible = unreadNotificationBadge != null && unreadNotificationBadge.getVisibility() == View.VISIBLE;
                    boolean shouldBeVisible = snapshots != null && !snapshots.isEmpty();

                    if (shouldBeVisible && !currentlyVisible && isNotificationListenerInitialized) {
                        playNotificationSound();
                    }

                    if (unreadNotificationBadge != null) unreadNotificationBadge.setVisibility(shouldBeVisible ? View.VISIBLE : View.GONE);
                    isNotificationListenerInitialized = true; // Mark as initialized after the first run
                });
    }

    private void playNotificationSound() {
        if (isFinishing()) return;

        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            v.vibrate(300);
        }

        try {
            MediaPlayer mediaPlayer = MediaPlayer.create(this, R.raw.notification_pop);
            if (mediaPlayer != null) {
                mediaPlayer.setOnCompletionListener(MediaPlayer::release);
                mediaPlayer.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (unreadListener != null) unreadListener.remove();
        if (notificationListener != null) notificationListener.remove();
    }
}