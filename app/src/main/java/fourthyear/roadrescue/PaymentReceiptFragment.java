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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PaymentReceiptFragment extends Fragment {

    private static final String TAG = "PaymentReceiptFragment";

    // UI Elements
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

    // Firebase & Logic
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String refId = "N/A";
    private String serviceProviderId;

    // Permission Launcher
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    saveReceiptImage();
                } else {
                    Toast.makeText(requireContext(), "Storage permission is required to save receipt.", Toast.LENGTH_SHORT).show();
                }
            });

    public PaymentReceiptFragment() {
        // Required empty public constructor
    }

    // Helper to create instance with arguments
    public static PaymentReceiptFragment newInstance(String refId, String amount, String date, String method,
                                                     String requestType, String pickup, String destination, String providerId) {
        PaymentReceiptFragment fragment = new PaymentReceiptFragment();
        Bundle args = new Bundle();
        args.putString("REFERENCE_ID", refId);
        args.putString("AMOUNT_PAID", amount);
        args.putString("PAYMENT_DATE", date);
        args.putString("PAYMENT_METHOD", method);
        args.putString("REQUEST_TYPE", requestType);
        args.putString("PICKUP_ADDRESS", pickup);
        args.putString("DESTINATION_ADDRESS", destination);
        args.putString("SERVICE_PROVIDER_ID", providerId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Ensure XML name is correct
        return inflater.inflate(R.layout.activity_payment_receipt, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Initialize Views
        referenceIdText = view.findViewById(R.id.receipt_reference_id);
        amountPaidText = view.findViewById(R.id.receipt_amount_paid);
        paymentDateText = view.findViewById(R.id.receipt_payment_date);
        paymentMethodText = view.findViewById(R.id.receipt_payment_method);
        customerNameText = view.findViewById(R.id.receipt_customer_name);
        serviceTypeText = view.findViewById(R.id.receipt_service_type);
        pickupAddressText = view.findViewById(R.id.receipt_pickup_address);
        destinationAddressText = view.findViewById(R.id.receipt_destination_address);

        closeButton = view.findViewById(R.id.receipt_close_btn);
        downloadReceiptButton = view.findViewById(R.id.receipt_download_btn);
        receiptCardView = view.findViewById(R.id.receipt_card_content);

        // Retrieve Arguments
        if (getArguments() != null) {
            refId = getArguments().getString("REFERENCE_ID");
            String amount = getArguments().getString("AMOUNT_PAID");
            String date = getArguments().getString("PAYMENT_DATE");
            String method = getArguments().getString("PAYMENT_METHOD");
            String requestType = getArguments().getString("REQUEST_TYPE");
            String pickupAddress = getArguments().getString("PICKUP_ADDRESS");
            String destinationAddress = getArguments().getString("DESTINATION_ADDRESS");
            serviceProviderId = getArguments().getString("SERVICE_PROVIDER_ID");

            // Set Data
            referenceIdText.setText(refId != null ? refId : "N/A");
            amountPaidText.setText(amount != null ? amount : "N/A");
            paymentDateText.setText(date != null ? date : getCurrentDateTime());
            paymentMethodText.setText(method != null ? method : "N/A");
            serviceTypeText.setText(requestType != null ? requestType : "N/A");
            pickupAddressText.setText(pickupAddress != null ? pickupAddress : "N/A");

            TextView destLabel = view.findViewById(R.id.label_destination_address);
            if (requestType != null && requestType.equalsIgnoreCase("Towing")) {
                destinationAddressText.setVisibility(View.VISIBLE);
                destinationAddressText.setText(destinationAddress != null ? destinationAddress : "N/A");
                if (destLabel != null) destLabel.setVisibility(View.VISIBLE);
            } else {
                destinationAddressText.setVisibility(View.GONE);
                if (destLabel != null) destLabel.setVisibility(View.GONE);
            }
        }

        loadCustomerName();

        closeButton.setOnClickListener(v -> checkRatingAndProceed());
        downloadReceiptButton.setOnClickListener(v -> checkPermissionAndSaveReceipt());
    }

    private void checkRatingAndProceed() {
        if (refId == null || refId.equals("N/A")) {
            navigateToHome();
            return;
        }

        db.collection("ratings")
                .whereEqualTo("referenceId", refId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        navigateToHome();
                    } else {
                        goToRatingActivity();
                    }
                })
                .addOnFailureListener(e -> {
                    navigateToHome();
                });
    }

    private void goToRatingActivity() {
        Intent intent = new Intent(requireContext(), RatingActivity.class);
        intent.putExtra("REFERENCE_ID", refId);
        intent.putExtra("SERVICE_PROVIDER_ID", serviceProviderId);
        startActivity(intent);

        // Remove this receipt from backstack so they don't return here
        if (getParentFragmentManager() != null) {
            getParentFragmentManager().popBackStack();
        }
    }

    private void navigateToHome() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            startActivity(new Intent(requireContext(), MainActivity.class));
            if (getActivity() != null) getActivity().finish();
            return;
        }

        db.collection("users").document(user.getUid()).get().addOnSuccessListener(doc -> {
            Intent intent;
            String type = doc.getString("userType");
            if (type != null && (type.equalsIgnoreCase("Service Provider") || type.equalsIgnoreCase("driver"))) {
                intent = new Intent(requireContext(), ServiceProviderHomeFragment.class);
            } else {
                // If you are using a NavigationActivity container for customers
                intent = new Intent(requireContext(), NavigationActivity.class);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            if (getActivity() != null) getActivity().finish();
        });
    }

    private void loadCustomerName() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            db.collection("users").document(currentUser.getUid()).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (!isAdded()) return;
                        if (documentSnapshot.exists()) {
                            String name = documentSnapshot.getString("name");
                            if (customerNameText != null) {
                                customerNameText.setText(name != null ? name : "N/A");
                            }
                        } else {
                            if (customerNameText != null) customerNameText.setText("N/A");
                        }
                    })
                    .addOnFailureListener(e -> {
                        if (customerNameText != null) customerNameText.setText("N/A");
                    });
        } else {
            if (customerNameText != null) customerNameText.setText("N/A");
        }
    }

    private void checkPermissionAndSaveReceipt() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveReceiptImage();
            return;
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            saveReceiptImage();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
    }

    private void saveReceiptImage() {
        if (receiptCardView == null) return;
        Bitmap receiptBitmap = getBitmapFromView(receiptCardView);

        if (receiptBitmap != null) {
            saveBitmapToGallery(receiptBitmap);
        } else {
            Toast.makeText(requireContext(), "Failed to capture receipt.", Toast.LENGTH_SHORT).show();
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

            Uri imageUri = requireContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            if (imageUri == null) {
                throw new IOException("Failed to create new MediaStore entry");
            }

            fos = requireContext().getContentResolver().openOutputStream(imageUri);

            if (fos != null) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.close();
            }

            Toast.makeText(requireContext(), "Receipt saved to Pictures", Toast.LENGTH_SHORT).show();
            checkRatingAndProceed();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "Error saving receipt: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getCurrentDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault());
        return sdf.format(new Date());
    }
}