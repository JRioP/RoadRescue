package fourthyear.roadrescue;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
import com.google.firebase.auth.FirebaseUser; // Import FirebaseUser

import java.util.concurrent.TimeUnit;

public class VerifyPhone extends AppCompatActivity {

    EditText digitNumberOne, digitNumberTwo, digitNumberThree, digitNumberFour, digitNumberFive, digitNumberSix;
    Button verifyBtn, resendBtn;

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

        verifyBtn = findViewById(R.id.verify_btn);
        resendBtn = findViewById(R.id.resend_button);

        verifyBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean allDigitsValid = true;
                if (!validateDigitField(digitNumberOne)) allDigitsValid = false;
                if (!validateDigitField(digitNumberTwo)) allDigitsValid = false;
                if (!validateDigitField(digitNumberThree)) allDigitsValid = false;
                if (!validateDigitField(digitNumberFour)) allDigitsValid = false;
                if (!validateDigitField(digitNumberFive)) allDigitsValid = false;
                if (!validateDigitField(digitNumberSix)) allDigitsValid = false;

                if (allDigitsValid) {
                    String otp = digitNumberOne.getText().toString() +
                            digitNumberTwo.getText().toString() + digitNumberThree.getText().toString() +
                            digitNumberFour.getText().toString() + digitNumberFive.getText().toString() +
                            digitNumberSix.getText().toString();

                    if (verificationId != null && !otp.isEmpty()) {
                        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, otp);
                        verifyAuthentication(credential);
                    } else {
                        Toast.makeText(VerifyPhone.this, "Verification ID or OTP is missing.", Toast.LENGTH_SHORT).show();
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
                resendBtn.setVisibility(View.GONE); // Hide resend button initially
                Toast.makeText(VerifyPhone.this, "OTP Sent to " + phone, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCodeAutoRetrievalTimeOut(@NonNull String s) {
                super.onCodeAutoRetrievalTimeOut(s);
                resendBtn.setVisibility(View.VISIBLE); // Show resend button if auto-retrieval times out
                Toast.makeText(VerifyPhone.this, "OTP Auto-retrieval timed out. Please enter manually or resend.", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                // This method is called when auto-retrieval of the OTP is successful
                verifyAuthentication(credential);
                resendBtn.setVisibility(View.GONE);
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
        resendBtn.setVisibility(View.GONE); // Hide resend button again after resending
    }

    // Modified validateDigitField to return boolean for individual field validity
    private boolean validateDigitField(EditText field) {
        if (field.getText().toString().isEmpty()) {
            field.setError("Required");
            return false;
        }
        return true;
    }

    public void verifyAuthentication(PhoneAuthCredential credential) {
        FirebaseUser currentUser = fAuth.getCurrentUser();
        if (currentUser != null) {
            currentUser.linkWithCredential(credential).addOnSuccessListener(new OnSuccessListener<AuthResult>() {
                @Override
                public void onSuccess(AuthResult authResult) {
                    Toast.makeText(VerifyPhone.this, "Account Created and Phone Linked", Toast.LENGTH_SHORT).show();
                    // TODO: Send to Dashboard or next activity
                    // For example: startActivity(new Intent(VerifyPhone.this, DashboardActivity.class));
                    finish(); // Close VerifyPhone activity
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(VerifyPhone.this, "Failed to link phone: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    Log.e("VerifyPhone", "Failed to link phone: " + e.getMessage());
                }
            });
        } else {
            // This case might happen if the user session is lost or not properly established
            // after email/password signup.
            // You might need to re-authenticate the user or handle this scenario.
            Toast.makeText(VerifyPhone.this, "No user currently signed in to link phone to.", Toast.LENGTH_LONG).show();
            Log.e("VerifyPhone", "No current user to link phone credential.");
        }
    }
}
