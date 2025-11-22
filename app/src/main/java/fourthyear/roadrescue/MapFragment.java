package fourthyear.roadrescue;

import android.Manifest;
import android.annotation.SuppressLint;
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
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
import com.google.firebase.firestore.SetOptions;
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

public class MapFragment extends Fragment implements OnMapReadyCallback, GoogleMap.OnMapClickListener {

    private static final String TAG = "MapFragment";
    private static final String FALLBACK_DISPATCH_NUMBER = "+639171234567";

    // UI Components
    private EditText destinationInput;
    private Button requestServiceButton;
    private Button editPickupButton;
    private Button messageButton;
    private Button callButton;
    private Button cancelRequestButton;
    private View statusCard;
    private View searchingCard;

    // Provider UI
    private ImageView providerImage;
    private TextView providerNameText;
    private TextView providerSubtitleText;
    private TextView distanceText;
    private TextView etaText;
    private TextView requestTypeText;
    private TextView providerRatingText;
    private ImageView providerRatingStar;

    // Map & Location
    private GoogleMap MyMap;
    private FusedLocationProviderClient fusedLocationClient;
    private LatLng pickupLatLng;
    private LatLng destinationLatLng;
    private String pickupAddress;
    private String destinationAddress;
    private Geocoder geocoder;

    // Launchers
    private ActivityResultLauncher<String> requestPermissionLauncher;

    // Logic & State
    private boolean isSettingPickup = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private GeoApiContext mGeoApiContext = null;
    private String selectedRequestType;
    private double calculatedAmount;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private String currentRequestId;

    private ListenerRegistration requestListener;
    private ListenerRegistration providerListener;

    // Provider Data
    private String mProviderId;
    private String mProviderName;
    private String mProviderPhone;
    private Marker providerMarker;
    private Marker pickupMarker;
    private Polyline mProviderToPickupLine;
    private Polyline mPickupToDestinationLine;

