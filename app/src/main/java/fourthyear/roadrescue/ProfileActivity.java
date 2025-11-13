package fourthyear.roadrescue;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    private static final String TAG = "ProfileActivity";

    private FirebaseStorage storage;
    private StorageReference storageRef;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private DocumentReference userDocRef;

    private ImageView profileImageView;
    private ActivityResultLauncher<PickVisualMediaRequest> pickMediaLauncher;

    private TextView profileName;
    private ImageView editButton;
    // --- Renamed variable for clarity ---
    private EditText editFullName, editEmail, editPhone;
    private Spinner spinnerGender, spinnerCarType, spinnerCarBrand, spinnerCarModel, spinnerCarYear;

    private TextView accountSettingsButton;
    private boolean isEditMode = false;

    private ArrayAdapter<String> genderAdapter;
    private ArrayAdapter<String> carTypeAdapter;
    private ArrayAdapter<String> carBrandAdapter;
    private ArrayAdapter<String> carYearAdapter;

    private ArrayAdapter<String> toyotaAdapter;
    private ArrayAdapter<String> hondaAdapter;
    private ArrayAdapter<String> mitsubishiAdapter;
    private ArrayAdapter<String> fordAdapter;
    private ArrayAdapter<String> nissanAdapter;
    private ArrayAdapter<String> hyundaiAdapter;
    private ArrayAdapter<String> kiaAdapter;
    private ArrayAdapter<String> suzukiAdapter;
    private ArrayAdapter<String> chevroletAdapter;
    private ArrayAdapter<String> otherAdapter;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        setupBottomNavigation();

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();
        storage = FirebaseStorage.getInstance();
        storageRef = storage.getReference();

        if (currentUser == null) {
            Toast.makeText(this, "No user signed in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        userDocRef = db.collection("users").document(currentUser.getUid());

        findViews();
        setupClickListeners();
        setupSpinners();
        setupImagePicker();

        setFieldsEditable(false);
        loadUserProfile();
    }

    private void findViews() {
        profileImageView = findViewById(R.id.profile_image_view);
        profileName = findViewById(R.id.profile_name);
        editButton = findViewById(R.id.edit_button);

        // --- Use new variable name, but find the same ID ---
        editFullName = findViewById(R.id.edit_username);
        editEmail = findViewById(R.id.edit_email);
        editPhone = findViewById(R.id.edit_phone);

        spinnerGender = findViewById(R.id.edit_gender);
        spinnerCarType = findViewById(R.id.edit_car_type);
        spinnerCarBrand = findViewById(R.id.edit_car_brand);
        spinnerCarModel = findViewById(R.id.edit_car_model);
        spinnerCarYear = findViewById(R.id.edit_car_year);

        accountSettingsButton = findViewById(R.id.btn_account_settings);
    }

    private void setupBottomNavigation() {
        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        if (notificationButton != null) {
            notificationButton.setOnClickListener(v -> startActivity(new Intent(ProfileActivity.this, NotificationsActivity.class)));
        }
        ImageView profileButton = findViewById(R.id.profile_icon_btn);
        if (profileButton != null) {
            profileButton.setOnClickListener(v -> startActivity(new Intent(ProfileActivity.this, ProfileActivity.class)));
        }
        ImageView homeButton = findViewById(R.id.home_icon_btn);
        if (homeButton != null) {
            homeButton.setOnClickListener(v -> startActivity(new Intent(ProfileActivity.this, homepage.class)));
        }
        ImageView messageButton = findViewById(R.id.message_icon_btn);
        if (messageButton != null) {
            messageButton.setOnClickListener(v -> startActivity(new Intent(ProfileActivity.this, ChatInboxActivity.class)));
        }
    }

    private void setupClickListeners() {
        profileImageView.setOnClickListener(v -> {
            if (isEditMode) {
                launchImagePicker();
            } else {
                Toast.makeText(ProfileActivity.this, "Press the edit button to change your photo", Toast.LENGTH_SHORT).show();
            }
        });

        editButton.setOnClickListener(v -> toggleEditMode());
        findViewById(R.id.back_button).setOnClickListener(v -> finish());

        accountSettingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileActivity.this, ProfileAccountSettingsActivity.class);
            startActivity(intent);
        });

        spinnerCarBrand.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedBrand = parent.getItemAtPosition(position).toString();
                updateCarModelSpinner(selectedBrand);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                updateCarModelSpinner(null);
            }
        });
    }

    private void setupSpinners() {
        String[] genders = {"Prefer not to say", "Male", "Female", "Other"};
        genderAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, genders);
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGender.setAdapter(genderAdapter);

        String[] carTypes = {"Sedan", "SUV", "Hatchback", "Coupe", "Convertible", "Truck", "Van", "Motorcycle", "Other"};
        carTypeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, carTypes);
        carTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCarType.setAdapter(carTypeAdapter);

        String[] carBrands = {"Toyota", "Honda", "Mitsubishi", "Ford", "Nissan", "Hyundai", "Kia", "Suzuki", "Chevrolet", "Other"};
        carBrandAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, carBrands);
        carBrandAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCarBrand.setAdapter(carBrandAdapter);

        ArrayList<String> years = new ArrayList<>();
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        years.add("Year");
        for (int i = currentYear; i >= 1980; i--) {
            years.add(Integer.toString(i));
        }
        carYearAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, years);
        carYearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCarYear.setAdapter(carYearAdapter);

        String[] toyotaModels = {"Vios", "Corolla", "Camry", "Fortuner", "Hilux", "Other"};
        toyotaAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, toyotaModels);
        toyotaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        String[] hondaModels = {"Civic", "City", "HR-V", "CR-V", "Brio", "Other"};
        hondaAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, hondaModels);
        hondaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        String[] mitsubishiModels = {"Montero Sport", "Mirage", "Xpander", "Strada", "Other"};
        mitsubishiAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, mitsubishiModels);
        mitsubishiAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        String[] fordModels = {"Ranger", "Everest", "Territory", "Other"};
        fordAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, fordModels);
        fordAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        String[] nissanModels = {"Navara", "Terra", "Almera", "Kicks", "Other"};
        nissanAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, nissanModels);
        nissanAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        String[] hyundaiModels = {"Tucson", "Creta", "Stargazer", "Other"};
        hyundaiAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, hyundaiModels);
        hyundaiAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        String[] kiaModels = {"Seltos", "Stonic", "Soluto", "Other"};
        kiaAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, kiaModels);
        kiaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        String[] suzukiModels = {"S-Presso", "Jimny", "Ertiga", "Dzire", "Other"};
        suzukiAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, suzukiModels);
        suzukiAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        String[] chevroletModels = {"Tracker", "Trailblazer", "Other"};
        chevroletAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, chevroletModels);
        chevroletAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        String[] otherModels = {"Other"};
        otherAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, otherModels);
        otherAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinnerCarModel.setAdapter(otherAdapter);
    }

    private void updateCarModelSpinner(String brand) {
        if (brand == null) {
            spinnerCarModel.setAdapter(otherAdapter);
            return;
        }

        switch (brand) {
            case "Toyota":
                spinnerCarModel.setAdapter(toyotaAdapter);
                break;
            case "Honda":
                spinnerCarModel.setAdapter(hondaAdapter);
                break;
            case "Mitsubishi":
                spinnerCarModel.setAdapter(mitsubishiAdapter);
                break;
            case "Ford":
                spinnerCarModel.setAdapter(fordAdapter);
                break;
            case "Nissan":
                spinnerCarModel.setAdapter(nissanAdapter);
                break;
            case "Hyundai":
                spinnerCarModel.setAdapter(hyundaiAdapter);
                break;
            case "Kia":
                spinnerCarModel.setAdapter(kiaAdapter);
                break;
            case "Suzuki":
                spinnerCarModel.setAdapter(suzukiAdapter);
                break;
            case "Chevrolet":
                spinnerCarModel.setAdapter(chevroletAdapter);
                break;
            default:
                spinnerCarModel.setAdapter(otherAdapter);
                break;
        }
    }

    private void setupImagePicker() {
        pickMediaLauncher = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                Log.d(TAG, "Photo selected: " + uri);
                Glide.with(ProfileActivity.this).load(uri).circleCrop().into(profileImageView);
                uploadImageToFirebase(uri);
            } else {
                Log.d(TAG, "No photo selected");
            }
        });
    }

    private void launchImagePicker() {
        pickMediaLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void uploadImageToFirebase(Uri imageUri) {
        Toast.makeText(this, "Uploading photo...", Toast.LENGTH_SHORT).show();
        StorageReference profileImageRef = storageRef.child("profile_images/" + currentUser.getUid() + ".jpg");
        profileImageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot ->
                        profileImageRef.getDownloadUrl()
                                .addOnSuccessListener(uri -> saveImageUrlToFirestore(uri.toString()))
                                .addOnFailureListener(e -> Toast.makeText(this, "Failed to get image URL", Toast.LENGTH_SHORT).show())
                )
                .addOnFailureListener(e -> Toast.makeText(this, "Image upload failed", Toast.LENGTH_SHORT).show());
    }

    private void saveImageUrlToFirestore(String imageUrl) {
        Map<String, Object> data = new HashMap<>();
        data.put("profileImageUrl", imageUrl);

        userDocRef.set(data, SetOptions.merge())
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Profile picture updated!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to save profile picture", Toast.LENGTH_SHORT).show());
    }

    private void toggleEditMode() {
        isEditMode = !isEditMode;
        if (isEditMode) {
            setFieldsEditable(true);
            editButton.setImageResource(R.drawable.save_icon);
            Toast.makeText(this, "Edit mode enabled", Toast.LENGTH_SHORT).show();
            editFullName.requestFocus();
        } else {
            setFieldsEditable(false);
            editButton.setImageResource(R.drawable.edit_icon);
            saveUserProfile();
        }
    }

    private void setFieldsEditable(boolean editable) {
        editEmail.setEnabled(false);
        editFullName.setEnabled(editable);
        editPhone.setEnabled(editable);

        spinnerGender.setEnabled(editable);
        spinnerCarType.setEnabled(editable);
        spinnerCarBrand.setEnabled(editable);
        spinnerCarModel.setEnabled(editable);
        spinnerCarYear.setEnabled(editable);
    }

    private void loadUserProfile() {
        String authEmail = currentUser.getEmail();
        String authPhone = currentUser.getPhoneNumber();

        userDocRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    Log.d(TAG, "User data loaded: " + document.getData());

                    String name = document.getString("name");
                    String fullName = document.getString("fullName");
                    String firstName = document.getString("firstName");
                    String lastName = document.getString("lastName");

                    String dbPhone = document.getString("phone");
                    String gender = document.getString("gender");
                    String carType = document.getString("carType");
                    String carBrand = document.getString("carBrand");
                    String carModel = document.getString("carModel");
                    String carYear = document.getString("carYear");
                    String imageUrl = document.getString("profileImageUrl");

                    String displayedName = "User"; // Default
                    if (name != null && !name.isEmpty()) {
                        displayedName = name;
                    } else if (fullName != null && !fullName.isEmpty()) {
                        displayedName = fullName;
                    } else if (firstName != null && !firstName.isEmpty()) {
                        displayedName = firstName + " " + (lastName != null ? lastName : "");
                    }

                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        Glide.with(ProfileActivity.this).load(imageUrl).circleCrop().into(profileImageView);
                    }

                    profileName.setText(displayedName);
                    editFullName.setText(displayedName);
                    editEmail.setText(authEmail);

                    if (dbPhone != null && !dbPhone.isEmpty()) {
                        editPhone.setText(dbPhone);
                    } else {
                        editPhone.setText(authPhone);
                    }

                    setSpinnerToValue(spinnerGender, gender, genderAdapter);
                    setSpinnerToValue(spinnerCarType, carType, carTypeAdapter);
                    setSpinnerToValue(spinnerCarBrand, carBrand, carBrandAdapter);

                    updateCarModelSpinner(carBrand);

                    setSpinnerToValue(spinnerCarModel, carModel, (ArrayAdapter<String>) spinnerCarModel.getAdapter());
                    setSpinnerToValue(spinnerCarYear, carYear, carYearAdapter);

                } else {
                    Log.d(TAG, "No such user document. Setting defaults.");
                    editEmail.setText(authEmail);
                    editPhone.setText(authPhone);
                    profileName.setText("User");
                }
            } else {
                Log.e(TAG, "Error loading user data", task.getException());
                Toast.makeText(this, "Error loading profile.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setSpinnerToValue(Spinner spinner, String value, ArrayAdapter<String> adapter) {
        if (value == null || adapter == null) {
            spinner.setSelection(0);
            return;
        }

        int position = adapter.getPosition(value);
        if (position >= 0) {
            spinner.setSelection(position);
        } else {
            int otherPosition = adapter.getPosition("Other");
            if (otherPosition >= 0) {
                spinner.setSelection(otherPosition);
            } else {
                spinner.setSelection(0);
            }
        }
    }

    private void saveUserProfile() {
        String newFullName = editFullName.getText().toString().trim();
        String newPhone = editPhone.getText().toString().trim();

        String newGender = spinnerGender.getSelectedItem().toString();
        String newCarType = spinnerCarType.getSelectedItem().toString();
        String newCarBrand = spinnerCarBrand.getSelectedItem().toString();
        String newCarModel = spinnerCarModel.getSelectedItem().toString();
        String newCarYear = spinnerCarYear.getSelectedItem().toString();

        Map<String, Object> updates = new HashMap<>();

        // --- START OF FIX ---
        // Save the edited name to BOTH 'name' and 'fullName' fields
        updates.put("name", newFullName);
        updates.put("fullName", newFullName);
        // --- END OF FIX ---

        updates.put("phone", newPhone);
        updates.put("gender", newGender);
        updates.put("carType", newCarType);
        updates.put("carBrand", newCarBrand);
        updates.put("carModel", newCarModel);
        updates.put("carYear", newCarYear);

        userDocRef.set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User profile updated successfully!");
                    Toast.makeText(this, "Profile Saved!", Toast.LENGTH_SHORT).show();
                    profileName.setText(newFullName);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating user profile", e);
                    Toast.makeText(this, "Error saving profile. Please try again.", Toast.LENGTH_SHORT).show();
                    loadUserProfile();
                });
    }
}