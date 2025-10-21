// MessageAdapter.java
package fourthyear.roadrescue;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private List<MessageModel> messageList;
    private String currentUserId;
    private SimpleDateFormat timeFormat;

    public MessageAdapter(List<MessageModel> messageList, String currentUserId) {
        this.messageList = messageList;
        this.currentUserId = currentUserId;
        this.timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        MessageModel message = messageList.get(position);
        holder.bind(message);
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    class MessageViewHolder extends RecyclerView.ViewHolder {
        private LinearLayout messageContainer;
        private LinearLayout messageBubble;
        private TextView messageText;
        private TextView senderName;
        private TextView messageTime;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageContainer = itemView.findViewById(R.id.messageContainer);
            messageBubble = itemView.findViewById(R.id.messageBubble);
            messageText = itemView.findViewById(R.id.messageText);
            senderName = itemView.findViewById(R.id.senderName);
            messageTime = itemView.findViewById(R.id.messageTime);
        }

        void bind(MessageModel message) {
            messageText.setText(message.getText());

            // Format and set timestamp
            if (message.getTimestamp() != null) {
                String time = timeFormat.format(message.getTimestamp().toDate());
                messageTime.setText(time);
            }

            // Check if message is from current user
            boolean isCurrentUser = message.getSenderId().equals(currentUserId);

            if (isCurrentUser) {
                // Current user's message - align to right
                messageContainer.setGravity(android.view.Gravity.END);
                messageBubble.setBackgroundResource(R.drawable.bubble_outgoing);
                senderName.setVisibility(View.GONE); // Hide sender name for own messages
            } else {
                // Other user's message - align to left
                messageContainer.setGravity(android.view.Gravity.START);
                messageBubble.setBackgroundResource(R.drawable.bubble_incoming);
                senderName.setVisibility(View.VISIBLE);
                senderName.setText(message.getSenderName());
            }
        }
    }
}