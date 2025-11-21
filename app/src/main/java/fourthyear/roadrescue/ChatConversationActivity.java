package fourthyear.roadrescue;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
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
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatConversationActivity extends AppCompatActivity {

    private static final String TAG = "ChatConversation";

    private RecyclerView messagesRecyclerView;
    private MessageAdapter messageAdapter;
    private List<MessageModel> messageList;
    private EditText messageInput;
    private ImageButton sendButton;
    private TextView userNameText;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration messagesListener;
    private ListenerRegistration chatStatusListener;

    private ListenerRegistration unreadBadgeListener;
    private ListenerRegistration notificationBadgeListener; // Matches your class variable
    private TextView unreadBadge;
    private TextView unreadNotificationBadge;
    private FirebaseUser currentUser;
    private String chatId;
    private String otherUserName;
    private boolean isChatClosed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_conversation);

        chatId = getIntent().getStringExtra("chatId");
        otherUserName = getIntent().getStringExtra("receiverName");

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser(); // ADDED: Initialized here to prevent NullPointerException

        if (chatId == null || chatId.isEmpty()) {
            Log.e(TAG, "Chat ID is null or empty. Finishing activity.");
            Toast.makeText(this, "Error: Could not open chat.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupToolbar();
        initializeRecyclerView();
        setupViews();
        setupFirestoreListener();
        setupChatStatusListener();
        setupNavbar();
        setupUnreadBadgeListener();
        setupNotificationBadgeListener(); // This calls the fixed method

        resetUnreadCount();
    }

    private void resetUnreadCount() {
        if (currentUser == null) return;

        String currentUserId = currentUser.getUid();

        db.collection("chats").document(chatId)
                .update("unreadCounts." + currentUserId, 0)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to reset unread count", e));
    }

    private void setupChatStatusListener() {
        chatStatusListener = db.collection("chats").document(chatId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.w(TAG, "Listen for chat status failed.", e);
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        String status = snapshot.getString("status");
                        if ("closed".equals(status) || "archived".equals(status)) {
                            isChatClosed = true;
                        } else {
                            isChatClosed = false;
                        }
                        updateUiForChatStatus();
                    }
                });
    }

    private void setupUnreadBadgeListener() {
        if (currentUser == null) return;
        String currentUserId = currentUser.getUid();

        unreadBadgeListener = db.collection("chats")
                .whereArrayContains("participantIds", currentUserId)
                .whereEqualTo("status", "active")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;

                    int totalUnread = 0;
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            Long count = doc.getLong("unreadCounts." + currentUserId);
                            if (count != null) {
                                totalUnread += count;
                            }
                        }
                    }

                    if (unreadBadge != null) {
                        unreadBadge.setVisibility(totalUnread > 0 ? View.VISIBLE : View.GONE);
                    }
                });
    }

    // --- FIXED METHOD START ---
    private void setupNotificationBadgeListener() {
        if (currentUser == null) return;
        String currentUserId = currentUser.getUid();

        // Fixed: Now listens to 'notifications' collection
        Query badgeQuery = db.collection("notifications")
                .whereEqualTo("userId", currentUserId)
                .whereEqualTo("read", false);

        if (notificationBadgeListener != null) {
            notificationBadgeListener.remove();
        }

        notificationBadgeListener = badgeQuery.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.e(TAG, "Notification listener error", e);
                return;
            }
            boolean hasUnread = snapshots != null && !snapshots.isEmpty();

            if (unreadNotificationBadge != null) {
                if (hasUnread) {
                    unreadNotificationBadge.setVisibility(View.VISIBLE);
                } else {
                    unreadNotificationBadge.setVisibility(View.GONE);
                }
            }
        });
    }
    // --- FIXED METHOD END ---

    private void updateUiForChatStatus() {
        if (isChatClosed) {
            messageInput.setEnabled(false);
            messageInput.setHint("This session is closed.");
            messageInput.setBackgroundResource(android.R.color.transparent);
            sendButton.setEnabled(false);
            sendButton.setVisibility(View.INVISIBLE);
            if (userNameText != null) {
                userNameText.setText(otherUserName + " (Closed)");
            }
        } else {
            messageInput.setEnabled(true);
            messageInput.setHint("Type a message...");
            sendButton.setEnabled(true);
            sendButton.setVisibility(View.VISIBLE);
            if (userNameText != null) {
                userNameText.setText(otherUserName);
            }
        }
    }

    private void setupNavbar() {
        unreadBadge = findViewById(R.id.unread_message_badge);
        unreadNotificationBadge = findViewById(R.id.unread_notification_badge);

        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        notificationButton.setOnClickListener(v -> {
            Intent intent = new Intent(ChatConversationActivity.this, NotificationsActivity.class);
            // --- FIX ADDED HERE ---
            // Prevents creating a new activity if one already exists.
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });

        ImageView profileButton = findViewById(R.id.profile_icon_btn);
        profileButton.setOnClickListener(v -> {
            Intent intent = new Intent(ChatConversationActivity.this, ProfileActivity.class);
            // --- FIX ADDED HERE ---
            // Prevents creating a new activity if one already exists.
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });

        ImageView homeButton = findViewById(R.id.home_icon_btn);
        homeButton.setOnClickListener(v -> {
            if (currentUser == null) {
                Intent intent = new Intent(ChatConversationActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
                return;
            }

            db.collection("users").document(currentUser.getUid()).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        String userType = "Customer"; // Default
                        if (documentSnapshot.exists()) {
                            String type = documentSnapshot.getString("userType");

                            if (type != null && (type.trim().equalsIgnoreCase("Service Provider") || type.trim().equalsIgnoreCase("driver"))) {
                                userType = "Service Provider";
                            }
                        }

                        Intent intent;
                        if (userType.equals("Service Provider")) {
                            intent = new Intent(ChatConversationActivity.this, ServiceProviderHomepage.class);
                        } else {
                            intent = new Intent(ChatConversationActivity.this, homepage.class);
                        }

                        // This is the correct flag for a "Home" button, it clears the stack.
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to get userType", e);
                        Intent intent = new Intent(ChatConversationActivity.this, homepage.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    });
        });

        // --- Active State Styling for Message Icon ---
        ImageView messageIcon = findViewById(R.id.message_icon_btn);
        ConstraintLayout messageLayout = findViewById(R.id.nav_message_layout);
        TextView messageText = findViewById(R.id.message_text);

        if (messageLayout != null) {
            messageLayout.setBackgroundResource(R.drawable.rounded_white_background);
        }
        if (messageIcon != null) {
            messageIcon.setColorFilter(Color.BLACK);
        }
        if (messageText != null) {
            messageText.setTextColor(Color.BLACK);
            messageText.setTypeface(null, Typeface.BOLD);
        }

        if (messageIcon != null) {
            messageIcon.setOnClickListener(v -> {
                Intent intent = new Intent(ChatConversationActivity.this, ChatInboxActivity.class);
                // This is the correct flag for going "up" to the parent inbox screen.
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            });
        }
    }

    private void setupToolbar() {
        ImageView backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        userNameText = findViewById(R.id.userNameText);
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
        if (isChatClosed) {
            Toast.makeText(this, "This session is closed. You cannot send messages.", Toast.LENGTH_SHORT).show();
            return;
        }

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
                    updateLastMessageAndUnreadCount(messageText);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error sending message: ", e);
                });
    }

    private void updateLastMessageAndUnreadCount(String lastMessage) {
        db.collection("chats").document(chatId).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                List<String> participants = (List<String>) doc.get("participantIds");
                if (participants != null) {
                    String currentUserId = getCurrentUserId();

                    for (String id : participants) {
                        if (!id.equals(currentUserId)) {
                            db.collection("chats").document(chatId)
                                    .update(
                                            "unreadCounts." + id, FieldValue.increment(1),
                                            "lastMessage", lastMessage,
                                            "lastMessageTimestamp", Timestamp.now()
                                    )
                                    .addOnFailureListener(e -> Log.e(TAG, "Failed to update chat meta", e));
                        }
                    }
                }
            }
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
        if (currentUser != null) {
            return currentUser.getUid();
        }
        return "default_user_id";
    }

    private String getCurrentUserName() {
        if (currentUser != null && currentUser.getDisplayName() != null) {
            return currentUser.getDisplayName();
        }
        return "You";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messagesListener != null) {
            messagesListener.remove();
        }
        if (chatStatusListener != null) {
            chatStatusListener.remove();
        }
        if (unreadBadgeListener != null) {
            unreadBadgeListener.remove();
        }
        if (notificationBadgeListener != null) {
            notificationBadgeListener.remove();
        }
    }
}