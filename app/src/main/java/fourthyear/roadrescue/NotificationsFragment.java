package fourthyear.roadrescue;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NotificationsFragment extends Fragment {

    private static final String TAG = "NotificationsFragment";

    private List<NotificationModel> notificationsList;
    private NotificationsAdapter notificationsAdapter;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // Listeners
    private ListenerRegistration mainNotificationListener;

    public NotificationsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        notificationsList = new ArrayList<>();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.activity_notification, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupClickListeners(view);
        setupRecyclerView(view);

        // Logic
        markNotificationsAsRead();
        listenForNotifications();
    }

    public void onNotificationClicked(NotificationModel notification) {
        if (notification.getRequestId() != null) {
            String requestId = notification.getRequestId();

            // 1. Mark as Read in Firestore
            db.collection("service_requests").document(requestId)
                    .update("isRead", true)
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to mark read", e));

            // 2. Fetch Request Details to decide navigation
            db.collection("service_requests").document(requestId).get()
                    .addOnSuccessListener(document -> {
                        if (!isAdded()) return;

                        if (document.exists()) {
                            String status = document.getString("status");

                            if ("completed".equals(status)) {
                                // --- CASE 1: JOB COMPLETED -> OPEN RECEIPT FRAGMENT ---
                                openReceiptFragment(document);
                            } else {
                                // --- CASE 2: JOB ACTIVE -> GO TO MAP ---
                                checkUserTypeAndRedirectToMap();
                            }
                        } else {
                            Toast.makeText(requireContext(), "Request details not found.", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void openReceiptFragment(DocumentSnapshot document) {
        PaymentReceiptFragment receiptFragment = new PaymentReceiptFragment();
        Bundle args = new Bundle();

        args.putString("REFERENCE_ID", document.getId());
        Double amount = document.getDouble("amount");
        args.putString("AMOUNT_PAID", String.format(Locale.getDefault(), "PHP %.2f", amount != null ? amount : 0.0));

        com.google.firebase.Timestamp ts = document.getTimestamp("timestamp");
        if (ts != null) {
            args.putString("PAYMENT_DATE", new SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault()).format(ts.toDate()));
        }

        args.putString("PAYMENT_METHOD", document.getString("paymentMethod"));
        args.putString("REQUEST_TYPE", document.getString("requestType"));
        args.putString("PICKUP_ADDRESS", document.getString("pickupAddress"));
        args.putString("DESTINATION_ADDRESS", document.getString("destinationAddress"));

        receiptFragment.setArguments(args);
        if (getParentFragmentManager() != null) {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, receiptFragment) // Ensure this ID matches your Activity XML
                    .addToBackStack(null)
                    .commit();
        }
    }

    private void checkUserTypeAndRedirectToMap() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid()).get().addOnSuccessListener(userDoc -> {
            if (!isAdded()) return;

            if (userDoc.exists()) {
                String type = userDoc.getString("userType");
                boolean isProvider = type != null && (type.trim().equalsIgnoreCase("Service Provider") || type.trim().equalsIgnoreCase("driver"));

                if (isProvider) {
                    Intent intent = new Intent(requireContext(), ServiceProviderHomeFragment.class);
                    startActivity(intent);
                } else {
                    // Start the Activity that holds the Map (NavigationActivity)
                    Intent intent = new Intent(requireContext(), NavigationActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                }
            }
        });
    }

    private void setupRecyclerView(View view) {
        RecyclerView notificationsRecyclerView = view.findViewById(R.id.notificationsRecyclerView);
        notificationsAdapter = new NotificationsAdapter(notificationsList, this::onNotificationClicked);
        notificationsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        notificationsRecyclerView.setAdapter(notificationsAdapter);
    }

    private void listenForNotifications() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;
        String userId = currentUser.getUid();

        db.collection("users").document(userId).get().addOnSuccessListener(userDoc -> {
            if (!isAdded()) return;
            if (userDoc.exists()) {
                String type = userDoc.getString("userType");
                boolean isProvider = type != null && (type.trim().equalsIgnoreCase("Service Provider") || type.trim().equalsIgnoreCase("driver"));

                Query requestsQuery;
                if (isProvider) {
                    requestsQuery = db.collection("service_requests")
                            .whereEqualTo("providerId", userId)
                            .orderBy("timestamp", Query.Direction.DESCENDING);
                } else {
                    requestsQuery = db.collection("service_requests")
                            .whereEqualTo("customerId", userId)
                            .orderBy("timestamp", Query.Direction.DESCENDING);
                }
                startFirestoreListener(requestsQuery, isProvider);
            }
        });
    }

    private void startFirestoreListener(Query query, boolean isProvider) {
        if (mainNotificationListener != null) mainNotificationListener.remove();

        mainNotificationListener = query.addSnapshotListener((snapshots, e) -> {
            if (e != null || !isAdded()) return;

            notificationsList.clear();
            if (snapshots != null) {
                for (QueryDocumentSnapshot doc : snapshots) {
                    NotificationModel notification = doc.toObject(NotificationModel.class);
                    notification.setRequestId(doc.getId());

                    String status = notification.getStatus();
                    if (status == null) status = "unknown";

                    if (isProvider) {
                        switch (status) {
                            case "pending": notification.setTitle("New Job Opportunity"); notification.setMessage("A customer is waiting."); break;
                            case "accepted": notification.setTitle("Job Active"); notification.setMessage("You accepted this request."); break;
                            case "completed": notification.setTitle("Job Completed"); notification.setMessage("Job finished successfully."); break;
                            default: notification.setTitle("Job Update"); notification.setMessage("Status: " + status); break;
                        }
                    } else {
                        switch (status) {
                            case "pending": notification.setTitle("Request Sent"); notification.setMessage("Searching for provider..."); break;
                            case "accepted": notification.setTitle("Request Accepted!"); notification.setMessage("Provider is on the way."); break;
                            case "completed": notification.setTitle("Service Completed"); notification.setMessage("Tap here to view your receipt."); break;
                            default: notification.setTitle("Status Update"); notification.setMessage("Status: " + status); break;
                        }
                    }
                    notificationsList.add(notification);
                }
            }
            notificationsAdapter.notifyDataSetChanged();
        });
    }

    private void setupClickListeners(View view) {
        ImageView backButton = view.findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        }
    }

    private void markNotificationsAsRead() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        // This marks the "general" notifications as read
        db.collection("notifications")
                .whereEqualTo("userId", currentUser.getUid())
                .whereEqualTo("read", false)
                .get()
                .addOnSuccessListener(snapshots -> {
                    for (DocumentSnapshot doc : snapshots) {
                        doc.getReference().update("read", true);
                    }
                })
                .addOnFailureListener(e -> Log.e("Notifications", "Error marking as read", e));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mainNotificationListener != null) mainNotificationListener.remove();
    }
}