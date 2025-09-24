package fourthyear.roadrescue;

import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;

public class ForgotPassword extends AppCompatActivity {

    private EditText emailEditText;
    private Button resetPasswordButton;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        emailEditText = findViewById(R.id.forgot_Email);
        resetPasswordButton = findViewById(R.id.forgot_btn);

        if (emailEditText == null || resetPasswordButton == null) {
            Toast.makeText(this, "Error initializing views", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mAuth = FirebaseAuth.getInstance();

        resetPasswordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetPassword();
            }
        });
    }

    private void resetPassword() {
        if (emailEditText == null) {
            Toast.makeText(this, "Email field not available", Toast.LENGTH_SHORT).show();
            return;
        }

        String email = emailEditText.getText().toString().trim();

        if (email.isEmpty()) {
            emailEditText.setError("Email is required");
            emailEditText.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEditText.setError("Please provide a valid email");
            emailEditText.requestFocus();
            return;
        }

        if (mAuth == null) {
            Toast.makeText(this, "Authentication error", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading state
        resetPasswordButton.setEnabled(false);
        resetPasswordButton.setText("Sending...");

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        try {
                            // Re-enable button regardless of outcome
                            resetPasswordButton.setEnabled(true);
                            resetPasswordButton.setText("Reset Password");

                            if (task.isSuccessful()) {
                                Toast.makeText(ForgotPassword.this,
                                        "Check your email to reset your password",
                                        Toast.LENGTH_LONG).show();

                                // Delay finish to allow Toast to display properly
                                resetPasswordButton.postDelayed(new Runnable() {
                                    public void run() {
                                        finish();
                                    }
                                }, 2000); // 2 second delay
                            } else {
                                String errorMessage = "Something went wrong. Try again.";
                                if (task.getException() != null) {
                                    errorMessage = task.getException().getMessage();
                                }
                                Toast.makeText(ForgotPassword.this,
                                        errorMessage,
                                        Toast.LENGTH_LONG).show();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.makeText(ForgotPassword.this, "Unexpected error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }
}