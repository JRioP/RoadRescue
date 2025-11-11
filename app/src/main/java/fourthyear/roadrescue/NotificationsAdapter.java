package fourthyear.roadrescue;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class NotificationsAdapter extends RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder> {
    private List<NotificationModel> notificationsList;
    public NotificationsAdapter(List<NotificationModel> notificationsList) {
        this.notificationsList = notificationsList;
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 4. Inflate the single item layout
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        // 5. Get the correct object from the list
        NotificationModel notification = notificationsList.get(position);
        holder.bind(notification);
    }

    @Override
    public int getItemCount() {
        // 6. Return the size of the correct list
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

            Long timestamp = notification.getTimestamp().getSeconds();

            if (timestamp == null) {
                notificationTime.setText(""); // Or "Just now"
            } else {
                // Format time using SimpleDateFormat
                SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
                String time = timeFormat.format(timestamp);
                notificationTime.setText(time);
            }
        }
    }
}