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
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatInboxActivity extends AppCompatActivity {

    private static final String TAG = "ChatInboxActivity";

    private RecyclerView usersRecyclerView;
    private UsersAdapter usersAdapter;
    private List<User> usersList;
    private TextView balanceText;
    private TextView titleText;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration usersListener;
    private DocumentReference currentUserRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_inbox);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        setupViews();
        initializeRecyclerView();
        setupOnlineStatus();
        setupFirestoreListener();

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

        ImageView homeButton = findViewById(R.id.home_icon_btn);
        homeButton.setOnClickListener(v -> {
            Intent intent = new Intent(ChatInboxActivity.this, homepage.class);
            startActivity(intent);
        });


        ImageView messageButton = findViewById(R.id.message_icon_btn);
        messageButton.setOnClickListener(v -> {
            Intent intent = new Intent(ChatInboxActivity.this, ChatInboxActivity.class);
            startActivity(intent);
        });
    }

    private void createUser(String userId, String name, String email, String phone, String userType, boolean isOnline, UserCreationCallback callback) {
        db.collection("users").document(userId).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            Log.d(TAG, "User " + userId + " already exists");
                            callback.onSuccess();
                        } else {
                            User user = new User(userId, name, email, phone, userType, isOnline);

                            db.collection("users").document(userId).set(user)
                                    .addOnSuccessListener(aVoid -> {
                                        Log.d(TAG, "Successfully created user: " + name + " (" + (isOnline ? "Online" : "Offline") + ")");
                                        callback.onSuccess();
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Error creating user " + name + ": " + e.getMessage());
                                        callback.onSuccess();
                                    });
                        }
                    } else {
                        Log.e(TAG, "Error checking user existence: " + task.getException());
                        callback.onSuccess();
                    }
                });
    }

    interface UserCreationCallback {
        void onSuccess();
    }

    private void setupViews() {
        ImageView backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish());

        titleText = findViewById(R.id.title);
        titleText.setText("All Users");
    }

    private void setupOnlineStatus() {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            Log.e(TAG, "Current user ID is null - user not authenticated");
            return;
        }

        currentUserRef = db.collection("users").document(currentUserId);

        Map<String, Object> userData = new HashMap<>();
        userData.put("isOnline", true);
        userData.put("lastSeen", FieldValue.serverTimestamp());

        String finalCurrentUserId = currentUserId;
        currentUserRef.update(userData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Current user online status updated");
                })
                .addOnFailureListener(e -> {
                    Log.d(TAG, "Current user doesn't exist, creating...");
                    createCurrentUser(finalCurrentUserId);
                });
    }

    private void createCurrentUser(String userId) {
        String phone = "";
        if (auth.getCurrentUser() != null && auth.getCurrentUser().getPhoneNumber() != null) {
            phone = auth.getCurrentUser().getPhoneNumber();
        }

        User currentUser = new User(userId,
                auth.getCurrentUser() != null && auth.getCurrentUser().getDisplayName() != null ?
                        auth.getCurrentUser().getDisplayName() : "Test User",
                auth.getCurrentUser() != null ? auth.getCurrentUser().getEmail() : "test@user.com",
                phone,
                "driver",
                true);

        db.collection("users").document(userId).set(currentUser)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Current user created successfully");
                    currentUserRef = db.collection("users").document(userId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error creating current user: " + e.getMessage());
                });
    }

    private void initializeRecyclerView() {
        usersList = new ArrayList<>();
        usersRecyclerView = findViewById(R.id.usersRecyclerView);

        if (usersRecyclerView == null) {
            Log.e(TAG, "RecyclerView not found! Check your layout file.");
            Toast.makeText(this, "RecyclerView not found in layout", Toast.LENGTH_LONG).show();
            return;
        }

        usersAdapter = new UsersAdapter(usersList, this::onUserItemClick);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        usersRecyclerView.setLayoutManager(layoutManager);

        try {
            androidx.recyclerview.widget.DividerItemDecoration divider =
                    new androidx.recyclerview.widget.DividerItemDecoration(this, layoutManager.getOrientation());
            usersRecyclerView.addItemDecoration(divider);
        } catch (Exception e) {
            Log.e(TAG, "Error adding divider: " + e.getMessage());
        }

        usersRecyclerView.setAdapter(usersAdapter);
        Log.d(TAG, "RecyclerView initialized");
    }

    private void setupFirestoreListener() {
        String currentUserId = getCurrentUserId();

        if (currentUserId == null) {
            Log.e(TAG, "User not authenticated - cannot setup Firestore listener");
            Toast.makeText(this, "Please sign in to see users", Toast.LENGTH_LONG).show();
            return;
        }

        Log.d(TAG, "Setting up Firestore listener for current user: " + currentUserId);

        usersListener = db.collection("users")
                .orderBy("isOnline", Query.Direction.DESCENDING)
                .orderBy("name")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Firestore listen failed: " + error.getMessage());
                        Toast.makeText(ChatInboxActivity.this, "Error loading users: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (value != null && !value.isEmpty()) {
                        Log.d(TAG, "Found " + value.size() + " total users");
                        usersList.clear();
                        int onlineCount = 0;
                        int offlineCount = 0;

                        for (QueryDocumentSnapshot doc : value) {
                            User user = doc.toObject(User.class);

                            if (user.getUserId() != null && !currentUserId.equals(user.getUserId())) {
                                usersList.add(user);
                                if (user.isOnline()) {
                                    onlineCount++;
                                    Log.d(TAG, "Added ONLINE user: " + user.getName());
                                } else {
                                    offlineCount++;
                                    Log.d(TAG, "Added OFFLINE user: " + user.getName());
                                }
                            } else {
                                if(user.getUserId() == null) {
                                    Log.w(TAG, "Skipped user with null ID");
                                } else {
                                    Log.d(TAG, "Skipped current user: " + user.getName());
                                }
                            }
                        }

                        usersAdapter.notifyDataSetChanged();
                        Log.d(TAG, "Updated adapter with " + usersList.size() + " users (" + onlineCount + " online, " + offlineCount + " offline)");

                        updateTitle(onlineCount, usersList.size());

                        if (usersList.isEmpty()) {
                            showNoUsersMessage();
                        }
                    } else {
                        showNoUsersMessage();
                        Log.d(TAG, "No users found in Firestore");
                    }
                });
    }

    private void updateTitle(int onlineCount, int totalCount) {
        if (titleText != null) {
            titleText.setText("All Users (" + onlineCount + " online)");
        }
    }

    private void showNoUsersMessage() {
        Log.d(TAG, "No users available");
        Toast.makeText(this, "No users found", Toast.LENGTH_SHORT).show();
    }

    private void onUserItemClick(User user) {
        Log.d(TAG, "User clicked: " + user.getName() + " (Online: " + user.isOnline() + ")");

        if (!user.isOnline()) {
            Toast.makeText(this, user.getName() + " is currently offline. They will see your message when they come online.", Toast.LENGTH_LONG).show();
        }

        createOrGetChat(user);
    }

    private void createOrGetChat(User otherUser) {
        String currentUserId = getCurrentUserId();
        if (currentUserId == null) return;

        String chatId = generateChatId(currentUserId, otherUser.getUserId());

        Log.d(TAG, "Creating/getting chat: " + chatId + " with user: " + otherUser.getName());

        db.collection("chats").document(chatId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        DocumentSnapshot document = task.getResult();
                        if (document.exists()) {
                            Log.d(TAG, "Chat already exists, opening...");
                            openChat(chatId, otherUser.getName(), otherUser.getUserId());
                        } else {
                            Log.d(TAG, "Creating new chat...");
                            createNewChat(chatId, currentUserId, otherUser);
                        }
                    } else {
                        Log.e(TAG, "Error checking chat existence: ", task.getException());
                    }
                });
    }

    private String generateChatId(String userId1, String userId2) {
        String[] userIds = {userId1, userId2};
        Arrays.sort(userIds);
        return userIds[0] + "_" + userIds[1];
    }

    private void createNewChat(String chatId, String currentUserId, User otherUser) {
        Map<String, Object> chatData = new HashMap<>();
        chatData.put("chatId", chatId);
        chatData.put("participants", Arrays.asList(currentUserId, otherUser.getUserId()));
        chatData.put("userName", otherUser.getName());
        chatData.put("lastMessage", "Chat started");
        chatData.put("price", "$0");
        chatData.put("lastMessageTimestamp", FieldValue.serverTimestamp());
        chatData.put("unreadCount", 0);
        chatData.put("createdAt", FieldValue.serverTimestamp());

        db.collection("chats").document(chatId)
                .set(chatData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "New chat created: " + chatId);
                    openChat(chatId, otherUser.getName(), otherUser.getUserId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error creating chat: ", e);
                    Toast.makeText(ChatInboxActivity.this, "Error creating chat", Toast.LENGTH_SHORT).show();
                });
    }

    private void openChat(String chatId, String userName, String userId) {
        Intent intent = new Intent(this, ChatConversationActivity.class);
        intent.putExtra("chatId", chatId);
        intent.putExtra("userName", userName);
        intent.putExtra("userId", userId);
        startActivity(intent);
    }

    private String getCurrentUserId() {
        if (auth.getCurrentUser() != null) {
            return auth.getCurrentUser().getUid();
        }
        return null;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (currentUserRef != null) {
            Map<String, Object> userData = new HashMap<>();
            userData.put("isOnline", false);
            userData.put("lastSeen", FieldValue.serverTimestamp());
            currentUserRef.update(userData);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentUserRef != null) {
            Map<String, Object> userData = new HashMap<>();
            userData.put("isOnline", true);
            userData.put("lastSeen", FieldValue.serverTimestamp());
            currentUserRef.update(userData);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (usersListener != null) {
            usersListener.remove();
        }

        if (currentUserRef != null) {
            Map<String, Object> userData = new HashMap<>();
            userData.put("isOnline", false);
            userData.put("lastSeen", FieldValue.serverTimestamp());
            currentUserRef.update(userData);
        }
    }
}