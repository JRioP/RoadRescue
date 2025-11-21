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
import com.google.firebase.firestore.SetOptions;

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
    private TextView userNameText;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration messagesListener;
    private ListenerRegistration chatStatusListener;

    private ListenerRegistration unreadBadgeListener;
    private ListenerRegistration notificationBadgeListener;
    private TextView unreadBadge;
    private TextView unreadNotificationBadge;

    private FirebaseUser currentUser;
    private String chatId;
    private String otherUserName;
    private String receiverId; // The ID of the person we are chatting with
    private boolean isChatClosed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_conversation);

        // 1. Get Data from Intent
        chatId = getIntent().getStringExtra("chatId");
        otherUserName = getIntent().getStringExtra("receiverName");
        receiverId = getIntent().getStringExtra("receiverId");

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            finish();
            return;
        }

        if (chatId == null || chatId.isEmpty()) {
            Toast.makeText(this, "Error: Could not open chat.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 2. FIX: Safer logic to calculate Receiver ID for 3-part Chat IDs (RequestId_User1_User2)
        if (receiverId == null) {
            String currentUid = currentUser.getUid();
            String[] parts = chatId.split("_");
            // Iterate through parts: if it's not me, and looks like a User ID (length check helps avoid Request IDs if short), assume it's receiver.
            for (String part : parts) {
                if (!part.equals(currentUid) && part.length() > 15) { // Simple length check to differentiate from some short request IDs
                    receiverId = part;
                    break;
                }
            }
        }

        setupToolbar();
        initializeRecyclerView();
        setupViews();
        setupFirestoreListener();
        setupChatStatusListener();
        setupNavbar();
        setupUnreadBadgeListener();
        setupNotificationBadgeListener();

        resetUnreadCount();

        // 3. Force fetch the correct Full Name
        loadReceiverDetails();
    }

    // Fetches real name from DB to avoid "RoadRescue User"
    private void loadReceiverDetails() {
        if (receiverId == null || receiverId.isEmpty()) return;

        db.collection("users").document(receiverId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String fullName = documentSnapshot.getString("name");
                        if (fullName != null && !fullName.isEmpty()) {
                            otherUserName = fullName; // Update local variable
                            if (userNameText != null) {
                                userNameText.setText(fullName); // Update UI
                            }
                        }
                    }
                });
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
                    if (e != null) return;
                    if (snapshot != null && snapshot.exists()) {
                        String status = snapshot.getString("status");
                        // Check if status is "closed"
                        isChatClosed = "closed".equals(status) || "archived".equals(status);
                        updateUiForChatStatus();
                    }
                });
    }

    private void updateUiForChatStatus() {
        if (isChatClosed) {
            messageInput.setEnabled(false);
            messageInput.setHint("This session is closed.");
            messageInput.setBackgroundResource(android.R.color.transparent);
            sendButton.setEnabled(false);
            sendButton.setVisibility(View.INVISIBLE);
            if (userNameText != null && otherUserName != null) {
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

        findViewById(R.id.notification_icon_btn).setOnClickListener(v -> {
            Intent intent = new Intent(this, NotificationsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });
        findViewById(R.id.profile_icon_btn).setOnClickListener(v -> {
            Intent intent = new Intent(this, ProfileActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });
        findViewById(R.id.home_icon_btn).setOnClickListener(v -> {
            if (currentUser == null) {
                startActivity(new Intent(this, MainActivity.class)); finish(); return;
            }
            db.collection("users").document(currentUser.getUid()).get().addOnSuccessListener(doc -> {
                Intent intent;
                String type = doc.getString("userType");
                if (type != null && (type.equalsIgnoreCase("Service Provider") || type.equalsIgnoreCase("driver"))) {
                    intent = new Intent(this, ServiceProviderHomepage.class);
                } else {
                    intent = new Intent(this, homepage.class);
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            });
        });

        ImageView messageIcon = findViewById(R.id.message_icon_btn);
        ConstraintLayout messageLayout = findViewById(R.id.nav_message_layout);
        TextView messageText = findViewById(R.id.message_text);
        if (messageLayout != null) messageLayout.setBackgroundResource(R.drawable.rounded_white_background);
        if (messageIcon != null) {
            messageIcon.setColorFilter(Color.BLACK);
            messageIcon.setOnClickListener(v -> {
                Intent intent = new Intent(this, ChatInboxActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            });
        }
        if (messageText != null) {
            messageText.setTextColor(Color.BLACK);
            messageText.setTypeface(null, Typeface.BOLD);
        }
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
                            if (count != null) totalUnread += count;
                        }
                    }
                    if (unreadBadge != null) unreadBadge.setVisibility(totalUnread > 0 ? View.VISIBLE : View.GONE);
                });
    }

    private void setupNotificationBadgeListener() {
        if (currentUser == null) return;
        String currentUserId = currentUser.getUid();
        Query badgeQuery = db.collection("notifications").whereEqualTo("userId", currentUserId).whereEqualTo("read", false);
        if (notificationBadgeListener != null) notificationBadgeListener.remove();
        notificationBadgeListener = badgeQuery.addSnapshotListener((snapshots, e) -> {
            if (e != null) return;
            boolean hasUnread = snapshots != null && !snapshots.isEmpty();
            if (unreadNotificationBadge != null) unreadNotificationBadge.setVisibility(hasUnread ? View.VISIBLE : View.GONE);
        });
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
        // 4. PREVENT SENDING IF CLOSED
        if (isChatClosed) {
            Toast.makeText(this, "This session is closed.", Toast.LENGTH_SHORT).show();
            return;
        }
        String messageText = messageInput.getText().toString().trim();
        if (TextUtils.isEmpty(messageText)) return;

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
                .addOnFailureListener(e -> Log.e(TAG, "Error sending message: ", e));
    }

    private void updateLastMessageAndUnreadCount(String lastMessage) {
        // 5. CRITICAL FIX: Do not update 'status' or metadata if the chat is supposedly closed.
        if (isChatClosed) return;

        Map<String, Object> baseUpdates = new HashMap<>();
        baseUpdates.put("lastMessage", lastMessage);
        baseUpdates.put("lastMessageTimestamp", FieldValue.serverTimestamp());
        // Only set active if we are sure it's not closed. Since we checked isChatClosed above,
        // this implies we are reactivating or keeping it active.
        baseUpdates.put("status", "active");

        if (receiverId != null && !receiverId.isEmpty()) {
            baseUpdates.put("participantIds", Arrays.asList(getCurrentUserId(), receiverId));

            // 6. Name Preservation: Only overwrite if we have a Valid Real Name
            if (otherUserName != null &&
                    !otherUserName.equals("Chat") &&
                    !otherUserName.equals("RoadRescue User")) {

                Map<String, String> names = new HashMap<>();
                names.put(getCurrentUserId(), getCurrentUserName());
                names.put(receiverId, otherUserName);
                baseUpdates.put("participantNames", names);
            }
        }

        db.collection("chats").document(chatId)
                .set(baseUpdates, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    if (receiverId != null) {
                        db.collection("chats").document(chatId)
                                .update("unreadCounts." + receiverId, FieldValue.increment(1));
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update chat meta", e));
    }

    private void setupFirestoreListener() {
        messagesListener = db.collection("chats").document(chatId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) return;
                    if (value == null) return;
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
        return currentUser != null ? currentUser.getUid() : "default_user_id";
    }

    private String getCurrentUserName() {
        return (currentUser != null && currentUser.getDisplayName() != null) ? currentUser.getDisplayName() : "You";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messagesListener != null) messagesListener.remove();
        if (chatStatusListener != null) chatStatusListener.remove();
        if (unreadBadgeListener != null) unreadBadgeListener.remove();
        if (notificationBadgeListener != null) notificationBadgeListener.remove();
    }
}