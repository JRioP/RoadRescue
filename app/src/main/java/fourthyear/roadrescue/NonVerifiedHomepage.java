package fourthyear.roadrescue;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class NonVerifiedHomepage extends AppCompatActivity {

    private TextView emailTextView;
    private Button resendVerificationButton, logoutButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_non_verified_homepage);

        emailTextView = findViewById(R.id.emailTextView);
        resendVerificationButton = findViewById(R.id.resendVerificationButton);
        logoutButton = findViewById(R.id.logoutButton);

        // Get user email from intent or Firebase
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            emailTextView.setText("Please verify your email: \n" + user.getEmail());
        }

        resendVerificationButton.setOnClickListener(v -> resendVerificationEmail());
        logoutButton.setOnClickListener(v -> logoutUser());
    }

    private void resendVerificationEmail() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            user.sendEmailVerification()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, "Verification email sent", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Failed to send verification email", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void logoutUser() {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, MainActivity.class); // Your main login activity
        startActivity(intent);
        finish();
    }
}