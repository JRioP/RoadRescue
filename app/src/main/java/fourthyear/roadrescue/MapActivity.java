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
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
import com.google.firebase.firestore.DocumentReference;
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

import java.text.SimpleDateFormat;
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
    private ActivityResultLauncher<Intent> paymentLauncher;

    private Button requestServiceButton;
    private Button editPickupButton;
    private Button messageButton;
    private Button callButton;

    private boolean isSettingPickup = false;
    private String pickupAddress;
    private String destinationAddress;
    private Geocoder geocoder;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentRequestId;
    private ListenerRegistration requestListener;
    private ListenerRegistration providerListener;

    // Provider Details
    private String mProviderId;
    private String mProviderName;
    private String mProviderPhone;

    // Map Objects
    private Marker providerMarker;
    private Marker pickupMarker;
    private Polyline mProviderToPickupLine;
    private Polyline mPickupToDestinationLine;
    private GeoApiContext mGeoApiContext = null;

    // UI
    private View statusCard;
    private TextView providerNameText;
    private TextView providerSubtitleText;
    private TextView distanceText;
    private TextView etaText;
    private TextView requestTypeText;

    // Request Data
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

        initializeViews();
        setupClickListeners();
        setupPermissionLauncher();
        setupPaymentLauncher();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void initializeViews() {
        destinationInput = findViewById(R.id.destination_input);
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

        if (!"Towing".equals(selectedRequestType)) {
            destinationInput.setHint("Service will be at your pickup location.");
            destinationInput.setText("Service will be at your pickup location.");
            destinationInput.setEnabled(false);
        } else {
            destinationInput.setHint("Tap on the map to set your destination.");
        }

        if (selectedRequestType != null && !selectedRequestType.isEmpty()) {
            requestServiceButton.setText("Request " + selectedRequestType + " Service");
        }
    }

    private void setupClickListeners() {
        requestServiceButton.setOnClickListener(v -> {
            if (!"Towing".equals(selectedRequestType)) {
                if (pickupLatLng != null) {
                    destinationLatLng = pickupLatLng;
                    destinationAddress = pickupAddress;
                } else {
                    Toast.makeText(this, "Please confirm your pickup location.", Toast.LENGTH_LONG).show();
                    return;
                }
            }
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

        messageButton.setOnClickListener(v -> findOrCreateChatRoom());

        callButton.setOnClickListener(v -> {
            if (mProviderPhone == null || mProviderPhone.isEmpty()) {
                Toast.makeText(this, "Provider phone number is not available.", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent dialIntent = new Intent(Intent.ACTION_DIAL);
            dialIntent.setData(Uri.parse("tel:" + mProviderPhone));
            startActivity(dialIntent);
        });

        findViewById(R.id.notification_icon_btn).setOnClickListener(v -> startActivity(new Intent(MapActivity.this, NotificationsActivity.class)));
        findViewById(R.id.home_icon_btn).setOnClickListener(v -> startActivity(new Intent(MapActivity.this, homepage.class)));
        findViewById(R.id.message_icon_btn).setOnClickListener(v -> startActivity(new Intent(MapActivity.this, ChatInboxActivity.class)));
        findViewById(R.id.back_btn).setOnClickListener(v -> finish());
    }


    private void findOrCreateChatRoom() {
        if (mProviderId == null || mProviderName == null) {
            Toast.makeText(this, "Provider details not available yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to chat.", Toast.LENGTH_SHORT).show();
            return;
        }

        String currentUserId = currentUser.getUid();

        db.collection("users").document(currentUserId).get().addOnSuccessListener(userDoc -> {
            String currentUserName = userDoc.getString("name");
            if (currentUserName == null || currentUserName.isEmpty()) {
                currentUserName = "Customer"; // Fallback name
            }


            String chatRoomId;
            if (currentUserId.compareTo(mProviderId) > 0) {
                chatRoomId = currentUserId + "_" + mProviderId;
            } else {
                chatRoomId = mProviderId + "_" + currentUserId;
            }

            DocumentReference chatRef = db.collection("chats").document(chatRoomId);
            String finalCurrentUserName = currentUserName;
            String finalCurrentUserName1 = currentUserName;
            chatRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();
                    if (!document.exists()) {

                        Map<String, Object> chatData = new HashMap<>();
                        chatData.put("chatId", chatRoomId);
                        chatData.put("participantIds", Arrays.asList(currentUserId, mProviderId));
                        chatData.put("lastMessage", "Chat started");
                        chatData.put("lastMessageTimestamp", FieldValue.serverTimestamp());

                        Map<String, String> names = new HashMap<>();
                        names.put(currentUserId, finalCurrentUserName1);
                        names.put(mProviderId, mProviderName);
                        chatData.put("participantNames", names);


                        chatData.put("requestRef", currentRequestId);

                        chatRef.set(chatData).addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "New chat room created: " + chatRoomId);
                            startChatActivity(chatRoomId);
                        });
                    } else {
                        Log.d(TAG, "Joining existing chat room: " + chatRoomId);
                        startChatActivity(chatRoomId);
                    }
                } else {
                    Log.e(TAG, "Error finding chat room", task.getException());
                }
            });

        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to get your user details to start chat.", Toast.LENGTH_SHORT).show();
        });
    }

    private void startChatActivity(String chatRoomId) {
        Intent intent = new Intent(this, ChatConversationActivity.class);
        intent.putExtra("chatId", chatRoomId);
        intent.putExtra("receiverName", mProviderName);
        startActivity(intent);
    }

    private void setupPermissionLauncher() {
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        enableMyLocation();
                    } else {
                        Toast.makeText(this, "Location permission is required to find your position.", Toast.LENGTH_LONG).show();
                        editPickupButton.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void setupPaymentLauncher() {
        paymentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        String paymentMethod = result.getData().getStringExtra("PAYMENT_METHOD");
                        if (paymentMethod != null) {
                            sendServiceRequest(paymentMethod);
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

        db.collection("service_requests")
                .whereEqualTo("customerId", currentUser.getUid())
                .whereIn("status", Arrays.asList("pending", "accepted"))
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                        currentRequestId = doc.getId();
                        String status = doc.getString("status");
                        pickupLatLng = new LatLng(doc.getDouble("pickupLat"), doc.getDouble("pickupLng"));
                        destinationLatLng = new LatLng(doc.getDouble("destinationLat"), doc.getDouble("destinationLng"));
                        pickupAddress = doc.getString("pickupAddress");
                        destinationAddress = doc.getString("destinationAddress");
                        selectedRequestType = doc.getString("requestType");

                        lockUiForTracking();

                        if ("accepted".equals(status)) {
                            MyMap.clear();
                            showTrackerCard(doc);
                            mProviderId = doc.getString("providerId"); // FIX: Store provider ID
                            if (mProviderId != null) {
                                listenForProviderLocation(mProviderId);
                            }
                        } else {
                            updateMapWithMarkers();
                            requestTypeText.setText(selectedRequestType);
                            showSearchingUI();
                        }
                        listenForRequestUpdates(currentRequestId);
                    } else {
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
            getAddressFromLatLng(point, true);
            toggleEditPickupMode();
        } else {
            if ("Towing".equals(selectedRequestType)) {
                destinationLatLng = point;
                getAddressFromLatLng(point, false);
            } else {
                Toast.makeText(this, "This service only requires a pickup location.", Toast.LENGTH_SHORT).show();
                if (pickupLatLng != null) {
                    destinationLatLng = pickupLatLng;
                    updateMapWithMarkers();
                }
            }
        }
    }

    private void toggleEditPickupMode() {
        isSettingPickup = !isSettingPickup;
        if (isSettingPickup) {
            Toast.makeText(this, "Tap on the map to set your new pickup location.", Toast.LENGTH_LONG).show();
            editPickupButton.setText("Confirm Pickup");
            destinationInput.setVisibility(View.GONE);
        } else {
            editPickupButton.setText("Edit Pickup Location");
            if ("Towing".equals(selectedRequestType)) {
                destinationInput.setVisibility(View.VISIBLE);
            }
        }
    }

    private void getAddressFromLatLng(LatLng latLng, boolean isPickup) {
        executor.execute(() -> {
            String addressText = "";
            @SuppressLint("DefaultLocale") String fallbackAddress = String.format("Lat: %.4f, Lng: %.4f", latLng.latitude, latLng.longitude);

            try {
                List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    addressText = addresses.get(0).getAddressLine(0);
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
        if (currentUser == null || pickupLatLng == null || destinationLatLng == null) return;

        Map<String, Object> requestData = new HashMap<>();
        requestData.put("customerId", currentUser.getUid());
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

        db.collection("service_requests").add(requestData)
                .addOnSuccessListener(documentReference -> {
                    currentRequestId = documentReference.getId();
                    listenForRequestUpdates(currentRequestId);
                    lockUiForTracking();
                })
                .addOnFailureListener(e -> resetUiForNewRequest());
    }

    private double calculateServiceAmount() {
        if (pickupLatLng == null || destinationLatLng == null) return 0.0;
        float[] results = new float[1];
        Location.distanceBetween(pickupLatLng.latitude, pickupLatLng.longitude, destinationLatLng.latitude, destinationLatLng.longitude, results);
        float distanceInKm = results[0] / 1000;
        double baseFee, perKmCharge;

        switch (selectedRequestType) {
            case "Towing":
                baseFee = 500.0; perKmCharge = 50.0; break;
            case "Fuel Delivery":
                baseFee = 250.0; perKmCharge = 0.0; break;
            case "Flat Tire Repair":
                baseFee = 300.0; perKmCharge = 10.0; break;
            case "Replace Battery":
                baseFee = 350.0; perKmCharge = 10.0; break;
            default:
                baseFee = 150.0; perKmCharge = 5.0; break;
        }
        return Math.round((baseFee + (distanceInKm * perKmCharge)) * 100.0) / 100.0;
    }

    @SuppressLint("MissingPermission")
    private void getDeviceLocation() {
        if (pickupLatLng != null) return; // Only get location if we don't have one
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                pickupLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                getAddressFromLatLng(pickupLatLng, true); // This will call updateMapWithMarkers
            } else {
                Toast.makeText(MapActivity.this, "Could not get current location. Please use 'Edit Pickup Location' to set it manually.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateMapWithMarkers() {
        if (MyMap == null) return;
        MyMap.clear();

        if (mProviderToPickupLine != null) mProviderToPickupLine.remove();
        if (mPickupToDestinationLine != null) mPickupToDestinationLine.remove();

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        boolean hasPoints = false;

        if (pickupLatLng != null) {
            pickupMarker = MyMap.addMarker(new MarkerOptions().position(pickupLatLng).title(pickupAddress != null ? pickupAddress : "Pickup").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            builder.include(pickupLatLng);
            hasPoints = true;
        }

        if ("Towing".equals(selectedRequestType)) {
            if (destinationLatLng != null) {
                MyMap.addMarker(new MarkerOptions().position(destinationLatLng).title(destinationAddress != null ? destinationAddress : "Destination").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
                builder.include(destinationLatLng);
                hasPoints = true;
            }
        } else {
            if (pickupLatLng != null) destinationLatLng = pickupLatLng; // Ensure destination is set for logic
        }

        if (hasPoints && providerMarker == null) {
            if (pickupLatLng != null && destinationLatLng != null && "Towing".equals(selectedRequestType) && pickupLatLng != destinationLatLng) {
                drawPickupToDestinationRoute(pickupLatLng, destinationLatLng);
                MyMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
            } else if (pickupLatLng != null) {
                MyMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pickupLatLng, 15f));
            }
        }

        boolean canRequest = ("Towing".equals(selectedRequestType) && pickupLatLng != null && destinationLatLng != null) ||
                (!"Towing".equals(selectedRequestType) && pickupLatLng != null);
        requestServiceButton.setVisibility(currentRequestId == null && canRequest ? View.VISIBLE : View.GONE);
    }

    @SuppressLint("MissingPermission")
    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            MyMap.setMyLocationEnabled(true);
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
        requestTypeText.setText(selectedRequestType != null ? selectedRequestType : "Searching...");
        messageButton.setVisibility(View.GONE);
        callButton.setVisibility(View.GONE);
    }

    private void showTrackerCard(DocumentSnapshot doc) {
        statusCard.setVisibility(View.VISIBLE);
        messageButton.setVisibility(View.VISIBLE);
        callButton.setVisibility(View.VISIBLE);
        requestTypeText.setText(doc.getString("requestType"));
        providerNameText.setText("Provider Found");
        providerSubtitleText.setText("Fetching details...");
        distanceText.setText("...");
        etaText.setText("...");

        // Also add the pickup marker back to the map
        if (pickupLatLng != null && MyMap != null) {
            pickupMarker = MyMap.addMarker(new MarkerOptions()
                    .position(pickupLatLng)
                    .title(pickupAddress != null ? pickupAddress : "Your Location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        }
    }

    private void listenForRequestUpdates(String requestId) {
        if (requestId == null) return;
        if (requestListener != null) requestListener.remove();
        requestListener = db.collection("service_requests").document(requestId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null || !snapshot.exists()) {
                        Toast.makeText(this, "Request was cancelled or completed.", Toast.LENGTH_SHORT).show();
                        resetUiForNewRequest();
                        return;
                    }

                    String status = snapshot.getString("status");
                    if ("accepted".equals(status) && providerListener == null) {
                        mProviderId = snapshot.getString("providerId");
                        if (mProviderId != null) {
                            MyMap.clear();
                            showTrackerCard(snapshot);
                            listenForProviderLocation(mProviderId);
                        }
                    } else if ("completed".equals(status)) {
                        Intent intent = new Intent(MapActivity.this, PaymentReceiptActivity.class);
                        intent.putExtra("REFERENCE_ID", snapshot.getId());
                        intent.putExtra("AMOUNT_PAID", String.format(Locale.getDefault(), "PHP %.2f", snapshot.getDouble("amount")));
                        com.google.firebase.Timestamp ts = snapshot.getTimestamp("timestamp");
                        if (ts != null) intent.putExtra("PAYMENT_DATE", new SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault()).format(ts.toDate()));
                        intent.putExtra("PAYMENT_METHOD", snapshot.getString("paymentMethod"));
                        intent.putExtra("REQUEST_TYPE", snapshot.getString("requestType"));
                        intent.putExtra("PICKUP_ADDRESS", snapshot.getString("pickupAddress"));
                        intent.putExtra("DESTINATION_ADDRESS", snapshot.getString("destinationAddress"));
                        startActivity(intent);

                        resetUiForNewRequest(); // This will also remove the listeners

                    } else if ("pending".equals(status)) {
                        requestTypeText.setText(snapshot.getString("requestType"));
                        showSearchingUI();
                    }
                });
    }

    private void resetUiForNewRequest() {
        statusCard.setVisibility(View.GONE);
        editPickupButton.setVisibility(View.VISIBLE);
        if ("Towing".equals(selectedRequestType)) {
            destinationInput.setEnabled(true);
            destinationInput.setText("");
            destinationInput.setHint("Tap on the map to set your destination.");
        }
        destinationLatLng = null;
        currentRequestId = null;
        mProviderId = null;
        mProviderName = null;
        mProviderPhone = null;

        if (providerListener != null) providerListener.remove();
        if (requestListener != null) requestListener.remove();
        if (MyMap != null) MyMap.clear();

        providerListener = null;
        requestListener = null;
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
                    if (e != null || snapshot == null || !snapshot.exists()) {
                        Log.w(TAG, "Provider listener failed or provider doc missing.", e);
                        return;
                    }

                    // Store details for Call/Message buttons
                    mProviderName = snapshot.getString("name");
                    mProviderPhone = snapshot.getString("phone");
                    String providerLocText = snapshot.getString("currentLocationAddress");
                    GeoPoint geoPoint = snapshot.getGeoPoint("liveLocation");

                    handler.post(() -> {
                        providerNameText.setText(mProviderName != null ? mProviderName : "Provider");
                        providerSubtitleText.setText(providerLocText != null ? "En route from " + providerLocText : "Awaiting location...");

                        if (geoPoint != null) {
                            LatLng providerLocation = new LatLng(geoPoint.getLatitude(), geoPoint.getLongitude());
                            updateProviderMarkerAndRoute(providerLocation);
                        }
                    });
                });
    }

    private BitmapDescriptor getScaledProviderIcon() {
        Bitmap b = BitmapFactory.decodeResource(getResources(), R.drawable.car_top_icon);
        Bitmap smallMarker = Bitmap.createScaledBitmap(b, 100, 100, false);
        return BitmapDescriptorFactory.fromBitmap(smallMarker);
    }

    private void updateProviderMarkerAndRoute(LatLng location) {
        if (MyMap == null) return;

        if (providerMarker == null) {
            providerMarker = MyMap.addMarker(new MarkerOptions().position(location).title("Your Provider").icon(getScaledProviderIcon()).anchor(0.5f, 0.5f).flat(true));
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
        executor.execute(() -> {
            try {
                com.google.maps.model.LatLng origin = new com.google.maps.model.LatLng(providerLocation.latitude, providerLocation.longitude);
                com.google.maps.model.LatLng destination = new com.google.maps.model.LatLng(pickupLocation.latitude, pickupLocation.longitude);

                DirectionsResult result = DirectionsApi.newRequest(mGeoApiContext).origin(origin).destination(destination).mode(TravelMode.DRIVING).await();
                if (result.routes != null && result.routes.length > 0) {
                    final List<LatLng> decodedPath = PolyUtil.decode(result.routes[0].overviewPolyline.getEncodedPath());
                    final String distance = result.routes[0].legs[0].distance.humanReadable;
                    final String duration = result.routes[0].legs[0].duration.humanReadable;

                    handler.post(() -> {
                        if (mProviderToPickupLine != null) mProviderToPickupLine.remove();
                        mProviderToPickupLine = MyMap.addPolyline(new PolylineOptions().addAll(decodedPath).width(12).color(Color.BLUE));
                        distanceText.setText(distance);
                        etaText.setText(String.format("~ %s", duration));
                        LatLngBounds.Builder builder = new LatLngBounds.Builder();
                        builder.include(pickupLocation).include(providerLocation);
                        MyMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 150));
                    });
                }
            } catch (Exception e) {
                handler.post(() -> drawStraightProviderLine(providerLocation, pickupLocation));
            }
        });
    }

    private void drawStraightProviderLine(LatLng providerLocation, LatLng pickupLocation) {
        if (mProviderToPickupLine != null) mProviderToPickupLine.remove();
        mProviderToPickupLine = MyMap.addPolyline(new PolylineOptions().add(providerLocation, pickupLocation).width(12).color(Color.BLUE).geodesic(true));
        calculateStraightLineEta(providerLocation);
    }

    private void drawPickupToDestinationRoute(LatLng pickupLocation, LatLng destinationLocation) {
        if (!"Towing".equals(selectedRequestType) || pickupLocation.equals(destinationLocation)) {
            if (mPickupToDestinationLine != null) mPickupToDestinationLine.remove();
            return;
        }
        executor.execute(() -> {
            try {
                com.google.maps.model.LatLng origin = new com.google.maps.model.LatLng(pickupLocation.latitude, pickupLocation.longitude);
                com.google.maps.model.LatLng destination = new com.google.maps.model.LatLng(destinationLocation.latitude, destinationLocation.longitude);
                DirectionsResult result = DirectionsApi.newRequest(mGeoApiContext).origin(origin).destination(destination).mode(TravelMode.DRIVING).await();
                if (result.routes != null && result.routes.length > 0) {
                    final List<LatLng> decodedPath = PolyUtil.decode(result.routes[0].overviewPolyline.getEncodedPath());
                    handler.post(() -> {
                        if (mPickupToDestinationLine != null) mPickupToDestinationLine.remove();
                        mPickupToDestinationLine = MyMap.addPolyline(new PolylineOptions().addAll(decodedPath).width(12).color(Color.RED));
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Preview Directions API failed", e);
            }
        });
    }

    private void calculateStraightLineEta(LatLng providerLocation) {
        if (pickupLatLng == null) return;
        float[] results = new float[1];
        Location.distanceBetween(providerLocation.latitude, providerLocation.longitude, pickupLatLng.latitude, pickupLatLng.longitude, results);
        float distanceInKm = results[0] / 1000;
        int timeInMinutes = Math.max(1, (int) (distanceInKm * 2));
        distanceText.setText(String.format(Locale.getDefault(), "%.1f km", distanceInKm));
        etaText.setText(String.format(Locale.getDefault(), "~ %d min", timeInMinutes));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (requestListener != null) requestListener.remove();
        if (providerListener != null) providerListener.remove();
        executor.execute(() -> {
            if (mGeoApiContext != null) {
                mGeoApiContext.shutdown();
            }
        });
        executor.shutdown();
    }
}