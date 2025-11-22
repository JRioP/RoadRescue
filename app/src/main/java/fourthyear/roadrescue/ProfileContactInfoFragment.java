package fourthyear.roadrescue;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfileContactInfoFragment extends Fragment {

    private static final String TAG = "ProfileContactInfoFrag";

    // UI Elements
    private ImageView backButton;
    private TextView textEmail;
    private TextView textPhone;
    private ImageView buttonAdd;
    private ImageView buttonAddSos;

    private LinearLayout secondaryNumbersContainer;
    private LinearLayout sosContactsContainer;

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private FirebaseUser currentUser;
    private DocumentReference userDocRef;

    // Result Launchers for Permissions and Contact Picking
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    launchContactPicker();
                } else {
                    if (!shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
                        showGoToSettingsDialog();
                    } else {
                        Toast.makeText(requireContext(), "Permission is required to select SOS contacts.", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    private final ActivityResultLauncher<Intent> contactPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == -1 && result.getData() != null) { // -1 is RESULT_OK
                    Uri contactUri = result.getData().getData();
                    handleContactSelection(contactUri);
                }
            });

    public ProfileContactInfoFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.activity_profile_contact_info, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(requireContext(), "User not logged in.", Toast.LENGTH_SHORT).show();
            if (getActivity() != null) getActivity().finish();
            return;
        }

        userDocRef = db.collection("users").document(currentUser.getUid());

        initializeViews(view);
        setupClickListeners();
        loadUserData();
    }

    private void initializeViews(View view) {
        backButton = view.findViewById(R.id.back_button);
        textEmail = view.findViewById(R.id.text_email);
        textPhone = view.findViewById(R.id.text_phone);
        buttonAdd = view.findViewById(R.id.button_add);
        buttonAddSos = view.findViewById(R.id.button_add_sos);

        secondaryNumbersContainer = view.findViewById(R.id.secondary_numbers_container);
        sosContactsContainer = view.findViewById(R.id.sos_contacts_container);
    }

    private void setupClickListeners() {
        // Simple back navigation using Dispatcher
        if (backButton != null) {
            backButton.setOnClickListener(v -> requireActivity().getOnBackPressedDispatcher().onBackPressed());
        }

        if (buttonAdd != null) {
            buttonAdd.setOnClickListener(v -> showAddNumberDialog());
        }

        if (buttonAddSos != null) {
            buttonAddSos.setOnClickListener(v -> checkAndRequestContactsPermission());
        }
    }

    private void loadUserData() {
        if (currentUser.getEmail() != null) {
            textEmail.setText(currentUser.getEmail());
        } else {
            textEmail.setText("No email provided");
        }

        String authPhone = currentUser.getPhoneNumber();

        userDocRef.get().addOnSuccessListener(documentSnapshot -> {
            if (!isAdded()) return;

            if (documentSnapshot.exists()) {
                String dbPhone = documentSnapshot.getString("phone");

                if (dbPhone != null && !dbPhone.isEmpty()) {
                    textPhone.setText(dbPhone);
                } else if (authPhone != null && !authPhone.isEmpty()) {
                    textPhone.setText(authPhone);
                } else {
                    textPhone.setText("No phone number added");
                }

                secondaryNumbersContainer.removeAllViews();
                sosContactsContainer.removeAllViews();

                List<String> secondaryPhones = (List<String>) documentSnapshot.get("secondary_phones");
                if (secondaryPhones != null) {
                    displaySecondaryPhones(secondaryPhones);
                }

                List<Map<String, Object>> sosContacts = (List<Map<String, Object>>) documentSnapshot.get("sos_contacts");
                if (sosContacts != null) {
                    displaySosContacts(sosContacts);
                }

            } else {
                textPhone.setText(authPhone != null ? authPhone : "No phone number added");
            }
        }).addOnFailureListener(e -> {
            if (isAdded()) {
                textPhone.setText("Error loading phone");
                Log.e(TAG, "Error loading user data", e);
            }
        });
    }

    private void displaySecondaryPhones(List<String> phones) {
        if (getContext() == null) return;
        for (String phone : phones) {
            TextView tv = new TextView(getContext());
            tv.setText(phone);
            tv.setTextSize(16);
            tv.setPadding(0, 8, 0, 8);
            secondaryNumbersContainer.addView(tv);
        }
    }

    private void displaySosContacts(List<Map<String, Object>> contacts) {
        if (getContext() == null) return;
        for (Map<String, Object> contact : contacts) {
            String name = (String) contact.get("name");
            String phone = (String) contact.get("phone");

            TextView tv = new TextView(getContext());
            tv.setText(name + ": " + phone);
            tv.setTextSize(16);
            tv.setPadding(0, 8, 0, 8);
            sosContactsContainer.addView(tv);
        }
    }

    private void showAddNumberDialog() {
        if (getContext() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Add Secondary Number");

        final EditText input = new EditText(getContext());
        input.setInputType(InputType.TYPE_CLASS_PHONE);
        input.setHint("+63 912 345 6789");
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newNumber = input.getText().toString().trim();
            if (!newNumber.isEmpty()) {
                saveSecondaryNumber(newNumber);
            } else {
                Toast.makeText(requireContext(), "Number cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void saveSecondaryNumber(String newNumber) {
        userDocRef.update("secondary_phones", FieldValue.arrayUnion(newNumber))
                .addOnSuccessListener(aVoid -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Number added!", Toast.LENGTH_SHORT).show();
                        loadUserData();
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void checkAndRequestContactsPermission() {
        String contactPermission = Manifest.permission.READ_CONTACTS;

        if (ContextCompat.checkSelfPermission(requireContext(), contactPermission) == PackageManager.PERMISSION_GRANTED) {
            launchContactPicker();
        } else if (shouldShowRequestPermissionRationale(contactPermission)) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Permission Needed")
                    .setMessage("This app needs permission to read your contacts so you can select an emergency SOS contact.")
                    .setPositiveButton("OK", (dialog, which) -> {
                        requestPermissionLauncher.launch(contactPermission);
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                    .create()
                    .show();
        } else {
            requestPermissionLauncher.launch(contactPermission);
        }
    }

    private void showGoToSettingsDialog() {
        if (getContext() == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Permission Denied")
                .setMessage("You have permanently denied contact permission. To add an SOS contact, you must enable it in the app settings.")
                .setPositiveButton("Go to Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", requireContext().getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .create()
                .show();
    }

    private void launchContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        contactPickerLauncher.launch(intent);
    }

    private void handleContactSelection(Uri contactUri) {
        if (contactUri == null) return;

        String[] contactDetails = getContactDetails(contactUri);
        String contactName = contactDetails[0];
        String contactNumber = contactDetails[1];

        if (contactName != null && contactNumber != null) {
            saveSosContact(contactName, contactNumber);
        } else {
            Toast.makeText(requireContext(), "Could not read contact details.", Toast.LENGTH_SHORT).show();
        }
    }

    private String[] getContactDetails(Uri contactUri) {
        String[] details = new String[2];
        ContentResolver cr = requireContext().getContentResolver();

        try (Cursor cursor = cr.query(contactUri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String id = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID));

                details[0] = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME));

                try (Cursor phoneCursor = cr.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        new String[]{id},
                        null
                )) {
                    if (phoneCursor != null && phoneCursor.moveToFirst()) {
                        details[1] = phoneCursor.getString(phoneCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER));
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error resolving contact", e);
        }
        return details;
    }

    private void saveSosContact(String name, String phone) {
        Map<String, Object> sosContact = new HashMap<>();
        sosContact.put("name", name);
        sosContact.put("phone", phone);

        userDocRef.update("sos_contacts", FieldValue.arrayUnion(sosContact))
                .addOnSuccessListener(aVoid -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), name + " added to SOS contacts!", Toast.LENGTH_SHORT).show();
                        loadUserData();
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}