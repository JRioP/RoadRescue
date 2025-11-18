package fourthyear.roadrescue;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
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
        private final LinearLayout messageContainer;
        private final LinearLayout messageBubble;
        private final TextView messageText;
        private final TextView senderName;
        private final TextView messageTime;

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

            if (message.getTimestamp() != null) {
                String time = timeFormat.format(message.getTimestamp().toDate());
                messageTime.setText(time);
            } else {
                messageTime.setText("");
            }

            boolean isCurrentUser = currentUserId.equals(message.getSenderId());

            if (isCurrentUser) {
                messageContainer.setGravity(android.view.Gravity.END);
                messageBubble.setBackgroundResource(R.drawable.bubble_outgoing);
                senderName.setVisibility(View.GONE);
                messageText.setTextColor(ContextCompat.getColor(itemView.getContext(), android.R.color.white));
                messageTime.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.white_dim));

            } else {
                messageContainer.setGravity(android.view.Gravity.START);
                messageBubble.setBackgroundResource(R.drawable.bubble_incoming);
                senderName.setVisibility(View.VISIBLE);

                String name = message.getSenderName() != null ? message.getSenderName() : "User";
                senderName.setText(name);

                messageText.setTextColor(ContextCompat.getColor(itemView.getContext(), android.R.color.black));
                messageTime.setTextColor(ContextCompat.getColor(itemView.getContext(), android.R.color.darker_gray));
                senderName.setTextColor(ContextCompat.getColor(itemView.getContext(), android.R.color.black));
            }
        }
    }
}
