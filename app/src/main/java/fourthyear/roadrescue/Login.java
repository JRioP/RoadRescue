package fourthyear.roadrescue;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
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
import android.content.SharedPreferences;
import android.content.Context;

public class Login extends Fragment {

    private Button loginButton;
    private EditText emailEditText, passwordEditText;
    private long lastAttemptTime = 0;
    private static final long MIN_TIME_BETWEEN_ATTEMPTS = 2000;

    private FirebaseFirestore db;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_login, container, false);

        db = FirebaseFirestore.getInstance();

        TextView forgotPassword = view.findViewById(R.id.btn_forget_password);
        loginButton = view.findViewById(R.id.btn_login);
        emailEditText = view.findViewById(R.id.login_email);
        passwordEditText = view.findViewById(R.id.login_password);
        setupInputListeners();
        forgotPassword.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Toast.makeText(getActivity(), "Forgot Password clicked", Toast.LENGTH_SHORT).show();
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

        loginButton.setEnabled(false);

        loginButton.setText("Verifying...");

        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(getActivity(), new OnCompleteListener<AuthResult>() {

                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        loginButton.setEnabled(true);

                        loginButton.setText("Login");

                        if (task.isSuccessful()) {
                            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

                            if (user != null) {
                                if (user.isEmailVerified()) {

                                    updateUserSession(user.getUid());

                                    Toast.makeText(getActivity(), "Login successful!", Toast.LENGTH_SHORT).show();

                                    Intent intent = new Intent(getActivity(), homepage.class);

                                    startActivity(intent);

                                    if (getActivity() != null) {
                                        getActivity().finish();
                                    }

                                } else {
                                    Toast.makeText(getActivity(), "Please verify your email address", Toast.LENGTH_LONG).show();

                                    Intent intent = new Intent(getActivity(), NonVerifiedHomepage.class);

                                    intent.putExtra("email", user.getEmail());

                                    startActivity(intent);

                                    if (getActivity() != null) {
                                        getActivity().finish();
                                    }
                                }
                            }
                        } else {
                            handleLoginError(task.getException());
                        }
                    }

                });

    }

    private void updateUserSession(String userId) {
        String newSessionId = UUID.randomUUID().toString();

        Map<String, Object> updates = new HashMap<>();
        updates.put("currentSessionId", newSessionId);
        updates.put("lastLoginTimestamp", System.currentTimeMillis());

        db.collection("users").document(userId)
                .update(updates)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (task.isSuccessful()) {
                            Log.d("LoginSecurity", "New session ID recorded in Firestore for user: " + userId);

                            // FIX: Save the newSessionId locally to SharedPreferences
                            if (getActivity() != null) {
                                SharedPreferences prefs = getActivity().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
                                prefs.edit()
                                        .putString("currentSessionId", newSessionId)
                                        .apply();
                                Log.d("LoginSecurity", "Local session ID successfully saved to SharedPreferences.");
                            }

                        } else {
                            Log.e("LoginSecurity", "Error recording new session ID: " + task.getException());
                        }
                    }
                });
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
            errorMessage = "Invalid credentials";
        } else if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            errorMessage = "Invalid credentials";
        } else if (exception instanceof FirebaseAuthRecentLoginRequiredException) {
            errorMessage = "Please re-authenticate";
        } else {
            errorMessage = "Authentication failed. Try again.";
        }

        Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_LONG).show();

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