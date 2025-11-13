package fourthyear.roadrescue;

import android.content.Context;
import android.text.format.DateUtils; // Import this
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
// Import CircleImageView
import de.hdodenhof.circleimageview.CircleImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.Timestamp; // Import this
import com.google.firebase.auth.FirebaseAuth;
// Import Glide or another image loading library
// import com.bumptech.glide.Glide;

import java.util.List;
import java.util.Map;

public class ChatInboxAdapter extends RecyclerView.Adapter<ChatInboxAdapter.ChatViewHolder> {

    private final List<ChatInboxItem> chatList;
    private final Context context;
    private final String currentUserId;
    private final OnChatItemClickListener clickListener;

    public interface OnChatItemClickListener {
        void onChatItemClick(ChatInboxItem chat);
    }

    public ChatInboxAdapter(Context context, List<ChatInboxItem> chatList, OnChatItemClickListener clickListener) {
        this.context = context;
        this.chatList = chatList;
        this.clickListener = clickListener;
        this.currentUserId = FirebaseAuth.getInstance().getUid();
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_chat_inbox, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        ChatInboxItem chat = chatList.get(position);
        // Pass context to the bind method
        holder.bind(chat, currentUserId, clickListener, context);
    }

    @Override
    public int getItemCount() {
        return chatList.size();
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        CircleImageView profileImage;
        TextView userNameText;
        TextView lastMessageText;
        TextView lastMessageTimeText;
        TextView refText;

        ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            profileImage = itemView.findViewById(R.id.profile_image);
            userNameText = itemView.findViewById(R.id.user_name_text);
            lastMessageText = itemView.findViewById(R.id.last_message_text);
            lastMessageTimeText = itemView.findViewById(R.id.last_message_time_text);
            refText = itemView.findViewById(R.id.request_ref_text);
        }

        // Receive context in the bind method
        void bind(final ChatInboxItem chat, String currentUserId, final OnChatItemClickListener listener, Context context) {

            // Set User Name
            String otherUserName = "Chat";
            if (chat.getParticipantNames() != null) {
                for (Map.Entry<String, String> entry : chat.getParticipantNames().entrySet()) {
                    if (!entry.getKey().equals(currentUserId)) {
                        otherUserName = entry.getValue();
                        break;
                    }
                }
            }
            userNameText.setText(otherUserName);

            // Set Last Message
            lastMessageText.setText(chat.getLastMessage());

            // Set Last Message Time
            if (chat.getLastMessageTimestamp() != null) {
                // Pass context to the formatter
                lastMessageTimeText.setText(formatTimestamp(context, chat.getLastMessageTimestamp()));
            } else {
                lastMessageTimeText.setText("");
            }

            // Set Ref text
            if(refText != null && chat.getChatId() != null) {
                refText.setText("Ref: " + chat.getChatId());
            }

            // (Optional) Load Profile Image
            // String imageUrl = null;
            // ... (your logic to get image url)
            // Glide.with(context)
            //     .load(imageUrl)
            //     .placeholder(R.drawable.ic_default_profile)
            //     .into(profileImage);


            itemView.setOnClickListener(v -> listener.onChatItemClick(chat));
        }

        /**
         * Formats a Timestamp into a relative string like "10:30 PM" or "Tue"
         * FIX: This method is no longer static and requires a Context.
         */
        private String formatTimestamp(Context context, Timestamp timestamp) {
            if (timestamp == null) return "";
            long timeInMillis = timestamp.toDate().getTime();

            if (DateUtils.isToday(timeInMillis)) {
                // FIX: Pass context
                return DateUtils.formatDateTime(context, timeInMillis, DateUtils.FORMAT_SHOW_TIME);
            } else if (DateUtils.isToday(timeInMillis + DateUtils.DAY_IN_MILLIS)) {
                return "Yesterday";
            } else {
                // FIX: Pass context
                return DateUtils.formatDateTime(context, timeInMillis, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH);
            }
        }
    }
}