    public MapFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        geocoder = new Geocoder(requireContext(), Locale.getDefault());
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext());

        if (mGeoApiContext == null) {
            mGeoApiContext = new GeoApiContext.Builder()
                    .apiKey(BuildConfig.MAPS_API_KEY)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();
        }

        setupPermissionLauncher();

        // --- LISTEN FOR PAYMENT RESULT ---
        getParentFragmentManager().setFragmentResultListener("payment_result_key", this, (requestKey, result) -> {
            String paymentMethod = result.getString("PAYMENT_METHOD");
            if (paymentMethod != null) {
                sendServiceRequest(paymentMethod);
            }
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Handle Intent/Arguments
        if (getArguments() != null) {
            selectedRequestType = getArguments().getString("REQUEST_TYPE");
        }
        if (selectedRequestType == null || selectedRequestType.isEmpty()) {
            selectedRequestType = "Towing";
        }

        initializeViews(view);
        setupClickListeners(view);

        // Initialize Map
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void initializeViews(View view) {
        destinationInput = view.findViewById(R.id.destination_input);
        requestServiceButton = view.findViewById(R.id.request_service_btn);
        editPickupButton = view.findViewById(R.id.edit_pickup_btn);
        statusCard = view.findViewById(R.id.status_card);
        searchingCard = view.findViewById(R.id.searching_card);

        providerImage = view.findViewById(R.id.provider_image);
        if (providerImage != null) {
            android.graphics.drawable.GradientDrawable border = new android.graphics.drawable.GradientDrawable();
            border.setColor(Color.WHITE);
            border.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            providerImage.setBackground(border);
            providerImage.setPadding(10, 10, 10, 10);
            providerImage.setCropToPadding(true);
        }

        providerNameText = view.findViewById(R.id.provider_name);
        providerSubtitleText = view.findViewById(R.id.provider_subtitle);
        distanceText = view.findViewById(R.id.distance_text);
        etaText = view.findViewById(R.id.eta_text);
        requestTypeText = view.findViewById(R.id.request_type_text);
        providerRatingText = view.findViewById(R.id.rating_text);
        providerRatingStar = view.findViewById(R.id.rating_star);
        messageButton = view.findViewById(R.id.message_button);
        callButton = view.findViewById(R.id.call_button);
        cancelRequestButton = view.findViewById(R.id.cancel_request_btn);

        android.graphics.drawable.GradientDrawable pillShape = new android.graphics.drawable.GradientDrawable();
        pillShape.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        pillShape.setColor(Color.WHITE);
        pillShape.setCornerRadius(1000f);

        if(destinationInput != null) {
            destinationInput.setBackground(pillShape);
            destinationInput.setElevation(20f);
            destinationInput.setPadding(50, 25, 50, 25);
            destinationInput.setGravity(android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START);
            destinationInput.setSingleLine(true);
            destinationInput.setEllipsize(android.text.TextUtils.TruncateAt.END);

            if ("Gas Station".equals(selectedRequestType)) {
                destinationInput.setHint("Finding nearest gas stations...");
                destinationInput.setEnabled(false);
                requestServiceButton.setText("Find Nearby Gas Stations");
                if(editPickupButton != null) editPickupButton.setVisibility(View.GONE);
            } else if (!"Towing".equals(selectedRequestType)) {
                destinationInput.setHint("Service will be at your pickup location.");
                destinationInput.setText("Service will be at your pickup location.");
                destinationInput.setEnabled(false);
                requestServiceButton.setText("Request " + selectedRequestType + " Service");
            } else {
                destinationInput.setHint("Tap on the map to set your destination.");
                requestServiceButton.setText("Request " + selectedRequestType + " Service");
            }
        }
    }

    private void setupClickListeners(View view) {
        if(requestServiceButton != null) {
            requestServiceButton.setOnClickListener(v -> {
                if ("Gas Station".equals(selectedRequestType)) {
                    findNearbyGasStations();
                    return;
                }

                if (!"Towing".equals(selectedRequestType)) {
                    if (pickupLatLng != null) {
                        destinationLatLng = pickupLatLng;
                        destinationAddress = pickupAddress;
                    } else {
                        Toast.makeText(requireContext(), "Please confirm your pickup location.", Toast.LENGTH_LONG).show();
                        return;
                    }
                }
                if (pickupLatLng != null && destinationLatLng != null) {
                    calculatedAmount = calculateServiceAmount();

                    PaymentFragment paymentFragment = new PaymentFragment();
                    Bundle args = new Bundle();
                    args.putDouble("AMOUNT_TO_BE_PAID", calculatedAmount);
                    paymentFragment.setArguments(args);

                    if (getParentFragmentManager() != null) {
                        getParentFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, paymentFragment) // Must match ID in host Activity
                                .addToBackStack(null)
                                .commit();
                    }
                } else {
                    Toast.makeText(requireContext(), "Please confirm both your pickup and destination locations.", Toast.LENGTH_LONG).show();
                }
            });
        }

        if(editPickupButton != null) editPickupButton.setOnClickListener(v -> toggleEditPickupMode());
        if(messageButton != null) messageButton.setOnClickListener(v -> findOrCreateChatRoom());

        if(callButton != null) {
            callButton.setOnClickListener(v -> {
                if (mProviderPhone == null || mProviderPhone.isEmpty()) {
                    Toast.makeText(requireContext(), "Provider phone number is not available.", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent dialIntent = new Intent(Intent.ACTION_DIAL);
                dialIntent.setData(Uri.parse("tel:" + mProviderPhone));
                startActivity(dialIntent);
            });
        }

        if(cancelRequestButton != null) cancelRequestButton.setOnClickListener(v -> cancelServiceRequest());

        View backBtn = view.findViewById(R.id.back_btn);
        if (backBtn != null) {
            backBtn.setOnClickListener(v -> {
                if (getParentFragmentManager() != null) {
                    getParentFragmentManager().popBackStack();
                }
            });
        }
    }

    private void findNearbyGasStations() {
        Uri gmmIntentUri = Uri.parse("geo:0,0?q=gas+station");
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        if (mapIntent.resolveActivity(requireContext().getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            Uri browserUri = Uri.parse("https://www.google.com/maps/search/gas+station/");
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, browserUri);
            startActivity(browserIntent);
        }
    }

    private double calculateServiceAmount() {
        if (pickupLatLng == null || destinationLatLng == null) return 0.0;
        float[] results = new float[1];
        Location.distanceBetween(pickupLatLng.latitude, pickupLatLng.longitude, destinationLatLng.latitude, destinationLatLng.longitude, results);
        float distanceInKm = results[0] / 1000;
        double baseFee, perKmCharge;

        switch (selectedRequestType) {
            case "Towing": baseFee = 500.0; perKmCharge = 50.0; break;
            case "Fuel Delivery": baseFee = 250.0; perKmCharge = 0.0; break;
            case "Flat Tire Repair": baseFee = 300.0; perKmCharge = 10.0; break;
            case "Replace Battery": baseFee = 350.0; perKmCharge = 10.0; break;
            default: baseFee = 150.0; perKmCharge = 5.0; break;
        }
        return Math.round((baseFee + (distanceInKm * perKmCharge)) * 100.0) / 100.0;
    }

    private void cancelServiceRequest() {
        if (currentRequestId == null || currentRequestId.isEmpty()) {
            resetUiForNewRequest();
            return;
        }
        if (mProviderId != null) closeChatSession(mProviderId, currentRequestId);
        String reqIdToCancel = currentRequestId;
        resetUiForNewRequest();
        db.collection("service_requests").document(reqIdToCancel).delete()
                .addOnSuccessListener(aVoid -> {
                    if (isAdded()) Toast.makeText(requireContext(), "Request cancelled.", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to delete request", e));
    }

    private void resetUiForNewRequest() {
        if(statusCard != null) statusCard.setVisibility(View.GONE);
        if(searchingCard != null) searchingCard.setVisibility(View.GONE);

        if (!"Gas Station".equals(selectedRequestType)) {
            if(editPickupButton != null) editPickupButton.setVisibility(View.VISIBLE);
        }

        if ("Towing".equals(selectedRequestType)) {
            if(destinationInput != null) {
                destinationInput.setEnabled(true);
                destinationInput.setText("");
                destinationInput.setHint("Tap on the map to set your destination.");
            }
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

    private void closeChatSession(String providerId, String requestId) {
        if (providerId == null || requestId == null || mAuth.getCurrentUser() == null) return;

        String currentUserId = mAuth.getCurrentUser().getUid();
        String chatRoomId;

        if (currentUserId.compareTo(providerId) > 0) {
            chatRoomId = requestId + "_" + currentUserId + "_" + providerId;
        } else {
            chatRoomId = requestId + "_" + providerId + "_" + currentUserId;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "closed");
        db.collection("chats").document(chatRoomId).set(updates, SetOptions.merge());
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
            enableMyLocation(); // If no user, just enable location for new request
            return;
        }

        db.collection("service_requests")
                .whereEqualTo("customerId", currentUser.getUid())
                .whereIn("status", Arrays.asList("pending", "accepted"))
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if (!isAdded()) return;
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
                            mProviderId = doc.getString("providerId");
                            if (mProviderId != null) {
                                listenForProviderLocation(mProviderId);
                            }
                        } else {
                            updateMapWithMarkers();
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
            Toast.makeText(requireContext(), "A service request is already in progress.", Toast.LENGTH_SHORT).show();
            return;
        }
        if ("Gas Station".equals(selectedRequestType)) {
            Toast.makeText(requireContext(), "Click the button to find Gas Stations.", Toast.LENGTH_SHORT).show();
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
                Toast.makeText(requireContext(), "This service only requires a pickup location.", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(requireContext(), "Tap on the map to set your new pickup location.", Toast.LENGTH_LONG).show();
            if(editPickupButton != null) editPickupButton.setText("Confirm Pickup");
            if(destinationInput != null) destinationInput.setVisibility(View.GONE);
        } else {
            if(editPickupButton != null) editPickupButton.setText("Edit Pickup Location");
            if ("Towing".equals(selectedRequestType) && destinationInput != null) {
                destinationInput.setVisibility(View.VISIBLE);
            }
        }
    }

    private void getAddressFromLatLng(LatLng latLng, boolean isPickup) {
        executor.execute(() -> {
            String addressText = "";
            @SuppressLint("DefaultLocale") String fallbackAddress = String.format("Lat: %.4f, Lng: %.4f", latLng.latitude, latLng.longitude);

            try {
                if (Geocoder.isPresent()) {
                    List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        addressText = addresses.get(0).getAddressLine(0);
                        if (addressText == null || addressText.isEmpty()) addressText = fallbackAddress;
                    } else {
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
                if (!isAdded()) return;
                if (isPickup) {
                    pickupAddress = finalAddressText;
                } else {
                    destinationAddress = finalAddressText;
                    if(destinationInput != null) destinationInput.setText(finalAddressText);
                }
                updateMapWithMarkers();
            });
        });
    }

    @SuppressLint("MissingPermission")
    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if(MyMap != null) MyMap.setMyLocationEnabled(true);
            getDeviceLocation();
            if (!"Gas Station".equals(selectedRequestType)) {
                if (editPickupButton != null) editPickupButton.setVisibility(View.VISIBLE);
            }
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    @SuppressLint("MissingPermission")
    private void getDeviceLocation() {
        if (pickupLatLng != null) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(requireActivity(), location -> {
            if (location != null && isAdded()) {
                pickupLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                getAddressFromLatLng(pickupLatLng, true);
            } else if (isAdded()) {
                Toast.makeText(requireContext(), "Could not get current location. Please use 'Edit Pickup Location' to set it manually.", Toast.LENGTH_LONG).show();
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
            if (pickupLatLng != null) destinationLatLng = pickupLatLng;
        }

        if (hasPoints && providerMarker == null) {
            if (pickupLatLng != null && destinationLatLng != null && "Towing".equals(selectedRequestType) && !pickupLatLng.equals(destinationLatLng)) {
                drawPickupToDestinationRoute(pickupLatLng, destinationLatLng);
                try {
                    MyMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Cannot animate camera to bounds with zero size", e);
                    MyMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pickupLatLng, 15f));
                }
            } else if (pickupLatLng != null) {
                MyMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pickupLatLng, 15f));
            }
        }

        boolean canRequest = ("Towing".equals(selectedRequestType) && pickupLatLng != null && destinationLatLng != null) ||
                (!"Towing".equals(selectedRequestType) && pickupLatLng != null);
        if(requestServiceButton != null)
            requestServiceButton.setVisibility(currentRequestId == null && canRequest ? View.VISIBLE : View.GONE);
    }

    @SuppressLint("DefaultLocale")
    private void sendServiceRequest(String paymentMethod) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null || pickupLatLng == null || destinationLatLng == null) return;

        String userId = currentUser.getUid();

        db.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if(!isAdded()) return;
                    String carBrand = null;
                    String carModel = null;
                    String carType = null;

                    if (documentSnapshot.exists()) {
                        carBrand = documentSnapshot.getString("carBrand");
                        carModel = documentSnapshot.getString("carModel");
                        carType = documentSnapshot.getString("carType");
                    }

                    Map<String, Object> requestData = new HashMap<>();
                    requestData.put("customerId", userId);
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
                    requestData.put("carBrand", carBrand);
                    requestData.put("carModel", carModel);
                    requestData.put("carType", carType);

                    showSearchingUI();

                    db.collection("service_requests").add(requestData)
                            .addOnSuccessListener(documentReference -> {
                                if(!isAdded()) return;
                                currentRequestId = documentReference.getId();
                                listenForRequestUpdates(currentRequestId);
                                lockUiForTracking();
                            })
                            .addOnFailureListener(e -> {
                                if(!isAdded()) return;
                                sendSmsFallbackRequest(FALLBACK_DISPATCH_NUMBER, requestData);
                                resetUiForNewRequest();
                            });
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        Log.e(TAG, "Failed to fetch user car info", e);
                        Toast.makeText(requireContext(), "Network error. Please try again.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void sendSmsFallbackRequest(String dispatchPhone, Map<String, Object> requestData) {
        String type = (String) requestData.get("requestType");
        Double lat = (Double) requestData.get("pickupLat");
        Double lng = (Double) requestData.get("pickupLng");
        String customerId = (String) requestData.get("customerId");

        String message = String.format(Locale.getDefault(),
                "EMERGENCY REQUEST: %s. ID:%s. LOC:%.4f,%.4f. Addr:%s. Car:%s %s",
                type, customerId, lat, lng,
                (String) requestData.get("pickupAddress"),
                (String) requestData.get("carBrand"),
                (String) requestData.get("carModel")
        );

        Intent smsIntent = new Intent(Intent.ACTION_VIEW);
        smsIntent.setData(Uri.parse("smsto:" + dispatchPhone));
        smsIntent.putExtra("sms_body", message);

        if (smsIntent.resolveActivity(requireContext().getPackageManager()) != null) {
            startActivity(smsIntent);
            Toast.makeText(requireContext(), "Network failed. Sending request via SMS.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(requireContext(), "Network failed and no SMS app found.", Toast.LENGTH_LONG).show();
        }
    }

    private void listenForRequestUpdates(String requestId) {
        if (requestId == null) return;
        if (requestListener != null) requestListener.remove();
        requestListener = db.collection("service_requests").document(requestId)
                .addSnapshotListener((snapshot, e) -> {
                    if (!isAdded()) return;
                    if (e != null || snapshot == null || !snapshot.exists()) {
                        if (isAdded()) Toast.makeText(requireContext(), "Request was cancelled or completed.", Toast.LENGTH_SHORT).show();
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
                        handleCompletedRequest(snapshot, requestId);
                    } else if ("pending".equals(status)) {
                        showSearchingUI();
                    }
                });
    }

    private void handleCompletedRequest(DocumentSnapshot snapshot, String requestId) {
        String providerId = snapshot.getString("providerId");
        if (providerId == null) providerId = mProviderId;
        if (providerId != null) {
            closeChatSession(providerId, requestId);
        }

        // --- FIX: Use FragmentTransaction to show the receipt ---
        PaymentReceiptFragment receiptFragment = new PaymentReceiptFragment();
        Bundle args = new Bundle();
        args.putString("REFERENCE_ID", snapshot.getId());
        args.putString("AMOUNT_PAID", String.format(Locale.getDefault(), "PHP %.2f", snapshot.getDouble("amount")));
        args.putString("SERVICE_PROVIDER_ID", providerId);
        com.google.firebase.Timestamp ts = snapshot.getTimestamp("timestamp");
        if (ts != null)
            args.putString("PAYMENT_DATE", new SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault()).format(ts.toDate()));
        args.putString("PAYMENT_METHOD", snapshot.getString("paymentMethod"));
        args.putString("REQUEST_TYPE", snapshot.getString("requestType"));
        args.putString("PICKUP_ADDRESS", snapshot.getString("pickupAddress"));
        args.putString("DESTINATION_ADDRESS", snapshot.getString("destinationAddress"));
        receiptFragment.setArguments(args);

        if (getParentFragmentManager() != null) {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, receiptFragment)
                    .commit(); // Don't add to backstack, it's a final screen
        }

        resetUiForNewRequest();
    }

    private void listenForProviderLocation(String providerId) {
        if (providerListener != null) providerListener.remove();
        providerListener = db.collection("users").document(providerId)
                .addSnapshotListener((snapshot, e) -> {
                    if (!isAdded() || e != null || snapshot == null || !snapshot.exists()) {
                        return;
                    }

                    mProviderName = snapshot.getString("name");
                    mProviderPhone = snapshot.getString("phone");
                    String providerLocText = snapshot.getString("currentLocationAddress");
                    String photoUrl = snapshot.getString("profileImageUrl");
                    GeoPoint geoPoint = snapshot.getGeoPoint("liveLocation");

                    Double avgRating = snapshot.getDouble("averageRating");
                    Long ratingCount = snapshot.getLong("ratingCount");

                    handler.post(() -> {
                        if(!isAdded()) return;
                        if(providerNameText != null) providerNameText.setText(mProviderName != null ? mProviderName : "Provider");
                        if(providerSubtitleText != null) providerSubtitleText.setText(providerLocText != null ? "En route from " + providerLocText : "Awaiting location...");

                        if (avgRating != null && ratingCount != null && ratingCount > 0) {
                            if(providerRatingText != null) providerRatingText.setText(String.format(Locale.getDefault(), "%.1f", avgRating));
                            if(providerRatingStar != null) providerRatingStar.setVisibility(View.VISIBLE);
                        } else {
                            if(providerRatingText != null) providerRatingText.setText("Not rated yet");
                            if(providerRatingStar != null) providerRatingStar.setVisibility(View.GONE);
                        }

                        if (providerImage != null) {
                            if (photoUrl != null && !photoUrl.isEmpty()) {
                                Glide.with(this).load(photoUrl).centerCrop().circleCrop().placeholder(R.drawable.profile_icon).error(R.drawable.profile_icon).into(providerImage);
                            } else {
                                Glide.with(this).load(R.drawable.profile_icon).circleCrop().into(providerImage);
                            }
                        }

                        if (geoPoint != null) {
                            LatLng providerLocation = new LatLng(geoPoint.getLatitude(), geoPoint.getLongitude());
                            updateProviderMarkerAndRoute(providerLocation);
                        }
                    });
                });
    }

    private void setupPermissionLauncher() {
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        enableMyLocation();
                    } else {
                        if(isAdded()) Toast.makeText(requireContext(), "Location permission is required.", Toast.LENGTH_LONG).show();
                        if (editPickupButton != null) editPickupButton.setVisibility(View.VISIBLE);
                    }
                });
    }

    // --- FIX: Removed the obsolete setupPaymentLauncher() method ---

    private void findOrCreateChatRoom() {
        if (mProviderId == null || mProviderName == null || currentRequestId == null) {
            Toast.makeText(requireContext(), "Service request details are not ready.", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(requireContext(), "You must be logged in.", Toast.LENGTH_SHORT).show();
            return;
        }
        String currentUserId = currentUser.getUid();

        db.collection("users").document(currentUserId).get().addOnSuccessListener(userDoc -> {
            if(!isAdded()) return;
            String currentUserName = userDoc.getString("name");
            if (currentUserName == null) currentUserName = "Customer";

            String chatRoomId;
            if (currentUserId.compareTo(mProviderId) > 0) {
                chatRoomId = currentRequestId + "_" + currentUserId + "_" + mProviderId;
            } else {
                chatRoomId = currentRequestId + "_" + mProviderId + "_" + currentUserId;
            }

            DocumentReference chatRef = db.collection("chats").document(chatRoomId);
            String finalCurrentUserName = currentUserName;

            chatRef.get().addOnCompleteListener(task -> {
                if(!isAdded()) return;
                if (task.isSuccessful()) {
                    DocumentSnapshot document = task.getResult();

                    if (!document.exists()) {
                        Map<String, Object> chatData = new HashMap<>();
                        chatData.put("chatId", chatRoomId);
                        chatData.put("requestId", currentRequestId);
                        chatData.put("participantIds", Arrays.asList(currentUserId, mProviderId));
                        chatData.put("lastMessage", "Service started.");
                        chatData.put("lastMessageTimestamp", FieldValue.serverTimestamp());
                        chatData.put("status", "active");

                        Map<String, String> names = new HashMap<>();
                        names.put(currentUserId, finalCurrentUserName);
                        names.put(mProviderId, mProviderName);
                        chatData.put("participantNames", names);

                        Map<String, Integer> unreadCounts = new HashMap<>();
                        unreadCounts.put(currentUserId, 0);
                        unreadCounts.put(mProviderId, 0);
                        chatData.put("unreadCounts", unreadCounts);

                        chatRef.set(chatData).addOnSuccessListener(aVoid -> startChatFragment(chatRoomId));
                    } else {
                        startChatFragment(chatRoomId);
                    }
                }
            });
        });
    }

    private void startChatFragment(String chatRoomId) {
        ChatConversationFragment chatFragment = new ChatConversationFragment();
        Bundle args = new Bundle();
        args.putString("chatId", chatRoomId);
        args.putString("receiverName", mProviderName);
        args.putString("receiverId", mProviderId);
        chatFragment.setArguments(args);
        if (getActivity() instanceof NavigationActivity) {
            ((NavigationActivity) getActivity()).loadDetailFragment(chatFragment);
        }
    }

    private BitmapDescriptor getScaledProviderIcon() {
        if (getContext() == null) return null;
        Bitmap b = BitmapFactory.decodeResource(getResources(), R.drawable.car_top_icon);
        Bitmap smallMarker = Bitmap.createScaledBitmap(b, 100, 100, false);
        return BitmapDescriptorFactory.fromBitmap(smallMarker);
    }

    private void lockUiForTracking() {
        if(requestServiceButton != null) requestServiceButton.setVisibility(View.GONE);
        if(editPickupButton != null) editPickupButton.setVisibility(View.GONE);
        if(destinationInput != null) destinationInput.setEnabled(false);
    }

    private void showSearchingUI() {
        if(statusCard != null) statusCard.setVisibility(View.GONE);
        if(searchingCard != null) searchingCard.setVisibility(View.VISIBLE);
    }

    private void showTrackerCard(DocumentSnapshot doc) {
        if(searchingCard != null) searchingCard.setVisibility(View.GONE);
        if(statusCard != null) statusCard.setVisibility(View.VISIBLE);
        if(messageButton != null) messageButton.setVisibility(View.VISIBLE);
        if(callButton != null) callButton.setVisibility(View.VISIBLE);
        if(requestTypeText != null) requestTypeText.setText(doc.getString("requestType"));
        if(providerNameText != null) providerNameText.setText("Provider Found");
        if(providerSubtitleText != null) providerSubtitleText.setText("Fetching details...");
        if(distanceText != null) distanceText.setText("...");
        if(etaText != null) etaText.setText("...");

        if(providerRatingText != null) providerRatingText.setText("Loading...");
        if(providerRatingStar != null) providerRatingStar.setVisibility(View.GONE);

        if (pickupLatLng != null && MyMap != null) {
            pickupMarker = MyMap.addMarker(new MarkerOptions()
                    .position(pickupLatLng)
                    .title(pickupAddress != null ? pickupAddress : "Your Location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        }
    }
    private void drawPickupToDestinationRoute(LatLng origin, LatLng destination) {
        Log.d(TAG, "Attempting to draw route from " + origin + " to " + destination);

        executor.execute(() -> {
            try {
                DirectionsResult result = DirectionsApi.newRequest(mGeoApiContext)
                        .mode(TravelMode.DRIVING)
                        .origin(new com.google.maps.model.LatLng(origin.latitude, origin.longitude))
                        .destination(new com.google.maps.model.LatLng(destination.latitude, destination.longitude))
                        .await();

                if (result != null && result.routes != null && result.routes.length > 0) {
                    List<LatLng> decodedPath = PolyUtil.decode(result.routes[0].overviewPolyline.getEncodedPath());
                    handler.post(() -> {
                        if (!isAdded() || MyMap == null) return;
                        if (mPickupToDestinationLine != null) mPickupToDestinationLine.remove();
                        PolylineOptions polylineOptions = new PolylineOptions()
                                .addAll(decodedPath)
                                .color(Color.BLUE)
                                .width(12f);
                        mPickupToDestinationLine = MyMap.addPolyline(polylineOptions);
                    });
                } else {
                    Log.w(TAG, "No routes found.");
                    handler.post(() -> {
                        if (isAdded()) Toast.makeText(requireContext(), "Could not find a route.", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error calculating directions: " + e.getMessage());
                handler.post(() -> {
                    if (isAdded()) Toast.makeText(requireContext(), "Error drawing route.", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void updateProviderMarkerAndRoute(LatLng providerLocation) {
        if (!isAdded() || MyMap == null || pickupLatLng == null) return;

        if (providerMarker == null) {
            providerMarker = MyMap.addMarker(new MarkerOptions()
                    .position(providerLocation)
                    .title("Service Provider")
                    .icon(getScaledProviderIcon())
                    .flat(true)
                    .anchor(0.5f, 0.5f));
        } else {
            providerMarker.setPosition(providerLocation);
        }

        executor.execute(() -> {
            try {
                DirectionsResult result = DirectionsApi.newRequest(mGeoApiContext)
                        .mode(TravelMode.DRIVING)
                        .origin(new com.google.maps.model.LatLng(providerLocation.latitude, providerLocation.longitude))
                        .destination(new com.google.maps.model.LatLng(pickupLatLng.latitude, pickupLatLng.longitude))
                        .await();

                if (result != null && result.routes != null && result.routes.length > 0) {
                    final String eta = result.routes[0].legs[0].duration.humanReadable;
                    final String distance = result.routes[0].legs[0].distance.humanReadable;
                    final List<LatLng> decodedPath = PolyUtil.decode(result.routes[0].overviewPolyline.getEncodedPath());

                    handler.post(() -> {
                        if (!isAdded() || MyMap == null) return;
                        if (mProviderToPickupLine != null) mProviderToPickupLine.remove();
                        mProviderToPickupLine = MyMap.addPolyline(new PolylineOptions()
                                .addAll(decodedPath)
                                .color(Color.GREEN)
                                .width(15f));

                        if (etaText != null) etaText.setText(eta);
                        if (distanceText != null) distanceText.setText(distance);

                        LatLngBounds.Builder builder = new LatLngBounds.Builder();
                        builder.include(providerLocation);
                        builder.include(pickupLatLng);
                        try {
                            MyMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 150));
                        } catch (IllegalStateException e) {
                            Log.e(TAG, "Cannot animate camera to bounds with zero size", e);
                            MyMap.animateCamera(CameraUpdateFactory.newLatLngZoom(providerLocation, 15f));
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error calculating provider-to-pickup route: " + e.getMessage());
            }
        });
    }
    // NEW CODE (Fixed)
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Only remove listeners here.
        // Listeners are tied to the UI/View updates, so removing them here is fine.
        if (requestListener != null) requestListener.remove();
        if (providerListener != null) providerListener.remove();

        // Do NOT shutdown executor or GeoApiContext here.
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        // Shutdown heavy resources here, when the Fragment instance is actually destroyed.
        if (mGeoApiContext != null) {
            mGeoApiContext.shutdown();
        }
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}