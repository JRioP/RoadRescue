package fourthyear.roadrescue;

import android.app.Activity;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View; // Import View
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.widget.NestedScrollView; // Import NestedScrollView

public class TermsAndConditionsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terms_and_condition);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        Button btnAgree = findViewById(R.id.btn_agree_terms);
        NestedScrollView scrollView = findViewById(R.id.terms_scroll_view);
        btnAgree.setEnabled(false);

        scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {

            View child = v.getChildAt(0);
            if (child != null) {
                int childHeight = child.getMeasuredHeight();
                int parentHeight = v.getMeasuredHeight();

                if (scrollY >= (childHeight - parentHeight)) {
                    btnAgree.setEnabled(true);
                }
            }
        });


        btnAgree.setOnClickListener(v -> {
            setResult(Activity.RESULT_OK);
            finish();
        });
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            setResult(Activity.RESULT_CANCELED);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        setResult(Activity.RESULT_CANCELED);
        super.onBackPressed();
    }
}