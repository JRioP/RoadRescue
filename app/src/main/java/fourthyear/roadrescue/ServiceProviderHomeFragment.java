// File: ServiceProviderHomeFragment.java

package fourthyear.roadrescue;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServiceProviderHomeFragment extends Fragment implements PendingRequestsAdapter.OnAcceptClickListener, PendingRequestsAdapter.OnItemClickListener {

    private static final String TAG = "SPHomeFragment";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private long lastClickTime = 0;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private RecyclerView requestsRecyclerView;
    private PendingRequestsAdapter requestsAdapter;

    private FloatingActionButton btnGoToMap;
    private TextView titleText;

    private final List<Map<String, Object>> requestsList = new ArrayList<>();
    private final List<Map<String, Object>> activeJobsList = new ArrayList<>();
    private final List<Map<String, Object>> pendingJobsList = new ArrayList<>();
    private List<String> driverServices = new ArrayList<>();

    private ListenerRegistration pendingListener;
    private ListenerRegistration activeJobListener;

    private FusedLocationProviderClient fusedLocationClient;
    private LatLng currentLatLng;

    public ServiceProviderHomeFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_service_provider_homepage, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        if (currentUser == null) {
            if (getActivity() != null) getActivity().finish();
            return;
        }

        setupViews(view);
        setupRecyclerView();
        checkLocationPermission();
        updateLocation();
    }

    @Override
    public void onStart() {
        super.onStart();
        fetchDriverServicesAndListen();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (pendingListener != null) pendingListener.remove();
        if (activeJobListener != null) activeJobListener.remove();
    }

    private boolean isSafeClick() {
        if (SystemClock.elapsedRealtime() - lastClickTime < 500) {
            return false;
        }
        lastClickTime = SystemClock.elapsedRealtime();
        return true;
    }

    private void setupViews(View view) {
        titleText = view.findViewById(R.id.title_text);
        requestsRecyclerView = view.findViewById(R.id.requests_recycler_view);

        btnGoToMap = view.findViewById(R.id.btn_go_to_map);

        if (btnGoToMap != null) {
            btnGoToMap.setVisibility(View.VISIBLE);
            btnGoToMap.setOnClickListener(v -> {
                if (!isSafeClick()) return;
                if (!activeJobsList.isEmpty()) {
                    redirectToActiveJob(activeJobsList.get(0));
                } else {
                    Toast.makeText(requireContext(), "You have no active jobs right now.", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    // Helper method to load fragments if needed (kept in case you add other buttons)
    private void loadFragment(Fragment fragment) {
        if (getActivity() != null) {
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit();
        }
    }

    private void fetchDriverServicesAndListen() {
        if (currentUser == null) return;
        db.collection("users").document(currentUser.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!isAdded()) return;
                    driverServices.clear();
                    if (documentSnapshot.exists()) {
                        Object servicesObj = documentSnapshot.get("servicesProvided");
                        if (servicesObj instanceof List) {
                            List<String> rawServices = (List<String>) servicesObj;
                            for (String service : rawServices) {
                                if (service != null) {
                                    driverServices.add(service.toLowerCase().trim());
                                }
                            }
                        }
                    }
                    listenForRequests();
                });
    }

    private void listenForRequests() {
        if (currentUser == null) return;
        String myUserId = currentUser.getUid();

        if (activeJobListener != null) activeJobListener.remove();
        activeJobListener = db.collection("service_requests")
                .whereEqualTo("providerId", myUserId)
                .whereEqualTo("status", "accepted")
                .addSnapshotListener((value, error) -> {
                    if (error != null || !isAdded()) return;
                    activeJobsList.clear();
                    if (value != null) {
                        for (QueryDocumentSnapshot doc : value) {
                            Map<String, Object> data = new HashMap<>(doc.getData());
                            data.put("requestId", doc.getId());
                            data.put("isMyActiveJob", true);
                            activeJobsList.add(data);
                        }
                    }
                    mergeAndDisplayRequests();
                });

        if (pendingListener != null) pendingListener.remove();
        pendingListener = db.collection("service_requests")
                .whereEqualTo("status", "pending")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null || !isAdded()) return;
                    pendingJobsList.clear();
                    if (value != null) {
                        for (QueryDocumentSnapshot doc : value) {
                            Map<String, Object> data = new HashMap<>(doc.getData());
                            String rawRequestType = (String) data.get("requestType");

                            if (rawRequestType != null) {
                                String processedRequestType = rawRequestType.toLowerCase().trim();
                                if (driverServices.contains(processedRequestType)) {
                                    data.put("requestId", doc.getId());
                                    pendingJobsList.add(data);
                                }
                            }
                        }
                    }
                    mergeAndDisplayRequests();
                });
    }

    private void mergeAndDisplayRequests() {
        if (!isAdded()) return;

        requestsList.clear();
        requestsList.addAll(activeJobsList);
        requestsList.addAll(pendingJobsList);
        requestsAdapter.notifyDataSetChanged();

        if (titleText != null) {
            if (!activeJobsList.isEmpty()) {
                titleText.setText("Current Job Active");
                titleText.setTextColor(Color.RED);
            } else {
                titleText.setText(String.format("Service Request (%d New)", pendingJobsList.size()));
                titleText.setTextColor(Color.WHITE);
            }
        }
    }

    @Override
    public void onAcceptClick(String requestId, Map<String, Object> requestData) {
        if (!isSafeClick()) return;

        if (currentUser == null) return;
        if (!activeJobsList.isEmpty()) {
            Toast.makeText(requireContext(), "Complete your current job first!", Toast.LENGTH_LONG).show();
            return;
        }

        String spUserId = currentUser.getUid();
        markRequestAsRead(requestId);

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", "accepted");
        updates.put("providerId", spUserId);
        updates.put("acceptedTimestamp", FieldValue.serverTimestamp());
        updates.put("isRead", true);

        db.collection("service_requests").document(requestId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Request accepted!", Toast.LENGTH_SHORT).show();
                        redirectToActiveJob(requestData);
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) Toast.makeText(requireContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onItemClick(Map<String, Object> requestData) {
        if (!isSafeClick()) return;
        String requestId = (String) requestData.get("requestId");
        markRequestAsRead(requestId);
        redirectToActiveJob(requestData);
    }

    private void redirectToActiveJob(Map<String, Object> requestData) {
        if (requestData == null || !isAdded()) return;

        String requestId = (String) requestData.get("requestId");
        Double pickupLat = (Double) requestData.get("pickupLat");
        Double pickupLng = (Double) requestData.get("pickupLng");
        String pickupAddress = (String) requestData.get("pickupAddress");
        String requestType = (String) requestData.get("requestType");
        String customerId = (String) requestData.get("customerId");

        if (pickupLat == null || pickupLng == null || requestId == null) {
            Toast.makeText(requireContext(), "Error: Request data incomplete.", Toast.LENGTH_SHORT).show();
            return;
        }

        ProviderMapFragment mapFragment = ProviderMapFragment.newInstance(
                requestId, pickupLat, pickupLng, pickupAddress, requestType, customerId
        );

        if (getActivity() != null) {
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, mapFragment)
                    .addToBackStack(null)
                    .commit();
        }
    }

    private void markRequestAsRead(String requestId) {
        if (requestId == null) return;
        db.collection("service_requests").document(requestId)
                .update("isRead", true)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to mark read", e));
    }

    private void setupRecyclerView() {
        requestsAdapter = new PendingRequestsAdapter(requestsList, this, this);
        requestsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        requestsRecyclerView.setAdapter(requestsAdapter);
    }

    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    private void updateLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(requireActivity(), location -> {
            if (location != null) {
                currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                requestsAdapter.updateProviderLocation(currentLatLng);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            updateLocation();
        }
    }
}