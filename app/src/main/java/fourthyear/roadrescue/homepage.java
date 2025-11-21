package fourthyear.roadrescue;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query; // Imported Query
import com.google.firebase.firestore.SetOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class homepage extends AppCompatActivity {

    private static final String TAG = "HomepageActivity";
    private List<RecentItemModel> recentItemModels;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private DocumentReference userDocRef;
    private String localSessionId;

    private FusedLocationProviderClient fusedLocationClient;
    private TextView locationTextView;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    // Launchers
    private ActivityResultLauncher<Intent> contactPickerLauncher;
    private ActivityResultLauncher<String> requestContactsPermissionLauncher;

    // Badges
    private ListenerRegistration unreadListener;
    private ListenerRegistration notificationListener;
    private TextView unreadBadge;
    private TextView unreadNotificationBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_homepage);

        Log.d(TAG, "Homepage onCreate started");

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Log.e(TAG, "No authenticated user found. Redirecting to login.");
            redirectToLogin();
            return;
        }

        // Check User Type & Redirect if needed
        checkUserTypeAndRedirect();

        userDocRef = db.collection("users").document(currentUser.getUid());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        initializeLaunchers();
        setupUIComponents();
        initializeRecentItems();
        setupRecyclerView();

        // Setup Badge Listeners
        setupUnreadMessageListener();
        setupNotificationListener(); // This now uses the fix

        // Delayed tasks
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
            localSessionId = prefs.getString("currentSessionId", null);

            if (localSessionId == null) {
                createNewSession(currentUser);
            } else {
                checkSingleSessionConstraint();
            }
            checkLocationPermissionAndFetch();
        }, 250);
    }

    private void checkUserTypeAndRedirect() {
        db.collection("users").document(currentUser.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String type = documentSnapshot.getString("userType");
                        if (type != null && (type.trim().equalsIgnoreCase("Service Provider") || type.trim().equalsIgnoreCase("driver"))) {
                            Log.d(TAG, "User is a Service Provider. Redirecting...");
                            Intent intent = new Intent(homepage.this, ServiceProviderHomepage.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        }
                    }
                });
    }

    // --- Badge Listeners ---

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

    private void initializeLaunchers() {
        requestContactsPermissionLauncher = registerForActivityResult(
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
                        if (contactUri != null) {
                            handleContactSelection(contactUri);
                        }
                    }
                });
    }

    private void onSosButtonClick() {
        if (currentUser == null) return;

        userDocRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                List<Map<String, Object>> sosContacts = (List<Map<String, Object>>) documentSnapshot.get("sos_contacts");
                if (sosContacts != null && !sosContacts.isEmpty()) {
                    Map<String, Object> firstContact = sosContacts.get(0);
                    String phone = (String) firstContact.get("phone");
                    String name = (String) firstContact.get("name");
                    if (phone != null && !phone.isEmpty()) {
                        Toast.makeText(this, "Sending SOS to " + name, Toast.LENGTH_SHORT).show();
                        sendSosMessage(phone);
                    } else {
                        promptToAddContact();
                    }
                } else {
                    promptToAddContact();
                }
            } else {
                promptToAddContact();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Could not fetch contact. Please check your internet.", Toast.LENGTH_SHORT).show();
        });
    }

    @SuppressLint("MissingPermission")
    private void sendSosMessage(String phone) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission is needed to send SOS.", Toast.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            double lat = 0.0;
            double lng = 0.0;

            if (location != null) {
                lat = location.getLatitude();
                lng = location.getLongitude();
            } else {
                Toast.makeText(this, "Could not get precise location. Sending general SOS.", Toast.LENGTH_SHORT).show();
            }

            String mapsLink = String.format(Locale.getDefault(), "http://googleusercontent.com/maps.google.com/maps?q=%.6f,%.6f", lat, lng);
            String name = (currentUser.getDisplayName() != null) ? currentUser.getDisplayName() : "User";
            String messageBody = "SOS! Emergency from " + name + ". My location: " + mapsLink;

            Intent smsIntent = new Intent(Intent.ACTION_VIEW);
            smsIntent.setData(Uri.parse("smsto:" + phone));
            smsIntent.putExtra("sms_body", messageBody);

            if (smsIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(smsIntent);
            } else {
                Toast.makeText(this, "No SMS app found.", Toast.LENGTH_LONG).show();
            }

        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Location error. Try calling directly.", Toast.LENGTH_LONG).show();
        });
    }

    private void promptToAddContact() {
        new AlertDialog.Builder(this)
                .setTitle("Add Emergency Contact")
                .setMessage("You have no emergency contact. Please select one from your phone's contacts to use the SOS feature.")
                .setPositiveButton("Select Contact", (dialog, which) -> checkAndRequestContactsPermission())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.cancel())
                .show();
    }

    private void checkAndRequestContactsPermission() {
        String contactPermission = Manifest.permission.READ_CONTACTS;
        if (ContextCompat.checkSelfPermission(this, contactPermission) == PackageManager.PERMISSION_GRANTED) {
            launchContactPicker();
        } else if (shouldShowRequestPermissionRationale(contactPermission)) {
            new AlertDialog.Builder(this)
                    .setTitle("Permission Needed")
                    .setMessage("This app needs permission to read your contacts so you can select an emergency SOS contact.")
                    .setPositiveButton("OK", (dialog, which) -> requestContactsPermissionLauncher.launch(contactPermission))
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .create()
                    .show();
        } else {
            requestContactsPermissionLauncher.launch(contactPermission);
        }
    }

    private void launchContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
        contactPickerLauncher.launch(intent);
    }

    private void handleContactSelection(Uri contactUri) {
        String[] contactDetails = getContactDetails(contactUri);
        String contactName = contactDetails[0];
        String contactNumber = contactDetails[1];
        if (contactName != null && contactNumber != null) {
            contactNumber = contactNumber.replaceAll("[^\\d+]", "");
            saveSosContact(contactName, contactNumber);
        } else {
            Toast.makeText(this, "Could not read contact details.", Toast.LENGTH_SHORT).show();
        }
    }

    private String[] getContactDetails(Uri contactUri) {
        String[] details = new String[2];
        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(contactUri, new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            details[0] = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
            details[1] = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
            cursor.close();
        }
        return details;
    }

    private void saveSosContact(String name, String phone) {
        Map<String, Object> sosContact = new HashMap<>();
        sosContact.put("name", name);
        sosContact.put("phone", phone);
        userDocRef.update("sos_contacts", FieldValue.arrayUnion(sosContact))
                .addOnSuccessListener(aVoid -> Toast.makeText(this, name + " added to SOS contacts!", Toast.LENGTH_LONG).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Error saving contact", Toast.LENGTH_SHORT).show());
    }

    private void showGoToSettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Permission Denied")
                .setMessage("You have permanently denied contact permission. Please enable it in settings.")
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

    private void createNewSession(FirebaseUser user) {
        String newSessionId = UUID.randomUUID().toString();
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        prefs.edit().putString("currentSessionId", newSessionId).apply();
        localSessionId = newSessionId;
        userDocRef.update("currentSessionId", newSessionId);
    }

    private void setupUIComponents() {
        locationTextView = findViewById(R.id.textView7);

        unreadBadge = findViewById(R.id.unread_message_badge);
        unreadNotificationBadge = findViewById(R.id.unread_notification_badge);

        Button sosButton = findViewById(R.id.sos_button);
        if (sosButton != null) {
            sosButton.setOnClickListener(v -> onSosButtonClick());
        }

        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        if (notificationButton != null) {
            notificationButton.setOnClickListener(v -> startActivity(new Intent(homepage.this, NotificationsActivity.class)));
        }

        ImageView profileButton = findViewById(R.id.profile_icon_btn);
        if (profileButton != null) {
            profileButton.setOnClickListener(v -> startActivity(new Intent(homepage.this, ProfileActivity.class)));
        }

        ImageView messageButton = findViewById(R.id.message_icon_btn);
        if (messageButton != null) {
            messageButton.setOnClickListener(v -> startActivity(new Intent(homepage.this, ChatInboxActivity.class)));
        }

        ConstraintLayout homeLayout = findViewById(R.id.nav_home_layout);
        ImageView homeIcon = findViewById(R.id.home_icon_btn);
        TextView homeText = findViewById(R.id.home_text);

        if (homeLayout != null) {
            homeLayout.setClickable(false);
            homeLayout.setFocusable(false);
            homeLayout.setBackgroundResource(R.drawable.rounded_white_background);
        }

        if (homeIcon != null) {
            homeIcon.setColorFilter(Color.BLACK);
        }

        if (homeText != null) {
            homeText.setTextColor(Color.BLACK);
            homeText.setTypeface(null, Typeface.BOLD);
        }

        setupRequestButton(R.id.towing_btn, "Towing");
        setupRequestButton(R.id.jump_start_btn, "Jump-Start");
        setupRequestButton(R.id.fuel_delivery_btn, "Fuel Delivery");
        setupRequestButton(R.id.flat_tire_repair_btn, "Flat Tire Repair");
        setupRequestButton(R.id.replace_battery_btn, "Replace Battery");
        setupRequestButton(R.id.gas_station_btn, "Gas Station");
    }

    private void setupRequestButton(int id, String type) {
        ConstraintLayout btn = findViewById(id);
        if (btn != null) {
            btn.setOnClickListener(v -> {
                checkVehicleAndProceed(type);
            });
        }
    }

    private void checkVehicleAndProceed(String serviceType) {
        if (currentUser == null) return;

        userDocRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                String carType = documentSnapshot.getString("carType");
                String carModel = documentSnapshot.getString("carModel");

                // CHECK: Are fields null or empty?
                if (carType != null && !carType.trim().isEmpty() &&
                        carModel != null && !carModel.trim().isEmpty()) {

                    Intent intent = new Intent(homepage.this, MapActivity.class);
                    intent.putExtra("REQUEST_TYPE", serviceType);
                    startActivity(intent);
                } else {
                    showMissingProfileDialog();
                }
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Network error checking profile. Try again.", Toast.LENGTH_SHORT).show();
        });
    }

    private void showMissingProfileDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Vehicle Profile Incomplete")
                .setMessage("You must add your Car Type and Car Model in your profile before requesting a service.")
                .setPositiveButton("Go to Profile", (dialog, which) -> {
                    startActivity(new Intent(homepage.this, ProfileActivity.class));
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void checkSingleSessionConstraint() {
        userDocRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    String dbSessionId = document.getString("currentSessionId");
                    if (dbSessionId == null) {
                        userDocRef.update("currentSessionId", localSessionId);
                    } else if (!dbSessionId.equals(localSessionId)) {
                        forceSignOut("Your account was logged into from another device.");
                    }
                } else {
                    Map<String, Object> sessionUpdate = new HashMap<>();
                    sessionUpdate.put("currentSessionId", localSessionId);
                    userDocRef.set(sessionUpdate, SetOptions.merge());
                }
            }
        });
    }

    private void forceSignOut(String message) {
        mAuth.signOut();
        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().remove("currentSessionId").apply();
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        redirectToLogin();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(homepage.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void initializeRecentItems() {
        recentItemModels = new ArrayList<>();
        recentItemModels.add(new RecentItemModel("Towing", "Tanauan City, Leyte"));
        recentItemModels.add(new RecentItemModel("Fuel", "Palo, Leyte"));
        recentItemModels.add(new RecentItemModel("Jump-Start", "Tacban City"));
        recentItemModels.add(new RecentItemModel("Flat Tire Repair", "Tacban City"));
        recentItemModels.add(new RecentItemModel("Replace Battery", "Dulag, Leyte"));
    }

    private void setupRecyclerView() {
        RecyclerView recentRecyclerView = findViewById(R.id.recentRecyclerView);
        if (recentRecyclerView != null) {
            RecentAdapter recentAdapter = new RecentAdapter(recentItemModels);
            LinearLayoutManager layoutManager = new LinearLayoutManager(this);
            recentRecyclerView.setLayoutManager(layoutManager);
            recentRecyclerView.addItemDecoration(new ItemSpacingDecoration(16));
            recentRecyclerView.setAdapter(recentAdapter);
        }
    }

    private void checkLocationPermissionAndFetch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fetchLastLocation();
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            new AlertDialog.Builder(this)
                    .setTitle("Location Permission Needed")
                    .setMessage("This app needs the location permission to show your current city.")
                    .setPositiveButton("OK", (dialog, which) -> ActivityCompat.requestPermissions(homepage.this,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE))
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .show();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchLastLocation();
            } else if (locationTextView != null) {
                locationTextView.setText("Location Denied");
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void fetchLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return;

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                new Thread(() -> {
                    Geocoder geocoder = new Geocoder(homepage.this, Locale.getDefault());
                    String addressText = "Address Not Found";

                    try {
                        List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                        if (addresses != null && !addresses.isEmpty()) {
                            String cityName = addresses.get(0).getLocality();
                            addressText = cityName != null ? cityName : addresses.get(0).getSubAdminArea();
                        }
                    } catch (IOException e) {
                        addressText = "Can't get address";
                    }

                    // 2. Update the UI back on the Main Thread
                    String finalAddressText = addressText;
                    runOnUiThread(() -> {
                        if (locationTextView != null) {
                            locationTextView.setText(finalAddressText);
                        }
                    });
                }).start();
            } else {
                if (locationTextView != null) locationTextView.setText("Location N/A");
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (unreadListener != null) unreadListener.remove();
        if (notificationListener != null) notificationListener.remove();
    }
}