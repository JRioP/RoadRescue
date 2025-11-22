package fourthyear.roadrescue;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

public class ProfileAccountLanguageSettingFragment extends Fragment {

    private static final String TAG = "LanguageSettingsFrag";

    // UI Elements
    private ImageView backButton;
    private RadioGroup radioGroupLanguage;
    private RadioButton radioEnglish, radioFilipino, radioSpanish, radioJapanese, radioChineseSimplified, radioFrench;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    public ProfileAccountLanguageSettingFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.activity_profile_account_language_setting, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize Firebase
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize Views
        backButton = view.findViewById(R.id.back_button);
        radioGroupLanguage = view.findViewById(R.id.radio_group_language);
        radioEnglish = view.findViewById(R.id.radio_english);
        radioFilipino = view.findViewById(R.id.radio_filipino);
        radioSpanish = view.findViewById(R.id.radio_spanish);
        radioJapanese = view.findViewById(R.id.radio_japanese);
        radioChineseSimplified = view.findViewById(R.id.radio_chinese_simplified);
        radioFrench = view.findViewById(R.id.radio_french);

        // Setup Logic
        loadLanguagePreference();

        backButton.setOnClickListener(v -> {
            // Correct way to go back from a Fragment
            if (getActivity() != null) {
                requireActivity().getOnBackPressedDispatcher().onBackPressed();
            }
        });

        radioGroupLanguage.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_english) {
                setAppLocale("en");
            } else if (checkedId == R.id.radio_filipino) {
                setAppLocale("fil");
            } else if (checkedId == R.id.radio_spanish) {
                setAppLocale("es");
            } else if (checkedId == R.id.radio_japanese) {
                setAppLocale("ja");
            } else if (checkedId == R.id.radio_chinese_simplified) {
                setAppLocale("zh-CN");
            } else if (checkedId == R.id.radio_french) {
                setAppLocale("fr");
            }
        });
    }

    private void loadLanguagePreference() {
        LocaleListCompat locales = AppCompatDelegate.getApplicationLocales();
        String currentLanguage;

        if (locales.isEmpty()) {
            currentLanguage = Locale.getDefault().toLanguageTag();
        } else {
            currentLanguage = locales.get(0).toLanguageTag();
        }

        if (currentLanguage.startsWith("fil")) {
            radioFilipino.setChecked(true);
        } else if (currentLanguage.startsWith("es")) {
            radioSpanish.setChecked(true);
        } else if (currentLanguage.startsWith("ja")) {
            radioJapanese.setChecked(true);
        } else if (currentLanguage.startsWith("zh-CN") || currentLanguage.startsWith("zh-Hans")) {
            radioChineseSimplified.setChecked(true);
        } else if (currentLanguage.startsWith("fr")) {
            radioFrench.setChecked(true);
        } else {
            radioEnglish.setChecked(true);
        }
    }

    private void setAppLocale(String languageCode) {
        LocaleListCompat newLocale = LocaleListCompat.forLanguageTags(languageCode);
        AppCompatDelegate.setApplicationLocales(newLocale);

        Toast.makeText(requireContext(), "Language set. Restarting app...", Toast.LENGTH_SHORT).show();

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Intent intent = new Intent(requireContext(), MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            if (getActivity() != null) getActivity().finish();
            return;
        }

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(documentSnapshot -> {
                    String userType = "Customer";
                    if (documentSnapshot.exists()) {
                        String type = documentSnapshot.getString("userType");
                        if (type != null && (type.trim().equalsIgnoreCase("Service Provider") || type.trim().equalsIgnoreCase("driver"))) {
                            userType = "Service Provider";
                        }
                    }

                    Intent intent;
                    if (userType.equals("Service Provider")) {
                        intent = new Intent(requireContext(), NavigationActivity.class);
                    } else {
                        intent = new Intent(requireContext(), NavigationActivity.class);
                    }

                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    if (getActivity() != null) getActivity().finish();
                })
                .addOnFailureListener(e -> {
                    // Fallback
                    Intent intent = new Intent(requireContext(), MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    if (getActivity() != null) getActivity().finish();
                });
    }
}