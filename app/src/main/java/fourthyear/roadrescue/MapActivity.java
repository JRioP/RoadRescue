package fourthyear.roadrescue;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

// ------------------- NEW FIREBASE IMPORTS -------------------
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;
// -----------------------------------------------------------

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;

public class MapActivity extends AppCompatActivity
        implements OnMapReadyCallback, GoogleMap.OnMapClickListener {

    private EditText destinationInput;
    private LatLng pickupLatLng;
    private LatLng destinationLatLng;
    private GoogleMap MyMap;
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    private Button requestServiceButton;
    private Button editPickupButton;

    private boolean isSettingPickup = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        // --- Find Views ---
        destinationInput = findViewById(R.id.destination_input);
        destinationInput.setHint("Tap on the map to set your destination.");
        requestServiceButton = findViewById(R.id.request_service_btn);
        editPickupButton = findViewById(R.id.edit_pickup_btn);

        // --- Set Click Listener for the Request Button ---
        requestServiceButton.setOnClickListener(v -> {
            if (pickupLatLng != null && destinationLatLng != null) {
                sendServiceRequest(); // <-- Now calls the new Firebase logic
            } else {
                Toast.makeText(this, "Please confirm both your pickup and destination locations.", Toast.LENGTH_LONG).show();
            }
        });

        // --- Set Click Listener for the Edit Pickup Button ---
        editPickupButton.setOnClickListener(v -> toggleEditPickupMode());

        // --- Navigation Buttons (Existing Logic) ---
        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        notificationButton.setOnClickListener(v -> {
            Intent intent = new Intent(MapActivity.this, NotificationsActivity.class);
            startActivity(intent);
        });

        ImageView homeButton = findViewById(R.id.home_icon_btn);
        homeButton.setOnClickListener(v -> {
            Intent intent = new Intent(MapActivity.this, homepage.class);
            startActivity(intent);
        });

        ImageView messageButton = findViewById(R.id.message_icon_btn);
        messageButton.setOnClickListener(v -> {
            Intent intent = new Intent(MapActivity.this, ChatInboxActivity.class);
            startActivity(intent);
        });
        // End of Navigation Buttons

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        initializePermissionLauncher();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        MyMap = googleMap;
        MyMap.setOnMapClickListener(this);
        enableMyLocation();
    }

    @Override
    public void onMapClick(@NonNull LatLng point) {
        // MODIFIED: Check the state to determine what to set
        if (isSettingPickup) {
            pickupLatLng = point;
            // Exit pickup setting mode after selection
            toggleEditPickupMode();
            Toast.makeText(this, "Pickup location set manually.", Toast.LENGTH_SHORT).show();
        } else {
            destinationLatLng = point;
            destinationInput.setText("Destination Set (Tap to change)");
            Toast.makeText(this, "Destination location set.", Toast.LENGTH_SHORT).show();
        }
        updateMapWithMarkers();
    }

    // ------------------- TOGGLE EDIT PICKUP MODE -------------------

    private void toggleEditPickupMode() {
        isSettingPickup = !isSettingPickup;
        if (isSettingPickup) {
            // Inform the user and change the button's appearance/text
            Toast.makeText(this, "Tap on the map to set your new pickup location.", Toast.LENGTH_LONG).show();
            editPickupButton.setText("Confirm Pickup");
            // Optionally hide the destination hint for clarity
            destinationInput.setVisibility(View.GONE);
        } else {
            // Revert back to normal state (setting destination)
            editPickupButton.setText("Edit Pickup Location");
            destinationInput.setVisibility(View.VISIBLE);
        }
        // Force a map redraw to give visual feedback (e.g., a temporary instruction marker)
        updateMapWithMarkers();
    }

    // ------------------- SERVICE REQUEST LOGIC (NEW/REPLACED) -------------------

    /**
     * Sends a service request by writing the customer's ID, pickup, and destination
     * coordinates to the "service_requests" collection in Firebase Firestore.
     */
    private void sendServiceRequest() {
        // 1. Check if user is logged in
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to make a request.", Toast.LENGTH_SHORT).show();
            // TODO: Optionally, redirect to LoginActivity
            return;
        }

        // Location checks are already handled by the button's OnClickListener, but a final check is safe
        if (pickupLatLng == null || destinationLatLng == null) {
            Toast.makeText(this, "Pickup and destination must be set.", Toast.LENGTH_SHORT).show();
            return;
        }

        String customerId = currentUser.getUid();
        Log.d("ServiceRequest", "Sending request for user: " + customerId);
        Log.d("ServiceRequest", "Pickup: " + pickupLatLng + ", Dest: " + destinationLatLng);

        // 2. Create a data object for the request
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("customerId", customerId);
        requestData.put("pickupLat", pickupLatLng.latitude);
        requestData.put("pickupLng", pickupLatLng.longitude);
        requestData.put("destinationLat", destinationLatLng.latitude);
        requestData.put("destinationLng", destinationLatLng.longitude);
        requestData.put("status", "pending"); // Providers will listen for this
        requestData.put("timestamp", System.currentTimeMillis()); // To sort requests by time

        // 3. Get a reference to Firestore and add the new request
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("service_requests")
                .add(requestData) // .add() creates a document with a random ID
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "Request sent! Searching for a provider...", Toast.LENGTH_LONG).show();
                    Log.d("ServiceRequest", "Request created with ID: " + documentReference.getId());

                    // Hide buttons and disable input to prevent multiple requests
                    requestServiceButton.setVisibility(View.GONE);
                    editPickupButton.setVisibility(View.GONE);
                    destinationInput.setEnabled(false); // Disable editing the destination

                    // TODO: (Optional but recommended)
                    // Move to a "waiting" screen or show a progress indicator
                    // while listening for the status of this request to change from "pending" to "accepted".
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to send request. Please try again.", Toast.LENGTH_SHORT).show();
                    Log.e("ServiceRequest", "Error adding document to Firestore", e);
                });
    }

    // ------------------- LOCATION & MAP UPDATES (Minor Changes) -------------------

    private void getDeviceLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // Only get the device's location if the user hasn't already set it manually
        if (pickupLatLng == null) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    if (location != null) {
                        pickupLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                        updateMapWithMarkers();
                    } else {
                        Log.e("MapActivity", "Location is null: Device might be slow or location services are off.");
                        Toast.makeText(MapActivity.this, "Could not get current location. Please use 'Edit Pickup Location' to set it manually.", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }
    }

    private void updateMapWithMarkers() {
        if (MyMap == null) return;

        MyMap.clear();

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        boolean hasPoints = false;

        // 1. Add Pickup Location (User's Current/Selected Location)
        if (pickupLatLng != null) {
            MyMap.addMarker(new MarkerOptions()
                    .position(pickupLatLng)
                    .title("Your Location (Pickup)")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            builder.include(pickupLatLng);
            hasPoints = true;
        } else if (isSettingPickup) {
            // NEW: Optional - Add a temporary instruction marker while setting pickup
            LatLng mapCenter = MyMap.getCameraPosition().target;
            MyMap.addMarker(new MarkerOptions()
                    .position(mapCenter)
                    .title("Tap to set PICKUP location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
                    .alpha(0.7f));
        }

        // 2. Add Destination Location
        if (destinationLatLng != null) {
            MyMap.addMarker(new MarkerOptions()
                    .position(destinationLatLng)
                    .title("Towed To (Destination Pin)")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
            builder.include(destinationLatLng);
            hasPoints = true;
        }

        // 3. Move Camera
        if (hasPoints) {
            if (pickupLatLng != null && destinationLatLng != null) {
                MyMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
            } else if (pickupLatLng != null) {
                MyMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pickupLatLng, 15f));
            } else if (destinationLatLng != null) {
                MyMap.animateCamera(CameraUpdateFactory.newLatLngZoom(destinationLatLng, 15f));
            }
        }

        // 4. Manage Button Visibility
        // Show the request button ONLY if both points are set AND we are NOT in edit mode
        if (pickupLatLng != null && destinationLatLng != null && !isSettingPickup) {
            requestServiceButton.setVisibility(View.VISIBLE);
            editPickupButton.setVisibility(View.VISIBLE); // Keep it visible to allow re-editing
        } else {
            requestServiceButton.setVisibility(View.GONE);
            // Keep edit pickup button visible if we are in edit mode, otherwise show it only when a pickup is set
            if (!isSettingPickup && pickupLatLng != null) {
                editPickupButton.setVisibility(View.VISIBLE);
            } else if (!isSettingPickup && pickupLatLng == null) {
                // Hide it if no location is available yet (initial state)
                editPickupButton.setVisibility(View.GONE);
            }
        }
    }

    // ------------------- PERMISSION LOGIC (Unchanged) -------------------

    private void initializePermissionLauncher() {
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        if (MyMap != null) {
                            try {
                                MyMap.setMyLocationEnabled(true);
                            } catch (SecurityException e) {
                                Log.e("MapActivity", "Location permission missing after grant check.");
                            }
                        }
                        getDeviceLocation();
                    } else {
                        Toast.makeText(this, "Location permission is required to find your position. You can set it manually by tapping 'Edit Pickup Location'.", Toast.LENGTH_LONG).show();
                        // Allow manual setting even without permission
                        editPickupButton.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            try {
                MyMap.setMyLocationEnabled(true);
            } catch (SecurityException e) {
                Log.e("MapActivity", "SecurityException on setMyLocationEnabled: " + e.getMessage());
            }
            getDeviceLocation();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }
}