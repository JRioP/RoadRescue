package fourthyear.roadrescue;

import com.google.firebase.Timestamp;

public class User {
    private String userId;
    private String name;
    private String email;
    private String phone;
    private String userType;
    private boolean isOnline;
    private Timestamp lastSeen;

    private String currentSessionId;
    private String carBrand;
    private String carType;
    private String gender;
    private String carYear;
    private String carModel;

    public User() {}

    public User(String userId, String name, String email, String phone, String userType, boolean isOnline) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.userType = userType;
        this.isOnline = isOnline;
        this.lastSeen = Timestamp.now();
    }

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

    // --- GETTERS AND SETTERS FOR NEW FIELDS ---

    public String getCurrentSessionId() { return currentSessionId; }
    public void setCurrentSessionId(String currentSessionId) { this.currentSessionId = currentSessionId; }

    public String getCarBrand() { return carBrand; }
    public void setCarBrand(String carBrand) { this.carBrand = carBrand; }

    public String getCarType() { return carType; }
    public void setCarType(String carType) { this.carType = carType; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getCarYear() { return carYear; }
    public void setCarYear(String carYear) { this.carYear = carYear; }

    public String getCarModel() { return carModel; }
    public void setCarModel(String carModel) { this.carModel = carModel; }
}