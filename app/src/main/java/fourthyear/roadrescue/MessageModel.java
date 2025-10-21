package fourthyear.roadrescue;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.ServerTimestamp;

public class MessageModel {
    private String messageId;
    private String senderId;
    private String senderName;
    private String text;
    private @ServerTimestamp Timestamp timestamp;
    private String messageType; // "text", "image", "location"

    public MessageModel() {}

    public MessageModel(String messageId, String senderId, String senderName, String text) {
        this.messageId = messageId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.text = text;
        this.messageType = "text";
    }

    // Getters and setters
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }


}