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
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.io.IOException;
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

    private String pickupAddress;
    private String destinationAddress;
    private Geocoder geocoder;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        geocoder = new Geocoder(this, Locale.getDefault());

        destinationInput = findViewById(R.id.destination_input);
        destinationInput.setHint("Tap on the map to set your destination.");
        requestServiceButton = findViewById(R.id.request_service_btn);
        editPickupButton = findViewById(R.id.edit_pickup_btn);

        requestServiceButton.setOnClickListener(v -> {
            if (pickupLatLng != null && destinationLatLng != null) {
                sendServiceRequest();
            } else {
                Toast.makeText(this, "Please confirm both your pickup and destination locations.", Toast.LENGTH_LONG).show();
            }
        });

        editPickupButton.setOnClickListener(v -> toggleEditPickupMode());

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
                        StringBuilder sb = new StringBuilder();
                        if (address.getFeatureName() != null) {
                            sb.append(address.getFeatureName());
                        } else if (address.getThoroughfare() != null) {
                            sb.append(address.getThoroughfare());
                        }

                        if (address.getLocality() != null) {
                            sb.append(", ").append(address.getLocality());
                        }

                        addressText = sb.length() > 0 ? sb.toString() : fallbackAddress;
                    }
                } else {
                    Log.w("Geocoder", "No address found for " + latLng);
                    addressText = fallbackAddress;
                }
            } catch (IOException e) {
                Log.e("Geocoder", "Geocoder service not available or network error", e);
                addressText = fallbackAddress;
            }

            final String finalAddressText = addressText;

            handler.post(() -> {
                if (isPickup) {
                    pickupAddress = finalAddressText;
                    Toast.makeText(MapActivity.this, "Pickup set: " + finalAddressText, Toast.LENGTH_SHORT).show();
                } else {
                    destinationAddress = finalAddressText;
                    destinationInput.setText(finalAddressText);
                    Toast.makeText(MapActivity.this, "Destination set: " + finalAddressText, Toast.LENGTH_SHORT).show();
                }
                updateMapWithMarkers();
            });
        });
    }

    private void sendServiceRequest() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to make a request.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (pickupLatLng == null || destinationLatLng == null) {
            Toast.makeText(this, "Pickup and destination must be set.", Toast.LENGTH_SHORT).show();
            return;
        }

        String customerId = currentUser.getUid();
        Log.d("ServiceRequest", "Sending request for user: " + customerId);
        Log.d("ServiceRequest", "Pickup: " + pickupLatLng + ", Dest: " + destinationLatLng);

        Map<String, Object> requestData = new HashMap<>();
        requestData.put("customerId", customerId);
        requestData.put("pickupLat", pickupLatLng.latitude);
        requestData.put("pickupLng", pickupLatLng.longitude);
        requestData.put("destinationLat", destinationLatLng.latitude);
        requestData.put("destinationLng", destinationLatLng.longitude);
        requestData.put("status", "pending");
        requestData.put("timestamp", System.currentTimeMillis());

        if (pickupAddress != null && !pickupAddress.isEmpty() && !pickupAddress.equals("Loading address...")) {
            requestData.put("pickupAddress", pickupAddress);
        } else {
            requestData.put("pickupAddress", String.format("Lat: %.4f, Lng: %.4f", pickupLatLng.latitude, pickupLatLng.longitude));
        }

        if (destinationAddress != null && !destinationAddress.isEmpty() && !destinationAddress.equals("Loading address...")) {
            requestData.put("destinationAddress", destinationAddress);
        } else {
            requestData.put("destinationAddress", String.format("Lat: %.4f, Lng: %.4f", destinationLatLng.latitude, destinationLatLng.longitude));
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("service_requests")
                .add(requestData)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "Request sent! Searching for a provider...", Toast.LENGTH_LONG).show();
                    Log.d("ServiceRequest", "Request created with ID: " + documentReference.getId());

                    requestServiceButton.setVisibility(View.GONE);
                    editPickupButton.setVisibility(View.GONE);
                    destinationInput.setEnabled(false);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to send request. Please try again.", Toast.LENGTH_SHORT).show();
                    Log.e("ServiceRequest", "Error adding document to Firestore", e);
                });
    }

    private void getDeviceLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if (pickupLatLng == null) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    if (location != null) {
                        pickupLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                        pickupAddress = "Loading address...";
                        getAddressFromLatLng(pickupLatLng, true);
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

        if (pickupLatLng != null) {
            String pickupTitle = (pickupAddress != null && !pickupAddress.isEmpty())
                    ? pickupAddress
                    : "Your Location (Pickup)";
            MyMap.addMarker(new MarkerOptions()
                    .position(pickupLatLng)
                    .title(pickupTitle)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            builder.include(pickupLatLng);
            hasPoints = true;
        } else if (isSettingPickup) {
            LatLng mapCenter = MyMap.getCameraPosition().target;
            MyMap.addMarker(new MarkerOptions()
                    .position(mapCenter)
                    .title("Tap to set PICKUP location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
                    .alpha(0.7f));
        }

        if (destinationLatLng != null) {
            String destTitle = (destinationAddress != null && !destinationAddress.isEmpty())
                    ? destinationAddress
                    : "Towed To (Destination Pin)";
            MyMap.addMarker(new MarkerOptions()
                    .position(destinationLatLng)
                    .title(destTitle)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
            builder.include(destinationLatLng);
            hasPoints = true;
        }

        if (hasPoints) {
            if (pickupLatLng != null && destinationLatLng != null) {
                MyMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
            } else if (pickupLatLng != null) {
                MyMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pickupLatLng, 15f));
            } else if (destinationLatLng != null) {
                MyMap.animateCamera(CameraUpdateFactory.newLatLngZoom(destinationLatLng, 15f));
            }
        }

        if (pickupLatLng != null && destinationLatLng != null && !isSettingPickup) {
            requestServiceButton.setVisibility(View.VISIBLE);
            editPickupButton.setVisibility(View.VISIBLE);
        } else {
            requestServiceButton.setVisibility(View.GONE);
            if (!isSettingPickup && pickupLatLng != null) {
                editPickupButton.setVisibility(View.VISIBLE);
            } else if (!isSettingPickup && pickupLatLng == null) {
                editPickupButton.setVisibility(View.GONE);
            }
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
                        Toast.makeText(this, "Location permission is required to find your position. You can set it manually by tapping 'Edit Pickup Location'.", Toast.LENGTH_LONG).show();
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