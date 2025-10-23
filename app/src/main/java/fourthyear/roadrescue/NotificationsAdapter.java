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
import java.util.Date; // Ensure java.util.Date is imported

/**
 * Adapter to display a list of notifications, supporting two view types:
 * 1. String headers (e.g., "Today", "Yesterday")
 * 2. NotificationModel items
 */
public class NotificationsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_NOTIFICATION = 1;

    // List to hold both String headers and NotificationModel objects
    private final List<Object> items;

    public NotificationsAdapter(List<Object> items) {
        this.items = items;
    }

    @Override
    public int getItemViewType(int position) {
        Object item = items.get(position);
        if (item instanceof String) {
            return TYPE_HEADER;
        } else if (item instanceof NotificationModel) {
            return TYPE_NOTIFICATION;
        }
        return -1; // Should not happen
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == TYPE_HEADER) {
            // Assumes R.layout.item_notification_header exists
            View view = inflater.inflate(R.layout.item_notification_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            // Assumes R.layout.item_notification exists
            View view = inflater.inflate(R.layout.item_notification, parent, false);
            return new NotificationViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = items.get(position);
        int viewType = holder.getItemViewType();

        if (viewType == TYPE_HEADER) {
            ((HeaderViewHolder) holder).bind((String) item);
        } else if (viewType == TYPE_NOTIFICATION) {
            // Cast to NotificationModel safely
            ((NotificationViewHolder) holder).bind((NotificationModel) item);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /**
     * ViewHolder for the header (String) items.
     */
    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView headerTitle;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            headerTitle = itemView.findViewById(R.id.headerTitle);
        }

        void bind(String header) {
            headerTitle.setText(header);
        }
    }

    /**
     * ViewHolder for the main NotificationModel items.
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

            Object rawTimestamp = notification.getTimestamp();
            Date dateToFormat = null;

            if (rawTimestamp == null) {
                // If timestamp is null
                notificationTime.setText("");
                return;
            } else if (rawTimestamp instanceof Date) {
                // If it's already a Date object (most likely from Firestore Timestamp)
                dateToFormat = (Date) rawTimestamp;
            } else if (rawTimestamp instanceof Long) {
                // If it's a Long (milliseconds), create a Date object
                dateToFormat = new Date((Long) rawTimestamp);
            } else {
                // Handle unexpected type
                notificationTime.setText("Time Unknown");
                return;
            }

            // Format time using SimpleDateFormat
            SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
            String time = timeFormat.format(dateToFormat);
            notificationTime.setText(time);
        }
    }
}
