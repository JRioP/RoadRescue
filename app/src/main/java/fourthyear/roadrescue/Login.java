package fourthyear.roadrescue;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

public class Login extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.activity_login, container, false);

        TextView forgotPassword = view.findViewById(R.id.btn_forget_password);
        forgotPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Handle forgot password
                Toast.makeText(getActivity(), "Forgot Password clicked", Toast.LENGTH_SHORT).show();
                // Add your forgot password logic here
                Intent intent = new Intent(getActivity(), ForgotPassword.class);
                startActivity(intent);
            }
        });
        return view;
    }
}