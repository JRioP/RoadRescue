package fourthyear.roadrescue;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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

// --- ADD FIRESTORE IMPORTS ---
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
// -----------------------------

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

public class PaymentReceiptActivity extends AppCompatActivity {
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
}