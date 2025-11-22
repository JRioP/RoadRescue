package fourthyear.roadrescue;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";

    private FirebaseStorage storage;
    private StorageReference storageRef;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private DocumentReference userDocRef;

    private ImageView profileImageView;

    // Result launcher must be registered unconditionally in the class body
    private final ActivityResultLauncher<PickVisualMediaRequest> pickMediaLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    // Ensure fragment is attached before loading
                    if (isAdded()) {
                        Glide.with(this).load(uri).circleCrop().into(profileImageView);
                        uploadImageToFirebase(uri);
                    }
                }
            });

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

    public ProfileFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.activity_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

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
            Toast.makeText(requireContext(), "No user signed in.", Toast.LENGTH_SHORT).show();
            if (getActivity() != null) getActivity().finish();
            return;
        }

        userDocRef = db.collection("users").document(currentUser.getUid());

        findViews(view);
        setupBottomNavigation(view);
        setupClickListeners(view);
        setupSpinners();

        // Setup Listeners
        setupUnreadMessageListener();
        setupNotificationListener();

        setFieldsEditable(false);
        loadUserProfile();
    }

    private void findViews(View view) {
        profileImageView = view.findViewById(R.id.profile_image_view);
        profileImageView.setBackground(null);

        profileName = view.findViewById(R.id.profile_name);
        editButton = view.findViewById(R.id.edit_button);

        editFullName = view.findViewById(R.id.edit_username);
        editEmail = view.findViewById(R.id.edit_email);
        editPhone = view.findViewById(R.id.edit_phone);

        spinnerGender = view.findViewById(R.id.edit_gender);
        spinnerCarType = view.findViewById(R.id.edit_car_type);
        spinnerCarBrand = view.findViewById(R.id.edit_car_brand);
        spinnerCarModel = view.findViewById(R.id.edit_car_model);
        spinnerCarYear = view.findViewById(R.id.edit_car_year);

        layoutServicesProvided = view.findViewById(R.id.layout_services_provided);
        cbTowing = view.findViewById(R.id.cb_towing);
        cbFuel = view.findViewById(R.id.cb_fuel);
        cbTire = view.findViewById(R.id.cb_tire);
        cbBattery = view.findViewById(R.id.cb_battery);
        cbJumpstart = view.findViewById(R.id.cb_jumpstart);

        accountSettingsButton = view.findViewById(R.id.btn_account_settings);
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

    private void setupBottomNavigation(View view) {
        unreadBadge = view.findViewById(R.id.unread_message_badge);
        unreadNotificationBadge = view.findViewById(R.id.unread_notification_badge);

        ImageView notificationButton = view.findViewById(R.id.notification_icon_btn);
        if (notificationButton != null) {
            notificationButton.setOnClickListener(v -> {
                // CHANGED: Use loadFragment instead of startActivity
                loadFragment(new NotificationsFragment());
            });
        }

        ImageView messageButton = view.findViewById(R.id.message_icon_btn);
        if (messageButton != null) {
            messageButton.setOnClickListener(v -> {
                // CHANGED: Use loadFragment instead of startActivity
                loadFragment(new ChatInboxFragment());
            });
        }

        ConstraintLayout profileLayout = view.findViewById(R.id.nav_profile_layout);
        ImageView profileIcon = view.findViewById(R.id.profile_icon_btn);
        TextView profileText = view.findViewById(R.id.profile_text);

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

        ImageView homeButton = view.findViewById(R.id.home_icon_btn);
        if (homeButton != null) {
            homeButton.setOnClickListener(v -> {
                if (currentUser == null) {
                    startActivity(new Intent(requireContext(), MainActivity.class));
                    if (getActivity() != null) getActivity().finish();
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
                                intent = new Intent(requireContext(), ServiceProviderHomeFragment.class);
                            } else {
                                intent = new Intent(requireContext(), MainActivity.class);
                            }
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to get userType", e);
                            Intent intent = new Intent(requireContext(), MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        });
            });
        }
    }

    private void setupClickListeners(View view) {
        profileImageView.setOnClickListener(v -> {
            if (isEditMode) launchImagePicker();
            else Toast.makeText(requireContext(), "Press the edit button to change your photo", Toast.LENGTH_SHORT).show();
        });
        editButton.setOnClickListener(v -> toggleEditMode());

        view.findViewById(R.id.back_button).setOnClickListener(v -> {
            if (getActivity() != null) getActivity().onBackPressed();
        });

        // FIX: Replaced Intent with loadFragment for the Account Settings
        accountSettingsButton.setOnClickListener(v ->
                loadFragment(new ProfileAccountSettingsFragment())
        );

        spinnerCarBrand.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateCarModelSpinner(parent.getItemAtPosition(position).toString());
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { updateCarModelSpinner(null); }
        });
    }

    // Helper method to switch fragments safely
    private void loadFragment(Fragment fragment) {
        if (getParentFragmentManager() != null) {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .commit();
        }
    }

    private void setupSpinners() {
        if (getContext() == null) return;

        String[] genders = {"Prefer not to say", "Male", "Female", "Other"};
        genderAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, genders);
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGender.setAdapter(genderAdapter);

        String[] carTypes = {"Sedan", "SUV", "Hatchback", "Coupe", "Convertible", "Truck", "Van", "Motorcycle", "Other"};
        carTypeAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, carTypes);
        carTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCarType.setAdapter(carTypeAdapter);

        String[] carBrands = {"Toyota", "Honda", "Mitsubishi", "Ford", "Nissan", "Hyundai", "Kia", "Suzuki", "Chevrolet", "Other"};
        carBrandAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, carBrands);
        carBrandAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCarBrand.setAdapter(carBrandAdapter);

        ArrayList<String> years = new ArrayList<>();
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        years.add("Year");
        for (int i = currentYear; i >= 1980; i--) { years.add(Integer.toString(i)); }
        carYearAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, years);
        carYearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCarYear.setAdapter(carYearAdapter);

        // Init brand adapters
        toyotaAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, new String[]{"Vios", "Corolla", "Camry", "Fortuner", "Hilux", "Other"});
        hondaAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, new String[]{"Civic", "City", "HR-V", "CR-V", "Brio", "Other"});
        mitsubishiAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, new String[]{"Montero Sport", "Mirage", "Xpander", "Strada", "Other"});
        fordAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, new String[]{"Ranger", "Everest", "Territory", "Other"});
        nissanAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, new String[]{"Navara", "Terra", "Almera", "Kicks", "Other"});
        hyundaiAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, new String[]{"Tucson", "Creta", "Stargazer", "Other"});
        kiaAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, new String[]{"Seltos", "Stonic", "Soluto", "Other"});
        suzukiAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, new String[]{"S-Presso", "Jimny", "Ertiga", "Dzire", "Other"});
        chevroletAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, new String[]{"Tracker", "Trailblazer", "Other"});
        otherAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, new String[]{"Other"});

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

    private void launchImagePicker() {
        pickMediaLauncher.launch(new PickVisualMediaRequest.Builder().setMediaType(PickVisualMedia.ImageOnly.INSTANCE).build());
    }

    private void uploadImageToFirebase(Uri imageUri) {
        Toast.makeText(requireContext(), "Uploading photo...", Toast.LENGTH_SHORT).show();
        if (storageRef == null) { Toast.makeText(requireContext(), "Storage not initialized", Toast.LENGTH_SHORT).show(); return; }

        StorageReference profileImageRef = storageRef.child("profile_images/" + currentUser.getUid() + ".jpg");
        profileImageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> profileImageRef.getDownloadUrl()
                        .addOnSuccessListener(uri -> {
                            Log.d(TAG, "Image Uploaded. Url: " + uri.toString());
                            saveImageUrlToFirestore(uri.toString());
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to get download URL", e);
                            if (isAdded()) Toast.makeText(requireContext(), "Failed to get image URL", Toast.LENGTH_SHORT).show();
                        }))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Image upload failed", e);
                    if (isAdded()) Toast.makeText(requireContext(), "Image upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void saveImageUrlToFirestore(String imageUrl) {
        Map<String, Object> data = new HashMap<>();
        data.put("profileImageUrl", imageUrl);

        userDocRef.set(data, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Profile picture updated!", Toast.LENGTH_SHORT).show();

                        // FORCE REFRESH
                        Glide.with(this)
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
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) Toast.makeText(requireContext(), "Failed to save profile picture", Toast.LENGTH_SHORT).show();
                });
    }

    private void toggleEditMode() {
        isEditMode = !isEditMode;
        if (isEditMode) {
            setFieldsEditable(true);
            editButton.setImageResource(R.drawable.save_icon);
            Toast.makeText(requireContext(), "Edit mode enabled", Toast.LENGTH_SHORT).show();
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
                    if (imageUrl != null && !imageUrl.isEmpty() && isAdded()) {
                        Glide.with(this)
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
                if (isAdded()) Toast.makeText(requireContext(), "Error loading profile.", Toast.LENGTH_SHORT).show();
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
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Profile Saved!", Toast.LENGTH_SHORT).show();
                        profileName.setText(newFullName);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating user profile", e);
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Error saving profile.", Toast.LENGTH_SHORT).show();
                        loadUserProfile();
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (unreadListener != null) unreadListener.remove();
        if (notificationListener != null) notificationListener.remove();
    }
}