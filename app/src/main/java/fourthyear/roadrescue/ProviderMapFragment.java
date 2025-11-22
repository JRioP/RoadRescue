package fourthyear.roadrescue;

import android.Manifest;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.android.PolyUtil;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.TravelMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ProviderMapFragment extends Fragment implements
        OnMapReadyCallback,
        PendingRequestsAdapter.OnAcceptClickListener,
        PendingRequestsAdapter.OnItemClickListener {

    private static final String TAG = "ProviderMapFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1002;
    private static final float COMPLETION_RADIUS_METERS = 5.0f;

    private static final String ARG_REQUEST_ID = "REQUEST_ID";
    private static final String ARG_PICKUP_LAT = "PICKUP_LAT";
    private static final String ARG_PICKUP_LNG = "PICKUP_LNG";
    private static final String ARG_PICKUP_ADDRESS = "PICKUP_ADDRESS";
    private static final String ARG_REQUEST_TYPE = "REQUEST_TYPE";
    private static final String ARG_CUSTOMER_ID = "CUSTOMER_ID";

    private long lastClickTime = 0;

    private GoogleMap mGoogleMap;
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationCallback mLocationCallback;
    private boolean mIsLocationUpdating = false;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private FirebaseUser mCurrentUser;
    private DocumentReference mProviderDocRef;

    private FirebaseStorage storage;
    private ActivityResultLauncher<String> imagePickerLauncher;
    private ProgressDialog progressDialog;

    private ListenerRegistration mPendingRequestsListener;
    private ListenerRegistration mActiveJobListener;
    private ListenerRegistration unreadListener;
    private ListenerRegistration notificationListener;

    private SwitchMaterial mOnlineSwitch;
    private TextView mStatusTextView;
    private RecyclerView mPendingRequestsRecyclerView;
    private CardView mActiveJobCard;
    private TextView mActiveJobTitle;
    private TextView mActiveJobVehicleText;
    private TextView mActiveJobPickupText;
    private TextView mActiveJobDestText;
    private TextView mActiveJobDistanceText;

    private Button mCompleteJobButton;
    private Button mNavigateButton;
    private Button mChatCustomerButton;

    private TextView unreadBadge;
    private TextView unreadNotificationBadge;

    private PendingRequestsAdapter mPendingRequestsAdapter;
    private final List<Map<String, Object>> mPendingRequestsList = new ArrayList<>();
    private Map<String, Object> mActiveJobData;
    private String mActiveJobId;
    private LatLng mPickupLatLng;
    private LatLng mProviderLatLng;
    private Marker mProviderMarker;
    private Marker mPickupMarker;
    private Marker mDestinationMarker;

    private GeoApiContext mGeoApiContext = null;
    private Polyline mProviderToPickupLine;
    private Geocoder mGeocoder;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean isCameraFollowingProvider = false;

    private String argRequestId;
    private Double argPickupLat;
    private Double argPickupLng;
    private String argPickupAddress;
    private String argRequestType;
    private String argCustomerId;

    public ProviderMapFragment() {
    }

    public static ProviderMapFragment newInstance(String requestId, Double pickupLat, Double pickupLng, String pickupAddress, String requestType, String customerId) {
        ProviderMapFragment fragment = new ProviderMapFragment();
        Bundle args = new Bundle();
        args.putString(ARG_REQUEST_ID, requestId);
        if (pickupLat != null) args.putDouble(ARG_PICKUP_LAT, pickupLat);
        if (pickupLng != null) args.putDouble(ARG_PICKUP_LNG, pickupLng);
        args.putString(ARG_PICKUP_ADDRESS, pickupAddress);
        args.putString(ARG_REQUEST_TYPE, requestType);
        args.putString(ARG_CUSTOMER_ID, customerId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            argRequestId = getArguments().getString(ARG_REQUEST_ID);
            argPickupLat = getArguments().getDouble(ARG_PICKUP_LAT);
            argPickupLng = getArguments().getDouble(ARG_PICKUP_LNG);
            argPickupAddress = getArguments().getString(ARG_PICKUP_ADDRESS);
            argRequestType = getArguments().getString(ARG_REQUEST_TYPE);
            argCustomerId = getArguments().getString(ARG_CUSTOMER_ID);
            mActiveJobId = argRequestId;
        }

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        mCurrentUser = auth.getCurrentUser();
        mGeocoder = new Geocoder(requireContext(), Locale.getDefault());

        try {
            storage = FirebaseStorage.getInstance("gs://roadrescue-b46e9.firebasestorage.app");
        } catch (Exception e) {
            storage = FirebaseStorage.getInstance();
        }

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        uploadProofImage(uri);
                    } else {
                        Toast.makeText(requireContext(), "No image selected.", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        if (mGeoApiContext == null) {
            mGeoApiContext = new GeoApiContext.Builder()
                    .apiKey(BuildConfig.MAPS_API_KEY)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();
        }

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_provider_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (mCurrentUser == null) {
            Toast.makeText(requireContext(), "Error: Not logged in.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(requireContext(), MainActivity.class));
            if (getActivity() != null) getActivity().finish();
            return;
        }
        mProviderDocRef = db.collection("users").document(mCurrentUser.getUid());

        progressDialog = new ProgressDialog(requireContext());
        progressDialog.setMessage("Uploading proof and completing job...");
        progressDialog.setCancelable(false);

        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        setupViews(view);
        setupRecyclerView(view);
        setupListeners();
        createLocationCallback();
        checkLocationPermission();
        setupUnreadMessageListener();
        setupNotificationListener();

        if (argRequestId != null) {
            mActiveJobId = argRequestId;
            listenForActiveJobUpdates();
        }
    }

    private boolean isSafeClick() {
        if (SystemClock.elapsedRealtime() - lastClickTime < 500) {
            return false;
        }
        lastClickTime = SystemClock.elapsedRealtime();
        return true;
    }

    private void setupViews(View view) {
        mOnlineSwitch = view.findViewById(R.id.online_switch);
        mStatusTextView = view.findViewById(R.id.status_text_view);
        mPendingRequestsRecyclerView = view.findViewById(R.id.pending_requests_recycler_view);

        mActiveJobCard = view.findViewById(R.id.active_job_card);
        mActiveJobTitle = view.findViewById(R.id.active_job_title);
        mActiveJobVehicleText = view.findViewById(R.id.active_job_vehicle_text);
        mChatCustomerButton = view.findViewById(R.id.chat_customer_button);

        mActiveJobPickupText = view.findViewById(R.id.active_job_pickup_text);
        mActiveJobDestText = view.findViewById(R.id.active_job_destination_text);
        mActiveJobDistanceText = view.findViewById(R.id.active_job_distance_text);
        mCompleteJobButton = view.findViewById(R.id.complete_job_button);
        mNavigateButton = view.findViewById(R.id.navigate_button);

        if (mOnlineSwitch != null) mOnlineSwitch.bringToFront();
        if (mStatusTextView != null) mStatusTextView.bringToFront();
        if (mActiveJobCard != null) mActiveJobCard.bringToFront();
        if (mNavigateButton != null) mNavigateButton.bringToFront();
        if (mPendingRequestsRecyclerView != null) mPendingRequestsRecyclerView.bringToFront();
    }

    private void setupListeners() {
        mOnlineSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) goOnline();
            else goOffline();
        });

        mCompleteJobButton.setOnClickListener(v -> {
            if (isSafeClick()) completeJob();
        });

        mNavigateButton.setOnClickListener(v -> {
            if (isSafeClick()) toggleFollowMode();
        });

        mChatCustomerButton.setOnClickListener(v -> {
            if (isSafeClick()) openChatWithCustomer();
        });
    }

    private void completeJob() {
        if (mActiveJobId == null) return;
        if (mCompleteJobButton.isEnabled()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Proof of Service Required")
                    .setMessage("Please upload a photo to mark this job as complete.")
                    .setPositiveButton("Take/Select Photo", (dialog, which) -> {
                        imagePickerLauncher.launch("image/*");
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .show();
        } else {
            Toast.makeText(requireContext(), "You are too far away! Move closer (5m) to the pickup point.", Toast.LENGTH_LONG).show();
        }
    }

    private void updateCompleteButtonState() {
        if (mActiveJobCard.getVisibility() != View.VISIBLE || mPickupLatLng == null || mProviderLatLng == null) {
            mCompleteJobButton.setEnabled(false);
            mCompleteJobButton.setAlpha(0.5f);
            return;
        }

        float[] results = new float[1];
        Location.distanceBetween(
                mProviderLatLng.latitude, mProviderLatLng.longitude,
                mPickupLatLng.latitude, mPickupLatLng.longitude,
                results
        );

        float distanceToPickup = results[0];

        if (distanceToPickup <= COMPLETION_RADIUS_METERS) {
            mCompleteJobButton.setEnabled(true);
            mCompleteJobButton.setAlpha(1.0f);
            mCompleteJobButton.setText("Mark as Complete");
        } else {
            mCompleteJobButton.setEnabled(false);
            mCompleteJobButton.setAlpha(0.5f);
            mCompleteJobButton.setText(String.format(Locale.getDefault(), "Get Closer (%.1fm)", distanceToPickup));
        }
    }

    private void uploadProofImage(Uri imageUri) {
        progressDialog.show();
        String filename = "proofs/" + mActiveJobId + "_" + UUID.randomUUID().toString() + ".jpg";
        StorageReference ref = storage.getReference().child(filename);

        ref.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> {
                    ref.getDownloadUrl().addOnSuccessListener(uri -> {
                        String downloadUrl = uri.toString();
                        finishJobInFirestore(downloadUrl);
                    });
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(requireContext(), "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void finishJobInFirestore(String imageUrl) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "completed");
        updates.put("proofImageUrl", imageUrl);
        updates.put("completedTimestamp", FieldValue.serverTimestamp());

        db.collection("service_requests").document(mActiveJobId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    if(!isAdded()) return;
                    closeChatSession();

                    progressDialog.dismiss();
                    Toast.makeText(requireContext(), "Job completed successfully!", Toast.LENGTH_LONG).show();
                    showPendingJobsUI();
                })
                .addOnFailureListener(e -> {
                    if(!isAdded()) return;
                    progressDialog.dismiss();
                    Toast.makeText(requireContext(), "Failed to update database.", Toast.LENGTH_SHORT).show();
                });
    }

    private void closeChatSession() {
        if (mActiveJobData == null || mCurrentUser == null || mActiveJobId == null) return;

        String customerId = (String) mActiveJobData.get("customerId");
        String currentUserId = mCurrentUser.getUid();

        if (customerId == null) return;

        String chatRoomId;
        if (currentUserId.compareTo(customerId) > 0) {
            chatRoomId = mActiveJobId + "_" + currentUserId + "_" + customerId;
        } else {
            chatRoomId = mActiveJobId + "_" + customerId + "_" + currentUserId;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "closed");

        db.collection("chats").document(chatRoomId)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Chat session closed successfully"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to close chat session", e));
    }

    private void createLocationCallback() {
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);
                if(!isAdded()) return;
                Location location = locationResult.getLastLocation();
                if (location == null) return;

                mProviderLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                updateProviderLocationInFirestore(location);
                updateProviderMarker(mProviderLatLng);
                if (mPendingRequestsAdapter != null) {
                    mPendingRequestsAdapter.updateProviderLocation(mProviderLatLng);
                }

                if (isCameraFollowingProvider && mGoogleMap != null) {
                    com.google.android.gms.maps.model.CameraPosition.Builder builder =
                            new com.google.android.gms.maps.model.CameraPosition.Builder()
                                    .target(mProviderLatLng)
                                    .zoom(17f)
                                    .tilt(45f);

                    if (location.hasBearing()) {
                        builder.bearing(location.getBearing());
                    } else {
                        builder.bearing(mGoogleMap.getCameraPosition().bearing);
                    }
                    mGoogleMap.animateCamera(CameraUpdateFactory.newCameraPosition(builder.build()));
                }

                if (mActiveJobCard.getVisibility() == View.VISIBLE && mPickupLatLng != null && mProviderToPickupLine == null) {
                    drawProviderRoute(mProviderLatLng, mPickupLatLng);
                }
                updateCompleteButtonState();
            }
        };
    }

    private void showActiveJobUI() {
        showCorrectUI(UIState.SHOWING_ACTIVE_JOB);
        if (mPendingRequestsListener != null) mPendingRequestsListener.remove();
        mPendingRequestsList.clear();
        mPendingRequestsAdapter.notifyDataSetChanged();

        String carBrand = (String) mActiveJobData.get("carBrand");
        String carModel = (String) mActiveJobData.get("carModel");
        String carType = (String) mActiveJobData.get("carType");

        String vehicleInfo = "Vehicle info not available";
        if (carBrand != null || carModel != null) {
            StringBuilder sb = new StringBuilder();
            if (carBrand != null) sb.append(carBrand).append(" ");
            if (carModel != null) sb.append(carModel);
            if (carType != null) sb.append(" (").append(carType).append(")");
            vehicleInfo = sb.toString();
        }
        mActiveJobVehicleText.setText("Vehicle: " + vehicleInfo);

        Double pickupLat = (Double) mActiveJobData.get("pickupLat");
        Double pickupLng = (Double) mActiveJobData.get("pickupLng");
        Double destLat = (Double) mActiveJobData.get("destinationLat");
        Double destLng = (Double) mActiveJobData.get("destinationLng");

        if (pickupLat == null || pickupLng == null) {
            if (argPickupLat != null && argPickupLng != null) {
                pickupLat = argPickupLat;
                pickupLng = argPickupLng;
            } else {
                Toast.makeText(requireContext(), "Job data incomplete.", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        mPickupLatLng = new LatLng(pickupLat, pickupLng);
        LatLng destLatLng = (destLat != null && destLng != null) ? new LatLng(destLat, destLng) : mPickupLatLng;

        String pAddress = (String) mActiveJobData.get("pickupAddress");
        if (pAddress == null) pAddress = argPickupAddress;
        mActiveJobPickupText.setText(pAddress);
        mActiveJobDestText.setText((String) mActiveJobData.get("destinationAddress"));

        if (mGoogleMap != null) {
            mGoogleMap.clear();
            mPickupMarker = mGoogleMap.addMarker(new MarkerOptions().position(mPickupLatLng).title("Pickup").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            if (!mPickupLatLng.equals(destLatLng)) {
                mDestinationMarker = mGoogleMap.addMarker(new MarkerOptions().position(destLatLng).title("Destination").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
            }
            drawProviderRoute(mProviderLatLng != null ? mProviderLatLng : mPickupLatLng, mPickupLatLng);
        }

        updateCompleteButtonState();
        listenForActiveJobUpdates();
    }

    private void toggleFollowMode() {
        isCameraFollowingProvider = !isCameraFollowingProvider;
        if (isCameraFollowingProvider) {
            mNavigateButton.setText("Following");
            Toast.makeText(requireContext(), "Camera locked to your location", Toast.LENGTH_SHORT).show();
            if (mProviderLatLng != null && mGoogleMap != null) {
                mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mProviderLatLng, 12f));
            }
        } else {
            mNavigateButton.setText("Navigate");
            Toast.makeText(requireContext(), "Camera unlocked", Toast.LENGTH_SHORT).show();
        }
    }

    private void openChatWithCustomer() {
        if (mActiveJobData == null || mCurrentUser == null || mActiveJobId == null) {
            Toast.makeText(requireContext(), "Job details missing.", Toast.LENGTH_SHORT).show();
            return;
        }

        String customerId = (String) mActiveJobData.get("customerId");
        if (customerId == null) customerId = argCustomerId;

        if (customerId == null) {
            Toast.makeText(requireContext(), "Customer info not available.", Toast.LENGTH_SHORT).show();
            return;
        }

        DocumentReference customerRef = db.collection("users").document(customerId);
        String finalCustomerId = customerId;

        customerRef.get().addOnSuccessListener(customerDoc -> {
                    if(!isAdded()) return;
                    String customerName = (customerDoc.exists() && customerDoc.getString("name") != null) ?
                            customerDoc.getString("name") : "Customer";

                    mProviderDocRef.get().addOnSuccessListener(providerDoc -> {
                        if(!isAdded()) return;
                        String providerName = (providerDoc.exists() && providerDoc.getString("name") != null) ?
                                providerDoc.getString("name") : "Service Provider";

                        String currentUserId = mCurrentUser.getUid();
                        String chatRoomId;

                        if (currentUserId.compareTo(finalCustomerId) > 0) {
                            chatRoomId = mActiveJobId + "_" + currentUserId + "_" + finalCustomerId;
                        } else {
                            chatRoomId = mActiveJobId + "_" + finalCustomerId + "_" + currentUserId;
                        }

                        DocumentReference chatRef = db.collection("chats").document(chatRoomId);

                        String finalCustomerName = customerName;
                        String finalProviderName = providerName;

                        chatRef.get().addOnSuccessListener(chatDoc -> {
                            if(!isAdded()) return;
                            if (!chatDoc.exists()) {
                                Map<String, Object> chatData = new HashMap<>();
                                chatData.put("chatId", chatRoomId);
                                chatData.put("requestId", mActiveJobId);
                                chatData.put("participantIds", Arrays.asList(currentUserId, finalCustomerId));
                                chatData.put("lastMessage", "Provider has connected.");
                                chatData.put("lastMessageTimestamp", FieldValue.serverTimestamp());
                                chatData.put("status", "active");

                                Map<String, String> names = new HashMap<>();
                                names.put(currentUserId, finalProviderName);
                                names.put(finalCustomerId, finalCustomerName);
                                chatData.put("participantNames", names);

                                Map<String, Integer> unreadCounts = new HashMap<>();
                                unreadCounts.put(currentUserId, 0);
                                unreadCounts.put(finalCustomerId, 0);
                                chatData.put("unreadCounts", unreadCounts);

                                chatRef.set(chatData).addOnSuccessListener(aVoid -> {
                                    launchChatActivity(chatRoomId, finalCustomerName, finalCustomerId);
                                });
                            } else {
                                chatRef.update("status", "active");
                                launchChatActivity(chatRoomId, finalCustomerName, finalCustomerId);
                            }
                        });
                    });
                })
                .addOnFailureListener(e -> Toast.makeText(requireContext(), "Network error fetching details", Toast.LENGTH_SHORT).show());
    }

    private void launchChatActivity(String chatId, String receiverName, String receiverId) {
        Intent intent = new Intent(requireContext(), ChatConversationFragment.class);
        intent.putExtra("chatId", chatId);
        intent.putExtra("receiverName", receiverName);
        intent.putExtra("receiverId", receiverId);
        startActivity(intent);
    }

    private void goOnline() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), "Location permission required.", Toast.LENGTH_SHORT).show();
            mOnlineSwitch.setChecked(false);
            return;
        }
        mStatusTextView.setText("Online");
        mProviderDocRef.update("isOnline", true);
        startLocationUpdates();
        checkProviderForActiveJob();
    }

    private void goOffline() {
        mStatusTextView.setText("Offline");
        mProviderDocRef.update("isOnline", false);
        stopLocationUpdates();
        if (mPendingRequestsListener != null) mPendingRequestsListener.remove();
        if (mActiveJobListener != null) mActiveJobListener.remove();
        mPendingRequestsList.clear();
        mPendingRequestsAdapter.notifyDataSetChanged();
        showCorrectUI(UIState.OFFLINE);
    }

    private void checkProviderForActiveJob() {
        if (mActiveJobId != null) {
            if (mActiveJobData == null) {
                db.collection("service_requests").document(mActiveJobId).get().addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        mActiveJobData = new HashMap<>(snapshot.getData());
                        showActiveJobUI();
                    }
                });
            } else {
                showActiveJobUI();
            }
            return;
        }

        db.collection("service_requests")
                .whereEqualTo("providerId", mCurrentUser.getUid())
                .whereEqualTo("status", "accepted")
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
                    if(!isAdded()) return;
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        DocumentSnapshot doc = task.getResult().getDocuments().get(0);
                        mActiveJobId = doc.getId();
                        mActiveJobData = new HashMap<>(doc.getData());
                        showActiveJobUI();
                    } else {
                        showPendingJobsUI();
                    }
                });
    }

    private enum UIState {OFFLINE, SHOWING_PENDING_LIST, SHOWING_ACTIVE_JOB}

    private void showCorrectUI(UIState state) {
        mPendingRequestsRecyclerView.setVisibility(state == UIState.SHOWING_PENDING_LIST ? View.VISIBLE : View.GONE);
        mActiveJobCard.setVisibility(state == UIState.SHOWING_ACTIVE_JOB ? View.VISIBLE : View.GONE);
        mStatusTextView.setVisibility(View.VISIBLE);
    }

    private void showPendingJobsUI() {
        showCorrectUI(UIState.SHOWING_PENDING_LIST);
        if (mActiveJobListener != null) mActiveJobListener.remove();
        if (mGoogleMap != null) mGoogleMap.clear();
        mActiveJobId = null;
        mActiveJobData = null;
        argRequestId = null;
        loadPendingRequests();
    }

    @Override
    public void onAcceptClick(String requestId, Map<String, Object> requestData) {
        if (mPendingRequestsListener != null) mPendingRequestsListener.remove();
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "accepted");
        updates.put("providerId", mCurrentUser.getUid());
        updates.put("acceptedTimestamp", FieldValue.serverTimestamp());

        db.collection("service_requests").document(requestId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    if(!isAdded()) return;
                    mActiveJobId = requestId;
                    mActiveJobData = requestData;
                    showActiveJobUI();
                })
                .addOnFailureListener(e -> {
                    if(!isAdded()) return;
                    Toast.makeText(requireContext(), "Job unavailable.", Toast.LENGTH_SHORT).show();
                    showPendingJobsUI();
                });
    }
    @Override
    public void onItemClick(Map<String, Object> requestData) {
        Double pickupLat = (Double) requestData.get("pickupLat");
        Double pickupLng = (Double) requestData.get("pickupLng");
        if (pickupLat != null && pickupLng != null && mGoogleMap != null) {
            mGoogleMap.clear();
            LatLng p = new LatLng(pickupLat, pickupLng);
            mGoogleMap.addMarker(new MarkerOptions().position(p).title("Request Location").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));
            mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(p, 14f));
        }
    }

    private void updateProviderLocationInFirestore(Location location) {
        GeoPoint geoPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
        Map<String, Object> updates = new HashMap<>();
        updates.put("liveLocation", geoPoint);
        mProviderDocRef.update(updates);
    }

    private LatLng lastProviderLocation = null;
    private ValueAnimator providerAnimator;

    private void updateProviderMarker(LatLng newLocation) {
        if (mGoogleMap == null || !isAdded()) return;
        int height = 100;
        int width = 100;
        Bitmap b = BitmapFactory.decodeResource(getResources(), R.drawable.car_top_icon);
        Bitmap smallMarker = Bitmap.createScaledBitmap(b, width, height, false);
        BitmapDescriptor providerIcon = BitmapDescriptorFactory.fromBitmap(smallMarker);

        if (mProviderMarker == null) {
            mProviderMarker = mGoogleMap.addMarker(new MarkerOptions().position(newLocation).title("Your Location").icon(providerIcon).flat(true).anchor(0.5f, 0.5f));
            mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newLocation, 15f));
            lastProviderLocation = newLocation;
            return;
        }

        if (lastProviderLocation == null) lastProviderLocation = mProviderMarker.getPosition();
        if (providerAnimator != null && providerAnimator.isRunning()) providerAnimator.cancel();

        providerAnimator = ValueAnimator.ofFloat(0, 1);
        providerAnimator.setDuration(2000);
        providerAnimator.setInterpolator(new LinearInterpolator());
        providerAnimator.addUpdateListener(animation -> {
            if (mProviderMarker == null) return;
            float v = (float) animation.getAnimatedValue();
            double lat = v * newLocation.latitude + (1 - v) * lastProviderLocation.latitude;
            double lng = v * newLocation.longitude + (1 - v) * lastProviderLocation.longitude;
            mProviderMarker.setPosition(new LatLng(lat, lng));
        });
        providerAnimator.start();
        lastProviderLocation = newLocation;
    }

    private void listenForActiveJobUpdates() {
        if (mActiveJobListener != null) mActiveJobListener.remove();
        mActiveJobListener = db.collection("service_requests").document(mActiveJobId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || !isAdded()) return;
                    if (snapshot == null || !snapshot.exists()) {
                        Toast.makeText(requireContext(), "Request cancelled.", Toast.LENGTH_LONG).show();
                        showPendingJobsUI();
                    }
                });
    }

    private void loadPendingRequests() {
        if (mPendingRequestsListener != null) mPendingRequestsListener.remove();
        mProviderDocRef.get().addOnSuccessListener(snapshot -> {
            if(!isAdded()) return;
            if (snapshot.exists()) {
                List<String> myServices = (List<String>) snapshot.get("servicesProvided");
                if (myServices == null || myServices.isEmpty()) {
                    mPendingRequestsList.clear();
                    if (mPendingRequestsAdapter != null) mPendingRequestsAdapter.notifyDataSetChanged();
                    return;
                }
                mPendingRequestsListener = db.collection("service_requests")
                        .whereEqualTo("status", "pending")
                        .whereIn("requestType", myServices)
                        .orderBy("timestamp", Query.Direction.ASCENDING)
                        .addSnapshotListener((value, error) -> {
                            if (error != null || !isAdded()) return;
                            mPendingRequestsList.clear();
                            if (value != null) {
                                for (QueryDocumentSnapshot doc : value) {
                                    Map<String, Object> requestData = new HashMap<>(doc.getData());
                                    requestData.put("requestId", doc.getId());
                                    mPendingRequestsList.add(requestData);
                                }
                            }
                            if (mPendingRequestsAdapter != null) mPendingRequestsAdapter.notifyDataSetChanged();
                        });
            }
        });
    }

    private void setupUnreadMessageListener() {
        if (mCurrentUser == null) return;
        String currentUserId = mCurrentUser.getUid();
        unreadListener = db.collection("chats")
                .whereArrayContains("participantIds", currentUserId)
                .whereEqualTo("status", "active")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || !isAdded()) return;
                    int totalUnread = 0;
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            Long count = doc.getLong("unreadCounts." + currentUserId);
                            if (count != null) totalUnread += count;
                        }
                    }
                    if (unreadBadge != null) unreadBadge.setVisibility(totalUnread > 0 ? View.VISIBLE : View.GONE);
                });
    }

    private void setupNotificationListener() {
        if (mCurrentUser == null) return;
        String currentUserId = mCurrentUser.getUid();
        Query badgeQuery = db.collection("notifications")
                .whereEqualTo("userId", currentUserId)
                .whereEqualTo("read", false);

        if (notificationListener != null) {
            notificationListener.remove();
        }

        notificationListener = badgeQuery.addSnapshotListener((snapshots, e) -> {
            if (e != null || !isAdded()) {
                Log.e(TAG, "Notification listener error", e);
                return;
            }
            boolean hasUnread = snapshots != null && !snapshots.isEmpty();

            if (unreadNotificationBadge != null) {
                unreadNotificationBadge.setVisibility(hasUnread ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (mGoogleMap != null) {
                try { mGoogleMap.setMyLocationEnabled(true); } catch (SecurityException e) {}
            }
            if (mOnlineSwitch.isChecked()) goOnline();
        }
    }

    private void setupRecyclerView(View view) {
        mPendingRequestsAdapter = new PendingRequestsAdapter(mPendingRequestsList, this, this);
        mPendingRequestsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        mPendingRequestsRecyclerView.setAdapter(mPendingRequestsAdapter);
    }

    private void drawProviderRoute(LatLng providerLocation, LatLng pickupLocation) {
        mExecutor.execute(() -> {
            try {
                com.google.maps.model.LatLng origin = new com.google.maps.model.LatLng(providerLocation.latitude, providerLocation.longitude);
                com.google.maps.model.LatLng destination = new com.google.maps.model.LatLng(pickupLocation.latitude, pickupLocation.longitude);
                DirectionsResult result = DirectionsApi.newRequest(mGeoApiContext).origin(origin).destination(destination).mode(TravelMode.DRIVING).await();
                if (result.routes != null && result.routes.length > 0) {
                    final List<LatLng> decodedPath = PolyUtil.decode(result.routes[0].overviewPolyline.getEncodedPath());
                    final String dist = result.routes[0].legs[0].distance.humanReadable;
                    final String dur = result.routes[0].legs[0].duration.humanReadable;
                    mHandler.post(() -> {
                        if(!isAdded()) return;
                        if (mProviderToPickupLine != null) mProviderToPickupLine.remove();
                        mProviderToPickupLine = mGoogleMap.addPolyline(new PolylineOptions().addAll(decodedPath).width(12).color(Color.BLUE));
                        mActiveJobDistanceText.setText(dist + " (~" + dur + ")");
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Route drawing failed", e);
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mGoogleMap = googleMap;
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mGoogleMap.setMyLocationEnabled(true);
        }
        mGoogleMap.setOnCameraMoveStartedListener(reason -> {
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                if (isCameraFollowingProvider) {
                    isCameraFollowingProvider = false;
                    mNavigateButton.setText("Navigate");
                    Toast.makeText(requireContext(), "Camera unlocked", Toast.LENGTH_SHORT).show();
                }
            }
        });

        if (argPickupLat != null && argPickupLng != null) {
            LatLng p = new LatLng(argPickupLat, argPickupLng);
            mGoogleMap.addMarker(new MarkerOptions().position(p).title("Pickup").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            mGoogleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(p, 15f));
        }
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        if (mIsLocationUpdating) return;
        LocationRequest locationRequest = new LocationRequest.Builder(10000).setPriority(Priority.PRIORITY_HIGH_ACCURACY).build();
        mFusedLocationClient.requestLocationUpdates(locationRequest, mLocationCallback, Looper.getMainLooper());
        mIsLocationUpdating = true;
    }

    private void stopLocationUpdates() {
        if (mLocationCallback != null) mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        mIsLocationUpdating = false;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mLocationCallback != null) mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        if (mPendingRequestsListener != null) mPendingRequestsListener.remove();
        if (mActiveJobListener != null) mActiveJobListener.remove();
        if (unreadListener != null) unreadListener.remove();
        if (notificationListener != null) notificationListener.remove();
        mExecutor.shutdown();
        if (mProviderDocRef != null) mProviderDocRef.update("isOnline", false);
    }
}