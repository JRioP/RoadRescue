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
import android.widget.CheckBox; // Import CheckBox
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Login extends Fragment {

    private Button loginButton;
    private EditText emailEditText, passwordEditText;
    private CheckBox rememberMeCheckBox; // 1. Add CheckBox reference

    private long lastAttemptTime = 0;
    private static final long MIN_TIME_BETWEEN_ATTEMPTS = 2000;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private View loadingOverlay;
    private Handler handler = new Handler(Looper.getMainLooper());

    // Shared Preferences constants
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "LoginPrefs";
    private static final String KEY_REMEMBER_ME = "rememberMe";
    private static final String KEY_EMAIL = "email";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Initialize Shared Preferences
        if (getActivity() != null) {
            sharedPreferences = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        // 2. Check if user is already logged in (Auto-Login)
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && currentUser.isEmailVerified()) {
            // User is already signed in and verified, redirect immediately
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

        // Make sure your XML has a CheckBox with this ID
        rememberMeCheckBox = view.findViewById(R.id.checkBox);

        // 3. Load saved Email if "Remember Me" was previously checked
        if (sharedPreferences != null) {
            boolean isRemembered = sharedPreferences.getBoolean(KEY_REMEMBER_ME, false);
            if (isRemembered) {
                String savedEmail = sharedPreferences.getString(KEY_EMAIL, "");
                emailEditText.setText(savedEmail);
                rememberMeCheckBox.setChecked(true);
            }
        }

        setupInputListeners();

        forgotPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), ForgotPassword.class);
                startActivity(intent);
            }
        });

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                authenticateUser();
            }
        });
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

    private void authenticateUser() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAttemptTime < MIN_TIME_BETWEEN_ATTEMPTS) {
            Toast.makeText(getActivity(), "Please wait before trying again", Toast.LENGTH_SHORT).show();
            return;
        }
        lastAttemptTime = currentTime;

        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (!validateInputs(email, password)) return;

        showLoading(true);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(getActivity(), new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        // Use handler only for the loading delay, logic runs immediately inside
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (task.isSuccessful()) {
                                    // 4. Handle "Remember Me" Logic on Success
                                    handleRememberMe(email);

                                    FirebaseUser user = mAuth.getCurrentUser();
                                    if (user != null) {
                                        if (user.isEmailVerified()) {
                                            updateUserSession(user);
                                        } else {
                                            showLoading(false);
                                            redirectToHomepage(user, true);
                                        }
                                    }
                                } else {
                                    showLoading(false);
                                    handleLoginError(task.getException());
                                }
                            }
                        }, 1000); // Reduced delay for better UX
                    }
                });
    }

    // 5. Helper method to save/clear preferences
    private void handleRememberMe(String email) {
        if (sharedPreferences == null) return;

        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (rememberMeCheckBox.isChecked()) {
            editor.putBoolean(KEY_REMEMBER_ME, true);
            editor.putString(KEY_EMAIL, email);
        } else {
            editor.clear(); // Clear stored data if unchecked
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
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        // Loading is still showing here, which is good
                        checkUserTypeAndRedirect(user);
                    }
                });
    }

    private void checkUserTypeAndRedirect(FirebaseUser user) {
        String userId = user.getUid();
        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    showLoading(false); // Stop loading here
                    if (documentSnapshot.exists()) {
                        String userType = documentSnapshot.getString("userType");
                        if (userType == null) {
                            redirectToHomepage(user, true);
                            return;
                        }
                        Intent intent;
                        switch (userType.toLowerCase()) {
                            case "service provider": // Updated based on common naming
                            case "driver":
                                intent = new Intent(getActivity(), ServiceProviderHomepage.class);
                                break;
                            case "customer": // Updated based on common naming
                            case "user":
                            default:
                                intent = new Intent(getActivity(), homepage.class);
                                break;
                        }
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        if (getActivity() != null) {
                            getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                            getActivity().finish();
                        }
                    } else {
                        redirectToHomepage(user, true);
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    redirectToHomepage(user, true);
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

    private void handleLoginError(Exception exception) {
        String errorMessage;
        if (exception instanceof FirebaseAuthInvalidUserException) {
            errorMessage = "Account not found";
        } else if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            errorMessage = "Invalid password";
        } else if (exception instanceof FirebaseAuthRecentLoginRequiredException) {
            errorMessage = "Session expired. Please login again.";
        } else {
            errorMessage = "Authentication failed. Check connection.";
        }
        if (getActivity() != null) {
            Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_LONG).show();
        }
        if (exception != null) {
            Log.e("LoginSecurity", "Auth error: " + exception.getMessage());
        }
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