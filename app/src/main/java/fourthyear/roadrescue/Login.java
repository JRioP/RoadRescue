package fourthyear.roadrescue;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Login extends Fragment {

    private Button loginButton;
    private EditText emailEditText, passwordEditText;
    private CheckBox rememberMeCheckBox;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private View loadingOverlay;
    private Handler handler = new Handler(Looper.getMainLooper());

    // Shared Preferences constants
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "LoginPrefs";
    private static final String KEY_REMEMBER_ME = "rememberMe";
    private static final String KEY_EMAIL = "email";
    private static final String SECURITY_PREFS = "SecurityPrefs";
    private static final String KEY_FAILED_ATTEMPTS = "failedAttempts";
    private static final String KEY_LOCKOUT_TIME = "lockoutTimestamp";
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION = 5 * 60 * 1000; // 5 Minutes in milliseconds
    private static final long INITIAL_WAIT_TIME = 2000; // 2 seconds

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (getActivity() != null) {
            sharedPreferences = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    public void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            showLoading(true);
            checkUserTypeAndRedirect(currentUser);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_login, container, false);

        TextView forgotPassword = view.findViewById(R.id.btn_forget_password);
        loginButton = view.findViewById(R.id.btn_login);
        emailEditText = view.findViewById(R.id.login_email);
        passwordEditText = view.findViewById(R.id.login_password);
        loadingOverlay = view.findViewById(R.id.loading_overlay);
        rememberMeCheckBox = view.findViewById(R.id.checkBox);

        // Load "Remember Me" data
        if (sharedPreferences != null) {
            boolean isRemembered = sharedPreferences.getBoolean(KEY_REMEMBER_ME, false);
            if (isRemembered) {
                String savedEmail = sharedPreferences.getString(KEY_EMAIL, "");
                emailEditText.setText(savedEmail);
                rememberMeCheckBox.setChecked(true);
            }
        }

        setupInputListeners();

        forgotPassword.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), ForgotPassword.class);
            startActivity(intent);
        });

        loginButton.setOnClickListener(v -> authenticateUser());

        return view;
    }

    private void showLoading(boolean isLoading) {
        if (isLoading) {
            if (loadingOverlay != null) loadingOverlay.setVisibility(View.VISIBLE);
            loginButton.setEnabled(false);
            if (emailEditText != null) emailEditText.setEnabled(false);
            if (passwordEditText != null) passwordEditText.setEnabled(false);
        } else {
            if (loadingOverlay != null) loadingOverlay.setVisibility(View.GONE);
            loginButton.setEnabled(true);
            if (emailEditText != null) emailEditText.setEnabled(true);
            if (passwordEditText != null) passwordEditText.setEnabled(true);
        }
    }
    // Find this method in your code
    private void authenticateUser() {
        if (isLockedOut()) {
            return;
        }

        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (!validateInputs(email, password)) return;

        showLoading(true);
        int failedAttempts = getFailedAttempts();
        long dynamicDelay = INITIAL_WAIT_TIME * (long) Math.pow(2, failedAttempts);
        long uiDelay = Math.min(dynamicDelay, 4000);

        handler.postDelayed(() -> {
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(getActivity(), task -> {
                        if (task.isSuccessful()) {
                            resetSecurityCounters();
                            handleRememberMe(email);
                            FirebaseUser user = mAuth.getCurrentUser();
                            if (user != null) {
                                updateUserSession(user);
                            }
                        } else {
                            showLoading(false);
                            handleLoginFailure(task.getException());
                        }
                    });
        }, uiDelay);
    }

    private boolean isLockedOut() {
        if (getActivity() == null) return false;
        SharedPreferences securePrefs = getActivity().getSharedPreferences(SECURITY_PREFS, Context.MODE_PRIVATE);

        long lockoutTimestamp = securePrefs.getLong(KEY_LOCKOUT_TIME, 0);
        long currentTime = System.currentTimeMillis();

        if (lockoutTimestamp > 0) {
            if (currentTime < lockoutTimestamp) {
                long remainingSeconds = (lockoutTimestamp - currentTime) / 1000;
                Toast.makeText(getActivity(), "Too many failed attempts. Try again in " + remainingSeconds + "s", Toast.LENGTH_LONG).show();
                return true;
            } else {
                resetSecurityCounters();
                return false;
            }
        }
        return false;
    }

    private int getFailedAttempts() {
        if (getActivity() == null) return 0;
        SharedPreferences securePrefs = getActivity().getSharedPreferences(SECURITY_PREFS, Context.MODE_PRIVATE);
        return securePrefs.getInt(KEY_FAILED_ATTEMPTS, 0);
    }

    private void handleLoginFailure(Exception exception) {
        if (getActivity() == null) return;

        SharedPreferences securePrefs = getActivity().getSharedPreferences(SECURITY_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = securePrefs.edit();

        int currentAttempts = securePrefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1;
        editor.putInt(KEY_FAILED_ATTEMPTS, currentAttempts);

        if (currentAttempts >= MAX_FAILED_ATTEMPTS) {
            // Trigger Lockout
            long unlockTime = System.currentTimeMillis() + LOCKOUT_DURATION;
            editor.putLong(KEY_LOCKOUT_TIME, unlockTime);
            editor.apply();

            Toast.makeText(getActivity(), "Too many attempts. Account locked for 5 minutes.", Toast.LENGTH_LONG).show();
        } else {
            editor.apply();

            // SECURITY: Generic Error Message to prevent Enumeration
            // We Log the real error for the developer, but show a generic one to the user
            if (exception != null) {
                Log.e("LoginSecurity", "Auth Error: " + exception.getMessage());
            }
            Toast.makeText(getActivity(), "Invalid email or password.", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetSecurityCounters() {
        if (getActivity() == null) return;
        SharedPreferences securePrefs = getActivity().getSharedPreferences(SECURITY_PREFS, Context.MODE_PRIVATE);
        securePrefs.edit().clear().apply();
    }

    private void handleRememberMe(String email) {
        if (sharedPreferences == null) return;

        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (rememberMeCheckBox.isChecked()) {
            editor.putBoolean(KEY_REMEMBER_ME, true);
            editor.putString(KEY_EMAIL, email);
        } else {
            editor.clear();
        }
        editor.apply();
    }

    private void updateUserSession(FirebaseUser user) {
        String userId = user.getUid();
        String newSessionId = UUID.randomUUID().toString();
        Map<String, Object> updates = new HashMap<>();
        updates.put("currentSessionId", newSessionId);
        updates.put("lastLoginTimestamp", System.currentTimeMillis());

        if (getActivity() != null) {
            SharedPreferences prefs = getActivity().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
            prefs.edit().putString("currentSessionId", newSessionId).apply();
        }

        db.collection("users").document(userId)
                .update(updates)
                .addOnCompleteListener(task -> checkUserTypeAndRedirect(user));
    }

    private void checkUserTypeAndRedirect(FirebaseUser user) {
        String userId = user.getUid();
        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    showLoading(false);
                    if (documentSnapshot.exists()) {
                        String userType = documentSnapshot.getString("userType");
                        if (userType == null) {
                            redirectToHomepage(user, true);
                            return;
                        }

                        Intent intent = null;

                        switch (userType.toLowerCase()) {
                            case "service provider":
                            case "driver":
                                intent = new Intent(getActivity(), ServiceProviderHomepage.class);
                                break;

                            case "customer":
                            case "user":
                            default:
                                redirectToHomepage(user, false);
                                return;
                        }

                        if (intent != null) {
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            if (getActivity() != null) {
                                getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                                getActivity().finish();
                            }
                        }
                    } else {
                        redirectToHomepage(user, false);
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    redirectToHomepage(user, false);
                });
    }

    private void redirectToHomepage(FirebaseUser user, boolean showLoginToast) {
        if (getActivity() == null) return;
        boolean isEmailVerified = user.isEmailVerified();
        boolean isPhoneVerified = user.getPhoneNumber() != null && !user.getPhoneNumber().isEmpty();

        if (isEmailVerified || isPhoneVerified) {
            if (showLoginToast) {
                Toast.makeText(getActivity(), "Login successful!", Toast.LENGTH_SHORT).show();
            }
            Intent intent = new Intent(getActivity(), homepage.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            getActivity().finish();
        } else {
            if (showLoginToast) {
                Toast.makeText(getActivity(), "Please verify your email or phone", Toast.LENGTH_LONG).show();
            }
            Intent intent = new Intent(getActivity(), NonVerifiedHomepage.class);
            intent.putExtra("email", user.getEmail());
            startActivity(intent);
        }
    }

    private boolean validateInputs(String email, String password) {
        if (email.isEmpty()) {
            emailEditText.setError("Email is required");
            emailEditText.requestFocus();
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Please enter a valid email");
            emailEditText.requestFocus();
            return false;
        }
        if (password.isEmpty()) {
            passwordEditText.setError("Password is required");
            passwordEditText.requestFocus();
            return false;
        }
        if (password.length() < 6) {
            passwordEditText.setError("Password must be at least 6 characters");
            passwordEditText.requestFocus();
            return false;
        }
        return true;
    }

    private void setupInputListeners() {
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                validateInputsForButton();
            }
        };
        emailEditText.addTextChangedListener(textWatcher);
        passwordEditText.addTextChangedListener(textWatcher);
    }

    private void validateInputsForButton() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        boolean isValid = !email.isEmpty() &&
                Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
                password.length() >= 6;
        loginButton.setEnabled(isValid);
    }
}