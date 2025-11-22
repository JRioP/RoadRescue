package fourthyear.roadrescue;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;

import java.util.Locale;

public class PaymentFragment extends Fragment {

    private MaterialCardView cardCash;
    private RadioButton radioCash;
    private Button continueButton;
    private ImageView backButton;
    private TextView amountTextView;

    private double finalAmount = 0.0;
    private String selectedPaymentMethod = "Cash";

    public PaymentFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // MAKE SURE this matches the name of the XML file you provided
        return inflater.inflate(R.layout.activity_payment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);

        // 1. Get the amount passed from MapFragment
        if (getArguments() != null) {
            finalAmount = getArguments().getDouble("AMOUNT_TO_BE_PAID", 0.0);
        }

        // 2. Display the amount
        if (amountTextView != null) {
            amountTextView.setText(String.format(Locale.getDefault(), "PHP %.2f", finalAmount));
        }

        // 3. Setup interaction
        setupClickListeners();
        updateSelection(selectedPaymentMethod);
    }

    private void initializeViews(View view) {
        continueButton = view.findViewById(R.id.button_continue);
        backButton = view.findViewById(R.id.back_button);
        amountTextView = view.findViewById(R.id.text_view_amount);

        // These IDs must exist in your activity_payment.xml
        cardCash = view.findViewById(R.id.card_cash);
        radioCash = view.findViewById(R.id.radio_cash);
    }

    private void setupClickListeners() {
        // Allow clicking the entire card to select Cash
        if (cardCash != null) {
            cardCash.setOnClickListener(v -> updateSelection("Cash"));
        }

        if (continueButton != null) {
            continueButton.setOnClickListener(v -> {
                if ("Cash".equals(selectedPaymentMethod)) {
                    confirmPayment(selectedPaymentMethod, finalAmount);
                } else {
                    Toast.makeText(requireContext(), "Only Cash is currently supported.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (backButton != null) {
            backButton.setOnClickListener(v -> {
                if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                    getParentFragmentManager().popBackStack();
                } else {
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            });
        }
    }

    private void updateSelection(String paymentMethod) {
        this.selectedPaymentMethod = paymentMethod;

        // Visual feedback for Cash selection
        if (radioCash != null) {
            radioCash.setChecked("Cash".equals(paymentMethod));
            radioCash.setVisibility("Cash".equals(paymentMethod) ? View.VISIBLE : View.GONE);
        }

    }

    private void confirmPayment(String method, double amount) {
        // 1. Create a Bundle with the result
        Bundle result = new Bundle();
        result.putString("PAYMENT_METHOD", method);
        result.putDouble("FINAL_AMOUNT", amount);

        // 2. Send result back to MapFragment (which is listening for "payment_result_key")
        getParentFragmentManager().setFragmentResult("payment_result_key", result);

        // 3. Close this fragment and go back to Map
        getParentFragmentManager().popBackStack();
    }
}