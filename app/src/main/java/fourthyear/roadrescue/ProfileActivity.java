package fourthyear.roadrescue;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable; // For Glide Listener
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable; // For Glide
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource; // For Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException; // For Glide
import com.bumptech.glide.request.RequestListener; // For Glide
import com.bumptech.glide.request.target.Target; // For Glide
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
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
    private EditText editFullName, editEmail, editPhone;
    private Spinner spinnerGender, spinnerCarType, spinnerCarBrand, spinnerCarModel, spinnerCarYear;

    // Service Provider UI Elements
    private LinearLayout layoutServicesProvided;
    private CheckBox cbTowing, cbFuel, cbTire, cbBattery, cbJumpstart, cbGasStation;
    private boolean isServiceProvider = false;

    private TextView accountSettingsButton;
    private boolean isEditMode = false;

    // Adapters
    private ArrayAdapter<String> genderAdapter, carTypeAdapter, carBrandAdapter, carYearAdapter;
    private ArrayAdapter<String> toyotaAdapter, hondaAdapter, mitsubishiAdapter, fordAdapter, nissanAdapter, hyundaiAdapter, kiaAdapter, suzukiAdapter, chevroletAdapter, otherAdapter;

    // Badge Listeners & UI
    private ListenerRegistration unreadListener;
    private ListenerRegistration notificationListener;
    private TextView unreadBadge;
    private TextView unreadNotificationBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        try {
            storage = FirebaseStorage.getInstance("gs://roadrescue-b46e9.firebasestorage.app");
            storageRef = storage.getReference();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing storage: " + e.getMessage());
            storage = FirebaseStorage.getInstance();
            storageRef = storage.getReference();
        }

        if (currentUser == null) {
            Toast.makeText(this, "No user signed in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        userDocRef = db.collection("users").document(currentUser.getUid());

        findViews();
        setupBottomNavigation();
        setupClickListeners();
        setupSpinners();
        setupImagePicker();

        // Setup Listeners
        setupUnreadMessageListener();
        setupNotificationListener();

        setFieldsEditable(false);
        loadUserProfile();
    }

    private void findViews() {
        profileImageView = findViewById(R.id.profile_image_view);
        profileImageView.setBackground(null); // Ensure no background blocks image

        profileName = findViewById(R.id.profile_name);
        editButton = findViewById(R.id.edit_button);

        editFullName = findViewById(R.id.edit_username);
        editEmail = findViewById(R.id.edit_email);
        editPhone = findViewById(R.id.edit_phone);

        spinnerGender = findViewById(R.id.edit_gender);
        spinnerCarType = findViewById(R.id.edit_car_type);
        spinnerCarBrand = findViewById(R.id.edit_car_brand);
        spinnerCarModel = findViewById(R.id.edit_car_model);
        spinnerCarYear = findViewById(R.id.edit_car_year);

        layoutServicesProvided = findViewById(R.id.layout_services_provided);
        cbTowing = findViewById(R.id.cb_towing);
        cbFuel = findViewById(R.id.cb_fuel);
        cbTire = findViewById(R.id.cb_tire);
        cbBattery = findViewById(R.id.cb_battery);
        cbJumpstart = findViewById(R.id.cb_jumpstart);

        accountSettingsButton = findViewById(R.id.btn_account_settings);
    }

    // --- Badge Listener Logic ---
    private void setupUnreadMessageListener() {
        if (currentUser == null) return;
        String currentUserId = currentUser.getUid();

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

    private void setupNotificationListener() {
        if (currentUser == null) return;
        String currentUserId = currentUser.getUid();

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

    private void setupBottomNavigation() {
        unreadBadge = findViewById(R.id.unread_message_badge);
        unreadNotificationBadge = findViewById(R.id.unread_notification_badge);

        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        if (notificationButton != null) {
            notificationButton.setOnClickListener(v -> startActivity(new Intent(ProfileActivity.this, NotificationsActivity.class)));
        }

        ImageView messageButton = findViewById(R.id.message_icon_btn);
        if (messageButton != null) {
            messageButton.setOnClickListener(v -> startActivity(new Intent(ProfileActivity.this, ChatInboxActivity.class)));
        }

        // Active State Styling
        ConstraintLayout profileLayout = findViewById(R.id.nav_profile_layout);
        ImageView profileIcon = findViewById(R.id.profile_icon_btn);
        TextView profileText = findViewById(R.id.profile_text);

        if (profileLayout != null) {
            profileLayout.setClickable(false);
            profileLayout.setFocusable(false);
            profileLayout.setBackgroundResource(R.drawable.rounded_white_background);
        }
        if (profileIcon != null) profileIcon.setColorFilter(Color.BLACK);
        if (profileText != null) {
            profileText.setTextColor(Color.BLACK);
            profileText.setTypeface(null, Typeface.BOLD);
        }

        // --- HOME BUTTON FIX ---
        ImageView homeButton = findViewById(R.id.home_icon_btn);
        if (homeButton != null) {
            homeButton.setOnClickListener(v -> {
                if (currentUser == null) {
                    startActivity(new Intent(ProfileActivity.this, MainActivity.class));
                    finish();
                    return;
                }

                db.collection("users").document(currentUser.getUid()).get()
                        .addOnSuccessListener(documentSnapshot -> {
                            String userType = "Customer";
                            if (documentSnapshot.exists()) {
                                String type = documentSnapshot.getString("userType");
                                if (type != null && (type.trim().equalsIgnoreCase("Service Provider") || type.trim().equalsIgnoreCase("driver"))) {
                                    userType = "Service Provider";
                                }
                            }

                            Intent intent;
                            if (userType.equals("Service Provider")) {
                                intent = new Intent(ProfileActivity.this, ServiceProviderHomepage.class);
                            } else {
                                intent = new Intent(ProfileActivity.this, homepage.class);
                            }

                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to get userType", e);
                            Intent intent = new Intent(ProfileActivity.this, homepage.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        });
            });
        }
    }

    private void setupClickListeners() {
        profileImageView.setOnClickListener(v -> {
            if (isEditMode) launchImagePicker();
            else Toast.makeText(ProfileActivity.this, "Press the edit button to change your photo", Toast.LENGTH_SHORT).show();
        });
        editButton.setOnClickListener(v -> toggleEditMode());
        findViewById(R.id.back_button).setOnClickListener(v -> finish());
        accountSettingsButton.setOnClickListener(v -> startActivity(new Intent(ProfileActivity.this, ProfileAccountSettingsActivity.class)));
        spinnerCarBrand.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateCarModelSpinner(parent.getItemAtPosition(position).toString());
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { updateCarModelSpinner(null); }
        });
    }

    private void setupSpinners() {
        // Same Adapter Logic as before
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
        for (int i = currentYear; i >= 1980; i--) { years.add(Integer.toString(i)); }
        carYearAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, years);
        carYearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCarYear.setAdapter(carYearAdapter);

        // Init brand adapters
        toyotaAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"Vios", "Corolla", "Camry", "Fortuner", "Hilux", "Other"});
        hondaAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"Civic", "City", "HR-V", "CR-V", "Brio", "Other"});
        mitsubishiAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"Montero Sport", "Mirage", "Xpander", "Strada", "Other"});
        fordAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"Ranger", "Everest", "Territory", "Other"});
        nissanAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"Navara", "Terra", "Almera", "Kicks", "Other"});
        hyundaiAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"Tucson", "Creta", "Stargazer", "Other"});
        kiaAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"Seltos", "Stonic", "Soluto", "Other"});
        suzukiAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"S-Presso", "Jimny", "Ertiga", "Dzire", "Other"});
        chevroletAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"Tracker", "Trailblazer", "Other"});
        otherAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"Other"});

        // Set drop downs
        toyotaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        hondaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mitsubishiAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fordAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        nissanAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        hyundaiAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        kiaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        suzukiAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        chevroletAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        otherAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinnerCarModel.setAdapter(otherAdapter);
    }

    private void updateCarModelSpinner(String brand) {
        if (brand == null) { spinnerCarModel.setAdapter(otherAdapter); return; }
        switch (brand) {
            case "Toyota": spinnerCarModel.setAdapter(toyotaAdapter); break;
            case "Honda": spinnerCarModel.setAdapter(hondaAdapter); break;
            case "Mitsubishi": spinnerCarModel.setAdapter(mitsubishiAdapter); break;
            case "Ford": spinnerCarModel.setAdapter(fordAdapter); break;
            case "Nissan": spinnerCarModel.setAdapter(nissanAdapter); break;
            case "Hyundai": spinnerCarModel.setAdapter(hyundaiAdapter); break;
            case "Kia": spinnerCarModel.setAdapter(kiaAdapter); break;
            case "Suzuki": spinnerCarModel.setAdapter(suzukiAdapter); break;
            case "Chevrolet": spinnerCarModel.setAdapter(chevroletAdapter); break;
            default: spinnerCarModel.setAdapter(otherAdapter); break;
        }
    }

    private void setupImagePicker() {
        pickMediaLauncher = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                Glide.with(ProfileActivity.this).load(uri).circleCrop().into(profileImageView);
                uploadImageToFirebase(uri);
            }
        });
    }

    private void launchImagePicker() {
        pickMediaLauncher.launch(new PickVisualMediaRequest.Builder().setMediaType(PickVisualMedia.ImageOnly.INSTANCE).build());
    }

    private void uploadImageToFirebase(Uri imageUri) {
        Toast.makeText(this, "Uploading photo...", Toast.LENGTH_SHORT).show();
        if (storageRef == null) { Toast.makeText(this, "Storage not initialized", Toast.LENGTH_SHORT).show(); return; }

        StorageReference profileImageRef = storageRef.child("profile_images/" + currentUser.getUid() + ".jpg");
        profileImageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> profileImageRef.getDownloadUrl()
                        .addOnSuccessListener(uri -> {
                            Log.d(TAG, "Image Uploaded. Url: " + uri.toString());
                            saveImageUrlToFirestore(uri.toString());
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to get download URL", e);
                            Toast.makeText(this, "Failed to get image URL", Toast.LENGTH_SHORT).show();
                        }))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Image upload failed", e);
                    Toast.makeText(this, "Image upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void saveImageUrlToFirestore(String imageUrl) {
        Map<String, Object> data = new HashMap<>();
        data.put("profileImageUrl", imageUrl);

        userDocRef.set(data, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Profile picture updated!", Toast.LENGTH_SHORT).show();

                    // FORCE REFRESH
                    Glide.with(ProfileActivity.this)
                            .load(imageUrl)
                            .circleCrop()
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .skipMemoryCache(true)
                            .error(android.R.drawable.stat_notify_error)
                            .listener(new RequestListener<Drawable>() {
                                @Override
                                public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                                    Log.e(TAG, "GLIDE LOAD FAILED: " + e.getMessage());
                                    return false;
                                }
                                @Override
                                public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                                    Log.d(TAG, "GLIDE LOAD SUCCESS");
                                    return false;
                                }
                            })
                            .into(profileImageView);
                })
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
        if (isServiceProvider) {
            cbTowing.setEnabled(editable);
            cbFuel.setEnabled(editable);
            cbTire.setEnabled(editable);
            cbBattery.setEnabled(editable);
            cbJumpstart.setEnabled(editable);
            if(cbGasStation != null) cbGasStation.setEnabled(editable);
        }
    }

    private void loadUserProfile() {
        String authEmail = currentUser.getEmail();
        String authPhone = currentUser.getPhoneNumber();

        userDocRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    String type = document.getString("userType");
                    if (type != null && (type.trim().equalsIgnoreCase("Service Provider") || type.trim().equalsIgnoreCase("driver"))) {
                        isServiceProvider = true;
                        layoutServicesProvided.setVisibility(View.VISIBLE);
                        List<String> services = (List<String>) document.get("servicesProvided");
                        if (services != null) {
                            if (services.contains("Towing")) cbTowing.setChecked(true);
                            if (services.contains("Fuel Delivery")) cbFuel.setChecked(true);
                            if (services.contains("Flat Tire Repair")) cbTire.setChecked(true);
                            if (services.contains("Replace Battery")) cbBattery.setChecked(true);
                            if (services.contains("Jump-Start")) cbJumpstart.setChecked(true);
                            if (services.contains("Gas Station") && cbGasStation != null) cbGasStation.setChecked(true);
                        }
                    } else {
                        isServiceProvider = false;
                        layoutServicesProvided.setVisibility(View.GONE);
                    }

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

                    String displayedName = "User";
                    if (name != null && !name.isEmpty()) displayedName = name;
                    else if (fullName != null && !fullName.isEmpty()) displayedName = fullName;
                    else if (firstName != null && !firstName.isEmpty()) displayedName = firstName + " " + (lastName != null ? lastName : "");

                    // Glide Load
                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        Glide.with(ProfileActivity.this)
                                .load(imageUrl)
                                .circleCrop()
                                .error(android.R.drawable.stat_notify_error)
                                .into(profileImageView);
                    }

                    profileName.setText(displayedName);
                    editFullName.setText(displayedName);
                    editEmail.setText(authEmail);
                    if (dbPhone != null && !dbPhone.isEmpty()) editPhone.setText(dbPhone);
                    else editPhone.setText(authPhone);

                    setSpinnerToValue(spinnerGender, gender, genderAdapter);
                    setSpinnerToValue(spinnerCarType, carType, carTypeAdapter);
                    setSpinnerToValue(spinnerCarBrand, carBrand, carBrandAdapter);
                    updateCarModelSpinner(carBrand);
                    setSpinnerToValue(spinnerCarModel, carModel, (ArrayAdapter<String>) spinnerCarModel.getAdapter());
                    setSpinnerToValue(spinnerCarYear, carYear, carYearAdapter);
                    setFieldsEditable(false);
                } else {
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
        if (value == null || adapter == null) { spinner.setSelection(0); return; }
        int position = adapter.getPosition(value);
        if (position >= 0) spinner.setSelection(position);
        else {
            int otherPosition = adapter.getPosition("Other");
            if (otherPosition >= 0) spinner.setSelection(otherPosition);
            else spinner.setSelection(0);
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
        updates.put("name", newFullName);
        updates.put("fullName", newFullName);
        updates.put("phone", newPhone);
        updates.put("gender", newGender);
        updates.put("carType", newCarType);
        updates.put("carBrand", newCarBrand);
        updates.put("carModel", newCarModel);
        updates.put("carYear", newCarYear);

        if (isServiceProvider) {
            List<String> selectedServices = new ArrayList<>();
            if (cbTowing.isChecked()) selectedServices.add("Towing");
            if (cbFuel.isChecked()) selectedServices.add("Fuel Delivery");
            if (cbTire.isChecked()) selectedServices.add("Flat Tire Repair");
            if (cbBattery.isChecked()) selectedServices.add("Replace Battery");
            if (cbJumpstart.isChecked()) selectedServices.add("Jump-Start");
            if (cbGasStation != null && cbGasStation.isChecked()) selectedServices.add("Gas Station");
            updates.put("servicesProvided", selectedServices);
        }

        userDocRef.set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Profile Saved!", Toast.LENGTH_SHORT).show();
                    profileName.setText(newFullName);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating user profile", e);
                    Toast.makeText(this, "Error saving profile.", Toast.LENGTH_SHORT).show();
                    loadUserProfile();
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (unreadListener != null) unreadListener.remove();
        if (notificationListener != null) notificationListener.remove();
    }
}