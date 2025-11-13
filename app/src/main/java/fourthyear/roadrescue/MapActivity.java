package fourthyear.roadrescue;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.ListenerRegistration;

import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.android.PolyUtil;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.TravelMode;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
    private Polyline mProviderToPickupLine;
    private Polyline mPickupToDestinationLine; // <-- ADDED FOR PREVIEW ROUTE

    private GeoApiContext mGeoApiContext = null;

    private View statusCard;
    private TextView providerNameText;
    private TextView providerSubtitleText;
    private TextView distanceText;
    private TextView etaText;
    private TextView requestTypeText;
    private Button messageButton;
    private Button callButton;
    private ActivityResultLauncher<Intent> paymentLauncher;

    private String selectedRequestType;
    private double calculatedAmount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        selectedRequestType = getIntent().getStringExtra("REQUEST_TYPE");
        if (selectedRequestType == null || selectedRequestType.isEmpty()) {
            selectedRequestType = "Towing";
        }

        geocoder = new Geocoder(this, Locale.getDefault());
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        if (mGeoApiContext == null) {
            mGeoApiContext = new GeoApiContext.Builder()
                    .apiKey(BuildConfig.MAPS_API_KEY)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();
        }

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
                calculatedAmount = calculateServiceAmount();
                Intent intent = new Intent(MapActivity.this, PaymentActivity.class);
                intent.putExtra("AMOUNT_TO_BE_PAID", calculatedAmount);
                paymentLauncher.launch(intent);
            } else {
                Toast.makeText(this, "Please confirm both your pickup and destination locations.", Toast.LENGTH_LONG).show();
            }
        });

        editPickupButton.setOnClickListener(v -> toggleEditPickupMode());
        messageButton.setOnClickListener(v -> Toast.makeText(this, "Message feature not implemented.", Toast.LENGTH_SHORT).show());
        callButton.setOnClickListener(v -> Toast.makeText(this, "Call feature not implemented.", Toast.LENGTH_SHORT).show());

        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        notificationButton.setOnClickListener(v -> startActivity(new Intent(MapActivity.this, NotificationsActivity.class)));

        ImageView homeButton = findViewById(R.id.home_icon_btn);
        homeButton.setOnClickListener(v -> startActivity(new Intent(MapActivity.this, homepage.class)));

        ImageView messageButtonNav = findViewById(R.id.message_icon_btn);
        messageButtonNav.setOnClickListener(v -> startActivity(new Intent(MapActivity.this, ChatInboxActivity.class)));

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        initializePermissionLauncher();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        ImageView backButton = findViewById(R.id.back_btn);
        backButton.setOnClickListener(v -> finish());

        paymentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        String paymentMethod = result.getData().getStringExtra("PAYMENT_METHOD");
                        if (paymentMethod != null) {
                            sendServiceRequest(paymentMethod);
                        } else {
                            Toast.makeText(this, "Could not get payment method.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "Payment was cancelled.", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        MyMap = googleMap;
        MyMap.setOnMapClickListener(this);
        checkUserForActiveRequest();
    }

    private void checkUserForActiveRequest() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;
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
                        selectedRequestType = doc.getString("requestType");

                        lockUiForTracking();

                        if ("accepted".equals(status)) {
                            MyMap.clear();
                            showTrackerCard(doc);
                            String providerId = doc.getString("providerId");
                            if (providerId != null) {
                                listenForProviderLocation(providerId);
                            }
                        } else {
                            updateMapWithMarkers();
                            requestTypeText.setText(selectedRequestType);
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
        if (currentRequestId != null) {
            Toast.makeText(this, "A service request is already in progress.", Toast.LENGTH_SHORT).show();
            return;
        }

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
            @SuppressLint("DefaultLocale") String fallbackAddress = String.format("Lat: %.4f, Lng: %.4f", latLng.latitude, latLng.longitude);

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

    @SuppressLint("DefaultLocale")
    private void sendServiceRequest(String paymentMethod) {
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
        requestData.put("requestType", selectedRequestType);
        requestData.put("paymentMethod", paymentMethod);
        requestData.put("amount", calculatedAmount);

        requestTypeText.setText(selectedRequestType);
        showSearchingUI();

        db.collection("service_requests")
                .add(requestData)
                .addOnSuccessListener(documentReference -> {
                    currentRequestId = documentReference.getId();
                    listenForRequestUpdates(currentRequestId);
                    lockUiForTracking();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to send request. Please try again.", Toast.LENGTH_SHORT).show();
                    Log.e("ServiceRequest", "Error adding document to Firestore", e);
                    resetUiForNewRequest();
                });
    }

    private double calculateServiceAmount() {
        if (pickupLatLng == null || destinationLatLng == null) {
            return 0.0;
        }

        float[] results = new float[1];
        Location.distanceBetween(
                pickupLatLng.latitude, pickupLatLng.longitude,
                destinationLatLng.latitude, destinationLatLng.longitude,
                results);

        float distanceInMeters = results[0];
        float distanceInKm = distanceInMeters / 1000;

        double baseFee = 0.0;
        double perKmCharge = 0.0;

        switch (selectedRequestType) {
            case "Towing":
                baseFee = 500.0;
                perKmCharge = 50.0;
                break;
            case "Fuel Delivery":
                baseFee = 250.0;
                perKmCharge = 0.0;
                break;
            case "Flat Tire Repair":
                baseFee = 300.0;
                perKmCharge = 10.0;
                break;
            case "Replace Battery":
                baseFee = 350.0;
                perKmCharge = 10.0;
                break;
            default:
                baseFee = 150.0;
                perKmCharge = 5.0;
                break;
        }

        double totalAmount = baseFee + (distanceInKm * perKmCharge);
        return Math.round(totalAmount * 100.0) / 100.0;
    }

    @SuppressLint("MissingPermission")
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

        if (mProviderToPickupLine != null) {
            mProviderToPickupLine.remove();
        }
        if (mPickupToDestinationLine != null) {
            mPickupToDestinationLine.remove();
        }

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
                // --- FIX: CALL METHOD TO DRAW PREVIEW ROUTE ---
                drawPickupToDestinationRoute(pickupLatLng, destinationLatLng);
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

    @SuppressLint("MissingPermission")
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

    private void lockUiForTracking() {
        requestServiceButton.setVisibility(View.GONE);
        editPickupButton.setVisibility(View.GONE);
        destinationInput.setEnabled(false);
    }

    private void showSearchingUI() {
        statusCard.setVisibility(View.VISIBLE);
        providerNameText.setText("Searching for a provider...");
        providerSubtitleText.setText("Please wait.");
        distanceText.setText("...");
        etaText.setText("...");
        if (requestTypeText.getText().toString().isEmpty() && selectedRequestType != null) {
            requestTypeText.setText(selectedRequestType);
        } else if (requestTypeText.getText().toString().isEmpty()) {
            requestTypeText.setText("Searching...");
        }
        messageButton.setVisibility(View.GONE);
        callButton.setVisibility(View.GONE);
    }

    private void showTrackerCard(DocumentSnapshot doc) {
        statusCard.setVisibility(View.VISIBLE);
        messageButton.setVisibility(View.VISIBLE);
        callButton.setVisibility(View.VISIBLE);
        String requestType = doc.getString("requestType");
        requestTypeText.setText(requestType != null ? requestType : "Service");
        providerNameText.setText("Provider Found");
        providerSubtitleText.setText("Fetching details...");
        distanceText.setText("...");
        etaText.setText("...");
    }

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

                        if ("accepted".equals(status) && providerListener == null) {
                            String providerId = snapshot.getString("providerId");
                            if (providerId != null) {
                                MyMap.clear();
                                // --- FIX: Clear the preview line ---
                                if (mPickupToDestinationLine != null) {
                                    mPickupToDestinationLine.remove();
                                    mPickupToDestinationLine = null;
                                }
                                showTrackerCard(snapshot);
                                listenForProviderLocation(providerId);
                            }
                        } else if ("completed".equals(status)) {
                            Toast.makeText(this, "Service Completed!", Toast.LENGTH_LONG).show();
                            if (providerListener != null) providerListener.remove();
                            if (requestListener != null) requestListener.remove();
                            if (providerMarker != null) providerMarker.remove();
                            if (pickupMarker != null) pickupMarker.remove();
                            if (mProviderToPickupLine != null) {
                                mProviderToPickupLine.remove();
                            }
                            mProviderToPickupLine = null;
                            resetUiForNewRequest();
                        } else if ("pending".equals(status)) {
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

        if (providerListener != null) {
            providerListener.remove();
            providerListener = null;
        }
        if (requestListener != null) {
            requestListener.remove();
            requestListener = null;
        }

        if (providerMarker != null) providerMarker.remove();
        if (pickupMarker != null) pickupMarker.remove();
        if (mProviderToPickupLine != null) {
            mProviderToPickupLine.remove();
        }
        // --- FIX: Clear the preview line ---
        if (mPickupToDestinationLine != null) {
            mPickupToDestinationLine.remove();
        }

        providerMarker = null;
        pickupMarker = null;
        mProviderToPickupLine = null;
        mPickupToDestinationLine = null;

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
                        String providerLocText = snapshot.getString("currentLocationAddress");
                        GeoPoint geoPoint = snapshot.getGeoPoint("liveLocation");

                        if (name != null) {
                            providerNameText.setText(name);
                        }

                        if (providerLocText != null && !providerLocText.isEmpty()) {
                            providerSubtitleText.setText("En route from " + providerLocText);
                        } else if (name != null) {
                            providerSubtitleText.setText("Provider accepted, awaiting location...");
                        } else {
                            providerSubtitleText.setText("Fetching details...");
                        }

                        if (geoPoint != null) {
                            LatLng providerLocation = new LatLng(geoPoint.getLatitude(), geoPoint.getLongitude());
                            updateProviderMarkerAndRoute(providerLocation);
                        }
                    }
                });
    }

    private BitmapDescriptor getScaledProviderIcon() {
        int height = 100;
        int width = 100;
        Bitmap b = BitmapFactory.decodeResource(getResources(), R.drawable.car_top_icon);
        Bitmap smallMarker = Bitmap.createScaledBitmap(b, width, height, false);
        return BitmapDescriptorFactory.fromBitmap(smallMarker);
    }

    private void updateProviderMarkerAndRoute(LatLng location) {
        if (MyMap == null) return;

        if (providerMarker == null) {
            providerMarker = MyMap.addMarker(new MarkerOptions()
                    .position(location)
                    .title("Your Provider")
                    .icon(getScaledProviderIcon())
                    .anchor(0.5f, 0.5f)
                    .flat(true));
        } else {
            providerMarker.setPosition(location);
        }

        if (pickupMarker == null && pickupLatLng != null) {
            pickupMarker = MyMap.addMarker(new MarkerOptions()
                    .position(pickupLatLng)
                    .title(pickupAddress != null ? pickupAddress : "Your Location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        }

        if (pickupLatLng != null) {
            drawProviderRoute(location, pickupLatLng);
        }
    }

    private void drawProviderRoute(LatLng providerLocation, LatLng pickupLocation) {
        Log.d(TAG, "Attempting to draw provider route...");

        executor.execute(() -> {
            try {
                com.google.maps.model.LatLng origin = new com.google.maps.model.LatLng(providerLocation.latitude, providerLocation.longitude);
                com.google.maps.model.LatLng destination = new com.google.maps.model.LatLng(pickupLocation.latitude, pickupLocation.longitude);

                DirectionsResult result = DirectionsApi.newRequest(mGeoApiContext)
                        .origin(origin)
                        .destination(destination)
                        .mode(TravelMode.DRIVING)
                        .await();

                if (result.routes != null && result.routes.length > 0) {
                    com.google.maps.model.DirectionsRoute route = result.routes[0];
                    com.google.maps.model.DirectionsLeg leg = route.legs[0];

                    String encodedPolyline = route.overviewPolyline.getEncodedPath();
                    final List<LatLng> decodedPath = PolyUtil.decode(encodedPolyline);

                    final String distance = leg.distance.humanReadable;
                    final String duration = leg.duration.humanReadable;

                    handler.post(() -> {
                        if (mProviderToPickupLine != null) {
                            mProviderToPickupLine.remove();
                        }

                        mProviderToPickupLine = MyMap.addPolyline(new PolylineOptions()
                                .addAll(decodedPath)
                                .width(12)
                                .color(Color.BLUE));

                        distanceText.setText(distance);
                        etaText.setText(String.format("~ %s", duration));

                        LatLngBounds.Builder builder = new LatLngBounds.Builder();
                        builder.include(pickupLocation);
                        builder.include(providerLocation);
                        MyMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 150));
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Directions API failed", e);
                handler.post(() -> drawStraightProviderLine(providerLocation, pickupLocation));
            }
        });
    }

    private void drawStraightProviderLine(LatLng providerLocation, LatLng pickupLocation) {
        if (mProviderToPickupLine != null) {
            mProviderToPickupLine.remove();
        }
        mProviderToPickupLine = MyMap.addPolyline(new PolylineOptions()
                .add(providerLocation, pickupLocation)
                .width(12)
                .color(Color.BLUE)
                .geodesic(true));

        calculateStraightLineEta(providerLocation);
    }

    // --- NEW METHOD TO DRAW PREVIEW ROUTE ---
    private void drawPickupToDestinationRoute(LatLng pickupLocation, LatLng destinationLocation) {
        Log.d(TAG, "Attempting to draw preview route...");

        executor.execute(() -> {
            try {
                com.google.maps.model.LatLng origin = new com.google.maps.model.LatLng(pickupLocation.latitude, pickupLocation.longitude);
                com.google.maps.model.LatLng destination = new com.google.maps.model.LatLng(destinationLocation.latitude, destinationLocation.longitude);

                DirectionsResult result = DirectionsApi.newRequest(mGeoApiContext)
                        .origin(origin)
                        .destination(destination)
                        .mode(TravelMode.DRIVING)
                        .await();

                if (result.routes != null && result.routes.length > 0) {
                    com.google.maps.model.DirectionsRoute route = result.routes[0];
                    String encodedPolyline = route.overviewPolyline.getEncodedPath();
                    final List<LatLng> decodedPath = PolyUtil.decode(encodedPolyline);

                    handler.post(() -> {
                        if (mPickupToDestinationLine != null) {
                            mPickupToDestinationLine.remove();
                        }
                        mPickupToDestinationLine = MyMap.addPolyline(new PolylineOptions()
                                .addAll(decodedPath)
                                .width(12)
                                .color(Color.RED)); // Changed color to red for distinction
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Preview Directions API failed", e);
                // No fallback straight line for this one, just don't draw it.
            }
        });
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

        if (mGeoApiContext != null) {
            mGeoApiContext.shutdown();
        }
    }
}