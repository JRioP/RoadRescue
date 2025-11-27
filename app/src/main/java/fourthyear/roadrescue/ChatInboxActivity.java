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
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.recyclerview.widget.ItemTouchHelper;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import com.google.firebase.firestore.FieldValue;

public class ChatInboxActivity extends AppCompatActivity {

    private static final String TAG = "ChatInboxActivity";

    private RecyclerView chatsRecyclerView;
    private ChatInboxAdapter chatInboxAdapter;
    private List<ChatInboxItem> chatList;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private ListenerRegistration chatsListener;
    private ListenerRegistration unreadListener;
    private ListenerRegistration notificationListener;

    private String currentUserId;

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
        setupFirestoreListener();
        setupUnreadMessageListener();
        setupNotificationListener();
        setupNavbar();
    }

    private void setupViews() {
        ImageView backButton = findViewById(R.id.backButton);
        if (backButton != null) backButton.setOnClickListener(v -> finish());

        TextView titleText = findViewById(R.id.title);
        if (titleText != null) titleText.setText("Messages");

        unreadBadge = findViewById(R.id.unread_message_badge);
        unreadNotificationBadge = findViewById(R.id.unread_notification_badge);
    }

    private void initializeRecyclerView() {
        chatList = new ArrayList<>();
        chatsRecyclerView = findViewById(R.id.usersRecyclerView);

        chatInboxAdapter = new ChatInboxAdapter(this, chatList, chat -> {
            if (chat.getChatId() == null) return;

            Intent intent = new Intent(this, ChatConversationActivity.class);
            intent.putExtra("chatId", chat.getChatId());

            // ... (Your existing intent extra logic for names) ...
            String otherUserName = "Chat";
            if (chat.getParticipantNames() != null) {
                for (Map.Entry<String, String> entry : chat.getParticipantNames().entrySet()) {
                    if (!entry.getKey().equals(currentUserId)) {
                        otherUserName = entry.getValue();
                        break;
                    }
                }
            }

            String otherUserId = null;
            if (chat.getParticipantIds() != null) {
                for (String id : chat.getParticipantIds()) {
                    if (!id.equals(currentUserId)) {
                        otherUserId = id;
                        break;
                    }
                }
            }

            intent.putExtra("receiverName", otherUserName);
            intent.putExtra("receiverId", otherUserId);
            startActivity(intent);
        });

        chatsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatsRecyclerView.setAdapter(chatInboxAdapter);

        // --- ADDED: SWIPE TO DELETE FUNCTIONALITY ---
        setupSwipeToDelete();
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

                            // Verify and Fix Names
                            fixMissingNames(chat, doc);

                            chatList.add(chat);
                        }
                        chatInboxAdapter.notifyDataSetChanged();
                    }
                });
    }

    private void fixMissingNames(ChatInboxItem chat, DocumentSnapshot doc) {
        List<String> ids = (List<String>) doc.get("participantIds");
        Map<String, String> currentNames = chat.getParticipantNames();

        if (ids != null) {
            for (String id : ids) {
                if (!id.equals(currentUserId)) {
                    String savedName = (currentNames != null) ? currentNames.get(id) : null;
                    if (savedName == null ||
                            savedName.trim().isEmpty() ||
                            savedName.equals("RoadRescue User") ||
                            savedName.equals("Chat")) {
                        fetchUserName(id, chat);
                    }
                }
            }
        }
    }

    private void fetchUserName(String userId, ChatInboxItem chatItem) {
        db.collection("users").document(userId).get().addOnSuccessListener(userDoc -> {
            if (userDoc.exists()) {
                // Prioritize "fullName" as per your request
                String name = userDoc.getString("fullName");

                // Fallback to "name" if fullName is empty
                if (name == null || name.isEmpty()) {
                    name = userDoc.getString("name");
                }

                // Fallback to "RoadRescue User" only if absolutely nothing exists
                if (name == null || name.isEmpty()) {
                    name = "RoadRescue User";
                }

                // Only update if the fetched name is different from what we currently display
                Map<String, String> namesMap = chatItem.getParticipantNames();
                if (namesMap == null) namesMap = new HashMap<>();

                String currentStoredName = namesMap.get(userId);

                // Update if the name changed (e.g., from "RoadRescue User" to "Joshua Alnie Rio")
                if (!name.equals(currentStoredName)) {

                    namesMap.put(userId, name);

                    // Ensure 'You' is present for the current user
                    if (!namesMap.containsKey(currentUserId)) {
                        namesMap.put(currentUserId, "You");
                    }

                    chatItem.setParticipantNames(namesMap);

                    // 1. Update UI Immediately
                    int index = chatList.indexOf(chatItem);
                    if (index != -1) {
                        chatInboxAdapter.notifyItemChanged(index);
                    } else {
                        chatInboxAdapter.notifyDataSetChanged();
                    }

                    // 2. Save the fixed name to Firestore so we don't have to fetch it next time
                    Map<String, Object> updateData = new HashMap<>();
                    updateData.put("participantNames", namesMap);

                    db.collection("chats").document(chatItem.getChatId())
                            .set(updateData, SetOptions.merge());
                }
            }
        });
    }

    // ... [Keep your existing setupNavbar, setupUnreadMessageListener, onDestroy methods] ...
    private void setupUnreadMessageListener() {
        if (currentUserId == null) return;
        unreadListener = db.collection("chats").whereArrayContains("participantIds", currentUserId).whereEqualTo("status", "active").addSnapshotListener((snapshots, e) -> {
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

    private void setupNotificationListener() {
        if (currentUserId == null) return;
        Query badgeQuery = db.collection("notifications").whereEqualTo("userId", currentUserId).whereEqualTo("read", false);
        if (notificationListener != null) notificationListener.remove();
        notificationListener = badgeQuery.addSnapshotListener((snapshots, e) -> {
            if (e != null) return;
            boolean hasUnread = snapshots != null && !snapshots.isEmpty();
            if (unreadNotificationBadge != null) unreadNotificationBadge.setVisibility(hasUnread ? View.VISIBLE : View.GONE);
        });
    }

    private void setupNavbar() {
        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        if(notificationButton != null) notificationButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, NotificationsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });
        // ... (Rest of your navbar code) ...
        ImageView profileButton = findViewById(R.id.profile_icon_btn);
        if(profileButton != null) profileButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProfileActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        });
        ImageView homeButton = findViewById(R.id.home_icon_btn);
        if(homeButton != null) homeButton.setOnClickListener(v -> {
            if (auth.getCurrentUser() == null) { startActivity(new Intent(this, MainActivity.class)); return; }
            db.collection("users").document(auth.getCurrentUser().getUid()).get().addOnSuccessListener(doc -> {
                String type = doc.getString("userType");
                Intent intent = new Intent(this, (type != null && (type.equalsIgnoreCase("Service Provider") || type.equalsIgnoreCase("driver"))) ? ServiceProviderHomepage.class : homepage.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            });
        });
        ConstraintLayout messageLayout = findViewById(R.id.nav_message_layout);
        if (messageLayout != null) messageLayout.setBackgroundResource(R.drawable.rounded_white_background);
        ImageView messageIcon = findViewById(R.id.message_icon_btn);
        if (messageIcon != null) messageIcon.setColorFilter(Color.BLACK);
        TextView messageText = findViewById(R.id.message_text);
        if (messageText != null) {
            messageText.setTextColor(Color.BLACK);
            messageText.setTypeface(null, Typeface.BOLD);
        }
    }private void setupSwipeToDelete() {
        ItemTouchHelper.SimpleCallback simpleItemTouchCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

            // We don't need move (drag and drop), so return false
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            // Handle the swipe
            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                ChatInboxItem chatToDelete = chatList.get(position);

                // Show confirmation dialog before deleting
                new AlertDialog.Builder(ChatInboxActivity.this)
                        .setTitle("Delete Chat")
                        .setMessage("Are you sure you want to remove this chat from your inbox?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            deleteChatFromFirestore(chatToDelete);
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> {
                            // If cancelled, refresh the adapter to bring the item back visually
                            chatInboxAdapter.notifyItemChanged(position);
                        })
                        .setCancelable(false)
                        .show();
            }

            // OPTIONAL: Draw a Red Background with Trash Icon while swiping
            @Override
            public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);

                Drawable icon = ContextCompat.getDrawable(ChatInboxActivity.this, android.R.drawable.ic_menu_delete); // Or your own R.drawable.ic_trash
                ColorDrawable background = new ColorDrawable(Color.RED);

                View itemView = viewHolder.itemView;
                int backgroundCornerOffset = 20;

                if (dX < 0) { // Swiping to the Left
                    background.setBounds(itemView.getRight() + ((int) dX) - backgroundCornerOffset,
                            itemView.getTop(), itemView.getRight(), itemView.getBottom());
                } else { // No Swipe or Right Swipe
                    background.setBounds(0, 0, 0, 0);
                }
                background.draw(c);

                if (icon != null && dX < 0) {
                    int iconMargin = (itemView.getHeight() - icon.getIntrinsicHeight()) / 2;
                    int iconTop = itemView.getTop() + (itemView.getHeight() - icon.getIntrinsicHeight()) / 2;
                    int iconBottom = iconTop + icon.getIntrinsicHeight();
                    int iconLeft = itemView.getRight() - iconMargin - icon.getIntrinsicWidth();
                    int iconRight = itemView.getRight() - iconMargin;

                    icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
                    icon.draw(c);
                }
            }
        };

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(simpleItemTouchCallback);
        itemTouchHelper.attachToRecyclerView(chatsRecyclerView);
    }

    private void deleteChatFromFirestore(ChatInboxItem chat) {
        if (chat.getChatId() == null) return;

        // We remove the current user's ID from the participantIds array.
        // Since your main query filters by 'whereArrayContains("participantIds", currentUserId)',
        // removing the ID will effectively hide it from this list.

        db.collection("chats").document(chat.getChatId())
                .update("participantIds", FieldValue.arrayRemove(currentUserId))
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Chat deleted", Toast.LENGTH_SHORT).show();
                    // The SnapshotListener will automatically update the UI, so we don't need to manually remove it from the list here
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to delete chat", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Error deleting chat", e);
                    // Refresh to show the item again if it failed
                    chatInboxAdapter.notifyDataSetChanged();
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (chatsListener != null) chatsListener.remove();
        if (unreadListener != null) unreadListener.remove();
        if (notificationListener != null) notificationListener.remove();
    }
}