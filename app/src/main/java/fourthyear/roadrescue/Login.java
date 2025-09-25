package fourthyear.roadrescue;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
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
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import android.util.Patterns;

public class Login extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.activity_login, container, false);

        TextView forgotPassword = view.findViewById(R.id.btn_forget_password);
        Button loginButton = view.findViewById(R.id.btn_login);

        forgotPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Handle forgot password
                Toast.makeText(getActivity(), "Forgot Password clicked", Toast.LENGTH_SHORT).show();
                // Add your forgot password logic here
                Intent intent = new Intent(getActivity(), ForgotPassword.class);
                startActivity(intent);
            }
        });

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Get email and password from EditText fields
                EditText emailEditText = getView().findViewById(R.id.login_email); // Replace with your actual ID
                EditText passwordEditText = getView().findViewById(R.id.login_password); // Replace with your actual ID

                String email = emailEditText.getText().toString().trim();
                String password = passwordEditText.getText().toString().trim();

                // Validate inputs
                if (email.isEmpty()) {
                    emailEditText.setError("Email is required");
                    emailEditText.requestFocus();
                    return;
                }

                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    emailEditText.setError("Please enter a valid email");
                    emailEditText.requestFocus();
                    return;
                }

                if (password.isEmpty()) {
                    passwordEditText.setError("Password is required");
                    passwordEditText.requestFocus();
                    return;
                }

                if (password.length() < 6) {
                    passwordEditText.setError("Password must be at least 6 characters");
                    passwordEditText.requestFocus();
                    return;
                }

                // Show loading state
                loginButton.setEnabled(false);
                loginButton.setText("Logging in...");

                // Authenticate with Firebase
                FirebaseAuth mAuth = FirebaseAuth.getInstance();
                mAuth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(getActivity(), new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                // Re-enable button
                                loginButton.setEnabled(true);
                                loginButton.setText("Login");

                                if (task.isSuccessful()) {
                                    // Login successful
                                    Toast.makeText(getActivity(), "Login successful!", Toast.LENGTH_SHORT).show();

                                    // Navigate to homepage
                                    Intent intent = new Intent(getActivity(), homepage.class);
                                    startActivity(intent);
                                    getActivity().finish(); // Optional: close login activity

                                } else {
                                    // Login failed - handle specific errors
                                    String errorMessage = "Login failed. Please try again.";

                                    if (task.getException() instanceof FirebaseAuthInvalidUserException) {
                                        errorMessage = "No account found with this email address.";
                                    } else if (task.getException() instanceof FirebaseAuthInvalidCredentialsException) {
                                        errorMessage = "Invalid password. Please try again.";
                                    } else if (task.getException() instanceof FirebaseAuthUserCollisionException) {
                                        errorMessage = "This account is already in use.";
                                    } else if (task.getException() != null) {
                                        errorMessage = task.getException().getMessage();
                                    }

                                    Toast.makeText(getActivity(), errorMessage, Toast.LENGTH_LONG).show();
                                }
                            }
                        });
            }
        });

        return view;
    }
}