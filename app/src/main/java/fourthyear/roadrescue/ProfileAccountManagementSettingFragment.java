package fourthyear.roadrescue;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.firebase.auth.FirebaseAuth;

public class ProfileAccountManagementSettingFragment extends Fragment {

    private static final String TAG = "AccountMgmtFragment";

    private ImageView backButton;
    private RelativeLayout buttonDeleteAccount;
    private RelativeLayout buttonLogout;

    private FirebaseAuth mAuth;

    public ProfileAccountManagementSettingFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Ensure this XML matches your layout file name
        return inflater.inflate(R.layout.activity_profile_account_management_setting, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        setupClickListeners();
    }

    private void initializeViews(View view) {
        // Make sure these IDs match your XML file
        backButton = view.findViewById(R.id.back_button);
        buttonDeleteAccount = view.findViewById(R.id.setting_delete_account);
        buttonLogout = view.findViewById(R.id.setting_logout);
    }

    private void setupClickListeners() {
        if (backButton != null) {
            backButton.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        }

        if (buttonDeleteAccount != null) {
            buttonDeleteAccount.setOnClickListener(v ->
                    loadFragment(new ProfileAccountDeleteSettingsFragment())
            );
        }

        if (buttonLogout != null) {
            buttonLogout.setOnClickListener(v -> showLogoutConfirmation());
        }
    }

    // Helper method to switch fragments correctly
    private void loadFragment(Fragment fragment) {
        if (getParentFragmentManager() != null) {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment) // Ensure this ID matches your Activity's container
                    .addToBackStack(null)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .commit();
        }
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    mAuth.signOut();

                    Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(requireContext(), MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    if (getActivity() != null) {
                        getActivity().finish();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}