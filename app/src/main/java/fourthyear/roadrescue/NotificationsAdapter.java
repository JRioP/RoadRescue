package fourthyear.roadrescue;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp; // Make sure this is imported

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder> {

    // 1. Define the Interface for click events
    public interface OnNotificationClickListener {
        void onNotificationClick(NotificationModel notification);
    }

    private List<NotificationModel> notificationsList;
    private OnNotificationClickListener listener; // Field to hold the listener

    // 2. Update Constructor to accept the listener
    public NotificationsAdapter(List<NotificationModel> notificationsList, OnNotificationClickListener listener) {
        this.notificationsList = notificationsList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        NotificationModel notification = notificationsList.get(position);
        holder.bind(notification);

        // 3. Set the Click Listener on the item view
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onNotificationClick(notification);
            }
        });
    }

    @Override
    public int getItemCount() {
        return notificationsList.size();
    }

    /**
     * ViewHolder for the NotificationModel items.
     */
    static class NotificationViewHolder extends RecyclerView.ViewHolder {
        private final TextView notificationTitle;
        private final TextView notificationMessage;
        private final TextView notificationTime;

        NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            notificationTitle = itemView.findViewById(R.id.notificationTitle);
            notificationMessage = itemView.findViewById(R.id.notificationMessage);
            notificationTime = itemView.findViewById(R.id.notificationTime);
        }

        void bind(NotificationModel notification) {
            notificationTitle.setText(notification.getTitle());
            notificationMessage.setText(notification.getMessage());

            // 4. FIX: Timestamp handling
            // Assuming getTimestamp() returns a com.google.firebase.Timestamp
            Timestamp firestoreTimestamp = notification.getTimestamp();

            if (firestoreTimestamp == null) {
                notificationTime.setText("Just now");
            } else {
                // Convert Firebase Timestamp to Java Date
                Date date = firestoreTimestamp.toDate();

                // Format the Date
                SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                notificationTime.setText(timeFormat.format(date));
            }
        }
    }
}