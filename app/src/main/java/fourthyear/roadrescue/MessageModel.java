package fourthyear.roadrescue;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.ServerTimestamp;

public class MessageModel {
    private String messageId;
    private String senderId;
    private String senderName;
    private String text;
    private @ServerTimestamp Timestamp timestamp;
    private String imageUrl;

    // A no-argument constructor is required for Firestore deserialization
    public MessageModel() {}

    // Constructor for creating a standard text message
    public MessageModel(String messageId, String senderId, String senderName, String text) {
        this.messageId = messageId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.text = text;
        // The imageUrl will be null by default, indicating a text message
    }

    // --- Getters and Setters ---

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }
}