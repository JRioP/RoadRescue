package fourthyear.roadrescue;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class NonVerifiedHomepage extends AppCompatActivity {

    private TextView emailTextView;
    private Button startVerificationButton, logoutButton;

    private static final String TAG = "NonVerifiedHome";
    private static final String PLACEHOLDER_PHONE = "+639369049879";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_non_verified_homepage);

        emailTextView = findViewById(R.id.emailTextView);
        startVerificationButton = findViewById(R.id.resendVerificationButton);
        logoutButton = findViewById(R.id.logoutButton);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            emailTextView.setText("Please verify your account: \n" + user.getEmail());
        }

        startVerificationButton.setText("Start Phone Verification");
        startVerificationButton.setOnClickListener(v -> startPhoneVerificationFlow());

        logoutButton.setOnClickListener(v -> logoutUser());
    }

    private void startPhoneVerificationFlow() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "No user currently signed in.", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = user.getUid();
        FirebaseFirestore.getInstance().collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    String phoneNumber = documentSnapshot.getString("phone");
                    String email = user.getEmail();
                    if (phoneNumber == null || phoneNumber.isEmpty()) {
                        phoneNumber = PLACEHOLDER_PHONE;
                        Toast.makeText(this, "⚠️ Using placeholder phone number for testing: " + PLACEHOLDER_PHONE, Toast.LENGTH_LONG).show();
                        Log.w(TAG, "Using placeholder phone number: " + PLACEHOLDER_PHONE);
                    }

                    if (phoneNumber != null && !phoneNumber.isEmpty()) {
                        Log.d(TAG, "Starting phone verification for user: " + phoneNumber);
                        Intent phoneVerificationIntent = new Intent(this, VerifyPhone.class);
                        phoneVerificationIntent.putExtra("phone", phoneNumber);
                        phoneVerificationIntent.putExtra("email", email);
                        startActivity(phoneVerificationIntent);
                    } else {
                        Toast.makeText(this, "Phone number is missing. Cannot verify.", Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to retrieve user document for phone number.", e);
                    Toast.makeText(this, "Error fetching phone number. Try again later.", Toast.LENGTH_SHORT).show();
                });
    }

    private void logoutUser() {
        FirebaseAuth.getInstance().signOut();
        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().clear().apply();
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}