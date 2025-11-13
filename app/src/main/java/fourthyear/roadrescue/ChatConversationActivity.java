package fourthyear.roadrescue;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ChatConversationActivity extends AppCompatActivity {

    private static final String TAG = "ChatConversation";

    private RecyclerView messagesRecyclerView;
    private MessageAdapter messageAdapter;
    private List<MessageModel> messageList;
    private EditText messageInput;
    private ImageButton sendButton;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration messagesListener;

    private String chatId;
    private String otherUserName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_conversation);

        chatId = getIntent().getStringExtra("chatId");
        otherUserName = getIntent().getStringExtra("receiverName");



        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        if (chatId == null || chatId.isEmpty()) {
            Log.e(TAG, "Chat ID is null or empty. Finishing activity.");
            Toast.makeText(this, "Error: Could not open chat.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        // -----------------------------

        setupToolbar();
        initializeRecyclerView();
        setupViews();
        setupFirestoreListener();
        setupNavbar();
    }

    private void setupNavbar() {
        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        notificationButton.setOnClickListener(v -> {
            Intent intent = new Intent(ChatConversationActivity.this, NotificationsActivity.class);
            startActivity(intent);
        });


        ImageView profileButton = findViewById(R.id.profile_icon_btn);
        profileButton.setOnClickListener(v -> {
        Intent intent = new Intent(ChatConversationActivity.this, ProfileActivity.class);
        startActivity(intent);
        });

        ImageView homeButton = findViewById(R.id.home_icon_btn);
        homeButton.setOnClickListener(v -> {
            Intent intent = new Intent(ChatConversationActivity.this, homepage.class);
            startActivity(intent);
        });

        ImageView messageButton = findViewById(R.id.message_icon_btn);
        messageButton.setOnClickListener(v -> {
            Intent intent = new Intent(ChatConversationActivity.this, ChatInboxActivity.class);
            startActivity(intent);
        });
    }

    private void setupToolbar() {
        ImageView backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        TextView userNameText = findViewById(R.id.userNameText);
        userNameText.setText(otherUserName != null ? otherUserName : "Chat");
    }

    private void initializeRecyclerView() {
        messageList = new ArrayList<>();
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);
        messageAdapter = new MessageAdapter(messageList, getCurrentUserId());

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        messagesRecyclerView.setLayoutManager(layoutManager);
        messagesRecyclerView.setAdapter(messageAdapter);
    }

    private void setupViews() {
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);

        sendButton.setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        String messageText = messageInput.getText().toString().trim();
        if (TextUtils.isEmpty(messageText)) {
            return;
        }

        String messageId = UUID.randomUUID().toString();
        String currentUserId = getCurrentUserId();
        String currentUserName = getCurrentUserName();

        MessageModel message = new MessageModel(messageId, currentUserId, currentUserName, messageText);
        message.setTimestamp(Timestamp.now());

        db.collection("chats").document(chatId)
                .collection("messages")
                .document(messageId)
                .set(message)
                .addOnSuccessListener(aVoid -> {
                    messageInput.setText("");
                    updateLastMessage(messageText);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error sending message: ", e);
                });
    }

    private void updateLastMessage(String lastMessage) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("lastMessage", lastMessage);
        updates.put("lastMessageTimestamp", Timestamp.now());

        db.collection("chats").document(chatId)
                .update(updates)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating last message: ", e);
                });
    }

    private void setupFirestoreListener() {
        messagesListener = db.collection("chats").document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.w(TAG, "Listen failed.", error);
                        return;
                    }

                    if (value == null) {
                        return;
                    }

                    messageList.clear();
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : value) {
                        MessageModel message = doc.toObject(MessageModel.class);
                        messageList.add(message);
                    }
                    messageAdapter.notifyDataSetChanged();

                    if (messageList.size() > 0) {
                        messagesRecyclerView.scrollToPosition(messageList.size() - 1);
                    }
                });
    }

    private String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            return user.getUid();
        }
        return "default_user_id";
    }

    private String getCurrentUserName() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null && user.getDisplayName() != null) {
            return user.getDisplayName();
        }
        return "You";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messagesListener != null) {
            messagesListener.remove();
        }
    }
}