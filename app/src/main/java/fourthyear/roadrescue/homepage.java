package fourthyear.roadrescue;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
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
    private DocumentReference userDocRef; // Added for SOS contacts
    private String localSessionId;

    private FusedLocationProviderClient fusedLocationClient;
    private TextView locationTextView;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    // --- NEW: Launchers for permissions and contact picking ---
    private ActivityResultLauncher<Intent> contactPickerLauncher;
    private ActivityResultLauncher<String> requestContactsPermissionLauncher;

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

        // --- NEW: Set up the user document reference ---
        userDocRef = db.collection("users").document(currentUser.getUid());

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // --- NEW: Initialize the launchers ---
        initializeLaunchers();

        setupUIComponents();
        initializeRecentItems();
        setupRecyclerView();

        // Delayed tasks
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.d(TAG, "Running delayed tasks...");
            SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
            localSessionId = prefs.getString("currentSessionId", null);
            Log.d(TAG, "Retrieved session ID: " + localSessionId);

            if (localSessionId == null) {
                Log.w(TAG, "Local session ID is missing. Creating new session.");
                createNewSession(currentUser);
            } else {
                checkSingleSessionConstraint();
            }
            checkLocationPermissionAndFetch();
        }, 250);
    }

    // --- NEW: Methods copied from your example for SOS contacts ---

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
                // Check for the 'sos_contacts' array field from your example
                List<Map<String, Object>> sosContacts = (List<Map<String, Object>>) documentSnapshot.get("sos_contacts");

                if (sosContacts != null && !sosContacts.isEmpty()) {
                    // Contact list exists, get the first one
                    Map<String, Object> firstContact = sosContacts.get(0);
                    String phone = (String) firstContact.get("phone");
                    String name = (String) firstContact.get("name");

                    if (phone != null && !phone.isEmpty()) {
                        Toast.makeText(this, "Sending SOS to " + name, Toast.LENGTH_SHORT).show();
                        sendSosMessage(phone);
                    } else {
                        Toast.makeText(this, "Emergency contact has no phone number.", Toast.LENGTH_SHORT).show();
                        promptToAddContact(); // Prompt to add a new one
                    }
                } else {
                    // Field doesn't exist or is empty
                    promptToAddContact();
                }
            } else {
                // User document doesn't exist? (Should not happen if logged in)
                promptToAddContact();
            }
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error checking SOS contact", e);
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void promptToAddContact() {
        new AlertDialog.Builder(this)
                .setTitle("Add Emergency Contact")
                .setMessage("You have no emergency contact. Please select one from your phone's contacts to use the SOS feature.")
                .setPositiveButton("Select Contact", (dialog, which) -> {
                    checkAndRequestContactsPermission();
                })
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
        // Use ACTION_PICK and Phone.CONTENT_URI to let user pick a phone number directly
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
        contactPickerLauncher.launch(intent);
    }

    private void handleContactSelection(Uri contactUri) {
        String[] contactDetails = getContactDetails(contactUri);
        String contactName = contactDetails[0];
        String contactNumber = contactDetails[1];

        if (contactName != null && contactNumber != null) {
            // Clean the number
            contactNumber = contactNumber.replaceAll("[^\\d+]", "");
            saveSosContact(contactName, contactNumber);
        } else {
            Toast.makeText(this, "Could not read contact details.", Toast.LENGTH_SHORT).show();
        }
    }

    private String[] getContactDetails(Uri contactUri) {
        String[] details = new String[2]; // [Name, Number]
        ContentResolver cr = getContentResolver();

        // This query works directly on the Phone.CONTENT_URI
        Cursor cursor = cr.query(contactUri,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
                null, null, null);

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

        // This uses arrayUnion, just like your example file
        userDocRef.update("sos_contacts", FieldValue.arrayUnion(sosContact))
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, name + " added to SOS contacts! Click SOS again to send.", Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
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

    @SuppressLint("MissingPermission")
    private void sendSosMessage(String phone) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission is needed to send SOS.", Toast.LENGTH_SHORT).show();
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location == null) {
                Toast.makeText(this, "Could not get your location. Please try again.", Toast.LENGTH_SHORT).show();
                return;
            }

            double lat = location.getLatitude();
            double lng = location.getLongitude();
            String mapsLink = "http://maps.google.com/maps?q=loc:" + lat + "," + lng;
            String message = "SOS! This is an emergency message from " +
                    (currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "a user") +
                    ". My current location is: " + mapsLink;

            Intent smsIntent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + phone));
            smsIntent.putExtra("sms_body", message);
            startActivity(smsIntent);

        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to get location for SOS.", Toast.LENGTH_SHORT).show();
        });
    }

    // --- End of new SOS methods ---


    private void createNewSession(FirebaseUser user) {
        String newSessionId = UUID.randomUUID().toString();
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        prefs.edit().putString("currentSessionId", newSessionId).apply();
        localSessionId = newSessionId;
        Log.i(TAG, "Created new session ID: " + newSessionId);

        // Use userDocRef which is already defined
        userDocRef.update("currentSessionId", newSessionId)
                .addOnSuccessListener(aVoid -> Log.i(TAG, "New session saved to Firestore successfully"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update session ID in Firestore", e));
    }

    private void setupUIComponents() {
        locationTextView = findViewById(R.id.textView7);

        Button sosButton = findViewById(R.id.sos_button);
        if (sosButton != null) {
            sosButton.setOnClickListener(v -> onSosButtonClick());
        } else {
            Log.e(TAG, "SOS Button not found! Make sure its ID is 'sos_button'");
        }
        // ------------------------------------

        //Navigation buttons
        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        if (notificationButton != null) {
            notificationButton.setOnClickListener(v -> {
                Intent intent = new Intent(homepage.this, NotificationsActivity.class);
                startActivity(intent);
            });
        }

        ImageView profileButton = findViewById(R.id.profile_icon_btn);
        if (profileButton != null) {
            profileButton.setOnClickListener(v -> {
                Intent intent = new Intent(homepage.this, ProfileActivity.class);
                startActivity(intent);
            });
        }

        ImageView homeButton = findViewById(R.id.home_icon_btn);
        if (homeButton != null) {
            homeButton.setOnClickListener(v -> {
                Intent intent = new Intent(homepage.this, homepage.class);
                startActivity(intent);
            });
        }

        ImageView messageButton = findViewById(R.id.message_icon_btn);
        if (messageButton != null) {
            messageButton.setOnClickListener(v -> {
                Intent intent = new Intent(homepage.this, ChatInboxActivity.class);
                startActivity(intent);
            });
        }


        //Request Buttons
        ConstraintLayout towingButton = findViewById(R.id.towing_btn);
        if (towingButton != null) {
            towingButton.setOnClickListener(v -> {
                Intent intent = new Intent(homepage.this, MapActivity.class);
                intent.putExtra("REQUEST_TYPE", "Towing");
                startActivity(intent);
            });
        }

        ConstraintLayout jumpStartButton = findViewById(R.id.jump_start_btn);
        if (jumpStartButton != null) {
            jumpStartButton.setOnClickListener(v -> {
                Intent intent = new Intent(homepage.this, MapActivity.class);
                intent.putExtra("REQUEST_TYPE", "Jump-Start");
                startActivity(intent);
            });
        }

        ConstraintLayout fuelDeliveryButton = findViewById(R.id.fuel_delivery_btn);
        if (fuelDeliveryButton != null) {
            fuelDeliveryButton.setOnClickListener(v -> {
                Intent intent = new Intent(homepage.this, MapActivity.class);
                intent.putExtra("REQUEST_TYPE", "Fuel Delivery");
                startActivity(intent);
            });
        }
        ConstraintLayout flatTireRepairButton = findViewById(R.id.flat_tire_repair_btn);
        if (flatTireRepairButton != null) {
            flatTireRepairButton.setOnClickListener(v -> {
                Intent intent = new Intent(homepage.this, MapActivity.class);
                intent.putExtra("REQUEST_TYPE", "Flat Tire Repair");
                startActivity(intent);
            });
        }

        ConstraintLayout replaceBatteryButton = findViewById(R.id.replace_battery_btn);
        if (replaceBatteryButton != null) {
            replaceBatteryButton.setOnClickListener(v -> {
                Intent intent = new Intent(homepage.this, MapActivity.class);
                intent.putExtra("REQUEST_TYPE", "Replace Battery");
                startActivity(intent);
            });
        }

        ConstraintLayout gasStationButton = findViewById(R.id.gas_station_btn);
        if (gasStationButton != null) {
            gasStationButton.setOnClickListener(v -> {
                Intent intent = new Intent(homepage.this, MapActivity.class);
                intent.putExtra("REQUEST_TYPE", "Gas Station");
                startActivity(intent);
            });
        }


    }

    private void checkSingleSessionConstraint() {
        // Use the class-level userDocRef
        userDocRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DocumentSnapshot document = task.getResult();
                if (document.exists()) {
                    String dbSessionId = document.getString("currentSessionId");
                    Log.d(TAG, "DB Session ID: " + dbSessionId);
                    Log.d(TAG, "Local Session ID: " + localSessionId);

                    if (dbSessionId == null) {
                        Log.w(TAG, "No session ID in Firestore. Updating with local session.");
                        userDocRef.update("currentSessionId", localSessionId)
                                .addOnSuccessListener(aVoid -> Log.i(TAG, "Session ID created in Firestore"))
                                .addOnFailureListener(e -> Log.e(TAG, "Failed to create session ID in Firestore", e));
                    } else if (!dbSessionId.equals(localSessionId)) {
                        Log.w(TAG, "Session mismatch. Forcing sign out.");
                        forceSignOut("Your account was logged into from another device.");
                    } else {
                        Log.i(TAG, "Session verified as active.");
                    }
                } else {
                    Log.e(TAG, "User document does not exist in Firestore!");
                    Map<String, Object> sessionUpdate = new HashMap<>();
                    sessionUpdate.put("currentSessionId", localSessionId);
                    userDocRef.set(sessionUpdate, SetOptions.merge()) // Use merge to be safe
                            .addOnSuccessListener(aVoid -> Log.i(TAG, "Created session ID in new user document"))
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to create session ID in new user document", e));
                }
            } else {
                Log.e(TAG, "Failed to fetch user document for session check.", task.getException());
                Toast.makeText(this, "Warning: Could not verify session status.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void forceSignOut(String message) {
        Log.i(TAG, "Force sign out: " + message);
        mAuth.signOut();
        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit()
                .remove("currentSessionId")
                .apply();
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
        recentItemModels.add(new RecentItemModel("Fire Station", "Tacban City"));
        recentItemModels.add(new RecentItemModel("Police Station", "Dulag, Leyte"));
        recentItemModels.add(new RecentItemModel("Emergency Shelter", "Basey, Samar"));
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
                    .setMessage("This app needs the location permission to show your current city. Please allow permission.")
                    .setPositiveButton("OK", (dialog, which) -> {
                        ActivityCompat.requestPermissions(homepage.this,
                                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                LOCATION_PERMISSION_REQUEST_CODE);
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        dialog.dismiss();
                        if (locationTextView != null) {
                            locationTextView.setText("Location Denied");
                        }
                    })
                    .create()
                    .show();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchLastLocation();
            } else {
                Toast.makeText(this, "Location permission is required to show your city", Toast.LENGTH_SHORT).show();
                if (locationTextView != null) {
                    locationTextView.setText("Location Denied");
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void fetchLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null && locationTextView != null) {
                        Geocoder geocoder = new Geocoder(homepage.this, Locale.getDefault());
                        try {
                            List<Address> addresses = geocoder.getFromLocation(
                                    location.getLatitude(),
                                    location.getLongitude(),
                                    1);

                            if (addresses != null && !addresses.isEmpty()) {
                                String cityName = addresses.get(0).getLocality();
                                if (cityName != null && !cityName.isEmpty()) {
                                    locationTextView.setText(cityName);
                                } else {
                                    String area = addresses.get(0).getSubAdminArea();
                                    if(area != null) {
                                        locationTextView.setText(area);
                                    } else {
                                        locationTextView.setText("City Not Found");
                                    }
                                }
                            } else {
                                locationTextView.setText("Address Not Found");
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Geocoder failed", e);
                            locationTextView.setText("Can't get address");
                        }
                    } else {
                        if (locationTextView != null) {
                            locationTextView.setText("Location N/A");
                        }
                    }
                })
                .addOnFailureListener(this, e -> {
                    Log.e(TAG, "Failed to get location", e);
                    if (locationTextView != null) {
                        locationTextView.setText("Location Error");
                    }
                });
    }
}