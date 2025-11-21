package fourthyear.roadrescue;

import static android.content.ContentValues.TAG;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.activity.result.ActivityResultLauncher; // Import this
import androidx.activity.result.contract.ActivityResultContracts; // Import this
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import android.text.TextWatcher;
import android.text.Editable;

import com.google.android.material.textfield.TextInputLayout;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class Signup extends Fragment {

    public static final String TAG = "SignupSecurity";

    private TextInputLayout passwordInputLayout, retypePasswordInputLayout;
    private EditText personUsername, personEmail, personPassword, personRPassword, phoneCountryCode, phoneNumber;
    private Button signupBtn;
    private CheckBox termsAndConditionsCheck;
    private RadioGroup roleRadioGroup;
    private RadioButton radioCustomer, radioProvider;
    private FirebaseAuth fAuth;
    private FirebaseFirestore db;
    private int signupAttemptCounter = 0;
    private long lastAttemptTimestamp = 0;
    private static final long BASE_DELAY = 5000;

    // --- 1. CREATE THE LAUNCHER ---
    // This waits for the TermsActivity to say "RESULT_OK"
    private final ActivityResultLauncher<Intent> termsLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    // Only NOW do we check the box
                    if (termsAndConditionsCheck != null) {
                        termsAndConditionsCheck.setChecked(true);
                        termsAndConditionsCheck.setError(null);
                    }
                }
            }
    );

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.activity_sign_up, container, false);
        initializeViews(v);

        fAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        signupBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptSignup();
            }
        });

        return v;
    }

    private void initializeViews(View v) {
        personUsername = v.findViewById(R.id.signup_username);
        personEmail = v.findViewById(R.id.signup_email);

        passwordInputLayout = v.findViewById(R.id.password_input_layout);
        retypePasswordInputLayout = v.findViewById(R.id.retype_password_input_layout);

        personPassword = v.findViewById(R.id.signup_password);
        personRPassword = v.findViewById(R.id.signup_password_retype);

        phoneCountryCode = v.findViewById(R.id.signup_phone_number_country_code);
        phoneNumber = v.findViewById(R.id.signup_phone_number);
        signupBtn = v.findViewById(R.id.btn_signup);

        termsAndConditionsCheck = v.findViewById(R.id.check_terms_and_conditions);

        roleRadioGroup = v.findViewById(R.id.role_radio_group);
        radioCustomer = v.findViewById(R.id.radio_customer);
        radioProvider = v.findViewById(R.id.radio_provider);

        setupTermsAndConditionsClickableText();

        // --- 2. INTERCEPT CHECKBOX CLICKS ---
        // This prevents manual checking. If they click the box, we force it off
        // and open the activity instead.
        termsAndConditionsCheck.setOnClickListener(view -> {
            if (termsAndConditionsCheck.isChecked()) {
                // If they tried to check it manually, uncheck it and open terms
                termsAndConditionsCheck.setChecked(false);
                openTermsActivity();
            } else {
                // If they are unchecking it (opting out), allow it.
                termsAndConditionsCheck.setChecked(false);
            }
        });

        // Listeners
        personPassword.addTextChangedListener(new SimpleTextWatcher(personPassword) {
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                passwordInputLayout.setError(null);
            }
        });
        personRPassword.addTextChangedListener(new SimpleTextWatcher(personRPassword) {
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                retypePasswordInputLayout.setError(null);
            }
        });
        personUsername.addTextChangedListener(new SimpleTextWatcher(personUsername));
        personEmail.addTextChangedListener(new SimpleTextWatcher(personEmail));
        phoneNumber.addTextChangedListener(new SimpleTextWatcher(phoneNumber));
        phoneCountryCode.addTextChangedListener(new SimpleTextWatcher(phoneCountryCode));
    }

    // Helper to open the activity
    private void openTermsActivity() {
        Intent intent = new Intent(getActivity(), TermsAndConditionsActivity.class);
        termsLauncher.launch(intent);
    }

    private void setupTermsAndConditionsClickableText() {
        String fullText = getString(R.string.sign_up_termsandcondition);

        // Use the Tagalog text if that's what is in your XML, otherwise use English
        // If this doesn't match exactly, the fallback below will link the whole sentence.
        String clickableText = "Mga Tuntunin at Kundisyon";

        SpannableString ss = new SpannableString(fullText);

        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                // Stop the check event from firing twice
                widget.cancelPendingInputEvents();
                openTermsActivity();
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(true);
                ds.setColor(getResources().getColor(R.color.blue));
            }
        };

        // --- 3. ROBUST TEXT FINDING ---
        // If we can't find the specific words, make the WHOLE text clickable
        int startIndex = fullText.indexOf(clickableText);
        if (startIndex != -1) {
            int endIndex = startIndex + clickableText.length();
            ss.setSpan(clickableSpan, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            // Fallback: Link the entire string
            ss.setSpan(clickableSpan, 0, fullText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        termsAndConditionsCheck.setText(ss);
        termsAndConditionsCheck.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private static class SimpleTextWatcher implements TextWatcher {
        private final EditText editText;
        SimpleTextWatcher(EditText editText) { this.editText = editText; }
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { editText.setError(null); }
        @Override public void afterTextChanged(Editable s) { }
    }

    private void attemptSignup() {
        // ... (Your existing attemptSignup logic - unchanged) ...
        long currentTime = System.currentTimeMillis();
        long requiredDelay = 0;
        if (signupAttemptCounter > 0) {
            requiredDelay = BASE_DELAY * (long) Math.pow(2, Math.min(signupAttemptCounter - 1, 5));
        }

        if (currentTime - lastAttemptTimestamp < requiredDelay) {
            long timeLeftSeconds = (requiredDelay - (currentTime - lastAttemptTimestamp)) / 1000;
            showToast("Too many attempts. Please wait " + timeLeftSeconds + " seconds.");
            return;
        }

        lastAttemptTimestamp = currentTime;
        signupAttemptCounter++;

        passwordInputLayout.setError(null);
        retypePasswordInputLayout.setError(null);
        personEmail.setError(null);

        if (validateAllFields()) {
            String email = personEmail.getText().toString().trim();
            String password = personPassword.getText().toString().trim();
            String phone = "+" + phoneCountryCode.getText().toString() + phoneNumber.getText().toString();
            String username = personUsername.getText().toString().trim();

            String userType = "Customer"; // Default
            if (radioProvider.isChecked()) {
                userType = "Service Provider";
            }

            signupBtn.setEnabled(false);
            signupBtn.setText("Checking Email...");

            checkIfEmailExists(email, password, phone, username, userType);
        }
    }

    private void checkIfEmailExists(String email, String password, String phone, String username, String userType) {
        // ... (Your existing logic - unchanged) ...
        fAuth.fetchSignInMethodsForEmail(email)
                .addOnCompleteListener(task -> {
                    signupBtn.setText("Creating Account...");
                    if (task.isSuccessful()) {
                        boolean isNewUser = task.getResult().getSignInMethods().isEmpty();

                        if (!isNewUser) {
                            personEmail.setError("An account with this email already exists.");
                            showToast("An account with this email already exists.");
                            signupBtn.setEnabled(true);
                            signupBtn.setText("Sign Up");
                        } else {
                            createFirebaseUser(email, password, phone, username, userType);
                        }
                    } else {
                        Log.e(TAG, "Error checking email existence: " + task.getException());
                        showToast("Error checking email. Please try again.");
                        signupBtn.setEnabled(true);
                        signupBtn.setText("Sign Up");
                    }
                });
    }

    private boolean validateAllFields() {
        boolean isValid = true;
        if (!validateUsername()) isValid = false;
        if (!validateEmail()) isValid = false;
        if (!validatePassword()) isValid = false;
        if (!validatePasswordMatch()) isValid = false;
        if (!validatePhone()) isValid = false;
        if (!validateTerms()) isValid = false;
        return isValid;
    }

    private boolean validateTerms() {
        if (!termsAndConditionsCheck.isChecked()) {
            showToast("You must accept the Terms and Conditions");
            return false;
        }
        return true;
    }

    // ... (Keep all your other validation methods: validateUsername, validateEmail, etc.) ...

    private boolean validateUsername() {
        String username = personUsername.getText().toString().trim();
        if (username.isEmpty()) {
            personUsername.setError("Username is required");
            return false;
        }
        if (username.length() < 3) {
            personUsername.setError("Username must be at least 3 characters");
            return false;
        }
        if (username.length() > 20) {
            personUsername.setError("Username too long (max 20 characters)");
            return false;
        }
        if (!Pattern.matches("^[a-zA-Z0-9_]+$", username)) {
            personUsername.setError("Only letters, numbers and underscores allowed");
            return false;
        }
        return true;
    }

    private boolean validateEmail() {
        String email = personEmail.getText().toString().trim();
        if (email.isEmpty()) {
            personEmail.setError("Email is required");
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            personEmail.setError("Please send a valid email");
            return false;
        }
        return true;
    }

    private boolean validatePassword() {
        String password = personPassword.getText().toString().trim();
        passwordInputLayout.setError(null);
        if (password.isEmpty()) {
            passwordInputLayout.setError("Password is required");
            return false;
        }
        if (password.length() < 8) {
            passwordInputLayout.setError("Password must be at least 8 characters");
            return false;
        }
        Pattern passwordPattern = Pattern.compile("^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$");
        if (!passwordPattern.matcher(password).matches()) {
            passwordInputLayout.setError("Password must contain uppercase, lowercase, number and special character");
            return false;
        }
        return true;
    }

    private boolean validatePasswordMatch() {
        String password = personPassword.getText().toString().trim();
        String confirmPassword = personRPassword.getText().toString().trim();
        retypePasswordInputLayout.setError(null);
        if (!password.equals(confirmPassword)) {
            retypePasswordInputLayout.setError("Passwords do not match");
            return false;
        }
        return true;
    }

    private boolean validatePhone() {
        String countryCode = phoneCountryCode.getText().toString().trim();
        String number = phoneNumber.getText().toString().trim();
        if (countryCode.isEmpty() || number.isEmpty()) {
            phoneNumber.setError("Phone number is required");
            return false;
        }
        if (!Pattern.matches("^[0-9]+$", countryCode)) {
            phoneCountryCode.setError("Invalid country code");
            return false;
        }
        if (!Pattern.matches("^[0-9]{10,15}$", number)) {
            phoneNumber.setError("Invalid phone number format");
            return false;
        }
        return true;
    }

    private void createFirebaseUser(String email, String password, String phone, String username, String userType) {
        fAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> handleSignupSuccess(email, phone, username, userType))
                .addOnFailureListener(this::handleSignupFailure);
    }

    private void handleSignupSuccess(String email, String phone, String username, String userType) {
        FirebaseUser newUser = fAuth.getCurrentUser();
        if (newUser != null) {
            String userId = newUser.getUid();

            Map<String, Object> user = new HashMap<>();
            user.put("userId", userId);
            user.put("email", email);
            user.put("name", username);
            user.put("userType", userType);
            user.put("phone", phone);
            user.put("isOnline", false);
            user.put("lastSeen", FieldValue.serverTimestamp());

            db.collection("users").document(userId).set(user)
                    .addOnSuccessListener(aVoid -> {
                        Log.d(TAG, "User document created in Firestore.");
                        sendEmailVerification(newUser, email, phone);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error creating user document", e);
                        sendEmailVerification(newUser, email, phone);
                    });
        } else {
            showToast("Account created but user not logged in");
            redirectToLogin();
        }
    }

    private void sendEmailVerification(FirebaseUser user, String email, String phone) {
        user.sendEmailVerification()
                .addOnSuccessListener(unused -> {
                    Log.i(TAG, "Verification email sent to: " + email);
                    showToast("Verification email sent. Please verify your email before logging in.");
                    redirectToPhoneVerification(phone, email);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Email verification send failed", e);
                    showToast("Account created but verification email failed. Please verify later.");
                    redirectToLogin();
                });
    }

    private void handleSignupFailure(Exception e) {
        signupBtn.setEnabled(true);
        signupBtn.setText("Sign Up");
        String errorMessage = "Signup failed. Please try again.";
        if (e instanceof FirebaseAuthUserCollisionException) {
            errorMessage = "An account with this email already exists.";
        } else if (e instanceof FirebaseAuthWeakPasswordException) {
            errorMessage = "Password is too weak. Please use a stronger password.";
        } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
            errorMessage = "Invalid email format.";
        }
        showToast(errorMessage);
        Log.e(TAG, "Signup error: " + e.getMessage());
    }

    private void redirectToPhoneVerification(String phone, String email) {
        Intent phoneVerificationIntent = new Intent(getActivity(), VerifyPhone.class);
        phoneVerificationIntent.putExtra("phone", phone);
        phoneVerificationIntent.putExtra("email", email);
        startActivity(phoneVerificationIntent);
    }

    private void redirectToLogin() {
        Intent loginIntent = new Intent(getActivity(), MainActivity.class);
        loginIntent.putExtra("LOAD_FRAGMENT_INDEX", 0);
        startActivity(loginIntent);
    }

    private void showToast(String message) {
        if (getActivity() != null) {
            Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
        }
    }
}