package fourthyear.roadrescue;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.ServerTimestamp;
import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.Exclude;

public class NotificationModel {
    private String title;
    private String message;
    private String status;

    @ServerTimestamp
    private Timestamp timestamp;
    private Double pickupLat;
    private Double pickupLng;
    private Double destinationLat;
    private Double destinationLng;
    private String pickupAddress;
    private String destinationAddress;
    private String requestType;
    private String providerId;
    private String customerId;
    private String requestId;


    public NotificationModel() {}

    public NotificationModel(String title, String message, Timestamp timestamp, String status) {
        this.title = title;
        this.message = message;
        this.timestamp = timestamp;
        this.status = status;
    }

    // Getters and setters for basic fields
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    // Correct timestamp handling - use Timestamp object
    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    @Exclude
    public Long getTimestampSeconds() {
        return timestamp != null ? timestamp.getSeconds() : null;
    }

    @Exclude
    public Long getTimestampMillis() {
        return timestamp != null ? timestamp.toDate().getTime() : null;
    }

    @Exclude
    public java.util.Date getTimestampAsDate() {
        return timestamp != null ? timestamp.toDate() : null;
    }

    @PropertyName("pickupLat")
    public Double getPickupLat() { return pickupLat; }

    @PropertyName("pickupLat")
    public void setPickupLat(Double pickupLat) { this.pickupLat = pickupLat; }

    @PropertyName("pickupLng")
    public Double getPickupLng() { return pickupLng; }

    @PropertyName("pickupLng")
    public void setPickupLng(Double pickupLng) { this.pickupLng = pickupLng; }

    @PropertyName("destinationLat")
    public Double getDestinationLat() { return destinationLat; }

    @PropertyName("destinationLat")
    public void setDestinationLat(Double destinationLat) { this.destinationLat = destinationLat; }

    @PropertyName("destinationLng")
    public Double getDestinationLng() { return destinationLng; }

    @PropertyName("destinationLng")
    public void setDestinationLng(Double destinationLng) { this.destinationLng = destinationLng; }

    @PropertyName("pickupAddress")
    public String getPickupAddress() { return pickupAddress; }

    @PropertyName("pickupAddress")
    public void setPickupAddress(String pickupAddress) { this.pickupAddress = pickupAddress; }

    @PropertyName("destinationAddress")
    public String getDestinationAddress() { return destinationAddress; }

    @PropertyName("destinationAddress")
    public void setDestinationAddress(String destinationAddress) { this.destinationAddress = destinationAddress; }

    @PropertyName("requestType")
    public String getRequestType() { return requestType; }

    @PropertyName("requestType")
    public void setRequestType(String requestType) { this.requestType = requestType; }

    @PropertyName("providerId")
    public String getProviderId() { return providerId; }

    @PropertyName("providerId")
    public void setProviderId(String providerId) { this.providerId = providerId; }

    @PropertyName("customerId")
    public String getCustomerId() { return customerId; }

    @PropertyName("customerId")
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    // Optional: Helper method to format timestamp for display
    @Exclude
    public String getFormattedTimestamp() {
        if (timestamp != null) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", java.util.Locale.getDefault());
            return sdf.format(timestamp.toDate());
        }
        return "";
    }
}