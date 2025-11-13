package fourthyear.roadrescue;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;

public class SplashScreen extends AppCompatActivity {

    private static final String TAG = "SplashScreen";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);

        setupFirebaseAppCheck();
        setupButtonListeners();
    }

    private void setupFirebaseAppCheck() {
        try {
            FirebaseApp.initializeApp(this);
            FirebaseAppCheck appCheck = FirebaseAppCheck.getInstance();
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance());

            verifyAppCheckAuthorization();

        } catch (Exception e) {
            Log.e(TAG, "Firebase App Check setup failed", e);
            Toast.makeText(this, "Firebase setup failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void verifyAppCheckAuthorization() {
        FirebaseAppCheck.getInstance().getAppCheckToken(false)
                .addOnSuccessListener(tokenResult -> {
                    String token = tokenResult.getToken();
                    if (token != null && !token.isEmpty()) {
                        logAuthorizationSuccess(token);
                    } else {
                        logAuthorizationFailure("Token is null or empty");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Token request failed: " + e.getMessage());
                    Toast.makeText(this, "Network error", Toast.LENGTH_SHORT).show();
                });
    }

    private void logAuthorizationSuccess(String token) {
        Log.d(TAG, "Firebase App Check: AUTHORIZED");
        Log.d(TAG, "Debug Token: " + token);
        Toast.makeText(this, "Firebase: Authorized", Toast.LENGTH_SHORT).show();
    }

    private void logAuthorizationFailure(String errorMessage) {
        Log.e(TAG, "Firebase App Check: FAILED - " + errorMessage);
        Toast.makeText(this, "Firebase: Unauthorized", Toast.LENGTH_LONG).show();
    }

    private void setupButtonListeners() {
        Button getStartedButton = findViewById(R.id.get_started_button);
        Button logInButton = findViewById(R.id.splash_button_login);

        getStartedButton.setOnClickListener(v -> navigateToMainActivity(1));
        logInButton.setOnClickListener(v -> navigateToMainActivity(0));
    }

    private void navigateToMainActivity(int fragmentIndex) {
        Intent intent = new Intent(SplashScreen.this, MainActivity.class);
        intent.putExtra("LOAD_FRAGMENT_INDEX", fragmentIndex);
        startActivity(intent);
        finish();
    }
}