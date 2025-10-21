// ChatAdapter.java
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

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    public interface OnChatClickListener {
        void onChatClick(ChatModel chat);
    }

    private List<ChatModel> chatList;
    private OnChatClickListener listener;
    private SimpleDateFormat timeFormat;

    public ChatAdapter(List<ChatModel> chatList, OnChatClickListener listener) {
        this.chatList = chatList;
        this.listener = listener;
        this.timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatModel chat = chatList.get(position);
        holder.bind(chat, listener);
    }

    @Override
    public int getItemCount() {
        return chatList.size();
    }

    class ChatViewHolder extends RecyclerView.ViewHolder {
        private TextView nameText;
        private TextView lastMessageText;
        private TextView timestampText;
        private TextView priceText;
        private TextView unreadBadge;

        ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.nameText);
            lastMessageText = itemView.findViewById(R.id.lastMessageText);
            timestampText = itemView.findViewById(R.id.timestampText);
            unreadBadge = itemView.findViewById(R.id.unreadBadge);
        }

        void bind(ChatModel chat, OnChatClickListener listener) {
            nameText.setText(chat.getUserName());
            lastMessageText.setText(chat.getLastMessage());
            priceText.setText(chat.getPrice());

            // Format timestamp
            if (chat.getTimestamp() != null) {
                String time = timeFormat.format(chat.getTimestamp().toDate());
                timestampText.setText(time);
            }

            // Show unread badge
            if (chat.getUnreadCount() > 0) {
                unreadBadge.setVisibility(View.VISIBLE);
                unreadBadge.setText(String.valueOf(chat.getUnreadCount()));
            } else {
                unreadBadge.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> listener.onChatClick(chat));
        }
    }
}