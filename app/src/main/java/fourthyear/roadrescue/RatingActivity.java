package fourthyear.roadrescue;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Map;

public class RatingActivity extends AppCompatActivity {

    private RatingBar ratingBar;
    private EditText commentInput;
    private Button submitButton;
    private TextView skipButton;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private String refId;
    private String serviceProviderId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rating);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        ratingBar = findViewById(R.id.rating_bar);
        commentInput = findViewById(R.id.rating_comment_input);
        submitButton = findViewById(R.id.submit_rating_btn);
        skipButton = findViewById(R.id.skip_rating_btn);

        refId = getIntent().getStringExtra("REFERENCE_ID");
        serviceProviderId = getIntent().getStringExtra("SERVICE_PROVIDER_ID");

        submitButton.setOnClickListener(v -> submitRating());
        skipButton.setOnClickListener(v -> navigateToHome());
    }

    private void submitRating() {
        float ratingValue = ratingBar.getRating();
        String comment = commentInput.getText().toString().trim();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ratingValue == 0) {
            Toast.makeText(this, "Please select a star rating", Toast.LENGTH_SHORT).show();
            return;
        }

        submitButton.setEnabled(false);

        Map<String, Object> ratingData = new HashMap<>();
        ratingData.put("referenceId", refId != null ? refId : "UNKNOWN");
        ratingData.put("serviceProviderId", serviceProviderId != null ? serviceProviderId : "UNKNOWN");
        ratingData.put("userId", currentUser.getUid());
        ratingData.put("rating", ratingValue);
        ratingData.put("comment", comment);
        ratingData.put("timestamp", System.currentTimeMillis());

        db.collection("ratings")
                .add(ratingData)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(RatingActivity.this, "Thank you for your feedback!", Toast.LENGTH_SHORT).show();
                    updateProviderAverageRating(ratingValue);
                })
                .addOnFailureListener(e -> {
                    submitButton.setEnabled(true);
                    Toast.makeText(RatingActivity.this, "Failed to submit rating: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void updateProviderAverageRating(float newRating) {
        if (serviceProviderId == null || serviceProviderId.equals("UNKNOWN") || serviceProviderId.isEmpty()) {
            navigateToHome();
            return;
        }

        final DocumentReference providerRef = db.collection("users").document(serviceProviderId);

        db.runTransaction(new Transaction.Function<Void>() {
            @Override
            public Void apply(Transaction transaction) throws FirebaseFirestoreException {
                DocumentSnapshot snapshot = transaction.get(providerRef);

                double currentAverage = 0.0;
                long totalRatings = 0;

                if (snapshot.exists()) {
                    Double avgObj = snapshot.getDouble("averageRating");
                    Long countObj = snapshot.getLong("ratingCount");

                    if (avgObj != null) currentAverage = avgObj;
                    if (countObj != null) totalRatings = countObj;
                }

                long newTotalRatings = totalRatings + 1;
                double newAverage = ((currentAverage * totalRatings) + newRating) / newTotalRatings;

                transaction.update(providerRef, "averageRating", newAverage);
                transaction.update(providerRef, "ratingCount", newTotalRatings);

                return null;
            }
        }).addOnSuccessListener(aVoid -> {
            navigateToHome();
        }).addOnFailureListener(e -> {
            forceUpdateProvider(serviceProviderId, newRating);
            navigateToHome();
        });
    }

    private void forceUpdateProvider(String providerId, float rating) {
        Map<String, Object> data = new HashMap<>();
        data.put("averageRating", (double) rating);
        data.put("ratingCount", 1L);
        db.collection("users").document(providerId).set(data, SetOptions.merge());
    }

    private void navigateToHome() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            finish();
            return;
        }

        db.collection("users").document(user.getUid()).get().addOnSuccessListener(doc -> {
            Intent intent;
            String type = doc.getString("userType");

            if (type != null && (type.equalsIgnoreCase("Service Provider") || type.equalsIgnoreCase("driver"))) {
                intent = new Intent(RatingActivity.this, ServiceProviderHomepage.class);
            } else {
                intent = new Intent(RatingActivity.this, homepage.class);
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}