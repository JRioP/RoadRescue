package fourthyear.roadrescue;

import java.util.Date;

public class NotificationModel {
    private final String id;
    private final String title;
    private final String message;
    private final Date timestamp;
    private boolean isRead;

    public NotificationModel(String id, String title, String message, Date timestamp) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.timestamp = timestamp;
        this.isRead = false;
    }

    // Getters and setters
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public Date getTimestamp() { return timestamp; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
}