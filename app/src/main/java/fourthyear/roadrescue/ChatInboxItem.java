package fourthyear.roadrescue;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.PropertyName;
import java.util.List;
import java.util.Map;

public class ChatInboxItem {
    private String chatId;
    private String lastMessage;
    private Timestamp lastMessageTimestamp;
    private List<String> participantIds;
    private Map<String, String> participantNames;

    private Map<String, Integer> unreadCounts;
    private String status;
    private String requestId;
    private String requestRef; // FIX: Added this field to match your database

    public ChatInboxItem() {}

    public ChatInboxItem(String chatId, String lastMessage, Timestamp lastMessageTimestamp,
                         List<String> participantIds, Map<String, String> participantNames) {
        this.chatId = chatId;
        this.lastMessage = lastMessage;
        this.lastMessageTimestamp = lastMessageTimestamp;
        this.participantIds = participantIds;
        this.participantNames = participantNames;
    }

    // --- GETTERS AND SETTERS ---

    @PropertyName("participantNames")
    public Map<String, String> getParticipantNames() { return participantNames; }

    @PropertyName("participantNames")
    public void setParticipantNames(Map<String, String> participantNames) { this.participantNames = participantNames; }

    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public Timestamp getLastMessageTimestamp() { return lastMessageTimestamp; }
    public void setLastMessageTimestamp(Timestamp lastMessageTimestamp) { this.lastMessageTimestamp = lastMessageTimestamp; }

    public List<String> getParticipantIds() { return participantIds; }
    public void setParticipantIds(List<String> participantIds) { this.participantIds = participantIds; }

    public Map<String, Integer> getUnreadCounts() { return unreadCounts; }
    public void setUnreadCounts(Map<String, Integer> unreadCounts) { this.unreadCounts = unreadCounts; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    // --- NEW GETTER/SETTER FOR requestRef ---
    public String getRequestRef() { return requestRef; }
    public void setRequestRef(String requestRef) { this.requestRef = requestRef; }
}