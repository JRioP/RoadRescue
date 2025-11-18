package fourthyear.roadrescue;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log; // Added for logging
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class ProfileSetupActivity extends AppCompatActivity {

    private static final String TAG = "ProfileSetupActivity"; // Added TAG
    private EditText editTextFirstName;
    private EditText editTextLastName;
    private Button buttonNext;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_setup);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        editTextFirstName = findViewById(R.id.edit_text_first_name);
        editTextLastName = findViewById(R.id.edit_text_last_name);
        buttonNext = findViewById(R.id.button_next);

        buttonNext.setOnClickListener(v -> saveUserName());
    }

    private void saveUserName() {
        String firstName = editTextFirstName.getText().toString().trim();
        String lastName = editTextLastName.getText().toString().trim();

        if (firstName.isEmpty()) {
            editTextFirstName.setError("First name is required");
            editTextFirstName.requestFocus();
            return;
        }

        if (lastName.isEmpty()) {
            editTextLastName.setError("Last name is required");
            editTextLastName.requestFocus();
            return;
        }

        FirebaseUser firebaseUser = mAuth.getCurrentUser();
        if (firebaseUser == null) {
            Toast.makeText(this, "No user logged in. Please restart.", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> userData = new HashMap<>();
        userData.put("firstName", firstName);
        userData.put("lastName", lastName);
        userData.put("fullName", firstName + " " + lastName);
        userData.put("name", firstName + " " + lastName);

        String userId = firebaseUser.getUid();

        // Save data first
        db.collection("users").document(userId)
                .set(userData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(ProfileSetupActivity.this, "Profile Saved!", Toast.LENGTH_SHORT).show();

                    // --- FIX: Check User Type and Redirect to Correct Homepage ---
                    checkUserTypeAndRedirect(userId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving profile", e);
                    Toast.makeText(ProfileSetupActivity.this, "Error saving profile.", Toast.LENGTH_SHORT).show();
                });
    }

    // --- NEW: Helper method to determine redirection ---
    private void checkUserTypeAndRedirect(String userId) {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    String userType = "Customer"; // Default
                    if (documentSnapshot.exists()) {
                        String type = documentSnapshot.getString("userType");
                        // Robust check for "driver" or "Service Provider"
                        if (type != null && (type.trim().equalsIgnoreCase("Service Provider") || type.trim().equalsIgnoreCase("driver"))) {
                            userType = "Service Provider";
                        }
                    }

                    Intent intent;
                    if (userType.equals("Service Provider")) {
                        intent = new Intent(ProfileSetupActivity.this, ServiceProviderHomepage.class);
                    } else {
                        intent = new Intent(ProfileSetupActivity.this, homepage.class);
                    }

                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to fetch user type, defaulting to customer homepage", e);
                    // Default fallback
                    Intent intent = new Intent(ProfileSetupActivity.this, homepage.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
    }
}