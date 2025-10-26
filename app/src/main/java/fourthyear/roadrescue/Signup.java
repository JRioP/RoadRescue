package fourthyear.roadrescue;

// ... (existing imports) ...

import static android.content.ContentValues.TAG;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
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

import java.util.regex.Pattern;

public class Signup extends Fragment {

    public static final String TAG = "SignupSecurity";

    private TextInputLayout passwordInputLayout, retypePasswordInputLayout;

    private EditText personUsername, personEmail, personPassword, personRPassword, phoneCountryCode, phoneNumber;
    private Button signupBtn;
    private FirebaseAuth fAuth;

    private long lastSignupAttempt = 0;
    private static final long MIN_TIME_BETWEEN_SIGNUPS = 5000; // 5 seconds

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.activity_sign_up, container, false);
        initializeViews(v);

        TextView termsAndConditionsBtn = v.findViewById(R.id.termsandconditions_btn);
        termsAndConditionsBtn.setOnClickListener(view -> {
            Intent intent = new Intent(getActivity(), NotificationsActivity.class);
            startActivity(intent);
        });

        fAuth = FirebaseAuth.getInstance();

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

        personPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { /* Not used */ }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                passwordInputLayout.setError(null);
            }
            @Override
            public void afterTextChanged(Editable s) { /* Not used */ }
        });

        personRPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { /* Not used */ }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                retypePasswordInputLayout.setError(null);
            }
            @Override
            public void afterTextChanged(Editable s) { /* Not used */ }
        });

        personUsername.addTextChangedListener(new SimpleTextWatcher(personUsername));
        personEmail.addTextChangedListener(new SimpleTextWatcher(personEmail));
        phoneNumber.addTextChangedListener(new SimpleTextWatcher(phoneNumber));
        phoneCountryCode.addTextChangedListener(new SimpleTextWatcher(phoneCountryCode));
    }

    // Helper class to quickly clear standard EditText errors
    private static class SimpleTextWatcher implements TextWatcher {
        private final EditText editText;
        SimpleTextWatcher(EditText editText) {
            this.editText = editText;
        }
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) { /* Not used */ }
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            editText.setError(null);
        }
        @Override
        public void afterTextChanged(Editable s) { /* Not used */ }
    }

    private void attemptSignup() {
        if (System.currentTimeMillis() - lastSignupAttempt < MIN_TIME_BETWEEN_SIGNUPS) {
            showToast("Please wait before trying again");
            return;
        }
        lastSignupAttempt = System.currentTimeMillis();

        passwordInputLayout.setError(null);
        retypePasswordInputLayout.setError(null);
        personEmail.setError(null);

        if (validateAllFields()) {
            String email = personEmail.getText().toString().trim();
            String password = personPassword.getText().toString().trim();
            String phone = "+" + phoneCountryCode.getText().toString() + phoneNumber.getText().toString();

            signupBtn.setEnabled(false);
            signupBtn.setText("Checking Email...");

            // Start the check for existing email
            checkIfEmailExists(email, password, phone);
        }
    }

    // MODIFIED: Method to check if the email is already registered and show red box/error
    private void checkIfEmailExists(String email, String password, String phone) {
        fAuth.fetchSignInMethodsForEmail(email)
                .addOnCompleteListener(task -> {
                    signupBtn.setText("Creating Account...");
                    if (task.isSuccessful()) {
                        boolean isNewUser = task.getResult().getSignInMethods().isEmpty();

                        if (!isNewUser) {
                            // FIX: Set error on the personEmail EditText, making the box glow red
                            personEmail.setError("An account with this email already exists.");

                            showToast("An account with this email already exists.");
                            signupBtn.setEnabled(true);
                            signupBtn.setText("Sign Up");
                        } else {
                            // Email is new, proceed with user creation
                            createFirebaseUser(email, password, phone);
                        }
                    } else {
                        // Error during the check (e.g., network error, invalid email format passed)
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

        return isValid;
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

    private void createFirebaseUser(String email, String password, String phone) {
        fAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(new OnSuccessListener<AuthResult>() {
                    @Override
                    public void onSuccess(AuthResult authResult) {
                        handleSignupSuccess(email, phone);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        handleSignupFailure(e);
                    }
                });
    }

    private void handleSignupSuccess(String email, String phone) {
        FirebaseUser newUser = fAuth.getCurrentUser();
        if (newUser != null) {
            sendEmailVerification(newUser, email, phone);
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

                        // Sign out until email is verified
                        fAuth.signOut();
                        redirectToPhoneVerification(phone, email);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.e(TAG, "Email verification send failed", e);
                        showToast("Account created but verification email failed. Please verify later.");
                        fAuth.signOut();
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

        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    private void redirectToLogin() {
        Intent loginIntent = new Intent(getActivity(), Login.class);
        startActivity(loginIntent);
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    private void showToast(String message) {
        if (getActivity() != null) {
            Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
        }
    }
}