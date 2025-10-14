package fourthyear.roadrescue;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class homepage extends AppCompatActivity {

    private RecyclerView recentRecyclerView;
    private RecentAdapter recentAdapter;
    private List<RecentItem> recentItems;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_homepage); // Make sure this matches your layout file name

        initializeRecentItems();
        setupRecyclerView();
    }

    private void initializeRecentItems() {
        recentItems = new ArrayList<>();

        // Add sample data - replace with your actual data
        recentItems.add(new RecentItem("Home of BP", "Tanauan City, Leyte"));
        recentItems.add(new RecentItem("Medical Center", "Palo, Leyte"));
        recentItems.add(new RecentItem("Fire Station", "Tacloban City"));
        recentItems.add(new RecentItem("Police Station", "Dulag, Leyte"));
        recentItems.add(new RecentItem("Emergency Shelter", "Basey, Samar"));

        // You can add more items here or load from database
    }

    private void setupRecyclerView() {
        recentRecyclerView = findViewById(R.id.recentRecyclerView);
        recentAdapter = new RecentAdapter(recentItems);

        // Use LinearLayoutManager for vertical scrolling
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recentRecyclerView.setLayoutManager(layoutManager);

        // Set the adapter
        recentRecyclerView.setAdapter(recentAdapter);
    }
}