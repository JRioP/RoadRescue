package fourthyear.roadrescue;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
// import android.widget.Button; // Not used in this layout
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

// import androidx.activity.EdgeToEdge; // Not used
import androidx.appcompat.app.AppCompatActivity;
// import androidx.core.graphics.Insets; // Not used
// import androidx.core.view.ViewCompat; // Not used
// import androidx.core.view.WindowInsetsCompat; // Not used

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    private static final String TAG = "ProfileActivity";

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private DocumentReference userDocRef;

    // UI Elements
    private TextView profileName;
    private ImageView editButton;

    // Updated to include all fields from your new XML
    private EditText editUsername, editEmail, editPhone, editGender, editCarType,
            editCarBrand, editCarModel, editCarYear;

    private boolean isEditMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            // No user is signed in, send back to login
            Toast.makeText(this, "No user signed in.", Toast.LENGTH_SHORT).show();
            finish(); // Or redirect to LoginActivity
            return;
        }

        // Get reference to the user's document
        userDocRef = db.collection("users").document(currentUser.getUid());

        // Find UI elements
        profileName = findViewById(R.id.profile_name);
        editButton = findViewById(R.id.edit_button);

        // Find all the EditText fields from the new XML
        editUsername = findViewById(R.id.edit_username);
        editEmail = findViewById(R.id.edit_email);
        editPhone = findViewById(R.id.edit_phone);
        editGender = findViewById(R.id.edit_gender);
        editCarType = findViewById(R.id.edit_car_type);
        editCarBrand = findViewById(R.id.edit_car_brand);
        editCarModel = findViewById(R.id.edit_car_model);
        editCarYear = findViewById(R.id.edit_car_year);


        // Set initial state (fields not editable)
        setFieldsEditable(false);

        // Load user data from Firestore
        loadUserProfile();

        // Set click listener for the Edit/Save button
        editButton.setOnClickListener(v -> toggleEditMode());

        // Back button listener
        findViewById(R.id.back_button).setOnClickListener(v -> finish());
    }

    private void toggleEditMode() {
        isEditMode = !isEditMode;
        if (isEditMode) {
            // Switched to EDIT mode
            setFieldsEditable(true);
            editButton.setImageResource(R.drawable.save_icon); // Make sure you have 'save_icon' in res/drawable
            Toast.makeText(this, "Edit mode enabled", Toast.LENGTH_SHORT).show();
            editUsername.requestFocus();
        } else {
            // Switched to VIEW mode (Save was clicked)
            setFieldsEditable(false);
            editButton.setImageResource(R.drawable.edit_icon); // Make sure you have 'edit_icon' in res/drawable
            saveUserProfile();
        }
    }

    private void setFieldsEditable(boolean editable) {
        // Email is never editable
        editEmail.setEnabled(false);

        // Toggle other fields
        editUsername.setEnabled(editable);
        editPhone.setEnabled(editable);
        editGender.setEnabled(editable);
        editCarType.setEnabled(editable);
        editCarBrand.setEnabled(editable);
        editCarModel.setEnabled(editable);
        editCarYear.setEnabled(editable);
    }

    private void loadUserProfile() {
        userDocRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    Log.d(TAG, "User data loaded: " + document.getData());

                    // Get data from Firestore
                    String name = document.getString("name");
                    String email = document.getString("email");
                    String phone = document.getString("phone");
                    String gender = document.getString("gender");
                    String carType = document.getString("carType");
                    String carBrand = document.getString("carBrand");
                    String carModel = document.getString("carModel");
                    String carYear = document.getString("carYear");

                    // Populate the fields
                    profileName.setText(name != null ? name : "User");
                    editUsername.setText(name);
                    editEmail.setText(email);
                    editPhone.setText(phone != null ? phone : "");
                    editGender.setText(gender != null ? gender : "");
                    editCarType.setText(carType != null ? carType : "");
                    editCarBrand.setText(carBrand != null ? carBrand : "");
                    editCarModel.setText(carModel != null ? carModel : "");
                    editCarYear.setText(carYear != null ? carYear : "");

                } else {
                    Log.d(TAG, "No such user document");
                    Toast.makeText(this, "Error: User profile not found.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.e(TAG, "Error loading user data", task.getException());
                Toast.makeText(this, "Error loading profile.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveUserProfile() {
        // Get new values from EditTexts
        String newUsername = editUsername.getText().toString().trim();
        String newPhone = editPhone.getText().toString().trim();
        String newGender = editGender.getText().toString().trim();
        String newCarType = editCarType.getText().toString().trim();
        String newCarBrand = editCarBrand.getText().toString().trim();
        String newCarModel = editCarModel.getText().toString().trim();
        String newCarYear = editCarYear.getText().toString().trim();

        // Create a map of data to update
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", newUsername);
        updates.put("phone", newPhone);
        updates.put("gender", newGender);
        updates.put("carType", newCarType);
        updates.put("carBrand", newCarBrand);
        updates.put("carModel", newCarModel);
        updates.put("carYear", newCarYear);

        // Update the document in Firestore
        userDocRef.update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User profile updated successfully!");
                    Toast.makeText(this, "Profile Saved!", Toast.LENGTH_SHORT).show();
                    // Update the main name display
                    profileName.setText(newUsername);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating user profile", e);
                    Toast.makeText(this, "Error saving profile. Please try again.", Toast.LENGTH_SHORT).show();
                    // (Optional) Reload the old data to revert changes
                    loadUserProfile();
                });
    }
}