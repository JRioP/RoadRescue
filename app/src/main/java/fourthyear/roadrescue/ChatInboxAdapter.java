package fourthyear.roadrescue;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import de.hdodenhof.circleimageview.CircleImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;

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

        void bind(final ChatInboxItem chat, String currentUserId, final OnChatItemClickListener listener, Context context) {

            String otherUserName = "RoadRescue User";
            if (chat.getParticipantNames() != null) {
                for (Map.Entry<String, String> entry : chat.getParticipantNames().entrySet()) {
                    if (!entry.getKey().equals(currentUserId)) {
                        String name = entry.getValue();
                        if (name != null && !name.trim().isEmpty()) {
                            otherUserName = name;
                        }
                        break;
                    }
                }
            }
            userNameText.setText(otherUserName);

            lastMessageText.setText(chat.getLastMessage());
            if (chat.getLastMessageTimestamp() != null) {
                lastMessageTimeText.setText(formatTimestamp(context, chat.getLastMessageTimestamp()));
            } else {
                lastMessageTimeText.setText("");
            }

            if(refText != null && chat.getChatId() != null) {
                refText.setVisibility(View.VISIBLE);
                String displayId = chat.getChatId();
                if (displayId.length() > 10) {
                    displayId = displayId.substring(0, 8) + "...";
                }
                refText.setText("Ref: " + displayId);
            }

            // Click Listener
            itemView.setOnClickListener(v -> listener.onChatItemClick(chat));
        }

        private String formatTimestamp(Context context, Timestamp timestamp) {
            if (timestamp == null) return "";
            long timeInMillis = timestamp.toDate().getTime();

            if (DateUtils.isToday(timeInMillis)) {
                return DateUtils.formatDateTime(context, timeInMillis, DateUtils.FORMAT_SHOW_TIME);
            } else {
                return DateUtils.formatDateTime(context, timeInMillis, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_MONTH);
            }
        }
    }
}