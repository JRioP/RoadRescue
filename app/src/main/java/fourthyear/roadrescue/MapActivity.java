package fourthyear.roadrescue;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView; // Make sure TextView is imported
import android.widget.Toast;
// Import ProgressBar
// import android.widget.ProgressBar; // No longer needed

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;

public class MapActivity extends AppCompatActivity
        implements OnMapReadyCallback, GoogleMap.OnMapClickListener {

    private static final String TAG = "MapActivity";
    private EditText destinationInput;
    private LatLng pickupLatLng;
    private LatLng destinationLatLng;
    private GoogleMap MyMap;
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    private Button requestServiceButton;
    private Button editPickupButton;

    private boolean isSettingPickup = false;

    private String pickupAddress;
    private String destinationAddress;
    private Geocoder geocoder;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentRequestId;
    private ListenerRegistration requestListener;
    private ListenerRegistration providerListener;

    private Marker providerMarker;
    private Marker pickupMarker;

    // --- UI Views for the Tracker Card ---
    private View statusCard;
    private TextView providerNameText;
    private TextView providerSubtitleText;
    private TextView distanceText;
    private TextView etaText;
    private TextView requestTypeText;
    private Button messageButton;
    private Button callButton;
    // --- End UI Views ---


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        geocoder = new Geocoder(this, Locale.getDefault());
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        destinationInput = findViewById(R.id.destination_input);
        destinationInput.setHint("Tap on the map to set your destination.");
        requestServiceButton = findViewById(R.id.request_service_btn);
        editPickupButton = findViewById(R.id.edit_pickup_btn);

        statusCard = findViewById(R.id.status_card);
        providerNameText = findViewById(R.id.provider_name);
        providerSubtitleText = findViewById(R.id.provider_subtitle);
        distanceText = findViewById(R.id.distance_text);
        etaText = findViewById(R.id.eta_text);
        requestTypeText = findViewById(R.id.request_type_text);
        messageButton = findViewById(R.id.message_button);
        callButton = findViewById(R.id.call_button);

        requestServiceButton.setOnClickListener(v -> {
            if (pickupLatLng != null && destinationLatLng != null) {
                sendServiceRequest();
            } else {
                Toast.makeText(this, "Please confirm both your pickup and destination locations.", Toast.LENGTH_LONG).show();
            }
        });

        editPickupButton.setOnClickListener(v -> toggleEditPickupMode());

        messageButton.setOnClickListener(v -> {
            // TODO: Start ChatInboxActivity with the provider's ID
            Toast.makeText(this, "Message feature not implemented.", Toast.LENGTH_SHORT).show();
        });

        callButton.setOnClickListener(v -> {
            // TODO: Start a phone call intent with the provider's phone number
            Toast.makeText(this, "Call feature not implemented.", Toast.LENGTH_SHORT).show();
        });

        // Navigation Listeners
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

        ImageView messageButtonNav = findViewById(R.id.message_icon_btn);
        messageButtonNav.setOnClickListener(v -> {
            Intent intent = new Intent(MapActivity.this, ChatInboxActivity.class);
            startActivity(intent);
        });

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
        checkUserForActiveRequest();
    }

    private void checkUserForActiveRequest() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            return;
        }
        String customerId = currentUser.getUid();

        db.collection("service_requests")
                .whereEqualTo("customerId", customerId)
                .whereIn("status", Arrays.asList("pending", "accepted"))
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                        currentRequestId = doc.getId();
                        String status = doc.getString("status");
                        Log.d(TAG, "Active request found: " + currentRequestId + " with status: " + status);

                        pickupLatLng = new LatLng(doc.getDouble("pickupLat"), doc.getDouble("pickupLng"));
                        destinationLatLng = new LatLng(doc.getDouble("destinationLat"), doc.getDouble("destinationLng"));
                        pickupAddress = doc.getString("pickupAddress");
                        destinationAddress = doc.getString("destinationAddress");

                        lockUiForTracking();

                        if ("accepted".equals(status)) {
                            MyMap.clear();
                            showTrackerCard(doc);
                            String providerId = doc.getString("providerId");
                            if (providerId != null) {
                                listenForProviderLocation(providerId);
                            }
                        } else {
                            // This is 'pending'
                            updateMapWithMarkers();
                            // Set the request type text from the existing request
                            requestTypeText.setText(doc.getString("requestType"));
                            showSearchingUI();
                        }

                        listenForRequestUpdates(currentRequestId);

                    } else if (task.isSuccessful()) {
                        Log.d(TAG, "No active requests found. Starting new request flow.");
                        enableMyLocation();
                    } else {
                        Log.e(TAG, "Error checking for active requests", task.getException());
                        Toast.makeText(this, "Error checking status. Please restart.", Toast.LENGTH_SHORT).show();
                        enableMyLocation();
                    }
                });
    }


    @Override
    public void onMapClick(@NonNull LatLng point) {
        if (isSettingPickup) {
            pickupLatLng = point;
            pickupAddress = "Loading address...";
            getAddressFromLatLng(point, true);
            toggleEditPickupMode();
        } else {
            destinationLatLng = point;
            destinationAddress = "Loading address...";
            destinationInput.setText(destinationAddress);
            getAddressFromLatLng(point, false);
        }
        updateMapWithMarkers();
    }

    private void toggleEditPickupMode() {
        isSettingPickup = !isSettingPickup;
        if (isSettingPickup) {
            Toast.makeText(this, "Tap on the map to set your new pickup location.", Toast.LENGTH_LONG).show();
            editPickupButton.setText("Confirm Pickup");
            destinationInput.setVisibility(View.GONE);
        } else {
            editPickupButton.setText("Edit Pickup Location");
            destinationInput.setVisibility(View.VISIBLE);
        }
        updateMapWithMarkers();
    }

    private void getAddressFromLatLng(LatLng latLng, boolean isPickup) {
        executor.execute(() -> {
            String addressText = "";
            String fallbackAddress = String.format("Lat: %.4f, Lng: %.4f", latLng.latitude, latLng.longitude);

            try {
                List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    addressText = address.getAddressLine(0);
                    if (addressText == null || addressText.isEmpty()) {
                        addressText = fallbackAddress;
                    }
                } else {
                    addressText = fallbackAddress;
                }
            } catch (Exception e) {
                Log.e("Geocoder", "Geocoder service error", e);
                addressText = fallbackAddress;
            }

            final String finalAddressText = addressText;
            handler.post(() -> {
                if (isPickup) {
                    pickupAddress = finalAddressText;
                } else {
                    destinationAddress = finalAddressText;
                    destinationInput.setText(finalAddressText);
                }
                updateMapWithMarkers();
            });
        });
    }

    private void sendServiceRequest() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to make a request.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (pickupLatLng == null || destinationLatLng == null) {
            Toast.makeText(this, "Pickup and destination must be set.", Toast.LENGTH_SHORT).show();
            return;
        }

        String customerId = currentUser.getUid();
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("customerId", customerId);
        requestData.put("pickupLat", pickupLatLng.latitude);
        requestData.put("pickupLng", pickupLatLng.longitude);
        requestData.put("destinationLat", destinationLatLng.latitude);
        requestData.put("destinationLng", destinationLatLng.longitude);
        requestData.put("status", "pending");
        requestData.put("timestamp", FieldValue.serverTimestamp());

        requestData.put("pickupAddress", (pickupAddress != null) ? pickupAddress : String.format("Lat: %.4f, Lng: %.4f", pickupLatLng.latitude, pickupLatLng.longitude));
        requestData.put("destinationAddress", (destinationAddress != null) ? destinationAddress : String.format("Lat: %.4f, Lng: %.4f", destinationLatLng.latitude, destinationLatLng.longitude));

        requestData.put("requestType", "Towing");

        // --- MERGED CODE ---
        // Set the text on the card and show it *before* sending
        requestTypeText.setText("Towing");
        showSearchingUI();
        // --- END MERGED CODE ---

        db.collection("service_requests")
                .add(requestData)
                .addOnSuccessListener(documentReference -> {
                    // Toast.makeText(this, "Request sent! Searching for a provider...", Toast.LENGTH_LONG).show(); // <-- Removed
                    currentRequestId = documentReference.getId();

                    // showSearchingUI(); // <-- Moved up
                    listenForRequestUpdates(currentRequestId);
                    lockUiForTracking();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to send request. Please try again.", Toast.LENGTH_SHORT).show();
                    Log.e("ServiceRequest", "Error adding document to Firestore", e);
                    // If it fails, reset the UI
                    resetUiForNewRequest();
                });
    }

    private void getDeviceLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (pickupLatLng == null) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    pickupLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                    pickupAddress = "Loading address...";
                    getAddressFromLatLng(pickupLatLng, true);
                    updateMapWithMarkers();
                } else {
                    Toast.makeText(MapActivity.this, "Could not get current location. Please use 'Edit Pickup Location' to set it manually.", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void updateMapWithMarkers() {
        if (MyMap == null) return;
        MyMap.clear();

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        boolean hasPoints = false;

        if (pickupLatLng != null) {
            String pickupTitle = (pickupAddress != null && !pickupAddress.isEmpty()) ? pickupAddress : "Your Location (Pickup)";
            pickupMarker = MyMap.addMarker(new MarkerOptions()
                    .position(pickupLatLng)
                    .title(pickupTitle)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            builder.include(pickupLatLng);
            hasPoints = true;
        }

        if (destinationLatLng != null) {
            String destTitle = (destinationAddress != null && !destinationAddress.isEmpty()) ? destinationAddress : "Towed To (Destination Pin)";
            MyMap.addMarker(new MarkerOptions()
                    .position(destinationLatLng)
                    .title(destTitle)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
            builder.include(destinationLatLng);
            hasPoints = true;
        }

        if (hasPoints && providerMarker == null) {
            if (pickupLatLng != null && destinationLatLng != null) {
                MyMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
            } else if (pickupLatLng != null) {
                MyMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pickupLatLng, 15f));
            }
        }
        if (pickupLatLng != null && destinationLatLng != null) {
            if (currentRequestId == null) {
                requestServiceButton.setVisibility(View.VISIBLE);
            }
        } else {
            requestServiceButton.setVisibility(View.GONE);
        }
    }

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
                        Toast.makeText(this, "Location permission is required to find your position.", Toast.LENGTH_LONG).show();
                        editPickupButton.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                MyMap.setMyLocationEnabled(true);
            } catch (SecurityException e) {
                Log.e("MapActivity", "SecurityException on setMyLocationEnabled: " + e.getMessage());
            }
            getDeviceLocation();
            editPickupButton.setVisibility(View.VISIBLE);
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    // --- UI AND LISTENER METHODS ---

    private void lockUiForTracking() {
        requestServiceButton.setVisibility(View.GONE);
        editPickupButton.setVisibility(View.GONE);
        destinationInput.setEnabled(false);
    }

    // --- MERGED CODE ---
    // This method is completely replaced
    private void showSearchingUI() {
        // 1. Make the card visible
        statusCard.setVisibility(View.VISIBLE);

        // 2. Set text to "Searching"
        providerNameText.setText("Searching for a provider...");
        providerSubtitleText.setText("Please wait.");

        // 3. Hide or reset fields that aren't relevant yet
        distanceText.setText("...");
        etaText.setText("...");

        // Use the request type from the data if available, otherwise just "Searching"
        // This text is now set *before* calling this method (in sendServiceRequest or checkUserForActiveRequest)
        // So we just ensure it's not "Searching..." if it was already set.
        if (requestTypeText.getText().toString().isEmpty()) {
            requestTypeText.setText("Searching...");
        }

        // 4. Hide buttons that aren't useful until a provider is found
        messageButton.setVisibility(View.GONE);
        callButton.setVisibility(View.GONE);
    }
    // --- END MERGED CODE ---

    // --- MERGED CODE ---
    // This method is modified
    private void showTrackerCard(DocumentSnapshot doc) {
        statusCard.setVisibility(View.VISIBLE);

        // --- ADDED CODE ---
        // Re-show buttons now that we have a provider
        messageButton.setVisibility(View.VISIBLE);
        callButton.setVisibility(View.VISIBLE);
        // --- END OF ADDED CODE ---

        String requestType = doc.getString("requestType");
        requestTypeText.setText(requestType != null ? requestType : "Service");

        providerNameText.setText("Provider Found");
        providerSubtitleText.setText("Fetching details...");
        distanceText.setText("...");
        etaText.setText("...");
    }
    // --- END MERGED CODE ---

    private void listenForRequestUpdates(String requestId) {
        if (requestId == null) return;
        if (requestListener != null) requestListener.remove();

        requestListener = db.collection("service_requests").document(requestId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Listen failed.", e);
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        String status = snapshot.getString("status");
                        if ("accepted".equals(status)) {
                            String providerId = snapshot.getString("providerId");
                            if (providerId != null) {
                                MyMap.clear();
                                showTrackerCard(snapshot);
                                listenForProviderLocation(providerId);
                            }
                        } else if ("completed".equals(status)) {
                            Toast.makeText(this, "Service Completed!", Toast.LENGTH_LONG).show();
                            if (providerListener != null) providerListener.remove();
                            if (requestListener != null) requestListener.remove();
                            if (providerMarker != null) providerMarker.remove();
                            if (pickupMarker != null) pickupMarker.remove();
                            resetUiForNewRequest();
                        } else if ("pending".equals(status)) {
                            // This ensures the "Searching" UI stays if we are already in pending state
                            // (e.g., on app resume)
                            requestTypeText.setText(snapshot.getString("requestType"));
                            showSearchingUI();
                        }
                    } else {
                        Log.d(TAG, "Current data: null or request cancelled");
                        Toast.makeText(this, "Request was cancelled or completed.", Toast.LENGTH_SHORT).show();
                        resetUiForNewRequest();
                    }
                });
    }

    private void resetUiForNewRequest() {
        statusCard.setVisibility(View.GONE);
        editPickupButton.setVisibility(View.VISIBLE);
        destinationInput.setEnabled(true);
        destinationInput.setText("");
        destinationInput.setHint("Tap on the map to set your destination.");
        destinationLatLng = null;
        currentRequestId = null;

        if (providerListener != null) providerListener.remove();
        if (requestListener != null) requestListener.remove();

        if (providerMarker != null) providerMarker.remove();
        if (pickupMarker != null) pickupMarker.remove();
        providerMarker = null;
        pickupMarker = null;

        // Re-enable location and update markers to show current pickup
        enableMyLocation();
    }

    private void listenForProviderLocation(String providerId) {
        if (providerListener != null) providerListener.remove();

        providerListener = db.collection("users").document(providerId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Provider listen failed.", e);
                        return;
                    }
                    if (snapshot != null && snapshot.exists()) {
                        String name = snapshot.getString("name");
                        if (name != null) {
                            providerNameText.setText(name);
                        }

                        String providerLocText = snapshot.getString("currentLocationAddress");
                        if(providerLocText != null) {
                            providerSubtitleText.setText("En route from " + providerLocText);
                        }

                        GeoPoint geoPoint = snapshot.getGeoPoint("liveLocation");
                        if (geoPoint != null) {
                            LatLng providerLocation = new LatLng(geoPoint.getLatitude(), geoPoint.getLongitude());
                            updateProviderMarker(providerLocation);
                            calculateStraightLineEta(providerLocation);
                        }
                    }
                });
    }

    private void updateProviderMarker(LatLng location) {
        if (MyMap == null) return;

        if (providerMarker == null) {
            providerMarker = MyMap.addMarker(new MarkerOptions()
                    .position(location)
                    .title("Your Provider")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
        } else {
            providerMarker.setPosition(location);
        }

        if (pickupMarker == null && pickupLatLng != null) {
            pickupMarker = MyMap.addMarker(new MarkerOptions()
                    .position(pickupLatLng)
                    .title(pickupAddress != null ? pickupAddress : "Your Location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        } else if (pickupMarker != null && pickupLatLng != null) {
            pickupMarker.setPosition(pickupLatLng);
        }

        if (pickupLatLng != null) {
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            builder.include(pickupLatLng);
            builder.include(location);
            MyMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 150));
        }
    }

    private void calculateStraightLineEta(LatLng providerLocation) {
        if (pickupLatLng == null) return;

        float[] results = new float[1];
        Location.distanceBetween(
                providerLocation.latitude, providerLocation.longitude,
                pickupLatLng.latitude, pickupLatLng.longitude,
                results);

        float distanceInMeters = results[0];
        float distanceInKm = distanceInMeters / 1000;

        // Simple ETA calculation: average speed of 30km/h (2 mins per km)
        int timeInMinutes = (int) (distanceInKm * 2);

        if (timeInMinutes < 1) {
            timeInMinutes = 1;
        }

        distanceText.setText(String.format(Locale.getDefault(), "%.1f km", distanceInKm));
        etaText.setText(String.format(Locale.getDefault(), "~ %d min", timeInMinutes));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (requestListener != null) {
            requestListener.remove();
        }
        if (providerListener != null) {
            providerListener.remove();
        }
        executor.shutdown();
    }
}