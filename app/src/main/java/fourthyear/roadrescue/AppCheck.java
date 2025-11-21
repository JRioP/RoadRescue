package fourthyear.roadrescue;

import android.app.Application;
import android.util.Log;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;
// NEW IMPORTS FOR OFFLINE PERSISTENCE
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

public class AppCheck extends Application {
    private static final String TAG = "MyApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "MyApplication onCreate - Initializing Firebase and App Check...");

        FirebaseApp.initializeApp(this);

        // --- 1. CONFIGURE OFFLINE PERSISTENCE (THE FIX) ---
        // This is crucial for making the app work when the network drops.
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true) // 🔑 This line enables the local data cache
                .build();

        FirebaseFirestore.getInstance().setFirestoreSettings(settings);
        Log.d(TAG, "Firestore Offline Persistence Enabled.");
        // ----------------------------------------------------

        // --- 2. EXISTING APP CHECK LOGIC ---
        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();

        // Note: For production, you should use PlayIntegrityAppCheckProviderFactory
        // For debugging, the current setup using DebugAppCheckProviderFactory is correct.
        firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
        );

        Log.d(TAG, "App Check with Debug Provider installed.");
    }
}