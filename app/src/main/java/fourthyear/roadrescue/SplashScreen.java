package fourthyear.roadrescue;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SplashScreen extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash_screen);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Button getStartedButton = findViewById(R.id.get_started_button);
        Button logInButton = findViewById(R.id.splash_button_login);

        getStartedButton.setOnClickListener(v -> {
            Intent intent = new Intent(SplashScreen.this, MainActivity.class);
            intent.putExtra("LOAD_FRAGMENT_INDEX", 1);
            startActivity(intent);
            finish();
        });

        logInButton.setOnClickListener(v -> {
            Intent intent = new Intent(SplashScreen.this, MainActivity.class);
            intent.putExtra("LOAD_FRAGMENT_INDEX", 0);
            startActivity(intent);
            finish();
        });
    }
}