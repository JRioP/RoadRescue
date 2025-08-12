package fourthyear.roadrescue;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

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


public class Signup extends Fragment {
        EditText personUsername, personEmail, personPassword, personRPassword;
        Button signupBtn;
        Boolean isDataValid = false;
        FirebaseAuth fAuth;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.activity_sign_up, container, false);
        personUsername = v.findViewById(R.id.signup_username);
        personEmail = v.findViewById(R.id.signup_email);
        personPassword = v.findViewById(R.id.signup_password);
        personRPassword = v.findViewById(R.id.signup_password_retype);

        fAuth = FirebaseAuth.getInstance();


        //validating the data
        validateData(personUsername);
        validateData(personEmail);
        validateData(personPassword );
        validateData(personRPassword);

        if(!personPassword.getText().toString().equals(personRPassword.getText().toString())){
            isDataValid = false;
            personRPassword.setError("Password Do not Match");
        } else {
            isDataValid = true;
        }

        if(isDataValid){
            //
            fAuth.createUserWithEmailAndPassword(personUsername.getText().toString(),personPassword.getText().toString()).addOnSuccessListener(new OnSuccessListener<AuthResult>() {
                @Override
                public void onSuccess(AuthResult authResult) {
                    Toast.makeText(getActivity(),"User Account is Created", Toast.LENGTH_SHORT).show();
                    //send the user to verify
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Toast.makeText(getActivity(), "Error" + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });

        }


        return v;
    }

    public void validateData(EditText field){
        if(field.getText().toString().isEmpty()){
            isDataValid = false;
            field.setError("Required Field");
        } else {
            isDataValid = true;
        }
    }
}