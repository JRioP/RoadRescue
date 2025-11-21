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
import com.google.firebase.firestore.Query;
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

    // --- RECYCLER VIEW VARIABLES ---
    private RecentAdapter recentAdapter;
    private List<RecentItemModel> recentItemModels;
    // -------------------------------

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private DocumentReference userDocRef;
    private String localSessionId;

    private FusedLocationProviderClient fusedLocationClient;
    private TextView locationTextView;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private ActivityResultLauncher<Intent> contactPickerLauncher;
    private ActivityResultLauncher<String> requestContactsPermissionLauncher;

    private ListenerRegistration unreadListener;
    private ListenerRegistration notificationListener;
    private TextView unreadBadge;
    private TextView unreadNotificationBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_homepage);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            redirectToLogin();
            return;
        }

        checkUserTypeAndRedirect();

        userDocRef = db.collection("users").document(currentUser.getUid());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        initializeLaunchers();
        setupUIComponents();

        // --- INITIALIZE LIST AND ADAPTER ---
        recentItemModels = new ArrayList<>();
        setupRecyclerView();
        loadRecentRequests(); // Calls Firestore
        // -----------------------------------

        setupUnreadMessageListener();
        setupNotificationListener();

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
    private void loadRecentRequests() {
        if (currentUser == null) return;

        db.collection("service_requests")
                .whereEqualTo("customerId", currentUser.getUid())
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(5)
                .get()
                .addOnSuccessListener(snapshots -> {
                    recentItemModels.clear();
                    if (!snapshots.isEmpty()) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            String serviceType = doc.getString("requestType");
                            String address = doc.getString("destinationAddress");
                            if (address == null || address.trim().isEmpty()) {
                                address = doc.getString("pickupAddress");
                            }
                            if (serviceType == null) serviceType = "Service Request";
                            if (address == null) address = "Location Unknown";

                            recentItemModels.add(new RecentItemModel(serviceType, address));
                        }
                        recentAdapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading recent requests", e);
                });
    }

    private void setupRecyclerView() {
        RecyclerView recentRecyclerView = findViewById(R.id.recentRecyclerView);
        if (recentRecyclerView != null) {
            recentAdapter = new RecentAdapter(recentItemModels);
            LinearLayoutManager layoutManager = new LinearLayoutManager(this);
            recentRecyclerView.setLayoutManager(layoutManager);
            recentRecyclerView.addItemDecoration(new ItemSpacingDecoration(16));
            recentRecyclerView.setAdapter(recentAdapter);
        }
    }

    private void checkUserTypeAndRedirect() {
        db.collection("users").document(currentUser.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String type = documentSnapshot.getString("userType");
                        if (type != null && (type.trim().equalsIgnoreCase("Service Provider") || type.trim().equalsIgnoreCase("driver"))) {
                            Intent intent = new Intent(homepage.this, ServiceProviderHomepage.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        }
                    }
                });
    }

    private void setupUnreadMessageListener() {
        if (currentUser == null) return;
        unreadListener = db.collection("chats")
                .whereArrayContains("participantIds", currentUser.getUid())
                .whereEqualTo("status", "active")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;
                    int totalUnread = 0;
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            Long count = doc.getLong("unreadCounts." + currentUser.getUid());
                            if (count != null) totalUnread += count;
                        }
                    }
                    if (unreadBadge != null) unreadBadge.setVisibility(totalUnread > 0 ? View.VISIBLE : View.GONE);
                });
    }

    private void setupNotificationListener() {
        if (currentUser == null) return;
        notificationListener = db.collection("notifications")
                .whereEqualTo("userId", currentUser.getUid())
                .whereEqualTo("read", false)
                .addSnapshotListener((snapshots, e) -> {
                    if (unreadNotificationBadge != null) {
                        unreadNotificationBadge.setVisibility((snapshots != null && !snapshots.isEmpty()) ? View.VISIBLE : View.GONE);
                    }
                });
    }

    private void initializeLaunchers() {
        requestContactsPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) launchContactPicker();
            else if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) showGoToSettingsDialog();
            else Toast.makeText(this, "Permission required for SOS.", Toast.LENGTH_SHORT).show();
        });

        contactPickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri contactUri = result.getData().getData();
                if (contactUri != null) handleContactSelection(contactUri);
            }
        });
    }

    private void onSosButtonClick() {
        if (currentUser == null) return;
        userDocRef.get().addOnSuccessListener(doc -> {
            List<Map<String, Object>> contacts = (List<Map<String, Object>>) doc.get("sos_contacts");
            if (contacts != null && !contacts.isEmpty()) {
                String phone = (String) contacts.get(0).get("phone");
                if (phone != null) sendSosMessage(phone);
                else promptToAddContact();
            } else promptToAddContact();
        });
    }

    @SuppressLint("MissingPermission")
    private void sendSosMessage(String phone) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            double lat = location != null ? location.getLatitude() : 0.0;
            double lng = location != null ? location.getLongitude() : 0.0;
            String mapsLink = String.format(Locale.getDefault(), "http://googleusercontent.com/maps.google.com/maps?q=%.6f,%.6f", lat, lng);
            String name = (currentUser.getDisplayName() != null) ? currentUser.getDisplayName() : "User";

            Intent smsIntent = new Intent(Intent.ACTION_VIEW);
            smsIntent.setData(Uri.parse("smsto:" + phone));
            smsIntent.putExtra("sms_body", "SOS! Emergency from " + name + ". " + mapsLink);
            if (smsIntent.resolveActivity(getPackageManager()) != null) startActivity(smsIntent);
            else Toast.makeText(this, "No SMS app found.", Toast.LENGTH_SHORT).show();
        });
    }

    private void promptToAddContact() {
        new AlertDialog.Builder(this).setTitle("Add Emergency Contact").setMessage("Please select an SOS contact.")
                .setPositiveButton("Select", (d, w) -> checkAndRequestContactsPermission())
                .setNegativeButton("Cancel", null).show();
    }

    private void checkAndRequestContactsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) launchContactPicker();
        else requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
    }

    private void launchContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        contactPickerLauncher.launch(intent);
    }

    private void handleContactSelection(Uri contactUri) {
        String[] details = getContactDetails(contactUri);
        if (details[0] != null && details[1] != null) saveSosContact(details[0], details[1].replaceAll("[^\\d+]", ""));
    }

    private String[] getContactDetails(Uri uri) {
        String[] details = new String[2];
        Cursor cursor = getContentResolver().query(uri, new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            details[0] = cursor.getString(0);
            details[1] = cursor.getString(1);
            cursor.close();
        }
        return details;
    }

    private void saveSosContact(String name, String phone) {
        Map<String, Object> contact = new HashMap<>();
        contact.put("name", name);
        contact.put("phone", phone);
        userDocRef.update("sos_contacts", FieldValue.arrayUnion(contact)).addOnSuccessListener(v -> Toast.makeText(this, "Contact Saved", Toast.LENGTH_SHORT).show());
    }

    private void showGoToSettingsDialog() {
        new AlertDialog.Builder(this).setTitle("Permission Denied").setMessage("Enable contacts in settings.")
                .setPositiveButton("Settings", (d, w) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null))))
                .setNegativeButton("Cancel", null).show();
    }

    private void createNewSession(FirebaseUser user) {
        localSessionId = UUID.randomUUID().toString();
        getSharedPreferences("AppPrefs", MODE_PRIVATE).edit().putString("currentSessionId", localSessionId).apply();
        userDocRef.update("currentSessionId", localSessionId);
    }

    private void setupUIComponents() {
        locationTextView = findViewById(R.id.textView7);
        unreadBadge = findViewById(R.id.unread_message_badge);
        unreadNotificationBadge = findViewById(R.id.unread_notification_badge);

        findViewById(R.id.sos_button).setOnClickListener(v -> onSosButtonClick());
        findViewById(R.id.notification_icon_btn).setOnClickListener(v -> startActivity(new Intent(this, NotificationsActivity.class)));
        findViewById(R.id.profile_icon_btn).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        findViewById(R.id.message_icon_btn).setOnClickListener(v -> startActivity(new Intent(this, ChatInboxActivity.class)));
        ConstraintLayout homeLayout = findViewById(R.id.nav_home_layout);
        ImageView homeIcon = findViewById(R.id.home_icon_btn);
        TextView homeText = findViewById(R.id.home_text);

        if (homeLayout != null) {
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
        View btn = findViewById(id);
        if (btn != null) btn.setOnClickListener(v -> checkVehicleAndProceed(type));
    }

    private void checkVehicleAndProceed(String type) {
        userDocRef.get().addOnSuccessListener(doc -> {
            if (doc.getString("carType") != null && doc.getString("carModel") != null) {
                Intent intent = new Intent(this, MapActivity.class);
                intent.putExtra("REQUEST_TYPE", type);
                startActivity(intent);
            } else showMissingProfileDialog();
        });
    }

    private void showMissingProfileDialog() {
        new AlertDialog.Builder(this).setTitle("Vehicle Info Needed").setMessage("Update profile first.")
                .setPositiveButton("Profile", (d, w) -> startActivity(new Intent(this, ProfileActivity.class)))
                .setNegativeButton("Cancel", null).show();
    }

    private void checkSingleSessionConstraint() {
        userDocRef.get().addOnSuccessListener(doc -> {
            String dbSession = doc.getString("currentSessionId");
            if (dbSession == null) userDocRef.update("currentSessionId", localSessionId);
            else if (!dbSession.equals(localSessionId)) forceSignOut("Logged in on another device.");
        });
    }

    private void forceSignOut(String msg) {
        mAuth.signOut();
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        redirectToLogin();
    }

    private void redirectToLogin() {
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    private void checkLocationPermissionAndFetch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) fetchLastLocation();
        else ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms, @NonNull int[] grants) {
        super.onRequestPermissionsResult(req, perms, grants);
        if (req == LOCATION_PERMISSION_REQUEST_CODE && grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) fetchLastLocation();
    }

    @SuppressLint("MissingPermission")
    private void fetchLastLocation() {
        fusedLocationClient.getLastLocation().addOnSuccessListener(loc -> {
            if (loc != null) {
                new Thread(() -> {
                    try {
                        List<Address> addrs = new Geocoder(this, Locale.getDefault()).getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
                        String city = (addrs != null && !addrs.isEmpty()) ? addrs.get(0).getLocality() : "Unknown";
                        runOnUiThread(() -> locationTextView.setText(city));
                    } catch (IOException e) { }
                }).start();
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