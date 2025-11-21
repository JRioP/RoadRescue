package fourthyear.roadrescue;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfileContactInfoActivity extends AppCompatActivity {

    private static final String TAG = "ProfileContactInfo";

    private ImageView backButton;
    private TextView textEmail;
    private TextView textPhone;
    private ImageView buttonAdd;
    private ImageView buttonAddSos;
    private ConstraintLayout navNotification, navHome, navMessage, navProfile;

    private LinearLayout secondaryNumbersContainer;
    private LinearLayout sosContactsContainer;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private DocumentReference userDocRef;

    private ActivityResultLauncher<Intent> contactPickerLauncher;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    // --- NEW: Badge Listeners & UI ---
    private ListenerRegistration unreadListener;
    private ListenerRegistration notificationListener;
    private TextView unreadBadge;
    private TextView unreadNotificationBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_contact_info);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        userDocRef = db.collection("users").document(currentUser.getUid());

        initializeViews();
        initializeLaunchers();

        // --- NEW: Setup Badge Listeners ---
        setupUnreadMessageListener();
        setupNotificationListener(); // Uses the fix

        setupClickListeners(); // Includes updated Navbar logic

        loadUserData();
    }

    private void initializeViews() {
        backButton = findViewById(R.id.back_button);
        textEmail = findViewById(R.id.text_email);
        textPhone = findViewById(R.id.text_phone);
        buttonAdd = findViewById(R.id.button_add);
        buttonAddSos = findViewById(R.id.button_add_sos);

        secondaryNumbersContainer = findViewById(R.id.secondary_numbers_container);
        sosContactsContainer = findViewById(R.id.sos_contacts_container);

        navNotification = findViewById(R.id.nav_notification_layout);
        navHome = findViewById(R.id.nav_home_layout);
        navMessage = findViewById(R.id.nav_message_layout);
        navProfile = findViewById(R.id.nav_profile_layout);

        // --- Init Badge Views ---
        unreadBadge = findViewById(R.id.unread_message_badge);
        unreadNotificationBadge = findViewById(R.id.unread_notification_badge);
    }

    // --- NEW: Badge Logic ---
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
                            if (count != null) totalUnread += count;
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

    private void initializeLaunchers() {
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        launchContactPicker();
                    } else {
                        if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
                            showGoToSettingsDialog();
                        } else {
                            Toast.makeText(this, "Permission is required to select SOS contacts.", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        contactPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri contactUri = result.getData().getData();
                        handleContactSelection(contactUri);
                    }
                });
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());
        buttonAdd.setOnClickListener(v -> showAddNumberDialog());
        buttonAddSos.setOnClickListener(v -> checkAndRequestContactsPermission());

        // --- Navbar Logic ---
        navNotification.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileContactInfoActivity.this, NotificationsActivity.class);
            // --- FIX ADDED HERE ---
            // Prevents creating a new activity if one already exists.
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });

        navMessage.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileContactInfoActivity.this, ChatInboxActivity.class);
            // --- FIX ADDED HERE ---
            // Prevents creating a new activity if one already exists.
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });

        // --- Active State: Profile Button ---
        navProfile.setBackgroundResource(R.drawable.rounded_white_background);
        ImageView profileIcon = findViewById(R.id.profile_icon_btn);
        TextView profileText = findViewById(R.id.profile_text);
        if (profileIcon != null) profileIcon.setColorFilter(Color.BLACK);
        if (profileText != null) {
            profileText.setTextColor(Color.BLACK);
            profileText.setTypeface(null, Typeface.BOLD);
        }
        navProfile.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileContactInfoActivity.this, ProfileActivity.class);
            // --- FIX ADDED HERE ---
            // This is important for navigating "up" to the main profile screen.
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });

        // --- Home Button Fix ---
        // This button's logic is already correct as it clears the stack to go "home".
        navHome.setOnClickListener(v -> {
            if (currentUser == null) {
                startActivity(new Intent(ProfileContactInfoActivity.this, MainActivity.class));
                finish();
                return;
            }

            db.collection("users").document(currentUser.getUid()).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        String userType = "Customer";
                        if (documentSnapshot.exists()) {
                            String type = documentSnapshot.getString("userType");
                            // Robust Check
                            if (type != null && (type.trim().equalsIgnoreCase("Service Provider") || type.trim().equalsIgnoreCase("driver"))) {
                                userType = "Service Provider";
                            }
                        }

                        Intent intent;
                        if (userType.equals("Service Provider")) {
                            intent = new Intent(ProfileContactInfoActivity.this, ServiceProviderHomepage.class);
                        } else {
                            intent = new Intent(ProfileContactInfoActivity.this, homepage.class);
                        }
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Intent intent = new Intent(ProfileContactInfoActivity.this, homepage.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    });
        });
    }

    private void loadUserData() {
        if (currentUser.getEmail() != null) {
            textEmail.setText(currentUser.getEmail());
        } else {
            textEmail.setText("No email provided");
        }

        String authPhone = currentUser.getPhoneNumber();

        userDocRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                String dbPhone = documentSnapshot.getString("phone");

                if (dbPhone != null && !dbPhone.isEmpty()) {
                    textPhone.setText(dbPhone);
                } else if (authPhone != null && !authPhone.isEmpty()) {
                    textPhone.setText(authPhone);
                } else {
                    textPhone.setText("No phone number added");
                }

                secondaryNumbersContainer.removeAllViews();
                sosContactsContainer.removeAllViews();

                List<String> secondaryPhones = (List<String>) documentSnapshot.get("secondary_phones");
                if (secondaryPhones != null) {
                    displaySecondaryPhones(secondaryPhones);
                }

                List<Map<String, Object>> sosContacts = (List<Map<String, Object>>) documentSnapshot.get("sos_contacts");
                if (sosContacts != null) {
                    displaySosContacts(sosContacts);
                }

            } else {
                textPhone.setText(authPhone != null ? authPhone : "No phone number added");
            }
        }).addOnFailureListener(e -> {
            textPhone.setText("Error loading phone");
            Log.e(TAG, "Error loading user data", e);
        });
    }

    private void displaySecondaryPhones(List<String> phones) {
        for (String phone : phones) {
            TextView tv = new TextView(this);
            tv.setText(phone);
            tv.setTextSize(16);
            tv.setPadding(0, 8, 0, 8);
            secondaryNumbersContainer.addView(tv);
        }
    }

    private void displaySosContacts(List<Map<String, Object>> contacts) {
        for (Map<String, Object> contact : contacts) {
            String name = (String) contact.get("name");
            String phone = (String) contact.get("phone");

            TextView tv = new TextView(this);
            tv.setText(name + ": " + phone);
            tv.setTextSize(16);
            tv.setPadding(0, 8, 0, 8);
            sosContactsContainer.addView(tv);
        }
    }

    private void showAddNumberDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Secondary Number");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        input.setHint("+63 912 345 6789");
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newNumber = input.getText().toString().trim();
            if (!newNumber.isEmpty()) {
                saveSecondaryNumber(newNumber);
            } else {
                Toast.makeText(this, "Number cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void saveSecondaryNumber(String newNumber) {
        userDocRef.update("secondary_phones", FieldValue.arrayUnion(newNumber))
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Number added!", Toast.LENGTH_SHORT).show();
                    loadUserData();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void checkAndRequestContactsPermission() {
        String contactPermission = Manifest.permission.READ_CONTACTS;

        if (ContextCompat.checkSelfPermission(this, contactPermission) == PackageManager.PERMISSION_GRANTED) {
            launchContactPicker();
        } else if (shouldShowRequestPermissionRationale(contactPermission)) {
            new AlertDialog.Builder(this)
                    .setTitle("Permission Needed")
                    .setMessage("This app needs permission to read your contacts so you can select an emergency SOS contact.")
                    .setPositiveButton("OK", (dialog, which) -> {
                        requestPermissionLauncher.launch(contactPermission);
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .create()
                    .show();
        } else {
            requestPermissionLauncher.launch(contactPermission);
        }
    }

    private void showGoToSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permission Denied")
                .setMessage("You have permanently denied contact permission. To add an SOS contact, you must enable it in the app settings.")
                .setPositiveButton("Go to Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }

    private void launchContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        contactPickerLauncher.launch(intent);
    }

    private void handleContactSelection(Uri contactUri) {
        String[] contactDetails = getContactDetails(contactUri);
        String contactName = contactDetails[0];
        String contactNumber = contactDetails[1];

        if (contactName != null && contactNumber != null) {
            saveSosContact(contactName, contactNumber);
        } else {
            Toast.makeText(this, "Could not read contact details.", Toast.LENGTH_SHORT).show();
        }
    }

    private String[] getContactDetails(Uri contactUri) {
        String[] details = new String[2];
        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(contactUri, null, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            String id = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID));

            details[0] = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));

            Cursor phoneCursor = cr.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    new String[]{id},
                    null
            );

            if (phoneCursor != null && phoneCursor.moveToFirst()) {
                details[1] = phoneCursor.getString(phoneCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
            }

            if (phoneCursor != null) phoneCursor.close();
        }

        if (cursor != null) cursor.close();
        return details;
    }

    private void saveSosContact(String name, String phone) {
        Map<String, Object> sosContact = new HashMap<>();
        sosContact.put("name", name);
        sosContact.put("phone", phone);

        userDocRef.update("sos_contacts", FieldValue.arrayUnion(sosContact))
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, name + " added to SOS contacts!", Toast.LENGTH_SHORT).show();
                    loadUserData();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (unreadListener != null) unreadListener.remove();
        if (notificationListener != null) notificationListener.remove();
    }
}