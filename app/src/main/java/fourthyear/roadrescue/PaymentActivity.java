// In PaymentActivity.java
package fourthyear.roadrescue;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;

public class PaymentActivity extends AppCompatActivity {

    private MaterialCardView cardGcash, cardMaya, cardCash;
    private RadioButton radioGcash, radioMaya, radioCash;
    private Button continueButton;
    private ImageView backButton;


    private String selectedPaymentMethod = "Cash";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);


        continueButton = findViewById(R.id.button_continue);
        backButton = findViewById(R.id.back_button);

        cardGcash = findViewById(R.id.card_gcash);
        cardMaya = findViewById(R.id.card_maya);
        cardCash = findViewById(R.id.card_cash);

        radioGcash = findViewById(R.id.radio_gcash);
        radioMaya = findViewById(R.id.radio_maya);
        radioCash = findViewById(R.id.radio_cash);

        cardGcash.setOnClickListener(v -> updateSelection("GCash"));
        cardMaya.setOnClickListener(v -> updateSelection("Maya"));
        cardCash.setOnClickListener(v -> updateSelection("Cash"));

        updateSelection(selectedPaymentMethod);

        continueButton.setOnClickListener(v -> {

            Intent resultIntent = new Intent();

            resultIntent.putExtra("PAYMENT_METHOD", selectedPaymentMethod);

            setResult(RESULT_OK, resultIntent);
            finish();
        });


        backButton.setOnClickListener(v -> {
            finish();
        });
    }

    private void updateSelection(String paymentMethod) {
        // Store the choice
        selectedPaymentMethod = paymentMethod;

        // Update the radio buttons visually
        radioGcash.setChecked(paymentMethod.equals("GCash"));
        radioMaya.setChecked(paymentMethod.equals("Maya"));
        radioCash.setChecked(paymentMethod.equals("Cash"));
    }
}