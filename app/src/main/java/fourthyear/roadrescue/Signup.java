package fourthyear.roadrescue;

import static android.content.ContentValues.TAG;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
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
import android.widget.Toast;
import android.text.TextWatcher;
import android.text.Editable;

import com.google.android.material.textfield.TextInputLayout;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.AuthResult;
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
    private FirebaseAuth fAuth;
    private FirebaseFirestore db;

    // --- SECURITY: Brute-force protection variables ---
    private int signupAttemptCounter = 0;
    private long lastAttemptTimestamp = 0;
    private static final long BASE_DELAY = 5000; // Start with 5 seconds

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

        setupTermsAndConditionsClickableText();

        personPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                passwordInputLayout.setError(null);
            }
            @Override
            public void afterTextChanged(Editable s) { }
        });

        personRPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                retypePasswordInputLayout.setError(null);
            }
            @Override
            public void afterTextChanged(Editable s) { }
        });

        personUsername.addTextChangedListener(new SimpleTextWatcher(personUsername));
        personEmail.addTextChangedListener(new SimpleTextWatcher(personEmail));
        phoneNumber.addTextChangedListener(new SimpleTextWatcher(phoneNumber));
        phoneCountryCode.addTextChangedListener(new SimpleTextWatcher(phoneCountryCode));
    }

    private void setupTermsAndConditionsClickableText() {
        String fullText = getString(R.string.sign_up_termsandcondition);
        String clickableText = "Terms and Conditions";

        SpannableString ss = new SpannableString(fullText);

        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                Intent intent = new Intent(getActivity(), TermsAndConditionsActivity.class);
                startActivity(intent);
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(true);
            }
        };

        int startIndex = fullText.indexOf(clickableText);
        int endIndex = startIndex + clickableText.length();

        if (startIndex != -1) {
            ss.setSpan(clickableSpan, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            termsAndConditionsCheck.setText(ss);
            termsAndConditionsCheck.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            termsAndConditionsCheck.setText(fullText);
            Log.w(TAG, "Could not find clickable text in terms and conditions string.");
        }
    }

    private static class SimpleTextWatcher implements TextWatcher {
        private final EditText editText;
        SimpleTextWatcher(EditText editText) {
            this.editText = editText;
        }
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            editText.setError(null);
        }
        @Override
        public void afterTextChanged(Editable s) { }
    }

    /**
     * Enhanced security method with Exponential Backoff to prevent registration flooding.
     */
    private void attemptSignup() {
        long currentTime = System.currentTimeMillis();

        // 1. Calculate required delay based on previous attempts
        // Logic: 5s * 2^n. (5s, 10s, 20s, 40s, 80s, capped at 160s)
        long requiredDelay = 0;
        if (signupAttemptCounter > 0) {
            // We cap the power at 5 to prevent the wait time from becoming hours long too quickly
            requiredDelay = BASE_DELAY * (long) Math.pow(2, Math.min(signupAttemptCounter - 1, 5));
        }

        // 2. Check if the user is currently blocked
        if (currentTime - lastAttemptTimestamp < requiredDelay) {
            long timeLeftSeconds = (requiredDelay - (currentTime - lastAttemptTimestamp)) / 1000;
            showToast("Too many attempts. Please wait " + timeLeftSeconds + " seconds.");
            return;
        }

        // 3. Update counters immediately to count this attempt
        lastAttemptTimestamp = currentTime;
        signupAttemptCounter++;

        // 4. Reset UI errors
        passwordInputLayout.setError(null);
        retypePasswordInputLayout.setError(null);
        personEmail.setError(null);

        // 5. Validate and Proceed
        if (validateAllFields()) {
            String email = personEmail.getText().toString().trim();
            String password = personPassword.getText().toString().trim();
            String phone = "+" + phoneCountryCode.getText().toString() + phoneNumber.getText().toString();
            String username = personUsername.getText().toString().trim();

            signupBtn.setEnabled(false);
            signupBtn.setText("Checking Email...");

            checkIfEmailExists(email, password, phone, username);
        }
    }

    private void checkIfEmailExists(String email, String password, String phone, String username) {
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
                            createFirebaseUser(email, password, phone, username);
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

    private void createFirebaseUser(String email, String password, String phone, String username) {
        fAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(new OnSuccessListener<AuthResult>() {
                    @Override
                    public void onSuccess(AuthResult authResult) {
                        handleSignupSuccess(email, phone, username);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        handleSignupFailure(e);
                    }
                });
    }

    private void handleSignupSuccess(String email, String phone, String username) {
        FirebaseUser newUser = fAuth.getCurrentUser();
        if (newUser != null) {
            String userId = newUser.getUid();

            Map<String, Object> user = new HashMap<>();
            user.put("userId", userId);
            user.put("email", email);
            user.put("name", username);
            user.put("userType", "user");
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
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        Log.i(TAG, "Verification email sent to: " + email);
                        showToast("Verification email sent. Please verify your email before logging in.");
                        redirectToPhoneVerification(phone, email);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "Email verification send failed", e);
                        showToast("Account created but verification email failed. Please verify later.");
                        redirectToLogin();
                    }
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