// UsersAdapter.java
package fourthyear.roadrescue;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class UsersAdapter extends RecyclerView.Adapter<UsersAdapter.UserViewHolder> {

    public interface OnUserClickListener {
        void onUserClick(User user);
    }

    private List<User> usersList;
    private OnUserClickListener listener;
    private SimpleDateFormat dateFormat;

    public UsersAdapter(List<User> usersList, OnUserClickListener listener) {
        this.usersList = usersList;
        this.listener = listener;
        this.dateFormat = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        User user = usersList.get(position);
        holder.bind(user, listener);
    }

    @Override
    public int getItemCount() {
        return usersList.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        private TextView nameText;
        private TextView userTypeText;
        private TextView onlineStatus;
        private TextView lastSeenText;
        private View onlineIndicator;

        UserViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.nameText);
            userTypeText = itemView.findViewById(R.id.userTypeText);
            onlineStatus = itemView.findViewById(R.id.onlineStatus);
            lastSeenText = itemView.findViewById(R.id.lastSeenText);
            onlineIndicator = itemView.findViewById(R.id.onlineIndicator);
        }

        void bind(User user, OnUserClickListener listener) {
            nameText.setText(user.getName());
            userTypeText.setText(user.getUserType());

            // Show online/offline status
            if (user.isOnline()) {
                onlineStatus.setText("Online");
                onlineStatus.setTextColor(itemView.getContext().getResources().getColor(android.R.color.holo_green_dark));
                onlineIndicator.setBackgroundColor(itemView.getContext().getResources().getColor(android.R.color.holo_green_dark));
                lastSeenText.setVisibility(View.GONE);
            } else {
                onlineStatus.setText("Offline");
                onlineStatus.setTextColor(itemView.getContext().getResources().getColor(android.R.color.darker_gray));
                onlineIndicator.setBackgroundColor(itemView.getContext().getResources().getColor(android.R.color.darker_gray));

                // Show last seen time if available
                if (user.getLastSeen() != null) {
                    lastSeenText.setVisibility(View.VISIBLE);
                    String lastSeen = formatLastSeen(user.getLastSeen().toDate());
                    lastSeenText.setText("Last seen: " + lastSeen);
                } else {
                    lastSeenText.setVisibility(View.GONE);
                }
            }

            itemView.setOnClickListener(v -> listener.onUserClick(user));
        }

        private String formatLastSeen(Date lastSeenDate) {
            long diff = System.currentTimeMillis() - lastSeenDate.getTime();
            long minutes = diff / (60 * 1000);
            long hours = diff / (60 * 60 * 1000);
            long days = diff / (24 * 60 * 60 * 1000);

            if (minutes < 1) {
                return "just now";
            } else if (minutes < 60) {
                return minutes + " min ago";
            } else if (hours < 24) {
                return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
            } else if (days < 7) {
                return days + " day" + (days > 1 ? "s" : "") + " ago";
            } else {
                SimpleDateFormat format = new SimpleDateFormat("MMM dd", Locale.getDefault());
                return format.format(lastSeenDate);
            }
        }
    }
}