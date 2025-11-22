package fourthyear.roadrescue;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class ProfileChangePasswordFragment extends Fragment {

    private static final String TAG = "ChangePasswordFrag";

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private TextInputEditText etCurrentPassword;
    private TextInputEditText etNewPassword;
    private TextInputEditText etRetypePassword;
    private Button btnSavePassword;

    // --- Badge Listeners & UI ---
    private ListenerRegistration unreadListener;
    private ListenerRegistration notificationListener;
    private TextView unreadBadge;
    private TextView unreadNotificationBadge;

    public ProfileChangePasswordFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Ensure XML matches your layout file name
        return inflater.inflate(R.layout.activity_profile_change_password, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Init Views
        unreadBadge = view.findViewById(R.id.unread_message_badge);
        unreadNotificationBadge = view.findViewById(R.id.unread_notification_badge);
        TextView forgotPasswordTextView = view.findViewById(R.id.text_forgot_password);
        ImageView backButton = view.findViewById(R.id.back_button);

        etCurrentPassword = view.findViewById(R.id.input_current_password);
        etNewPassword = view.findViewById(R.id.input_new_password);
        etRetypePassword = view.findViewById(R.id.input_retype_password);
        btnSavePassword = view.findViewById(R.id.button_save_password);

        // Setup Listeners
        setupNavbar(view);
        setupBadgeListeners();
        setupNotificationListener();

        forgotPasswordTextView.setOnClickListener(v -> sendResetEmail());

        // Use dispatcher for back navigation
        backButton.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());

        btnSavePassword.setOnClickListener(v -> validateAndChangePassword());
    }

    private void validateAndChangePassword() {
        String currentPass = etCurrentPassword.getText().toString().trim();
        String newPass = etNewPassword.getText().toString().trim();
        String retypePass = etRetypePassword.getText().toString().trim();

        if (currentPass.isEmpty() || newPass.isEmpty() || retypePass.isEmpty()) {
            Toast.makeText(requireContext(), "All fields are required.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (newPass.length() < 8) {
            Toast.makeText(requireContext(), "Password must be at least 8 characters.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check: New Password != Current Password
        if (newPass.equals(currentPass)) {
            etNewPassword.setError("New password cannot be the same as current password.");
            Toast.makeText(requireContext(), "New password must be different.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newPass.equals(retypePass)) {
            etRetypePassword.setError("Passwords do not match");
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(requireContext(), "User not logged in or email not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Re-authenticate
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPass);

        user.reauthenticate(credential)
                .addOnCompleteListener(task -> {
                    if (!isAdded()) return; // Check if fragment is still attached
                    if (task.isSuccessful()) {
                        // 2. Check History
                        checkPasswordHistoryAndUpdate(user, newPass);
                    } else {
                        etCurrentPassword.setError("Incorrect current password.");
                        Toast.makeText(requireContext(), "Authentication failed: Incorrect current password.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    // --- Check History and Update Logic ---
    private void checkPasswordHistoryAndUpdate(FirebaseUser user, String newPassword) {
        String newPasswordHash = hashPassword(newPassword);
        DocumentReference userDoc = db.collection("users").document(user.getUid());

        userDoc.get().addOnSuccessListener(documentSnapshot -> {
            if (!isAdded()) return;

            if (documentSnapshot.exists()) {
                List<String> passwordHistory = (List<String>) documentSnapshot.get("passwordHistory");
                if (passwordHistory == null) {
                    passwordHistory = new ArrayList<>();
                }

                if (passwordHistory.contains(newPasswordHash)) {
                    Toast.makeText(requireContext(), "You cannot reuse your current or previous 5 passwords.", Toast.LENGTH_LONG).show();
                    etNewPassword.setError("Used recently");
                    return;
                }

                performFirebasePasswordUpdate(user, newPassword, newPasswordHash, passwordHistory);
            } else {
                performFirebasePasswordUpdate(user, newPassword, newPasswordHash, new ArrayList<>());
            }
        }).addOnFailureListener(e -> {
            if (isAdded()) Toast.makeText(requireContext(), "Failed to check password history.", Toast.LENGTH_SHORT).show();
        });
    }

    private void performFirebasePasswordUpdate(FirebaseUser user, String newPassword, String newPasswordHash, List<String> history) {
        user.updatePassword(newPassword)
                .addOnCompleteListener(task -> {
                    if (!isAdded()) return;

                    if (task.isSuccessful()) {
                        history.add(newPasswordHash);
                        if (history.size() > 5) {
                            history.remove(0);
                        }

                        db.collection("users").document(user.getUid())
                                .update("passwordHistory", history)
                                .addOnSuccessListener(aVoid -> {
                                    if (isAdded()) {
                                        Toast.makeText(requireContext(), "Password updated successfully.", Toast.LENGTH_SHORT).show();
                                        requireActivity().getOnBackPressedDispatcher().onBackPressed();
                                    }
                                });

                    } else {
                        if (task.getException() != null) {
                            Toast.makeText(requireContext(), "Update failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            Log.e(TAG, "Hashing error", e);
            return password;
        }
    }

    private void sendResetEmail() {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(requireContext(), "No user is currently logged in.", Toast.LENGTH_SHORT).show();
            return;
        }

        String emailAddress = currentUser.getEmail();

        if (emailAddress == null || emailAddress.isEmpty()) {
            Toast.makeText(requireContext(), "User email not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.sendPasswordResetEmail(emailAddress)
                .addOnCompleteListener(task -> {
                    if (!isAdded()) return;
                    if (task.isSuccessful()) {
                        Toast.makeText(requireContext(), "Password reset email sent to " + emailAddress, Toast.LENGTH_LONG).show();
                    } else {
                        if (task.getException() != null) {
                            Toast.makeText(requireContext(), "Failed to send reset email. " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    // --- Badge Logic ---
    private void setupBadgeListeners() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;
        String currentUserId = user.getUid();

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
                    if (unreadBadge != null) {
                        unreadBadge.setVisibility(totalUnread > 0 ? View.VISIBLE : View.GONE);
                    }
                });
    }

    private void setupNotificationListener() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;
        String currentUserId = currentUser.getUid();

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

    private void setupNavbar(View view) {
        ImageView notificationButton = view.findViewById(R.id.notification_icon_btn);
        if (notificationButton != null) {
            notificationButton.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), NotificationsFragment.class);
                startActivity(intent);
            });
        }

        ConstraintLayout profileLayout = view.findViewById(R.id.nav_profile_layout);
        ImageView profileIcon = view.findViewById(R.id.profile_icon_btn);
        TextView profileText = view.findViewById(R.id.profile_text);

        if (profileLayout != null) {
            profileLayout.setBackgroundResource(R.drawable.rounded_white_background);
            profileLayout.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), ProfileFragment.class);
                startActivity(intent);
            });
        }
        if (profileIcon != null) profileIcon.setColorFilter(Color.BLACK);
        if (profileText != null) {
            profileText.setTextColor(Color.BLACK);
            profileText.setTypeface(null, Typeface.BOLD);
        }

        ImageView homeButton = view.findViewById(R.id.home_icon_btn);
        if (homeButton != null) {
            homeButton.setOnClickListener(v -> {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user == null) {
                    Intent intent = new Intent(requireContext(), MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    requireActivity().finish();
                    return;
                }

                db.collection("users").document(user.getUid()).get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (!isAdded()) return;
                            String userType = "Customer";
                            if (documentSnapshot.exists()) {
                                String type = documentSnapshot.getString("userType");
                                if (type != null && (type.trim().equalsIgnoreCase("Service Provider") || type.trim().equalsIgnoreCase("driver"))) {
                                    userType = "Service Provider";
                                }
                            }

                            Intent intent;
                            if (userType.equals("Service Provider")) {
                                intent = new Intent(requireContext(), ServiceProviderHomeFragment.class);
                            } else {
                                intent = new Intent(requireContext(), MainActivity.class); // Or HomepageFragment
                            }
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            requireActivity().finish();
                        });
            });
        }

        ImageView messageButton = view.findViewById(R.id.message_icon_btn);
        if (messageButton != null) {
            messageButton.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), ChatInboxFragment.class);
                startActivity(intent);
            });
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (unreadListener != null) unreadListener.remove();
        if (notificationListener != null) notificationListener.remove();
    }
}