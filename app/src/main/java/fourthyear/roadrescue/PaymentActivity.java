package fourthyear.roadrescue;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Base64;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class PaymentActivity extends AppCompatActivity {

    private MaterialCardView cardGcash, cardMaya, cardCash;
    private RadioButton radioGcash, radioMaya, radioCash;
    private Button continueButton;
    private ImageView backButton;

    private TextView amountTextView;
    private double finalAmount;
    private String selectedPaymentMethod = "Cash";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);

        continueButton = findViewById(R.id.button_continue);
        backButton = findViewById(R.id.back_button);
        amountTextView = findViewById(R.id.text_view_amount);

        cardGcash = findViewById(R.id.card_gcash);
        cardMaya = findViewById(R.id.card_maya);
        cardCash = findViewById(R.id.card_cash);

        radioGcash = findViewById(R.id.radio_gcash);
        radioMaya = findViewById(R.id.radio_maya);
        radioCash = findViewById(R.id.radio_cash);

        finalAmount = getIntent().getDoubleExtra("AMOUNT_TO_BE_PAID", 0.0);
        if (amountTextView != null) {
            amountTextView.setText(String.format(Locale.getDefault(), "PHP %.2f", finalAmount));
        }

        // --- Card selection listeners ---
        cardGcash.setOnClickListener(v -> updateSelection("GCash"));
        cardMaya.setOnClickListener(v -> updateSelection("Maya"));
        cardCash.setOnClickListener(v -> updateSelection("Cash"));

        updateSelection(selectedPaymentMethod);

        continueButton.setOnClickListener(v -> {
            if (selectedPaymentMethod.equals("Cash")) {
                confirmPayment(selectedPaymentMethod, finalAmount);
            } else {
                createPaymentIntent(selectedPaymentMethod, finalAmount);
            }
        });

        backButton.setOnClickListener(v -> finish());
    }

    private void updateSelection(String paymentMethod) {
        selectedPaymentMethod = paymentMethod;
        radioGcash.setChecked(paymentMethod.equals("GCash"));
        radioMaya.setChecked(paymentMethod.equals("Maya"));
        radioCash.setChecked(paymentMethod.equals("Cash"));
    }

    private void confirmPayment(String method, double amount) {
        Toast.makeText(this, "Payment confirmed via " + method + " for PHP " + amount, Toast.LENGTH_LONG).show();
        Intent resultIntent = new Intent();
        resultIntent.putExtra("PAYMENT_METHOD", method);
        resultIntent.putExtra("FINAL_AMOUNT", amount);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void createPaymentIntent(String method, double amount) {
        int amountInCents = (int)(amount * 100);
        String secretKey = getString(R.string.paymongo_publishable_key);
        String authHeader = "Basic " + Base64.getEncoder().encodeToString((secretKey + ":").getBytes());

        JSONObject json = new JSONObject();
        try {
            JSONObject attributes = new JSONObject();
            attributes.put("amount", amountInCents);
            attributes.put("currency", "PHP");
            attributes.put("payment_method_allowed", new String[]{method.toLowerCase()});
            attributes.put("description", "RoadRescue Order Payment");

            JSONObject data = new JSONObject();
            data.put("attributes", attributes);

            json.put("data", data);

        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }

        OkHttpClient client = new OkHttpClient();
        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url("https://api.paymongo.com/v1/payment_intents")
                .post(body)
                .addHeader("Authorization", authHeader)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(PaymentActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String respStr = response.body().string();
                try {
                    JSONObject respJson = new JSONObject(respStr);
                    JSONObject attributes = respJson.getJSONObject("data").getJSONObject("attributes");
                    JSONObject nextAction = attributes.optJSONObject("next_action");

                    if (nextAction != null && "redirect".equals(nextAction.getString("type"))) {
                        String redirectUrl = nextAction.getJSONObject("redirect").getString("url");
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(redirectUrl));
                        startActivity(browserIntent);
                    } else if ("succeeded".equals(attributes.getString("status"))) {
                        runOnUiThread(() -> confirmPayment(method, amount));
                    } else {
                        runOnUiThread(() -> Toast.makeText(PaymentActivity.this, "Payment pending or failed.", Toast.LENGTH_LONG).show());
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
