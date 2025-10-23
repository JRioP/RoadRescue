package fourthyear.roadrescue;

import android.os.Bundle;
import android.content.Intent;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;

import java.util.ArrayList;
import java.util.List;

public class homepage extends AppCompatActivity {

    private List<RecentItemModel> recentItemModels;
    private GoogleMap myMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_homepage); // Make sure this matches your layout file name

        //Navigation Buttons
        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        notificationButton.setOnClickListener(v -> {
            Intent intent = new Intent(homepage.this, NotificationsActivity.class);
            startActivity(intent);
        });

        ImageView profileButton = findViewById(R.id.profile_icon_btn);
        //profileButton.setOnClickListener(v -> {
        //    Intent intent = new Intent(homepage.this, ProfileActivity.class);
        //    startActivity(intent);
        //});

        ImageView homeButton = findViewById(R.id.home_icon_btn);
        homeButton.setOnClickListener(v -> {
            Intent intent = new Intent(homepage.this, homepage.class);
            startActivity(intent);
        });

        ImageView messageButton = findViewById(R.id.message_icon_btn);
        messageButton.setOnClickListener(v -> {
            Intent intent = new Intent(homepage.this, ChatInboxActivity.class);
            startActivity(intent);
        });
        //End of Navigation Buttons

        ConstraintLayout towingButton = findViewById(R.id.towing_btn);
        towingButton.setOnClickListener(v -> {
            Intent intent = new Intent(homepage.this, MapActivity.class);
            startActivity(intent);
        });

        ConstraintLayout jumpStartButton = findViewById(R.id.jump_start_btn);
        jumpStartButton.setOnClickListener(v -> {
            Intent intent = new Intent(homepage.this, ProviderMapActivity.class);
            startActivity(intent);
        });




        initializeRecentItems();
        setupRecyclerView();



    }

    private void initializeRecentItems() {
        recentItemModels = new ArrayList<>();

        // Add sample data - replace with your actual data
        recentItemModels.add(new RecentItemModel("Home of BP", "Tanauan City, Leyte"));
        recentItemModels.add(new RecentItemModel("Medical Center", "Palo, Leyte"));
        recentItemModels.add(new RecentItemModel("Fire Station", "Tacloban City"));
        recentItemModels.add(new RecentItemModel("Police Station", "Dulag, Leyte"));
        recentItemModels.add(new RecentItemModel("Emergency Shelter", "Basey, Samar"));

        // You can add more items here or load from database
    }

    private void setupRecyclerView() {
        RecyclerView recentRecyclerView = findViewById(R.id.recentRecyclerView);
        RecentAdapter recentAdapter = new RecentAdapter(recentItemModels);

        // Use LinearLayoutManager for vertical scrolling
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recentRecyclerView.setLayoutManager(layoutManager);

        recentRecyclerView.addItemDecoration(new ItemSpacingDecoration(16));

        // Set the adapter
        recentRecyclerView.setAdapter(recentAdapter);
    }
}