package fourthyear.roadrescue;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap MyMap;
    private FusedLocationProviderClient fusedLocationClient;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        //Navigation Buttons
        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        notificationButton.setOnClickListener(v -> {
            Intent intent = new Intent(MapActivity.this, NotificationsActivity.class);
            startActivity(intent);
        });

        ImageView profileButton = findViewById(R.id.profile_icon_btn);
        //profileButton.setOnClickListener(v -> {
        //    Intent intent = new Intent(homepage.this, ProfileActivity.class);
        //    startActivity(intent);
        //});

        ImageView homeButton = findViewById(R.id.home_icon_btn);
        homeButton.setOnClickListener(v -> {
            Intent intent = new Intent(MapActivity.this, homepage.class);
            startActivity(intent);
        });

        ImageView messageButton = findViewById(R.id.message_icon_btn);
        messageButton.setOnClickListener(v -> {
            Intent intent = new Intent(MapActivity.this, ChatInboxActivity.class);
            startActivity(intent);
        });
        //End of Navigation Buttons

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Step 1: Initialize the permission launcher
        initializePermissionLauncher();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        MyMap = googleMap;

        // Step 3: Call the method to check or request permission
        enableMyLocation();
    }

    // ------------------- LOCATION LOGIC -------------------

    private void initializePermissionLauncher() {
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        // Permission granted, proceed to enable location
                        if (MyMap != null) {
                            try {
                                MyMap.setMyLocationEnabled(true);
                            } catch (SecurityException e) {
                                Log.e("MapActivity", "Location permission missing after grant check.");
                            }
                        }
                        getDeviceLocation();
                    } else {
                        // Permission denied, show a message to the user
                        Toast.makeText(this, "Location permission is required to find your position.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {

            // Permissions are already granted, enable map feature and get location
            try {
                MyMap.setMyLocationEnabled(true);
            } catch (SecurityException e) {
                Log.e("MapActivity", "SecurityException on setMyLocationEnabled: " + e.getMessage());
            }
            getDeviceLocation();
        } else {
            // Permissions are NOT granted, launch the standard Android popup
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void getDeviceLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // This is a failsafe check; permission should already be granted here.
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, new OnSuccessListener<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location != null) {
                    // Move the map camera to the user's current location
                    LatLng userLocation = new LatLng(location.getLatitude(), location.getLongitude());
                    MyMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 15f));

                    // You can remove or modify the custom marker if you only want the blue dot
                    MyMap.addMarker(new MarkerOptions().position(userLocation).title("My Location"));
                } else {
                    Log.e("MapActivity", "Location is null: Device might be slow or location services are off.");
                    Toast.makeText(MapActivity.this, "Could not get current location.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}