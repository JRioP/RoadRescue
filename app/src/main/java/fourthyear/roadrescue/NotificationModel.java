package fourthyear.roadrescue;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;
import java.lang.Long; // Import Long to be explicit

public class NotificationModel {
    private String title;
    private String message;
    private Long timestamp;

    private String status;

    public NotificationModel() {}

    public NotificationModel(String title, String message, Long timestamp, String status) {
        this.title = title;
        this.message = message;
        this.timestamp = timestamp;
        this.status = status;
    }

    // Getters and Setters

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Long getTimestamp() { return timestamp; }

    // MODIFIED: Parameter type is now Long
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    // --- Existing status methods ---
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
