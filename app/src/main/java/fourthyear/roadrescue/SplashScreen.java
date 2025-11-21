package fourthyear.roadrescue;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class SplashScreen extends AppCompatActivity {

    private static final String TAG = "SplashScreen";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        setupFirebaseAppCheck();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser != null && currentUser.isEmailVerified()) {
            performAutoLogin();
        } else {
            setupButtonListeners();
        }
    }

    private void performAutoLogin() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        db.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String userType = documentSnapshot.getString("userType");
                        if (userType == null) {
                            setupButtonListeners(); // Fallback
                            return;
                        }

                        Intent intent;
                        String typeLower = userType.toLowerCase();

                        if (typeLower.contains("driver") || typeLower.contains("provider")) {
                            intent = new Intent(SplashScreen.this, ServiceProviderHomepage.class);
                        } else {
                            intent = new Intent(SplashScreen.this, homepage.class);
                        }

                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                        finish();
                    } else {
                        setupButtonListeners();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Auto-login error: " + e.getMessage());
                    setupButtonListeners();
                });
    }
    private void setupFirebaseAppCheck() {
        try {
            FirebaseAppCheck appCheck = FirebaseAppCheck.getInstance();
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance());
            verifyAppCheckAuthorization();
        } catch (Exception e) {
            Log.e(TAG, "App Check Error", e);
        }
    }

    private void verifyAppCheckAuthorization() {
        FirebaseAppCheck.getInstance().getAppCheckToken(false)
                .addOnSuccessListener(tokenResult -> Log.d(TAG, "App Check Token: Success"))
                .addOnFailureListener(e -> Log.e(TAG, "App Check Token: Failed"));
    }

    private void setupButtonListeners() {
        Button getStartedButton = findViewById(R.id.get_started_button);
        Button logInButton = findViewById(R.id.splash_button_login);

        if(getStartedButton != null) {
            getStartedButton.setEnabled(true);
            getStartedButton.setOnClickListener(v -> navigateToMainActivity(1));
        }

        if(logInButton != null) {
            logInButton.setEnabled(true);
            logInButton.setOnClickListener(v -> navigateToMainActivity(0));
        }
    }

    private void navigateToMainActivity(int fragmentIndex) {
        Intent intent = new Intent(SplashScreen.this, MainActivity.class);
        intent.putExtra("LOAD_FRAGMENT_INDEX", fragmentIndex);
        startActivity(intent);
        finish();
    }
}