// ChatConversationActivity.java
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

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Arrays;
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
    private String otherUserId;
    private String otherUserName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_conversation);

        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        notificationButton.setOnClickListener(v -> {
            Intent intent = new Intent(ChatConversationActivity.this, NotificationsActivity.class);
            startActivity(intent);
        });

        ImageView profileButton = findViewById(R.id.profile_icon_btn);
        //profileButton.setOnClickListener(v -> {
        //    Intent intent = new Intent(homepage.this, ProfileActivity.class);
        //    startActivity(intent);
        //});

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


        // Get intent data
        chatId = getIntent().getStringExtra("chatId");
        otherUserId = getIntent().getStringExtra("userId");
        otherUserName = getIntent().getStringExtra("userName");

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        setupToolbar();
        initializeRecyclerView();
        setupViews();
        setupFirestoreListener();
    }

    private void setupToolbar() {
        ImageView backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        TextView userNameText = findViewById(R.id.userNameText);
        userNameText.setText(otherUserName);
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

        // Create message object
        MessageModel message = new MessageModel(messageId, currentUserId, currentUserName, messageText);
        message.setTimestamp(Timestamp.now());

        // Add message to Firestore
        db.collection("chats").document(chatId)
                .collection("messages")
                .document(messageId)
                .set(message)
                .addOnSuccessListener(aVoid -> {
                    messageInput.setText("");

                    // Update last message in chat document
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

                    messageList.clear();
                    for (com.google.firebase.firestore.QueryDocumentSnapshot doc : value) {
                        MessageModel message = doc.toObject(MessageModel.class);
                        messageList.add(message);
                    }
                    messageAdapter.notifyDataSetChanged();

                    // Scroll to bottom
                    messagesRecyclerView.scrollToPosition(messageList.size() - 1);
                });
    }

    private String getCurrentUserId() {
        if (auth.getCurrentUser() != null) {
            return auth.getCurrentUser().getUid();
        }
        return "default_user_id";
    }

    private String getCurrentUserName() {
        if (auth.getCurrentUser() != null && auth.getCurrentUser().getDisplayName() != null) {
            return auth.getCurrentUser().getDisplayName();
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

    private void createSampleChats() {
        String currentUserId = getCurrentUserId();

        // Sample chat data
        Map<String, Object> chat1 = new HashMap<>();
        chat1.put("participants", Arrays.asList(currentUserId, "user1"));
        chat1.put("userName", "John's Auto Repair");
        chat1.put("lastMessage", "Thanks for choosing our service!");
        chat1.put("price", "$20");
        chat1.put("lastMessageTimestamp", com.google.firebase.Timestamp.now());

        db.collection("chats").add(chat1);

        Map<String, Object> chat2 = new HashMap<>();
        chat2.put("participants", Arrays.asList(currentUserId, "user2"));
        chat2.put("userName", "Sarah Mechanic");
        chat2.put("lastMessage", "Your car is ready for pickup");
        chat2.put("price", "$45");
        chat2.put("lastMessageTimestamp", com.google.firebase.Timestamp.now());
        chat2.put("unreadCount", 2);

        db.collection("chats").add(chat2);
    }
}
