package fourthyear.roadrescue;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.ServerTimestamp;

import java.util.List;
import java.util.Map;

public class ChatInboxItem {
    private String chatId;
    private String lastMessage;
    private @ServerTimestamp Timestamp lastMessageTimestamp;
    private List<String> participantIds;
    private Map<String, String> participantNames;
    private String requestRef; // For the request reference text

    public ChatInboxItem() {}

    public String getChatId() { return chatId; }
    public String getLastMessage() { return lastMessage; }
    public Timestamp getLastMessageTimestamp() { return lastMessageTimestamp; }
    public List<String> getParticipantIds() { return participantIds; }
    public Map<String, String> getParticipantNames() { return participantNames; }
    public String getRequestRef() { return requestRef; }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }
    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public void setLastMessageTimestamp(Timestamp lastMessageTimestamp) {
        this.lastMessageTimestamp = lastMessageTimestamp;
    }

    public void setParticipantIds(List<String> participantIds) {
        this.participantIds = participantIds;
    }

    public void setParticipantNames(Map<String, String> participantNames) {
        this.participantNames = participantNames;
    }

    public void setRequestRef(String requestRef) {
        this.requestRef = requestRef;
    }
}
