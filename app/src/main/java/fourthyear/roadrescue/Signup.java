package fourthyear.roadrescue;

import static android.content.ContentValues.TAG;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;  // Added import

public class Signup extends Fragment {

    public static final String TAG = "TAG";
    EditText personUsername, personEmail, personPassword, personRPassword, phoneCountryCode, phoneNumber;
    Button signupBtn;
    FirebaseAuth fAuth;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.activity_sign_up, container, false);
        personUsername = v.findViewById(R.id.signup_username);
        personEmail = v.findViewById(R.id.signup_email);
        personPassword = v.findViewById(R.id.signup_password);
        personRPassword = v.findViewById(R.id.signup_password_retype);
        phoneCountryCode = v.findViewById(R.id.signup_phone_number_country_code);
        phoneNumber = v.findViewById(R.id.signup_phone_number);
        signupBtn = v.findViewById(R.id.btn_signup);

        fAuth = FirebaseAuth.getInstance();

        signupBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean allFieldsValid = true;

                // Validate all fields
                if (!validateField(personUsername)) allFieldsValid = false;
                if (!validateField(personEmail)) allFieldsValid = false;
                if (!validateField(personPassword)) allFieldsValid = false;
                if (!validateField(personRPassword)) allFieldsValid = false;
                if (!validateField(phoneCountryCode)) allFieldsValid = false;
                if (!validateField(phoneNumber)) allFieldsValid = false;

                if (!personPassword.getText().toString().equals(personRPassword.getText().toString())) {
                    allFieldsValid = false;
                    personRPassword.setError("Passwords do not match");
                }

                if (allFieldsValid) {
                    String email = personEmail.getText().toString().trim();
                    String password = personPassword.getText().toString().trim();

                    fAuth.createUserWithEmailAndPassword(email, password)  // Fixed typo
                            .addOnSuccessListener(new OnSuccessListener<AuthResult>() {
                                @Override
                                public void onSuccess(AuthResult authResult) {
                                    FirebaseUser newUser = fAuth.getCurrentUser();
                                    if (newUser != null) {
                                        // Send email verification
                                        newUser.sendEmailVerification()
                                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                                    @Override
                                                    public void onSuccess(Void unused) {
                                                        Toast.makeText(getActivity(), "Verification email sent to " + email, Toast.LENGTH_SHORT).show();
                                                    }
                                                })
                                                .addOnFailureListener(new OnFailureListener() {
                                                    @Override
                                                    public void onFailure(@NonNull Exception e) {
                                                        Toast.makeText(getActivity(), "Failed to send verification email: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                                        Log.e(TAG, "Email verification send failed", e);
                                                    }
                                                });

                                        // Proceed to phone verification
                                        Intent phoneVerificationIntent = new Intent(getActivity(), VerifyPhone.class);
                                        phoneVerificationIntent.putExtra("phone", "+" + phoneCountryCode.getText().toString() + phoneNumber.getText().toString());
                                        startActivity(phoneVerificationIntent);
                                        Log.d(TAG, "User created and email verification sent. Phone: " + "+" + phoneCountryCode.getText().toString() + phoneNumber.getText().toString());
                                    }
                                }
                            }).addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    Toast.makeText(getActivity(), "Signup failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    Log.e(TAG, "Signup failed", e);
                                }
                            });
                } else {
                    Toast.makeText(getActivity(), "Please correct the errors in the form.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        return v;
    }

    private boolean validateField(EditText field) {
        if (field.getText().toString().isEmpty()) {
            field.setError("Required Field");
            return false;
        }
        return true;
    }
}