package fourthyear.roadrescue;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ChatConversationFragment extends Fragment {

    private static final String TAG = "ChatConversation";

    // UI Elements
    private ImageButton attachButton;
    private RecyclerView messagesRecyclerView;
    private MessageAdapter messageAdapter;
    private List<MessageModel> messageList;
    private EditText messageInput;
    private ImageButton sendButton;
    private TextView userNameText;

    // Note: Removed unreadBadge and unreadNotificationBadge as they belong to the BottomNav

    // Firebase
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private StorageReference storageReference;
    private ListenerRegistration messagesListener;
    private ListenerRegistration chatStatusListener;

    // Chat Data
    private String chatId;
    private String otherUserName;
    private String receiverId;
    private boolean isChatClosed = false;

    // Image Picker Launcher
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    public ChatConversationFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize Firebase
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();
        storageReference = FirebaseStorage.getInstance().getReference("chat_images");

        // Register the Image Picker
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            uploadImageToFirebase(imageUri);
                        }
                    }
                }
        );
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Ensure this XML file exists and matches your layout
        return inflater.inflate(R.layout.activity_chat_conversation, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (currentUser == null) {
            return;
        }

        // 1. Get Data from Arguments
        Bundle args = getArguments();
        if (args != null) {
            chatId = args.getString("chatId");
            otherUserName = args.getString("receiverName");
            receiverId = args.getString("receiverId");
        } else if (requireActivity().getIntent() != null) {
            chatId = requireActivity().getIntent().getStringExtra("chatId");
            otherUserName = requireActivity().getIntent().getStringExtra("receiverName");
            receiverId = requireActivity().getIntent().getStringExtra("receiverId");
        }

        if (chatId == null || chatId.isEmpty()) {
            Toast.makeText(requireContext(), "Error: Could not open chat.", Toast.LENGTH_SHORT).show();
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
            return;
        }

        // 2. Calculate Receiver ID if missing
        if (receiverId == null) {
            String currentUid = currentUser.getUid();
            String[] parts = chatId.split("_");
            for (String part : parts) {
                if (!part.equals(currentUid) && part.length() > 15) {
                    receiverId = part;
                    break;
                }
            }
        }

        setupToolbar(view);
        initializeRecyclerView(view);
        setupViews(view);

        // 4. Setup Listeners
        setupFirestoreListener();
        setupChatStatusListener();

        resetUnreadCount();

        loadReceiverDetails();
    }

    private void setupToolbar(View view) {
        ImageView backButton = view.findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        }

        userNameText = view.findViewById(R.id.userNameText);
        if (userNameText != null) {
            userNameText.setText(otherUserName != null ? otherUserName : "Chat");
        }
    }

    private void initializeRecyclerView(View view) {
        messageList = new ArrayList<>();
        messagesRecyclerView = view.findViewById(R.id.messagesRecyclerView);
        messageAdapter = new MessageAdapter(messageList, getCurrentUserId());
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        layoutManager.setStackFromEnd(true);
        messagesRecyclerView.setLayoutManager(layoutManager);
        messagesRecyclerView.setAdapter(messageAdapter);
    }

    private void setupViews(View view) {
        messageInput = view.findViewById(R.id.messageInput);
        sendButton = view.findViewById(R.id.sendButton);
        sendButton.setOnClickListener(v -> sendMessage());
        attachButton = view.findViewById(R.id.attachButton);
        attachButton.setOnClickListener(v -> openFileChooser());
    }

    private void loadReceiverDetails() {
        if (receiverId == null || receiverId.isEmpty()) return;

        db.collection("users").document(receiverId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!isAdded()) return;
                    if (documentSnapshot.exists()) {
                        String fullName = documentSnapshot.getString("name");
                        if (fullName != null && !fullName.isEmpty()) {
                            otherUserName = fullName;
                            if (userNameText != null) {
                                userNameText.setText(fullName);
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
                    if (e != null || !isAdded()) return;
                    if (snapshot != null && snapshot.exists()) {
                        String status = snapshot.getString("status");
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

    private void openFileChooser() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        imagePickerLauncher.launch(intent);
    }

    private void uploadImageToFirebase(Uri imageUri) {
        Toast.makeText(requireContext(), "Uploading image...", Toast.LENGTH_SHORT).show();

        final StorageReference fileReference = storageReference.child(System.currentTimeMillis() + ".jpg");

        fileReference.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> fileReference.getDownloadUrl()
                        .addOnSuccessListener(uri -> {
                            String imageUrl = uri.toString();
                            sendImageMessage(imageUrl);
                        }))
                .addOnFailureListener(e -> Toast.makeText(requireContext(), "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    private void sendImageMessage(String imageUrl) {
        if (isChatClosed) {
            Toast.makeText(requireContext(), "This session is closed.", Toast.LENGTH_SHORT).show();
            return;
        }

        String messageId = UUID.randomUUID().toString();
        String currentUserId = getCurrentUserId();
        String currentUserName = getCurrentUserName();

        MessageModel message = new MessageModel();
        message.setMessageId(messageId);
        message.setSenderId(currentUserId);
        message.setSenderName(currentUserName);
        message.setTimestamp(Timestamp.now());
        message.setImageUrl(imageUrl);
        message.setText("");

        db.collection("chats").document(chatId)
                .collection("messages")
                .document(messageId)
                .set(message)
                .addOnSuccessListener(aVoid -> {
                    updateLastMessageAndUnreadCount("[Image]");
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error sending image message: ", e));
    }

    private void sendMessage() {
        if (isChatClosed) {
            Toast.makeText(requireContext(), "This session is closed.", Toast.LENGTH_SHORT).show();
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
        if (isChatClosed) return;

        Map<String, Object> baseUpdates = new HashMap<>();
        baseUpdates.put("lastMessage", lastMessage);
        baseUpdates.put("lastMessageTimestamp", FieldValue.serverTimestamp());
        baseUpdates.put("status", "active");

        if (receiverId != null && !receiverId.isEmpty()) {
            baseUpdates.put("participantIds", Arrays.asList(getCurrentUserId(), receiverId));

            if (otherUserName != null && !otherUserName.equals("Chat") && !otherUserName.equals("RoadRescue User")) {
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
                    if (error != null || value == null || !isAdded()) return;
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
    public void onDestroyView() {
        super.onDestroyView();
        // Remove listeners to prevent memory leaks
        if (messagesListener != null) messagesListener.remove();
        if (chatStatusListener != null) chatStatusListener.remove();
    }
}