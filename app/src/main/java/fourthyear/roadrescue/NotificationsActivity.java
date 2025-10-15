package fourthyear.roadrescue;

import android.app.Notification;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity {

    private List<Object> notificationsList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification);

        // Back button
        ImageView backButton = findViewById(R.id.back_btn);
        backButton.setOnClickListener(v -> finish());

        initializeNotifications();
        setupRecyclerView();
    }

    private void initializeNotifications() {
        notificationsList = new ArrayList<>();

        // Get current date
        Calendar calendar = Calendar.getInstance();

        // Today's notifications
        notificationsList.add("Today");
        notificationsList.add(new NotificationModel("1", "Road Assistance", "Your road rescue request has been accepted", calendar.getTime()));
        notificationsList.add(new NotificationModel("2", "Service Update", "Mechanic is on the way to your location", calendar.getTime()));

        // Yesterday's notifications
        calendar.add(Calendar.DAY_OF_YEAR, -1);
        notificationsList.add("Yesterday");
        notificationsList.add(new NotificationModel("3", "Payment Confirmed", "Your payment has been processed successfully", calendar.getTime()));
        notificationsList.add(new NotificationModel("4", "Service Completed", "Your vehicle service has been completed", calendar.getTime()));
        notificationsList.add(new NotificationModel("5", "Rating Reminder", "Please rate your recent service", calendar.getTime()));
        notificationsList.add(new NotificationModel("6", "Promotion", "Special discount on your next service", calendar.getTime()));
    }

    private void setupRecyclerView() {
        RecyclerView notificationsRecyclerView = findViewById(R.id.notificationsRecyclerView);
        NotificationsAdapter notificationsAdapter = new NotificationsAdapter(notificationsList);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        notificationsRecyclerView.setLayoutManager(layoutManager);

        notificationsRecyclerView.setAdapter(notificationsAdapter);
    }
}