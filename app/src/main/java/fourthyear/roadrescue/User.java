package fourthyear.roadrescue;

import com.google.firebase.Timestamp;

// User.java
public class User {
    private String userId;
    private String name;
    private String email;
    private String phone;
    private String userType;
    private boolean isOnline;
    private Timestamp lastSeen;

    // Default constructor (required for Firestore)
    public User() {}

    // Full constructor
    public User(String userId, String name, String email, String phone, String userType, boolean isOnline) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.userType = userType;
        this.isOnline = isOnline;
        this.lastSeen = Timestamp.now();
    }

    // Getters and setters...
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }

    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }

    public Timestamp getLastSeen() { return lastSeen; }
    public void setLastSeen(Timestamp lastSeen) { this.lastSeen = lastSeen; }
}