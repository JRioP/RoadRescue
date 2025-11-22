package fourthyear.roadrescue;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class HomepageFragment extends Fragment {

    private static final String TAG = "HomepageFragment";

    // --- RECYCLER VIEW VARIABLES ---
    private RecentAdapter recentAdapter;
    private List<RecentItemModel> recentItemModels;
    // -------------------------------

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private DocumentReference userDocRef;
    private String localSessionId;

    private FusedLocationProviderClient fusedLocationClient;
    private TextView locationTextView;

    // Permissions Launchers
    private ActivityResultLauncher<String> requestLocationPermissionLauncher;
    private ActivityResultLauncher<Intent> contactPickerLauncher;
    private ActivityResultLauncher<String> requestContactsPermissionLauncher;
    private ActivityResultLauncher<String> requestCallPermissionLauncher;

    private ListenerRegistration unreadListener;
    private ListenerRegistration notificationListener;
    private TextView unreadBadge;
    private TextView unreadNotificationBadge;

    public HomepageFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();
        initializeLaunchers();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_homepage, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (currentUser == null) {
            redirectToLogin();
            return;
        }

        userDocRef = db.collection("users").document(currentUser.getUid());
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        setupUIComponents(view);

        recentItemModels = new ArrayList<>();
        setupRecyclerView(view);
        loadRecentRequests();

        setupUnreadMessageListener(view);
        setupNotificationListener(view);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isAdded()) return;
            SharedPreferences prefs = requireActivity().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
            localSessionId = prefs.getString("currentSessionId", null);

            if (localSessionId == null) {
                createNewSession(currentUser);
            } else {
                checkSingleSessionConstraint();
            }
            checkLocationPermissionAndFetch();
        }, 250);
    }

    private void initializeLaunchers() {
        requestLocationPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) fetchLastLocation();
            else Toast.makeText(requireContext(), "Location needed for homepage.", Toast.LENGTH_SHORT).show();
        });

        requestContactsPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) launchContactPicker();
            else if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) showGoToSettingsDialog();
            else Toast.makeText(requireContext(), "Permission required for SOS.", Toast.LENGTH_SHORT).show();
        });

        contactPickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == -1 && result.getData() != null) {
                Uri contactUri = result.getData().getData();
                if (contactUri != null) handleContactSelection(contactUri);
            }
        });

        requestCallPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if(isGranted) Toast.makeText(requireContext(), "Call permission granted.", Toast.LENGTH_SHORT).show();
        });
    }

    // --- SOS & Logic ---
    private void onSosButtonClick() {
        if (currentUser == null) return;
        userDocRef.get().addOnSuccessListener(doc -> {
            List<Map<String, Object>> contacts = (List<Map<String, Object>>) doc.get("sos_contacts");
            if (contacts != null && !contacts.isEmpty()) {
                String phone = (String) contacts.get(0).get("phone");
                if (phone != null) executeEmergencyActions(phone);
                else promptToAddContact();
            } else promptToAddContact();
        });
    }

    @SuppressLint("MissingPermission")
    private void executeEmergencyActions(String phone) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            double lat = location != null ? location.getLatitude() : 0.0;
            double lng = location != null ? location.getLongitude() : 0.0;
            String name = (currentUser.getDisplayName() != null) ? currentUser.getDisplayName() : "User";
            String mapsLink = String.format(Locale.getDefault(), "http://googleusercontent.com/maps.google.com/maps?q=%.6f,%.6f", lat, lng);

            Intent smsIntent = new Intent(Intent.ACTION_VIEW);
            smsIntent.setData(Uri.parse("smsto:" + phone));
            smsIntent.putExtra("sms_body", "SOS! Emergency from " + name + ". " + mapsLink);
            try { startActivity(smsIntent); } catch (Exception e) { Toast.makeText(requireContext(), "No SMS app.", Toast.LENGTH_SHORT).show(); }

            new Handler(Looper.getMainLooper()).postDelayed(() -> makeDirectCall(phone), 1500);
        });
    }

    private void makeDirectCall(String phone) {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestCallPermissionLauncher.launch(Manifest.permission.CALL_PHONE);
            return;
        }
        try { startActivity(new Intent(Intent.ACTION_CALL).setData(Uri.parse("tel:" + phone))); } catch (Exception e) { Log.e(TAG, "Call failed", e); }
    }

    private void promptToAddContact() {
        new AlertDialog.Builder(requireContext()).setTitle("Add Emergency Contact").setMessage("Please select an SOS contact.")
                .setPositiveButton("Select", (d, w) -> checkAndRequestContactsPermission()).setNegativeButton("Cancel", null).show();
    }

    private void checkAndRequestContactsPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) launchContactPicker();
        else requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
    }

    private void launchContactPicker() {
        contactPickerLauncher.launch(new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI));
    }

    private void handleContactSelection(Uri contactUri) {
        Cursor cursor = requireContext().getContentResolver().query(contactUri, new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            String name = cursor.getString(0);
            String phone = cursor.getString(1).replaceAll("[^\\d+]", "");
            cursor.close();
            saveSosContact(name, phone);
        }
    }

    private void saveSosContact(String name, String phone) {
        Map<String, Object> contact = new HashMap<>();
        contact.put("name", name);
        contact.put("phone", phone);
        userDocRef.update("sos_contacts", FieldValue.arrayUnion(contact)).addOnSuccessListener(v -> Toast.makeText(requireContext(), "Contact Saved", Toast.LENGTH_SHORT).show());
    }

    private void setupUIComponents(View view) {
        locationTextView = view.findViewById(R.id.textView7);
        unreadBadge = view.findViewById(R.id.unread_message_badge);
        unreadNotificationBadge = view.findViewById(R.id.unread_notification_badge);

        view.findViewById(R.id.sos_button).setOnClickListener(v -> onSosButtonClick());
        setupRequestButton(view, R.id.towing_btn, "Towing");
        setupRequestButton(view, R.id.jump_start_btn, "Jump-Start");
        setupRequestButton(view, R.id.fuel_delivery_btn, "Fuel Delivery");
        setupRequestButton(view, R.id.flat_tire_repair_btn, "Flat Tire Repair");
        setupRequestButton(view, R.id.replace_battery_btn, "Replace Battery");
        setupRequestButton(view, R.id.gas_station_btn, "Gas Station");
    }

    private void setupRequestButton(View view, int id, String type) {
        View btn = view.findViewById(id);
        if (btn != null) btn.setOnClickListener(v -> checkVehicleAndProceed(type));
    }

    private void checkVehicleAndProceed(String type) {
        userDocRef.get().addOnSuccessListener(doc -> {
            if (doc.getString("carType") != null && doc.getString("carModel") != null) {
                MapFragment mapFragment = new MapFragment();
                Bundle args = new Bundle();
                args.putString("REQUEST_TYPE", type);
                mapFragment.setArguments(args);

                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, mapFragment)
                        .addToBackStack(null)
                        .commit();
            } else showMissingProfileDialog();
        });
    }

    private void showMissingProfileDialog() {
        new AlertDialog.Builder(requireContext()).setTitle("Vehicle Info Needed").setMessage("Update profile first.")
                .setPositiveButton("Profile", (d, w) -> {
                    if (getActivity() instanceof NavigationActivity) ((NavigationActivity) getActivity()).navigateToTab(3);
                }).setNegativeButton("Cancel", null).show();
    }

    private void setupRecyclerView(View view) {
        RecyclerView recentRecyclerView = view.findViewById(R.id.recentRecyclerView);
        if (recentRecyclerView != null) {
            recentAdapter = new RecentAdapter(recentItemModels);
            recentRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
            recentRecyclerView.addItemDecoration(new ItemSpacingDecoration(16));
            recentRecyclerView.setAdapter(recentAdapter);
        }
    }

    private void loadRecentRequests() {
        if (currentUser == null) return;
        db.collection("service_requests")
                .whereEqualTo("customerId", currentUser.getUid())
                .orderBy("timestamp", Query.Direction.DESCENDING).limit(5)
                .get()
                .addOnSuccessListener(snapshots -> {
                    recentItemModels.clear();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        String serviceType = doc.getString("requestType");
                        String address = doc.getString("destinationAddress");
                        if (address == null || address.trim().isEmpty()) address = doc.getString("pickupAddress");
                        if (serviceType == null) serviceType = "Service Request";
                        if (address == null) address = "Location Unknown";
                        recentItemModels.add(new RecentItemModel(serviceType, address));
                    }
                    recentAdapter.notifyDataSetChanged();
                });
    }

    private void setupUnreadMessageListener(View view) {
        if (currentUser == null) return;
        unreadBadge = view.findViewById(R.id.unread_message_badge);

        unreadListener = db.collection("chats")
                .whereArrayContains("participantIds", currentUser.getUid())
                .whereEqualTo("status", "active")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || !isAdded() || unreadBadge == null) return;
                    int totalUnread = 0;
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            Long count = doc.getLong("unreadCounts." + currentUser.getUid());
                            if (count != null) totalUnread += count;
                        }
                    }
                    if (totalUnread > 0) {
                        if (unreadBadge.getVisibility() == View.GONE) playNotificationSound();
                        unreadBadge.setVisibility(View.VISIBLE);
                    } else {
                        unreadBadge.setVisibility(View.GONE);
                    }
                });
    }

    private void setupNotificationListener(View view) {
        if (currentUser == null) return;
        // Re-bind views
        unreadNotificationBadge = view.findViewById(R.id.unread_notification_badge);

        notificationListener = db.collection("notifications")
                .whereEqualTo("userId", currentUser.getUid())
                .whereEqualTo("read", false)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || !isAdded() || unreadNotificationBadge == null) return;
                    boolean hasUnread = (snapshots != null && !snapshots.isEmpty());
                    if (hasUnread) {
                        if (unreadNotificationBadge.getVisibility() == View.GONE) playNotificationSound();
                        unreadNotificationBadge.setVisibility(View.VISIBLE);
                    } else {
                        unreadNotificationBadge.setVisibility(View.GONE);
                    }
                });
    }

    private void playNotificationSound() {
        if (!isAdded() || getContext() == null) return;
        try {
            MediaPlayer player = MediaPlayer.create(requireContext(), R.raw.notification_pop);
            player.setOnCompletionListener(mp -> mp.release());
            player.start();
        } catch (Exception e) { Log.e(TAG, "Error playing notification sound", e); }
    }

    private void createNewSession(FirebaseUser user) {
        localSessionId = UUID.randomUUID().toString();
        requireActivity().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE).edit().putString("currentSessionId", localSessionId).apply();
        userDocRef.update("currentSessionId", localSessionId);
    }

    private void checkSingleSessionConstraint() {
        userDocRef.get().addOnSuccessListener(doc -> {
            String dbSession = doc.getString("currentSessionId");
            if (dbSession == null) userDocRef.update("currentSessionId", localSessionId);
            else if (!dbSession.equals(localSessionId)) forceSignOut("Logged in on another device.");
        });
    }

    private void forceSignOut(String msg) {
        mAuth.signOut();
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
        redirectToLogin();
    }

    private void redirectToLogin() {
        startActivity(new Intent(requireContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
        requireActivity().finish();
    }

    private void checkLocationPermissionAndFetch() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) fetchLastLocation();
        else requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
    }

    @SuppressLint("MissingPermission")
    private void fetchLastLocation() {
        fusedLocationClient.getLastLocation().addOnSuccessListener(loc -> {
            if (loc != null) {
                new Thread(() -> {
                    try {
                        List<Address> addrs = new Geocoder(requireContext(), Locale.getDefault()).getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
                        String city = (addrs != null && !addrs.isEmpty()) ? addrs.get(0).getLocality() : "Unknown";
                        if (getActivity() != null) getActivity().runOnUiThread(() -> { if (locationTextView != null) locationTextView.setText(city); });
                    } catch (IOException e) { }
                }).start();
            }
        });
    }

    private void showGoToSettingsDialog() {
        new AlertDialog.Builder(requireContext()).setTitle("Permission Denied").setMessage("Enable contacts in settings.")
                .setPositiveButton("Settings", (d, w) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", requireContext().getPackageName(), null)))).setNegativeButton("Cancel", null).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (unreadListener != null) unreadListener.remove();
        if (notificationListener != null) notificationListener.remove();
    }
}