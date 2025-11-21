package fourthyear.roadrescue;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log; // Added Log
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class PaymentReceiptActivity extends AppCompatActivity {

    private static final String TAG = "PaymentReceiptActivity"; // Added TAG

    private TextView referenceIdText;
    private TextView amountPaidText;
    private TextView paymentDateText;
    private TextView paymentMethodText;
    private TextView customerNameText;
    private TextView serviceTypeText;
    private TextView pickupAddressText;
    private TextView destinationAddressText;
    private ImageView closeButton;
    private Button downloadReceiptButton;
    private CardView receiptCardView;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private ListenerRegistration unreadListener;
    private ListenerRegistration notificationListener;
    private TextView unreadBadge;
    private TextView unreadNotificationBadge;

    private String refId = "N/A";
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    saveReceiptImage();
                } else {
                    Toast.makeText(this, "Storage permission is required to save receipt.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_receipt);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        referenceIdText = findViewById(R.id.receipt_reference_id);
        amountPaidText = findViewById(R.id.receipt_amount_paid);
        paymentDateText = findViewById(R.id.receipt_payment_date);
        paymentMethodText = findViewById(R.id.receipt_payment_method);
        customerNameText = findViewById(R.id.receipt_customer_name);
        serviceTypeText = findViewById(R.id.receipt_service_type);
        pickupAddressText = findViewById(R.id.receipt_pickup_address);
        destinationAddressText = findViewById(R.id.receipt_destination_address);
        closeButton = findViewById(R.id.receipt_close_btn);
        downloadReceiptButton = findViewById(R.id.receipt_download_btn);
        receiptCardView = findViewById(R.id.receipt_card_content);

        refId = getIntent().getStringExtra("REFERENCE_ID");
        String amount = getIntent().getStringExtra("AMOUNT_PAID");
        String date = getIntent().getStringExtra("PAYMENT_DATE");
        String method = getIntent().getStringExtra("PAYMENT_METHOD");
        String requestType = getIntent().getStringExtra("REQUEST_TYPE");
        String pickupAddress = getIntent().getStringExtra("PICKUP_ADDRESS");
        String destinationAddress = getIntent().getStringExtra("DESTINATION_ADDRESS");

        referenceIdText.setText(refId != null ? refId : "N/A");
        amountPaidText.setText(amount != null ? amount : "N/A");
        paymentDateText.setText(date != null ? date : getCurrentDateTime());
        paymentMethodText.setText(method != null ? method : "N/A");
        serviceTypeText.setText(requestType != null ? requestType : "N/A");
        pickupAddressText.setText(pickupAddress != null ? pickupAddress : "N/A");
        destinationAddressText.setText(destinationAddress != null ? destinationAddress : "N/A");

        loadCustomerName();

        closeButton.setOnClickListener(v -> finish());
        downloadReceiptButton.setOnClickListener(v -> checkPermissionAndSaveReceipt());
        setupNavbar();
        setupUnreadMessageListener();
        setupNotificationListener();
    }


    private void setupNavbar() {
        unreadBadge = findViewById(R.id.unread_message_badge);
        unreadNotificationBadge = findViewById(R.id.unread_notification_badge);

        ImageView notificationButton = findViewById(R.id.notification_icon_btn);
        if (notificationButton != null) {
            notificationButton.setOnClickListener(v -> {
                Intent intent = new Intent(PaymentReceiptActivity.this, NotificationsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
            });
        }
        ImageView messageButton = findViewById(R.id.message_icon_btn);
        if (messageButton != null) {
            messageButton.setOnClickListener(v -> {
                Intent intent = new Intent(PaymentReceiptActivity.this, ChatInboxActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
            });
        }
        ImageView profileButton = findViewById(R.id.profile_icon_btn);
        if (profileButton != null) {
            profileButton.setOnClickListener(v -> {
                Intent intent = new Intent(PaymentReceiptActivity.this, ProfileActivity.class);
                // --- FIX ADDED HERE ---
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
            });
        }
        ImageView homeButton = findViewById(R.id.home_icon_btn);
        if (homeButton != null) {
            homeButton.setOnClickListener(v -> {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user == null) {
                    Intent intent = new Intent(PaymentReceiptActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                    return;
                }
                db.collection("users").document(user.getUid()).get().addOnSuccessListener(doc -> {
                    Intent intent;
                    String type = doc.getString("userType");
                    if (type != null && (type.equalsIgnoreCase("Service Provider") || type.equalsIgnoreCase("driver"))) {
                        intent = new Intent(PaymentReceiptActivity.this, ServiceProviderHomepage.class);
                    } else {
                        intent = new Intent(PaymentReceiptActivity.this, homepage.class);
                    }
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                });
            });
        }
    }

    private void setupUnreadMessageListener() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;
        String currentUserId = user.getUid();

        unreadListener = db.collection("chats")
                .whereArrayContains("participantIds", currentUserId)
                .whereEqualTo("status", "active")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;
                    int totalUnread = 0;
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            Long count = doc.getLong("unreadCounts." + currentUserId);
                            if (count != null) totalUnread += count;
                        }
                    }
                    if (unreadBadge != null) {
                        unreadBadge.setVisibility(totalUnread > 0 ? View.VISIBLE : View.GONE);
                    }
                });
    }
    private void setupNotificationListener() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;
        String currentUserId = currentUser.getUid();

        Query badgeQuery = db.collection("notifications")
                .whereEqualTo("userId", currentUserId)
                .whereEqualTo("read", false);

        if (notificationListener != null) {
            notificationListener.remove();
        }

        notificationListener = badgeQuery.addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.e(TAG, "Notification listener error", e);
                return;
            }
            boolean hasUnread = snapshots != null && !snapshots.isEmpty();

            if (unreadNotificationBadge != null) {
                if (hasUnread) {
                    unreadNotificationBadge.setVisibility(View.VISIBLE);
                } else {
                    unreadNotificationBadge.setVisibility(View.GONE);
                }
            }
        });
    }

    private void loadCustomerName() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            db.collection("users").document(currentUser.getUid()).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String name = documentSnapshot.getString("name");
                            customerNameText.setText(name != null ? name : "N/A");
                        } else {
                            customerNameText.setText("N/A");
                        }
                    })
                    .addOnFailureListener(e -> {
                        customerNameText.setText("N/A");
                    });
        } else {
            customerNameText.setText("N/A");
        }
    }

    private void checkPermissionAndSaveReceipt() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveReceiptImage();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            saveReceiptImage();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
    }

    private void saveReceiptImage() {
        Bitmap receiptBitmap = getBitmapFromView(receiptCardView);

        if (receiptBitmap != null) {
            saveBitmapToGallery(receiptBitmap);
        } else {
            Toast.makeText(this, "Failed to capture receipt.", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap getBitmapFromView(View view) {
        try {
            Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            view.draw(canvas);
            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void saveBitmapToGallery(Bitmap bitmap) {
        String fileName = "RoadRescue_Receipt_" + (refId != null ? refId : System.currentTimeMillis()) + ".png";

        OutputStream fos;

        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
            } else {
                String picturesDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString();
                values.put(MediaStore.Images.Media.DATA, picturesDirectory + "/" + fileName);
            }

            Uri imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            if (imageUri == null) {
                throw new IOException("Failed to create new MediaStore entry");
            }

            fos = getContentResolver().openOutputStream(imageUri);

            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);

            Objects.requireNonNull(fos).close();

            Toast.makeText(this, "Receipt saved to Pictures", Toast.LENGTH_SHORT).show();

            finish();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error saving receipt: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getCurrentDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault());
        return sdf.format(new Date());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (unreadListener != null) unreadListener.remove();
        if (notificationListener != null) notificationListener.remove();
    }
}