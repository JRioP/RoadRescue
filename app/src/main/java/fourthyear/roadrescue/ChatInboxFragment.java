package fourthyear.roadrescue;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
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

public class ChatInboxFragment extends Fragment {

    private static final String TAG = "ChatInboxFragment";

    private RecyclerView chatsRecyclerView;
    private ChatInboxAdapter chatInboxAdapter;
    private List<ChatInboxItem> chatList;

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration chatsListener;
    private String currentUserId;

    public ChatInboxFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Ensure this layout exists (e.g., activity_chat_inbox.xml)
        return inflater.inflate(R.layout.activity_chat_inbox, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(requireContext(), "You must be logged in.", Toast.LENGTH_SHORT).show();
            return;
        }
        currentUserId = currentUser.getUid();

        setupViews(view);
        initializeRecyclerView(view);
        setupFirestoreListener();
    }

    private void setupViews(View view) {
        ImageView backButton = view.findViewById(R.id.backButton);
        if (backButton != null) {
            backButton.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        }

        TextView titleText = view.findViewById(R.id.title);
        if (titleText != null) titleText.setText("Messages");
    }

    private void initializeRecyclerView(View view) {
        chatList = new ArrayList<>();
        chatsRecyclerView = view.findViewById(R.id.usersRecyclerView);

        chatInboxAdapter = new ChatInboxAdapter(requireContext(), chatList, chat -> {
            if (chat.getChatId() == null) return;

            // --- NAVIGATION LOGIC START ---
            // Prepare the fragment arguments
            ChatConversationFragment conversationFragment = new ChatConversationFragment();
            Bundle args = new Bundle();
            args.putString("chatId", chat.getChatId());

            // Determine other user's name
            String otherUserName = "Chat";
            if (chat.getParticipantNames() != null) {
                for (Map.Entry<String, String> entry : chat.getParticipantNames().entrySet()) {
                    if (!entry.getKey().equals(currentUserId)) {
                        otherUserName = entry.getValue();
                        break;
                    }
                }
            }
            args.putString("receiverName", otherUserName);

            // Determine other user's ID
            String otherUserId = null;
            if (chat.getParticipantIds() != null) {
                for (String id : chat.getParticipantIds()) {
                    if (!id.equals(currentUserId)) {
                        otherUserId = id;
                        break;
                    }
                }
            }
            args.putString("receiverId", otherUserId);

            conversationFragment.setArguments(args);

            // Perform Fragment Transaction
            if (getParentFragmentManager() != null) {
                getParentFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, conversationFragment) // Ensure this ID matches your Activity's container
                        .addToBackStack(null)
                        .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                        .commit();
            }
            // --- NAVIGATION LOGIC END ---
        });

        chatsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        chatsRecyclerView.setAdapter(chatInboxAdapter);
    }

    private void setupFirestoreListener() {
        if (currentUserId == null) return;

        chatsListener = db.collection("chats")
                .whereArrayContains("participantIds", currentUserId)
                .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (!isAdded()) return;

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
            if (!isAdded()) return;

            if (userDoc.exists()) {
                String name = userDoc.getString("fullName");
                if (name == null || name.isEmpty()) name = userDoc.getString("name");
                if (name == null || name.isEmpty()) name = "RoadRescue User";

                Map<String, String> namesMap = chatItem.getParticipantNames();
                if (namesMap == null) namesMap = new HashMap<>();

                String currentStoredName = namesMap.get(userId);

                if (!name.equals(currentStoredName)) {
                    namesMap.put(userId, name);
                    if (!namesMap.containsKey(currentUserId)) {
                        namesMap.put(currentUserId, "You");
                    }
                    chatItem.setParticipantNames(namesMap);

                    int index = chatList.indexOf(chatItem);
                    if (index != -1) chatInboxAdapter.notifyItemChanged(index);
                    else chatInboxAdapter.notifyDataSetChanged();

                    Map<String, Object> updateData = new HashMap<>();
                    updateData.put("participantNames", namesMap);
                    db.collection("chats").document(chatItem.getChatId())
                            .set(updateData, SetOptions.merge());
                }
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (chatsListener != null) chatsListener.remove();
    }
}