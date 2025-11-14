package fourthyear.roadrescue;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
    private ListenerRegistration chatsListener;
    private String currentUserId;

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
        setupFirestoreListener();
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

        // --- THIS IS THE FIX ---
        ImageView homeButton = findViewById(R.id.home_icon_btn);
        homeButton.setOnClickListener(v -> {
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) {
                // Failsafe, go to login
                Intent intent = new Intent(ChatInboxActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
                return;
            }

            // Check the user's type from Firestore
            db.collection("users").document(user.getUid()).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        String userType = "Customer"; // Default to customer
                        if (documentSnapshot.exists()) {
                            String type = documentSnapshot.getString("userType");
                            if (type != null && type.equals("Service Provider")) {
                                userType = type;
                            }
                        }

                        if (userType.equals("Service Provider")) {
                            // Go to provider homepage
                            Intent intent = new Intent(ChatInboxActivity.this, ServiceProviderHomepage.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        } else {
                            // Go to customer homepage
                            Intent intent = new Intent(ChatInboxActivity.this, homepage.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                        finish(); // Finish chat activity after navigating home
                    })
                    .addOnFailureListener(e -> {
                        // On failure, just default to the customer homepage
                        Log.e(TAG, "Failed to get userType, defaulting to customer homepage", e);
                        Intent intent = new Intent(ChatInboxActivity.this, homepage.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        finish();
                    });
        });

        ImageView messageButton = findViewById(R.id.message_icon_btn);
        messageButton.setOnClickListener(v -> {
            Intent intent = new Intent(ChatInboxActivity.this, ChatInboxActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatsListener != null) {
            chatsListener.remove();
        }
    }
}