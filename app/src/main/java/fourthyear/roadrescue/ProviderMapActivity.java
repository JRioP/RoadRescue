package fourthyear.roadrescue;

import android.Manifest;
import android.animation.ValueAnimator;
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
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.maps.DirectionsApi;
import com.google.maps.GeoApiContext;
import com.google.maps.android.PolyUtil;
import com.google.maps.model.DirectionsResult;
import com.google.maps.model.TravelMode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ProviderMapActivity extends AppCompatActivity implements
        OnMapReadyCallback,
        PendingRequestsAdapter.OnAcceptClickListener,
        PendingRequestsAdapter.OnItemClickListener {

    private static final String TAG = "ProviderMapActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1002;
    private GoogleMap mGoogleMap;
    private FusedLocationProviderClient mFusedLocationClient;
    private LocationCallback mLocationCallback;
    private boolean mIsLocationUpdating = false;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private FirebaseUser mCurrentUser;
    private DocumentReference mProviderDocRef;
    private ListenerRegistration mPendingRequestsListener;
    private ListenerRegistration mActiveJobListener;

    private ListenerRegistration unreadListener;
    private ListenerRegistration notificationListener;

    private SwitchMaterial mOnlineSwitch;
    private TextView mStatusTextView;
    private RecyclerView mPendingRequestsRecyclerView;
    private CardView mActiveJobCard;
    private TextView mActiveJobPickupText;
    private TextView mActiveJobDestText;
    private TextView mActiveJobDistanceText;
    private Button mCompleteJobButton;
    private Button mNavigateButton;
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
    private Marker mPreviewMarker;
    private Marker mPreviewDestinationMarker;
    private GeoApiContext mGeoApiContext = null;
    private Polyline mProviderToPickupLine;
    private Polyline mPreviewRouteLine;
    private Polyline mCustomerRouteLine;
    private Geocoder mGeocoder;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean isCameraFollowingProvider = false;
    private List<String> providerServices = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_map);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        mCurrentUser = auth.getCurrentUser();
        mGeocoder = new Geocoder(this, Locale.getDefault());

        if (mGeoApiContext == null) {
            mGeoApiContext = new GeoApiContext.Builder()
                    .apiKey(BuildConfig.MAPS_API_KEY)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();
        }

        if (mCurrentUser == null) {
            Toast.makeText(this, "Error: Not logged in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        mProviderDocRef = db.collection("users").document(mCurrentUser.getUid());

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        setupViews();
        setupNavbar();
        setupRecyclerView();
        setupListeners();
        createLocationCallback();
        checkLocationPermission();

        // Setup Badge Listeners
        setupUnreadMessageListener();
        setupNotificationListener();
    }


    private void setupUnreadMessageListener() {
        if (mCurrentUser == null) return;
        String currentUserId = mCurrentUser.getUid();

        unreadListener = db.collection("chats")
                .whereArrayContains("participantIds", currentUserId)
                .whereEqualTo("status", "active")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;
                    int totalUnread = 0;
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            Long count = doc.getLong("unreadCounts." + currentUserId);
                            if (count != null) totalUnread += count;
                        }
                    }
                    if (unreadBadge != null) {
                        unreadBadge.setVisibility(totalUnread > 0 ? View.VISIBLE : View.GONE);
                    }
                });
    }

    private void setupNotificationListener() {
        if (mCurrentUser == null) return;
        String currentUserId = mCurrentUser.getUid();

        notificationListener = db.collection("notifications")
                .whereEqualTo("userId", currentUserId)
                .whereEqualTo("read", false)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;
                    boolean hasUnread = snapshots != null && !snapshots.isEmpty();
                    if (unreadNotificationBadge != null) {
                        unreadNotificationBadge.setVisibility(hasUnread ? View.VISIBLE : View.GONE);
                    }
                });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mGoogleMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mGoogleMap.setMyLocationEnabled(true);
        }

        mGoogleMap.setOnCameraMoveStartedListener(reason -> {
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                if (isCameraFollowingProvider) {
                    isCameraFollowingProvider = false;
                    mNavigateButton.setText("Navigate");
                    Toast.makeText(ProviderMapActivity.this, "Camera unlocked", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
    private void setupViews() {
        mOnlineSwitch = findViewById(R.id.online_switch);
        mStatusTextView = findViewById(R.id.status_text_view);
        mPendingRequestsRecyclerView = findViewById(R.id.pending_requests_recycler_view);
        mActiveJobCard = findViewById(R.id.active_job_card);
        mActiveJobPickupText = findViewById(R.id.active_job_pickup_text);
        mActiveJobDestText = findViewById(R.id.active_job_destination_text);
        mActiveJobDistanceText = findViewById(R.id.active_job_distance_text);
        mCompleteJobButton = findViewById(R.id.complete_job_button);
        mNavigateButton = findViewById(R.id.navigate_button);
    }
    private void setupNavbar() {
        unreadBadge = findViewById(R.id.unread_message_badge);
        unreadNotificationBadge = findViewById(R.id.unread_notification_badge);

        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        if (notificationButton != null) {
            notificationButton.setOnClickListener(v -> {
                Intent intent = new Intent(ProviderMapActivity.this, NotificationsActivity.class);
                startActivity(intent);
            });
        }

        ImageView profileButton = findViewById(R.id.profile_icon_btn);
        if (profileButton != null) {
            profileButton.setOnClickListener(v -> {
                Intent intent = new Intent(ProviderMapActivity.this, ProfileActivity.class);
                startActivity(intent);
            });
        }

        ImageView homeButton = findViewById(R.id.home_icon_btn);
        if (homeButton != null) {
            homeButton.setOnClickListener(v -> {
                Intent intent = new Intent(ProviderMapActivity.this, ServiceProviderHomepage.class);
                startActivity(intent);
            });
        }

        ImageView messageButton = findViewById(R.id.message_icon_btn);
        if (messageButton != null) {
            messageButton.setOnClickListener(v -> {
                Intent intent = new Intent(ProviderMapActivity.this, ChatInboxActivity.class);
                startActivity(intent);
            });
        }
    }

    private void setupRecyclerView() {
        mPendingRequestsAdapter = new PendingRequestsAdapter(mPendingRequestsList, this, this);
        mPendingRequestsRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        mPendingRequestsRecyclerView.setAdapter(mPendingRequestsAdapter);
    }

    private void setupListeners() {
        mOnlineSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                goOnline();
            } else {
                goOffline();
            }
        });
        mCompleteJobButton.setOnClickListener(v -> completeJob());
        mNavigateButton.setOnClickListener(v -> toggleFollowMode());
    }

    private void toggleFollowMode() {
        isCameraFollowingProvider = !isCameraFollowingProvider;
        if (isCameraFollowingProvider) {
            mNavigateButton.setText("Following");
            Toast.makeText(this, "Camera locked to provider", Toast.LENGTH_SHORT).show();
            if (mProviderLatLng != null && mGoogleMap != null) {
                mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mProviderLatLng, 18f));
            }
        } else {
            mNavigateButton.setText("Navigate");
            Toast.makeText(this, "Camera unlocked", Toast.LENGTH_SHORT).show();
        }
    }

    private void goOnline() {
        Log.d(TAG, "Going online...");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission is required to go online.", Toast.LENGTH_SHORT).show();
            mOnlineSwitch.setChecked(false);
            return;
        }
        mStatusTextView.setText("Online");
        mProviderDocRef.update("isOnline", true);
        startLocationUpdates();
        checkProviderForActiveJob();
    }

    private void goOffline() {
        Log.d(TAG, "Going offline...");
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
        db.collection("service_requests")
                .whereEqualTo("providerId", mCurrentUser.getUid())
                .whereEqualTo("status", "accepted")
                .limit(1)
                .get()
                .addOnCompleteListener(task -> {
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
        mStatusTextView.setVisibility(state == UIState.OFFLINE ? View.VISIBLE : View.GONE);
    }

    private void showActiveJobUI() {
        showCorrectUI(UIState.SHOWING_ACTIVE_JOB);
        if (mPendingRequestsListener != null) mPendingRequestsListener.remove();
        mPendingRequestsList.clear();
        mPendingRequestsAdapter.notifyDataSetChanged();

        Double pickupLat = (Double) mActiveJobData.get("pickupLat");
        Double pickupLng = (Double) mActiveJobData.get("pickupLng");
        Double destLat = (Double) mActiveJobData.get("destinationLat");
        Double destLng = (Double) mActiveJobData.get("destinationLng");

        if (pickupLat == null || pickupLng == null || destLat == null || destLng == null) {
            Toast.makeText(this, "Job data is incomplete. Cannot display.", Toast.LENGTH_LONG).show();
            goOffline();
            return;
        }

        mPickupLatLng = new LatLng(pickupLat, pickupLng);
        LatLng destLatLng = new LatLng(destLat, destLng);
        mActiveJobPickupText.setText((String) mActiveJobData.get("pickupAddress"));
        mActiveJobDestText.setText((String) mActiveJobData.get("destinationAddress"));

        if (mGoogleMap != null) {
            mGoogleMap.clear();
            if (mProviderToPickupLine != null) {
                mProviderToPickupLine.remove();
                mProviderToPickupLine = null;
            }
            if (mPreviewRouteLine != null) {
                mPreviewRouteLine.remove();
                mPreviewRouteLine = null;
            }
            if (mCustomerRouteLine != null) {
                mCustomerRouteLine.remove();
                mCustomerRouteLine = null;
            }
            mPreviewMarker = null;
            mPreviewDestinationMarker = null;

            mPickupMarker = mGoogleMap.addMarker(new MarkerOptions().position(mPickupLatLng).title("Pickup").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            mDestinationMarker = mGoogleMap.addMarker(new MarkerOptions().position(destLatLng).title("Destination").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

            drawCustomerRoute(mPickupLatLng, destLatLng);

            if (mProviderLatLng != null) {
                mProviderMarker = null;
                updateProviderMarker(mProviderLatLng);
            }
        }
        listenForActiveJobUpdates();
    }

    private void showPendingJobsUI() {
        showCorrectUI(UIState.SHOWING_PENDING_LIST);
        if (mActiveJobListener != null) mActiveJobListener.remove();
        if (mGoogleMap != null) {
            mGoogleMap.clear();
            if (mProviderToPickupLine != null) {
                mProviderToPickupLine.remove();
                mProviderToPickupLine = null;
            }
            if (mPreviewRouteLine != null) {
                mPreviewRouteLine.remove();
                mPreviewRouteLine = null;
            }
            if (mCustomerRouteLine != null) {
                mCustomerRouteLine.remove();
                mCustomerRouteLine = null;
            }
        }
        mPickupMarker = null;
        mDestinationMarker = null;
        mPreviewMarker = null;
        mPreviewDestinationMarker = null;

        if (mGoogleMap != null && mProviderLatLng != null) {
            mProviderMarker = null;
            updateProviderMarker(mProviderLatLng);
        }

        mActiveJobId = null;
        mActiveJobData = null;
        loadPendingRequests();
    }

    @Override
    public void onAcceptClick(String requestId, Map<String, Object> requestData) {
        if (mPendingRequestsListener != null) mPendingRequestsListener.remove();
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "accepted");
        updates.put("providerId", mCurrentUser.getUid());

        db.collection("service_requests").document(requestId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    mActiveJobId = requestId;
                    mActiveJobData = requestData;
                    showActiveJobUI();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to accept, job may be taken.", Toast.LENGTH_SHORT).show();
                    showPendingJobsUI();
                });
    }

    @Override
    public void onItemClick(Map<String, Object> requestData) {
        Double pickupLat = (Double) requestData.get("pickupLat");
        Double pickupLng = (Double) requestData.get("pickupLng");
        Double destLat = (Double) requestData.get("destinationLat");
        Double destLng = (Double) requestData.get("destinationLng");

        if (pickupLat != null && pickupLng != null && destLat != null && destLng != null && mGoogleMap != null) {
            if (mPreviewMarker != null) {
                mPreviewMarker.remove();
            }
            if (mPreviewDestinationMarker != null) {
                mPreviewDestinationMarker.remove();
            }
            if (mPreviewRouteLine != null) {
                mPreviewRouteLine.remove();
            }

            LatLng pickupLocation = new LatLng(pickupLat, pickupLng);
            LatLng destinationLocation = new LatLng(destLat, destLng);

            mPreviewMarker = mGoogleMap.addMarker(new MarkerOptions()
                    .position(pickupLocation)
                    .title("Pending Pickup")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));

            mPreviewDestinationMarker = mGoogleMap.addMarker(new MarkerOptions()
                    .position(destinationLocation)
                    .title("Pending Destination")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));

            drawPreviewRoute(pickupLocation, destinationLocation);
        }
    }

    @SuppressLint("MissingPermission")
    private void startLocationUpdates() {
        if (mIsLocationUpdating) return;
        LocationRequest locationRequest = new LocationRequest.Builder(10000)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .build();
        mFusedLocationClient.requestLocationUpdates(locationRequest, mLocationCallback, Looper.getMainLooper());
        mIsLocationUpdating = true;
    }

    private void stopLocationUpdates() {
        if (mLocationCallback != null) {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        }
        mIsLocationUpdating = false;
    }

    private void createLocationCallback() {
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                super.onLocationResult(locationResult);
                Location location = locationResult.getLastLocation();
                if (location == null) return;
                mProviderLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                updateProviderLocationInFirestore(location);
                updateProviderMarker(mProviderLatLng);

                if (isCameraFollowingProvider && mGoogleMap != null) {
                    mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mProviderLatLng, 18f));
                }

                mPendingRequestsAdapter.updateProviderLocation(mProviderLatLng);
                if (mActiveJobCard.getVisibility() == View.VISIBLE && mPickupLatLng != null) {
                    drawProviderRoute(mProviderLatLng, mPickupLatLng);
                }
            }
        };
    }

    private LatLng lastProviderLocation = null;
    private ValueAnimator providerAnimator;

    private float calculateBearing(LatLng from, LatLng to) {
        double lat1 = Math.toRadians(from.latitude);
        double lon1 = Math.toRadians(from.longitude);
        double lat2 = Math.toRadians(to.latitude);
        double lon2 = Math.toRadians(to.longitude);
        double dLon = lon2 - lon1;
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        return (float) ((Math.toDegrees(Math.atan2(y, x)) + 360) % 360);
    }

    private void updateProviderMarker(LatLng newLocation) {
        if (mGoogleMap == null) return;
        int height = 100;
        int width = 100;
        Bitmap b = BitmapFactory.decodeResource(getResources(), R.drawable.car_top_icon);
        Bitmap smallMarker = Bitmap.createScaledBitmap(b, width, height, false);
        BitmapDescriptor providerIcon = BitmapDescriptorFactory.fromBitmap(smallMarker);

        if (mProviderMarker == null) {
            mProviderMarker = mGoogleMap.addMarker(new MarkerOptions()
                    .position(newLocation)
                    .title("Your Location")
                    .icon(providerIcon)
                    .flat(true)
                    .anchor(0.5f, 0.5f));
            if (mActiveJobCard.getVisibility() != View.VISIBLE && !isCameraFollowingProvider) {
                mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(newLocation, 15f));
            }
            lastProviderLocation = newLocation;
            return;
        }

        if (lastProviderLocation == null) {
            lastProviderLocation = mProviderMarker.getPosition();
        }
        if (providerAnimator != null && providerAnimator.isRunning()) {
            providerAnimator.cancel();
        }

        final LatLng startLatLng = lastProviderLocation;
        final LatLng endLatLng = newLocation;

        providerAnimator = ValueAnimator.ofFloat(0, 1);
        providerAnimator.setDuration(2000);
        providerAnimator.setInterpolator(new LinearInterpolator());
        providerAnimator.addUpdateListener(animation -> {
            try {
                float v = (float) animation.getAnimatedValue();
                double lat = v * endLatLng.latitude + (1 - v) * startLatLng.latitude;
                double lng = v * endLatLng.longitude + (1 - v) * startLatLng.longitude;
                LatLng newPos = new LatLng(lat, lng);
                mProviderMarker.setPosition(newPos);
                float bearing = calculateBearing(startLatLng, endLatLng);
                mProviderMarker.setRotation(bearing);
            } catch (Exception e) {
                Log.e(TAG, "Marker animation failed", e);
            }
        });
        providerAnimator.start();
        lastProviderLocation = newLocation;
    }

    private void updateProviderLocationInFirestore(Location location) {
        GeoPoint geoPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
        Map<String, Object> updates = new HashMap<>();
        updates.put("liveLocation", geoPoint);
        mExecutor.execute(() -> {
            try {
                List<Address> addresses = mGeocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                if (addresses != null && !addresses.isEmpty()) {
                    String address = addresses.get(0).getAddressLine(0);
                    mHandler.post(() -> {
                        updates.put("currentLocationAddress", address);
                        mProviderDocRef.update(updates);
                    });
                } else {
                    mHandler.post(() -> mProviderDocRef.update(updates));
                }
            } catch (IOException e) {
                mHandler.post(() -> mProviderDocRef.update(updates));
            }
        });
    }

    private void completeJob() {
        if (mActiveJobId == null) return;
        db.collection("service_requests").document(mActiveJobId)
                .update("status", "completed")
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Job marked as complete!", Toast.LENGTH_LONG).show();
                    showPendingJobsUI();
                });
    }

    private void listenForActiveJobUpdates() {
        if (mActiveJobListener != null) mActiveJobListener.remove();
        mActiveJobListener = db.collection("service_requests").document(mActiveJobId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) return;
                    if (snapshot == null || !snapshot.exists()) {
                        Toast.makeText(this, "This request was cancelled.", Toast.LENGTH_LONG).show();
                        showPendingJobsUI();
                    }
                });
    }

    // --- UPDATED: Filter Requests by Provider Skills ---
    private void loadPendingRequests() {
        if (mPendingRequestsListener != null) mPendingRequestsListener.remove();

        // 1. Get Provider's Skills First
        mProviderDocRef.get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                List<String> myServices = (List<String>) snapshot.get("servicesProvided");

                if (myServices == null || myServices.isEmpty()) {
                    Log.w(TAG, "Provider has no services set. No requests will be shown.");
                    mPendingRequestsList.clear();
                    mPendingRequestsAdapter.notifyDataSetChanged();
                    Toast.makeText(this, "Please update your Profile with services you provide.", Toast.LENGTH_LONG).show();
                    return;
                }

                // 2. Listen ONLY for requests matching skills
                mPendingRequestsListener = db.collection("service_requests")
                        .whereEqualTo("status", "pending")
                        // Ideally use array-contains-any if filtering by multiple, but 'in' query is limited to 10 items.
                        // For filtering by 'requestType', the 'whereIn' query is efficient.
                        .whereIn("requestType", myServices)
                        .orderBy("timestamp", Query.Direction.ASCENDING)
                        .addSnapshotListener((value, error) -> {
                            if (error != null) {
                                Log.e(TAG, "Request listener failed", error);
                                return;
                            }
                            mPendingRequestsList.clear();
                            if (value != null) {
                                for (QueryDocumentSnapshot doc : value) {
                                    Map<String, Object> requestData = new HashMap<>(doc.getData());
                                    requestData.put("requestId", doc.getId());
                                    mPendingRequestsList.add(requestData);
                                }
                            }
                            mPendingRequestsAdapter.notifyDataSetChanged();
                        });

            } else {
                Log.e(TAG, "Provider profile missing.");
            }
        });
    }
    // --------------------------------------------------

    private void checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (mGoogleMap != null) {
                    try {
                        mGoogleMap.setMyLocationEnabled(true);
                    } catch (SecurityException e) {
                        Log.e(TAG, "Security exception after permission grant", e);
                    }
                }
                if (mOnlineSwitch.isChecked()) {
                    goOnline();
                }
            } else {
                Toast.makeText(this, "Location permission is required for this app to function.", Toast.LENGTH_LONG).show();
                mOnlineSwitch.setChecked(false);
            }
        }
    }

    private void openGoogleMaps() {
        if (mPickupLatLng == null) {
            Toast.makeText(this, "No destination to navigate to.", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri gmmIntentUri = Uri.parse("google.navigation:q=" + mPickupLatLng.latitude + "," + mPickupLatLng.longitude);
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");
        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            Toast.makeText(this, "Google Maps is not installed.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mOnlineSwitch.isChecked()) {
            goOnline();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mIsLocationUpdating) {
            stopLocationUpdates();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mLocationCallback != null)
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        if (mPendingRequestsListener != null) mPendingRequestsListener.remove();
        if (mActiveJobListener != null) mActiveJobListener.remove();
        if (unreadListener != null) unreadListener.remove();
        if (notificationListener != null) notificationListener.remove();
        mExecutor.shutdown();
        if (mGeoApiContext != null) {
            mGeoApiContext.shutdown();
        }
        if (mProviderDocRef != null) mProviderDocRef.update("isOnline", false);
    }

    private void drawProviderRoute(LatLng providerLocation, LatLng pickupLocation) {
        Log.d(TAG, "Attempting to draw provider route...");
        mExecutor.execute(() -> {
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

                    mHandler.post(() -> {
                        if (mProviderToPickupLine != null) {
                            mProviderToPickupLine.remove();
                        }
                        mProviderToPickupLine = mGoogleMap.addPolyline(new PolylineOptions()
                                .addAll(decodedPath)
                                .width(12)
                                .color(Color.CYAN));
                        mActiveJobDistanceText.setText(String.format("%s (~%s)", distance, duration));

                        if (!isCameraFollowingProvider) {
                            LatLngBounds.Builder builder = new LatLngBounds.Builder();
                            builder.include(pickupLocation);
                            builder.include(providerLocation);
                            try {
                                mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 150));
                            } catch (Exception e) {
                                Log.e(TAG, "Camera animation for bounds failed", e);
                            }
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Directions API failed", e);
                mHandler.post(() -> drawStraightProviderLine(providerLocation, pickupLocation));
            }
        });
    }

    private void drawStraightProviderLine(LatLng providerLocation, LatLng pickupLocation) {
        if (mProviderToPickupLine != null) {
            mProviderToPickupLine.remove();
        }
        mProviderToPickupLine = mGoogleMap.addPolyline(new PolylineOptions()
                .add(providerLocation, pickupLocation)
                .width(12f)
                .color(Color.CYAN)
                .geodesic(true));

        float[] results = new float[1];
        Location.distanceBetween(
                providerLocation.latitude, providerLocation.longitude,
                pickupLocation.latitude, pickupLocation.longitude,
                results);

        float distanceInKm = results[0] / 1000;
        int timeInMinutes = (int) (distanceInKm * 2);
        if (timeInMinutes < 1) timeInMinutes = 1;
        mActiveJobDistanceText.setText(String.format(Locale.getDefault(), "%.1f km away (~%d min)", distanceInKm, timeInMinutes));
    }

    private void drawPreviewRoute(LatLng pickupLocation, LatLng destinationLocation) {
        Log.d(TAG, "Attempting to draw PREVIEW route...");
        mExecutor.execute(() -> {
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

                    mHandler.post(() -> {
                        if (mPreviewRouteLine != null) {
                            mPreviewRouteLine.remove();
                        }
                        mPreviewRouteLine = mGoogleMap.addPolyline(new PolylineOptions()
                                .addAll(decodedPath)
                                .width(12)
                                .color(Color.GRAY));

                        LatLngBounds.Builder builder = new LatLngBounds.Builder();
                        builder.include(pickupLocation);
                        builder.include(destinationLocation);
                        try {
                            mGoogleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 150));
                        } catch (Exception e) {
                            Log.e(TAG, "Camera animation for preview bounds failed", e);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Preview Directions API failed", e);
            }
        });
    }

    private void drawCustomerRoute(LatLng pickupLocation, LatLng destinationLocation) {
        Log.d(TAG, "Attempting to draw CUSTOMER route...");
        mExecutor.execute(() -> {
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

                    mHandler.post(() -> {
                        if (mCustomerRouteLine != null) {
                            mCustomerRouteLine.remove();
                        }
                        mCustomerRouteLine = mGoogleMap.addPolyline(new PolylineOptions()
                                .addAll(decodedPath)
                                .width(12)
                                .color(Color.RED));
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Customer Directions API failed", e);
            }
        });
    }
}