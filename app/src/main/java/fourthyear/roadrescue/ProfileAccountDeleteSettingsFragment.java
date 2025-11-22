package fourthyear.roadrescue;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileAccountDeleteSettingsFragment extends Fragment {

    private static final String TAG = "DeleteAccountFragment";

    private ImageView backButton;
    private MaterialButton buttonDelete;
    private Button buttonCancel;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    public ProfileAccountDeleteSettingsFragment() {
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
        // Inflate the layout for this fragment (Ensure XML name matches)
        return inflater.inflate(R.layout.activity_profile_account_deletion_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        setupClickListeners();
    }

    private void initializeViews(View view) {
        backButton = view.findViewById(R.id.back_button);
        buttonDelete = view.findViewById(R.id.button_delete);
        buttonCancel = view.findViewById(R.id.button_cancel);
    }

    private void setupClickListeners() {
        // Use onBackPressedDispatcher for Fragment back navigation
        if (backButton != null) {
            backButton.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        }

        if (buttonCancel != null) {
            buttonCancel.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        }

        if (buttonDelete != null) {
            buttonDelete.setOnClickListener(v -> showDeleteConfirmationDialog());
        }
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Account")
                .setMessage("Are you absolutely sure?\nThis action is permanent and cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteUserAccount();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void deleteUserAccount() {
        final FirebaseUser user = mAuth.getCurrentUser();

        if (user == null) {
            Toast.makeText(requireContext(), "No user logged in.", Toast.LENGTH_SHORT).show();
            return;
        }

        final String userId = user.getUid();

        db.collection("users").document(userId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    if (!isAdded()) return;
                    Log.d(TAG, "User's Firestore document deleted.");
                    deleteFirebaseAuthRecord(user);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    Log.e(TAG, "Error deleting Firestore document", e);
                    Toast.makeText(requireContext(), "Error deleting data. " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void deleteFirebaseAuthRecord(FirebaseUser user) {
        user.delete()
                .addOnCompleteListener(task -> {
                    if (!isAdded()) return;
                    if (task.isSuccessful()) {
                        Log.d(TAG, "User account deleted.");
                        Toast.makeText(requireContext(), "Account deleted successfully.", Toast.LENGTH_LONG).show();

                        Intent intent = new Intent(requireContext(), MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        requireActivity().finish();

                    } else {
                        Log.w(TAG, "Error deleting user account", task.getException());

                        if (task.getException() instanceof FirebaseAuthRecentLoginRequiredException) {
                            Toast.makeText(requireContext(), "Please sign in again to delete your account.", Toast.LENGTH_LONG).show();
                            startActivity(new Intent(requireContext(), MainActivity.class));
                            requireActivity().finish();
                        } else {
                            Toast.makeText(requireContext(), "Error: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }
}