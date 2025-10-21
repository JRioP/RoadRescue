package fourthyear.roadrescue;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.ServerTimestamp;

public class ChatModel {
    private String chatId;
    private String userId;
    private String userName;
    private String lastMessage;
    private String userProfileImage;
    private String price;
    private @ServerTimestamp Timestamp timestamp;
    private int unreadCount;

    // Empty constructor for Firestore
    public ChatModel() {}

    public ChatModel(String chatId, String userId, String userName, String lastMessage, String price) {
        this.chatId = chatId;
        this.userId = userId;
        this.userName = userName;
        this.lastMessage = lastMessage;
        this.price = price;
        this.unreadCount = 0;
    }

    // Getters and setters
    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public String getUserProfileImage() { return userProfileImage; }
    public void setUserProfileImage(String userProfileImage) { this.userProfileImage = userProfileImage; }

    public String getPrice() { return price; }
    public void setPrice(String price) { this.price = price; }

    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }

    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }
}