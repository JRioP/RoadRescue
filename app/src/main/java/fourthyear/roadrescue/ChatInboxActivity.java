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
        setContentView(R.layout.activity_chat_inbox); // Your layout file

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in.", Toast.LENGTH_SHORT).show();
            finish(); // Close if no user is logged in
            return;
        }
        currentUserId = currentUser.getUid();

        setupViews();
        initializeRecyclerView();
        setupFirestoreListener(); // This is the new query
        setupNavbar();
    }

    private void setupViews() {
        ImageView backButton = findViewById(R.id.backButton); // Assumes you have a back button
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }

        TextView titleText = findViewById(R.id.title); // Assumes you have this
        if (titleText != null) {
            titleText.setText("Messages");
        }
    }

    private void initializeRecyclerView() {
        chatList = new ArrayList<>();
        chatsRecyclerView = findViewById(R.id.usersRecyclerView); // The ID from your XML

        chatInboxAdapter = new ChatInboxAdapter(this, chatList, chat -> {
            // Click listener for a chat item

            // --- FIX FOR BUG 2 ---
            if (chat.getChatId() == null) {
                Log.e(TAG, "Chat ID is null on click, cannot open chat.");
                Toast.makeText(this, "Error opening chat.", Toast.LENGTH_SHORT).show();
                return;
            }
            // ---------------------

            Intent intent = new Intent(this, ChatConversationActivity.class);
            intent.putExtra("chatId", chat.getChatId());

            // Pass the other user's name
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

                            // --- FIX FOR BUG 2 ---
                            // Manually set the document ID onto the chat object
                            // This guarantees chat.getChatId() is NOT null.
                            chat.setChatId(doc.getId());
                            // ---------------------

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
        notificationButton.setOnClickListener(v -> startActivity(new Intent(ChatInboxActivity.this, NotificationsActivity.class)));

        ImageView profileButton = findViewById(R.id.profile_icon_btn);
        profileButton.setOnClickListener(v -> startActivity(new Intent(ChatInboxActivity.this, ProfileActivity.class)));

        ImageView homeButton = findViewById(R.id.home_icon_btn);
        homeButton.setOnClickListener(v -> startActivity(new Intent(ChatInboxActivity.this, homepage.class)));

        // No listener for message button, we are already here
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatsListener != null) {
            chatsListener.remove();
        }
    }
}