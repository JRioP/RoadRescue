package fourthyear.roadrescue;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;  // Import this
import android.text.TextWatcher; // Import this
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView; // Import this
import android.widget.TextView; // Import this
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.auth.FirebaseUser;

import java.util.concurrent.TimeUnit;

public class VerifyPhone extends AppCompatActivity {

    EditText digitNumberOne, digitNumberTwo, digitNumberThree, digitNumberFour, digitNumberFive, digitNumberSix;

    Button verifyBtn;
    TextView resendBtn;
    TextView didNotReceiveText;
    TextView phoneNumberText;
    ImageView backButton;

    FirebaseAuth fAuth;
    PhoneAuthProvider.ForceResendingToken token;
    String verificationId;

    String phone;

    PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verify_phone);
        Intent data = getIntent();
        phone = data.getStringExtra("phone");

        fAuth = FirebaseAuth.getInstance();


        digitNumberOne = findViewById(R.id.digit_number_one);
        digitNumberTwo = findViewById(R.id.digit_number_two);
        digitNumberThree = findViewById(R.id.digit_number_three);
        digitNumberFour = findViewById(R.id.digit_number_four);
        digitNumberFive = findViewById(R.id.digit_number_five);
        digitNumberSix = findViewById(R.id.digit_number_six);

        verifyBtn = findViewById(R.id.button_next); // Was verify_btn
        resendBtn = findViewById(R.id.button_get_new_code); // Was resend_button

        didNotReceiveText = findViewById(R.id.text_did_not_receive);
        phoneNumberText = findViewById(R.id.text_phone_number);
        backButton = findViewById(R.id.back_button);

        phoneNumberText.setText(phone);
        backButton.setOnClickListener(v -> finish());
        setupTextWatchers();

        verifyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String otp = digitNumberOne.getText().toString() +
                        digitNumberTwo.getText().toString() + digitNumberThree.getText().toString() +
                        digitNumberFour.getText().toString() + digitNumberFive.getText().toString() +
                        digitNumberSix.getText().toString();

                if (otp.length() == 6) {
                    if (verificationId != null) {
                        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, otp);
                        verifyAuthentication(credential);
                    } else {
                        Toast.makeText(VerifyPhone.this, "Verification ID is missing.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(VerifyPhone.this, "Please enter all 6 digits of the OTP.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            @Override
            public void onCodeSent(@NonNull String s, @NonNull PhoneAuthProvider.ForceResendingToken forceResendingToken) {
                super.onCodeSent(s, forceResendingToken);
                verificationId = s;
                token = forceResendingToken;

                resendBtn.setVisibility(View.GONE);
                didNotReceiveText.setVisibility(View.GONE);
                Toast.makeText(VerifyPhone.this, "OTP Sent to " + phone, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCodeAutoRetrievalTimeOut(@NonNull String s) {
                super.onCodeAutoRetrievalTimeOut(s);

                resendBtn.setVisibility(View.VISIBLE);
                didNotReceiveText.setVisibility(View.VISIBLE);
                Toast.makeText(VerifyPhone.this, "OTP Auto-retrieval timed out.", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                verifyAuthentication(credential);

                resendBtn.setVisibility(View.GONE);
                didNotReceiveText.setVisibility(View.GONE);
            }

            @Override
            public void onVerificationFailed(@NonNull FirebaseException e) {
                Toast.makeText(VerifyPhone.this, "OTP Verification Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                Log.e("VerifyPhone", "Verification Failed: " + e.getMessage());
            }
        };

        sendOTP(phone);

        resendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (token != null) {
                    resendOTP(phone);
                } else {
                    Toast.makeText(VerifyPhone.this, "Cannot resend OTP. Please try again.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public void sendOTP(String phoneNumber) {
        PhoneAuthProvider.getInstance().verifyPhoneNumber(phoneNumber,
                60, // Timeout duration
                TimeUnit.SECONDS,
                this,
                mCallbacks);
    }

    public void resendOTP(String phoneNumber) {
        PhoneAuthProvider.getInstance().verifyPhoneNumber(phoneNumber,
                60, // Timeout duration
                TimeUnit.SECONDS,
                this,
                mCallbacks,
                token); // Use the forceResendingToken for resending
        Toast.makeText(VerifyPhone.this, "Resending OTP to " + phoneNumber, Toast.LENGTH_SHORT).show();

        // --- MODIFIED: Hide both TextViews again ---
        resendBtn.setVisibility(View.GONE);
        didNotReceiveText.setVisibility(View.GONE);
    }

    private void setupTextWatchers() {
        TextWatcher textWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                boolean allFieldsFilled = !digitNumberOne.getText().toString().isEmpty() &&
                        !digitNumberTwo.getText().toString().isEmpty() &&
                        !digitNumberThree.getText().toString().isEmpty() &&
                        !digitNumberFour.getText().toString().isEmpty() &&
                        !digitNumberFive.getText().toString().isEmpty() &&
                        !digitNumberSix.getText().toString().isEmpty();

                verifyBtn.setEnabled(allFieldsFilled);

                if (allFieldsFilled) {
                    verifyBtn.setBackgroundColor(getResources().getColor(R.color.blue)); // Use your app's color
                } else {
                    verifyBtn.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
                }

                if (s.length() == 1) {
                    if (digitNumberOne.isFocused()) {
                        digitNumberTwo.requestFocus();
                    } else if (digitNumberTwo.isFocused()) {
                        digitNumberThree.requestFocus();
                    } else if (digitNumberThree.isFocused()) {
                        digitNumberFour.requestFocus();
                    } else if (digitNumberFour.isFocused()) {
                        digitNumberFive.requestFocus();
                    } else if (digitNumberFive.isFocused()) {
                        digitNumberSix.requestFocus();
                    }
                }
            }
        };

        digitNumberOne.addTextChangedListener(textWatcher);
        digitNumberTwo.addTextChangedListener(textWatcher);
        digitNumberThree.addTextChangedListener(textWatcher);
        digitNumberFour.addTextChangedListener(textWatcher);
        digitNumberFive.addTextChangedListener(textWatcher);
        digitNumberSix.addTextChangedListener(textWatcher);
    }


    public void verifyAuthentication(PhoneAuthCredential credential) {
        FirebaseUser currentUser = fAuth.getCurrentUser();
        if (currentUser != null) {
            currentUser.linkWithCredential(credential).addOnSuccessListener(new OnSuccessListener<AuthResult>() {
                @Override
                public void onSuccess(AuthResult authResult) {
                    Toast.makeText(VerifyPhone.this, "Phone Verified!", Toast.LENGTH_SHORT).show();


                    Intent intent = new Intent(VerifyPhone.this, ProfileSetupActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(VerifyPhone.this, "Failed to link phone: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e("VerifyPhone", "Failed to link phone: " + e.getMessage());
                }
            });
        } else {
            Toast.makeText(VerifyPhone.this, "No user currently signed in to link phone to.", Toast.LENGTH_LONG).show();
            Log.e("VerifyPhone", "No current user to link phone credential.");
        }
    }
}