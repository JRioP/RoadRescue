package fourthyear.roadrescue;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileAccountSettingsFragment extends Fragment {

    private static final String TAG = "ProfileSettingsFrag";
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    public ProfileAccountSettingsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Ensure the XML name matches your layout file
        return inflater.inflate(R.layout.activity_profile_account_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupUIMainComponents(view);
    }

    public void setupUIMainComponents(View view) {
        ImageView backButton = view.findViewById(R.id.back_button2);

        // Use popBackStack for Fragments, not onBackPressed
        backButton.setOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            } else {
                if (getActivity() != null) getActivity().onBackPressed();
            }
        });

        // --- NAVIGATION LOGIC FIXED BELOW ---
        // We use loadFragment() helper to switch screens instead of startActivity()

        RelativeLayout passwordSetting = view.findViewById(R.id.setting_password);
        passwordSetting.setOnClickListener(v ->
                loadFragment(new ProfileChangePasswordFragment()));

        RelativeLayout contactInfoSetting = view.findViewById(R.id.setting_contact);
        contactInfoSetting.setOnClickListener(v ->
                loadFragment(new ProfileContactInfoFragment()));

        RelativeLayout languageSetting = view.findViewById(R.id.setting_language);
        languageSetting.setOnClickListener(v ->
                loadFragment(new ProfileAccountLanguageSettingFragment()));

        RelativeLayout accountManagementSetting = view.findViewById(R.id.setting_account_management);
        accountManagementSetting.setOnClickListener(v ->
                loadFragment(new ProfileAccountManagementSettingFragment()));
    }

    // Helper method to switch fragments
    private void loadFragment(Fragment fragment) {
        if (getParentFragmentManager() != null) {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment) // Ensure this ID matches your Activity's container ID
                    .addToBackStack(null)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .commit();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }
}