package fourthyear.roadrescue;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.Locale;

public class PaymentActivity extends AppCompatActivity {

    private static final String TAG = "PaymentActivity";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private MaterialCardView cardCash;
    private RadioButton radioCash;

    private Button continueButton;
    private ImageView backButton;

    private TextView amountTextView;
    private double finalAmount;

    private String selectedPaymentMethod = "Cash";

    // Badge Listeners & UI
    private ListenerRegistration unreadListener;
    private ListenerRegistration notificationListener;
    private TextView unreadBadge;
    private TextView unreadNotificationBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        continueButton = findViewById(R.id.button_continue);
        backButton = findViewById(R.id.back_button);
        amountTextView = findViewById(R.id.text_view_amount);

        cardCash = findViewById(R.id.card_cash);
        radioCash = findViewById(R.id.radio_cash);

        finalAmount = getIntent().getDoubleExtra("AMOUNT_TO_BE_PAID", 0.0);
        if (amountTextView != null) {
            amountTextView.setText(String.format(Locale.getDefault(), "PHP %.2f", finalAmount));
        }

        cardCash.setOnClickListener(v -> updateSelection("Cash"));

        updateSelection(selectedPaymentMethod);

        continueButton.setOnClickListener(v -> {
            if (selectedPaymentMethod.equals("Cash")) {
                confirmPayment(selectedPaymentMethod, finalAmount);
            } else {
                // Handle other payments (e.g., PayMongo) here if added later
                Toast.makeText(this, "Only Cash is currently supported.", Toast.LENGTH_SHORT).show();
            }
        });

        backButton.setOnClickListener(v -> finish());

        // Setup Nav and Badges
        setupNavbar();
        setupUnreadMessageListener();
        setupNotificationListener(); // This now uses the fix
    }

    // --- Listener for Chat Badges ---
    private void setupUnreadMessageListener() {
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

    // ---------------------------------------------------------
    // FIXED: Notification Badge Logic (Updated to use 'notifications' collection)
    // ---------------------------------------------------------
    private void setupNotificationListener() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
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

    private void updateSelection(String paymentMethod) {
        selectedPaymentMethod = paymentMethod;
        radioCash.setChecked(paymentMethod.equals("Cash"));
    }

    private void confirmPayment(String method, double amount) {
        Toast.makeText(this, "Payment confirmed via " + method + " for PHP " + amount, Toast.LENGTH_LONG).show();
        Intent resultIntent = new Intent();
        resultIntent.putExtra("PAYMENT_METHOD", method);
        resultIntent.putExtra("FINAL_AMOUNT", amount);
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    private void setupNavbar() {
        unreadBadge = findViewById(R.id.unread_message_badge);
        unreadNotificationBadge = findViewById(R.id.unread_notification_badge);

        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        notificationButton.setOnClickListener(v -> {
            Intent intent = new Intent(PaymentActivity.this, NotificationsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });

        ImageView profileButton = findViewById(R.id.profile_icon_btn);
        profileButton.setOnClickListener(v -> {
            Intent intent = new Intent(PaymentActivity.this, ProfileActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });
        ImageView homeButton = findViewById(R.id.home_icon_btn);
        homeButton.setOnClickListener(v -> {
            FirebaseUser user = mAuth.getCurrentUser();
            if (user == null) {
                Intent intent = new Intent(PaymentActivity.this, MainActivity.class);
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
                            if (type != null && (type.trim().equalsIgnoreCase("Service Provider") || type.trim().equalsIgnoreCase("driver"))) {
                                userType = "Service Provider";
                            }
                        }

                        Intent intent;
                        if (userType.equals("Service Provider")) {
                            intent = new Intent(PaymentActivity.this, ServiceProviderHomepage.class);
                        } else {
                            intent = new Intent(PaymentActivity.this, homepage.class);
                        }

                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to get userType", e);
                        Intent intent = new Intent(PaymentActivity.this, homepage.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    });
        });

        ImageView messageButton = findViewById(R.id.message_icon_btn);
        messageButton.setOnClickListener(v -> {
            Intent intent = new Intent(PaymentActivity.this, ChatInboxActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
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