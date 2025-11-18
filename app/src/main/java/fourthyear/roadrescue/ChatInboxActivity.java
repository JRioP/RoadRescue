package fourthyear.roadrescue;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChatInboxActivity extends AppCompatActivity {

    private static final String TAG = "ChatInboxActivity";

    private RecyclerView chatsRecyclerView;
    private ChatInboxAdapter chatInboxAdapter;
    private List<ChatInboxItem> chatList;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    // Listeners
    private ListenerRegistration chatsListener;
    private ListenerRegistration unreadListener; // For Message Badge
    private ListenerRegistration notificationListener; // For Notification Badge

    private String currentUserId;

    // Badges
    private TextView unreadBadge;
    private TextView unreadNotificationBadge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_inbox);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentUserId = currentUser.getUid();

        setupViews();
        initializeRecyclerView();
        setupFirestoreListener(); // Listener for the chat list

        // Setup Badge Listeners
        setupUnreadMessageListener();
        setupNotificationListener();

        setupNavbar();
    }

    private void setupViews() {
        ImageView backButton = findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        TextView titleText = findViewById(R.id.title);
        if (titleText != null) {
            titleText.setText("Messages");
        }

        // Initialize Badge TextViews
        unreadBadge = findViewById(R.id.unread_message_badge);
        unreadNotificationBadge = findViewById(R.id.unread_notification_badge);
    }

    // --- Listener for Unread Chat Messages Badge ---
    private void setupUnreadMessageListener() {
        if (currentUserId == null) return;

        unreadListener = db.collection("chats")
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

                    if (totalUnread > 0) {
                        unreadBadge.setVisibility(View.VISIBLE);
                    } else {
                        unreadBadge.setVisibility(View.GONE);
                    }
                });
    }

    // --- Listener for Unread Notifications Badge ---
    private void setupNotificationListener() {
        if (currentUserId == null) return;

        notificationListener = db.collection("notifications")
                .whereEqualTo("userId", currentUserId)
                .whereEqualTo("read", false)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;

                    if (snapshots != null && !snapshots.isEmpty()) {
                        unreadNotificationBadge.setVisibility(View.VISIBLE);
                    } else {
                        unreadNotificationBadge.setVisibility(View.GONE);
                    }
                });
    }

    private void initializeRecyclerView() {
        chatList = new ArrayList<>();
        chatsRecyclerView = findViewById(R.id.usersRecyclerView);

        chatInboxAdapter = new ChatInboxAdapter(this, chatList, chat -> {

            if (chat.getChatId() == null) {
                Log.e(TAG, "Chat ID is null on click, cannot open chat.");
                Toast.makeText(this, "Error opening chat.", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(this, ChatConversationActivity.class);
            intent.putExtra("chatId", chat.getChatId());

            String otherUserName = "Chat";
            if (chat.getParticipantNames() != null) {
                for (Map.Entry<String, String> entry : chat.getParticipantNames().entrySet()) {
                    if (!entry.getKey().equals(currentUserId)) {
                        otherUserName = entry.getValue();
                        break;
                    }
                }
            }
            intent.putExtra("receiverName", otherUserName);
            startActivity(intent);
        });

        chatsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatsRecyclerView.setAdapter(chatInboxAdapter);
    }

    private void setupFirestoreListener() {
        if (currentUserId == null) return;

        chatsListener = db.collection("chats")
                .whereArrayContains("participantIds", currentUserId)
                .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Firestore listen failed: ", error);
                        return;
                    }

                    if (value != null) {
                        chatList.clear();
                        for (QueryDocumentSnapshot doc : value) {
                            ChatInboxItem chat = doc.toObject(ChatInboxItem.class);
                            chat.setChatId(doc.getId());
                            chatList.add(chat);
                        }
                        chatInboxAdapter.notifyDataSetChanged();
                        Log.d(TAG, "Inbox updated with " + chatList.size() + " conversations.");
                    } else {
                        Log.d(TAG, "Chat query returned null");
                    }
                });
    }

    private void setupNavbar() {
        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        notificationButton.setOnClickListener(v -> {
            Intent intent = new Intent(ChatInboxActivity.this, NotificationsActivity.class);
            startActivity(intent);
        });

        ImageView profileButton = findViewById(R.id.profile_icon_btn);
        profileButton.setOnClickListener(v -> {
            Intent intent = new Intent(ChatInboxActivity.this, ProfileActivity.class);
            startActivity(intent);
        });

        // --- HOME BUTTON FIX ---
        ImageView homeButton = findViewById(R.id.home_icon_btn);
        homeButton.setOnClickListener(v -> {
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) {
                Intent intent = new Intent(ChatInboxActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
                return;
            }

            db.collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        String userType = "Customer"; // Default
                        if (documentSnapshot.exists()) {
                            String type = documentSnapshot.getString("userType");

                            // Robust check for "driver" or "Service Provider"
                            if (type != null && (type.trim().equalsIgnoreCase("Service Provider") || type.trim().equalsIgnoreCase("driver"))) {
                                userType = "Service Provider";
                            }
                        }

                        if (userType.equals("Service Provider")) {
                            Intent intent = new Intent(ChatInboxActivity.this, ServiceProviderHomepage.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        } else {
                            Intent intent = new Intent(ChatInboxActivity.this, homepage.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to get userType, defaulting to customer homepage", e);
                        Intent intent = new Intent(ChatInboxActivity.this, homepage.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    });
        });

        // --- MESSAGE BUTTON (Active State Styling) ---
        ConstraintLayout messageLayout = findViewById(R.id.nav_message_layout);
        ImageView messageIcon = findViewById(R.id.message_icon_btn);
        TextView messageText = findViewById(R.id.message_text);

        if (messageLayout != null) {
            // Disable click so it stays on the page
            messageLayout.setClickable(false);
            messageLayout.setFocusable(false);
            // Add Rounded White Background
            messageLayout.setBackgroundResource(R.drawable.rounded_white_background);
        }

        if (messageIcon != null) {
            messageIcon.setColorFilter(Color.BLACK); // Force black icon
            messageIcon.setClickable(false);
            messageIcon.setFocusable(false);
        }

        if (messageText != null) {
            messageText.setTextColor(Color.BLACK); // Force black text
            messageText.setTypeface(null, Typeface.BOLD); // Force bold text
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatsListener != null) {
            chatsListener.remove();
        }
        if (unreadListener != null) {
            unreadListener.remove();
        }
        if (notificationListener != null) {
            notificationListener.remove();
        }
    }
}