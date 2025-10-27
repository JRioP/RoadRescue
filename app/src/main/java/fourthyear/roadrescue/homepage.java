package fourthyear.roadrescue;

import android.Manifest;
import android.app.AlertDialog; // You need to add this import
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class homepage extends AppCompatActivity {

    private static final String TAG = "HomepageActivity";
    private List<RecentItemModel> recentItemModels;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String localSessionId;

    private FusedLocationProviderClient fusedLocationClient;
    private TextView locationTextView;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_homepage);

        Log.d(TAG, "Homepage onCreate started");

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Log.e(TAG, "No authenticated user found. Redirecting to login.");
            redirectToLogin();
            return;
        }

        Log.d(TAG, "User authenticated: " + currentUser.getEmail());

        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        localSessionId = prefs.getString("currentSessionId", null);

        Log.d(TAG, "Retrieved session ID from SharedPreferences: " + localSessionId);

        if (localSessionId == null) {
            Log.w(TAG, "Local session ID is missing. Creating new session.");
            createNewSession(currentUser);
        } else {
            checkSingleSessionConstraint();
        }

        setupUIComponents();
        initializeRecentItems();
        setupRecyclerView();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        checkLocationPermissionAndFetch();
    }

    private void createNewSession(FirebaseUser user) {
        String newSessionId = UUID.randomUUID().toString();
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        prefs.edit().putString("currentSessionId", newSessionId).apply();

        localSessionId = newSessionId;

        Log.i(TAG, "Created new session ID: " + newSessionId);

        db.collection("users").document(user.getUid())
                .update("currentSessionId", newSessionId)
                .addOnSuccessListener(aVoid -> {
                    Log.i(TAG, "New session saved to Firestore successfully");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update session ID in Firestore", e);
                    Toast.makeText(this, "Session initialized locally", Toast.LENGTH_SHORT).show();
                });
    }

    private void setupUIComponents() {
        locationTextView = findViewById(R.id.textView7);

        ConstraintLayout temporaryLogoutButton = findViewById(R.id.constraintLayout10);
        if (temporaryLogoutButton != null) {
            temporaryLogoutButton.setOnClickListener(v -> {
                forceSignOut("You have been successfully logged out.");
            });
        }

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
                Intent intent = new Intent(homepage.this, homepage.class);
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

        ConstraintLayout towingButton = findViewById(R.id.towing_btn);
        if (towingButton != null) {
            towingButton.setOnClickListener(v -> {
                Intent intent = new Intent(homepage.this, MapActivity.class);
                startActivity(intent);
            });
        }

        ConstraintLayout jumpStartButton = findViewById(R.id.jump_start_btn);
        if (jumpStartButton != null) {
            jumpStartButton.setOnClickListener(v -> {
                Intent intent = new Intent(homepage.this, ProviderMapActivity.class);
                startActivity(intent);
            });
        }
    }

    private void checkSingleSessionConstraint() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid())
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            String dbSessionId = document.getString("currentSessionId");

                            Log.d(TAG, "DB Session ID: " + dbSessionId);
                            Log.d(TAG, "Local Session ID: " + localSessionId);

                            if (dbSessionId == null) {
                                Log.w(TAG, "No session ID in Firestore. Updating with local session.");
                                db.collection("users").document(user.getUid())
                                        .update("currentSessionId", localSessionId)
                                        .addOnSuccessListener(aVoid ->
                                                Log.i(TAG, "Session ID created in Firestore"))
                                        .addOnFailureListener(e ->
                                                Log.e(TAG, "Failed to create session ID in Firestore", e));
                            } else if (!dbSessionId.equals(localSessionId)) {
                                Log.w(TAG, "Session mismatch. Forcing sign out.");
                                forceSignOut("Your account was logged into from another device.");
                            } else {
                                Log.i(TAG, "Session verified as active.");
                            }
                        } else {
                            Log.e(TAG, "User document does not exist in Firestore!");
                            db.collection("users").document(user.getUid())
                                    .update("currentSessionId", localSessionId)
                                    .addOnSuccessListener(aVoid ->
                                            Log.i(TAG, "Created session ID in new user document"))
                                    .addOnFailureListener(e ->
                                            Log.e(TAG, "Failed to create session ID in new user document", e));
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
        Intent intent = new Intent(homepage.this, Login.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void initializeRecentItems() {
        recentItemModels = new ArrayList<>();

        recentItemModels.add(new RecentItemModel("Home of BP", "Tanauan City, Leyte"));
        recentItemModels.add(new RecentItemModel("Medical Center", "Palo, Leyte"));
        recentItemModels.add(new RecentItemModel("Fire Station", "Tacloban City"));
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

    // --- THIS IS THE UPDATED METHOD ---
    private void checkLocationPermissionAndFetch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            // Permission is already granted
            fetchLastLocation();
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            // User has denied permission before. Show a rationale.
            new AlertDialog.Builder(this)
                    .setTitle("Location Permission Needed")
                    .setMessage("This app needs the location permission to show your current city. Please allow permission.")
                    .setPositiveButton("OK", (dialog, which) -> {
                        // After rationale, request permission again
                        ActivityCompat.requestPermissions(homepage.this,
                                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                LOCATION_PERMISSION_REQUEST_CODE);
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        // User cancelled the rationale dialog
                        dialog.dismiss();
                        if (locationTextView != null) {
                            locationTextView.setText("Location Denied");
                        }
                    })
                    .create()
                    .show();
        } else {
            // No rationale needed (first time asking), just request the permission
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