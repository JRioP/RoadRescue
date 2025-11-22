package fourthyear.roadrescue;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView; // <-- ADD THIS IMPORT
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide; // <-- ADD THIS IMPORT
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private final List<MessageModel> messageList;
    private final String currentUserId;
    private final SimpleDateFormat timeFormat;

    public MessageAdapter(List<MessageModel> messageList, String currentUserId) {
        this.messageList = messageList;
        this.currentUserId = currentUserId;
        this.timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false); // Make sure this is your correct XML file name
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
        private final LinearLayout messageContainer;
        private final LinearLayout messageBubble;
        private final TextView messageTextView; // <-- RENAMED from messageText
        private final ImageView messageImageView; // <-- ADDED
        private final TextView senderName;
        private final TextView messageTime;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageContainer = itemView.findViewById(R.id.messageContainer);
            messageBubble = itemView.findViewById(R.id.messageBubble);
            messageTextView = itemView.findViewById(R.id.messageTextView);
            messageImageView = itemView.findViewById(R.id.messageImageView);
            senderName = itemView.findViewById(R.id.senderName);
            messageTime = itemView.findViewById(R.id.messageTime);
        }

        void bind(MessageModel message) {
            // 1. Set timestamp
            if (message.getTimestamp() != null) {
                String time = timeFormat.format(message.getTimestamp().toDate());
                messageTime.setText(time);
            } else {
                messageTime.setText("");
            }

            // 2. Check if the message is an image or text
            boolean isImageMessage = message.getImageUrl() != null && !message.getImageUrl().isEmpty();
            boolean isCurrentUser = currentUserId.equals(message.getSenderId());

            // 3. Set the content (Image or Text)
            if (isImageMessage) {
                messageTextView.setVisibility(View.GONE);
                messageImageView.setVisibility(View.VISIBLE);
                Glide.with(itemView.getContext())
                        .load(message.getImageUrl())
                        .into(messageImageView);
            } else {
                messageImageView.setVisibility(View.GONE);
                messageTextView.setVisibility(View.VISIBLE);
                messageTextView.setText(message.getText());
            }
            if (isCurrentUser) {
                messageContainer.setGravity(android.view.Gravity.END);
                messageBubble.setBackgroundResource(R.drawable.bubble_outgoing);
                senderName.setVisibility(View.GONE);
                messageTime.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.white_dim));

                // Only set text color if it's a text message
                if (!isImageMessage) {
                    messageTextView.setTextColor(ContextCompat.getColor(itemView.getContext(), android.R.color.white));
                }

            } else {
                messageContainer.setGravity(android.view.Gravity.START);
                messageBubble.setBackgroundResource(R.drawable.bubble_incoming);
                senderName.setVisibility(View.VISIBLE);

                String name = message.getSenderName() != null ? message.getSenderName() : "User";
                senderName.setText(name);
                senderName.setTextColor(ContextCompat.getColor(itemView.getContext(), android.R.color.black));
                messageTime.setTextColor(ContextCompat.getColor(itemView.getContext(), android.R.color.darker_gray));

                // Only set text color if it's a text message
                if (!isImageMessage) {
                    messageTextView.setTextColor(ContextCompat.getColor(itemView.getContext(), android.R.color.black));
                }
            }
        }
    }
}