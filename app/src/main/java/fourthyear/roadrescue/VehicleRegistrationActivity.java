package fourthyear.roadrescue;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class VehicleRegistrationActivity extends AppCompatActivity {

    private Spinner spinnerCarBrand, spinnerCarModel, spinnerCarType, spinnerCarYear;
    private TextInputEditText editPlateNumber;
    private Button btnFinish;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private ArrayAdapter<String> brandAdapter, modelAdapter, typeAdapter, yearAdapter;
    private ArrayAdapter<String> toyotaAdapter, hondaAdapter, mitsubishiAdapter, fordAdapter, nissanAdapter,
            hyundaiAdapter, kiaAdapter, suzukiAdapter, chevroletAdapter, otherAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vehicle_registration);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initializeViews();
        setupSpinners();

        spinnerCarBrand.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateCarModelSpinner(parent.getItemAtPosition(position).toString());
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnFinish.setOnClickListener(v -> saveVehicleData());
    }

    private void initializeViews() {
        spinnerCarBrand = findViewById(R.id.spinner_car_brand);
        spinnerCarModel = findViewById(R.id.spinner_car_model);
        spinnerCarType = findViewById(R.id.spinner_car_type);
        spinnerCarYear = findViewById(R.id.spinner_car_year);
        editPlateNumber = findViewById(R.id.edit_plate_number);
        btnFinish = findViewById(R.id.btn_finish_setup);
    }

    private void setupSpinners() {
        String[] carBrands = {"Toyota", "Honda", "Mitsubishi", "Ford", "Nissan", "Hyundai", "Kia", "Suzuki", "Chevrolet", "Other"};
        brandAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, carBrands);
        brandAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCarBrand.setAdapter(brandAdapter);

        String[] carTypes = {"Sedan", "SUV", "Hatchback", "Coupe", "Convertible", "Truck", "Van", "Motorcycle", "Other"};
        typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, carTypes);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCarType.setAdapter(typeAdapter);

        ArrayList<String> years = new ArrayList<>();
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        for (int i = currentYear; i >= 1980; i--) {
            years.add(Integer.toString(i));
        }
        yearAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, years);
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCarYear.setAdapter(yearAdapter);

        initModelAdapters();
    }

    private void initModelAdapters() {
        int layout = android.R.layout.simple_spinner_item;
        int dropdown = android.R.layout.simple_spinner_dropdown_item;

        toyotaAdapter = new ArrayAdapter<>(this, layout, new String[]{"Vios", "Corolla", "Camry", "Fortuner", "Hilux", "Innova", "Wigo", "Rush", "Other"});
        hondaAdapter = new ArrayAdapter<>(this, layout, new String[]{"Civic", "City", "HR-V", "CR-V", "Brio", "BR-V", "Jazz", "Other"});
        mitsubishiAdapter = new ArrayAdapter<>(this, layout, new String[]{"Montero Sport", "Mirage", "Mirage G4", "Xpander", "Strada", "L300", "Other"});
        fordAdapter = new ArrayAdapter<>(this, layout, new String[]{"Ranger", "Everest", "Territory", "Mustang", "Explorer", "Other"});
        nissanAdapter = new ArrayAdapter<>(this, layout, new String[]{"Navara", "Terra", "Almera", "Kicks", "Urvan", "Patrol", "Other"});
        hyundaiAdapter = new ArrayAdapter<>(this, layout, new String[]{"Tucson", "Creta", "Stargazer", "Santa Fe", "Staria", "Accent", "Other"});
        kiaAdapter = new ArrayAdapter<>(this, layout, new String[]{"Seltos", "Stonic", "Soluto", "Carnival", "Sorento", "Other"});
        suzukiAdapter = new ArrayAdapter<>(this, layout, new String[]{"S-Presso", "Jimny", "Ertiga", "Dzire", "Celerio", "XL7", "Other"});
        chevroletAdapter = new ArrayAdapter<>(this, layout, new String[]{"Tracker", "Trailblazer", "Camaro", "Corvette", "Suburban", "Other"});
        otherAdapter = new ArrayAdapter<>(this, layout, new String[]{"Other"});

        toyotaAdapter.setDropDownViewResource(dropdown);
        hondaAdapter.setDropDownViewResource(dropdown);
        mitsubishiAdapter.setDropDownViewResource(dropdown);
        fordAdapter.setDropDownViewResource(dropdown);
        nissanAdapter.setDropDownViewResource(dropdown);
        hyundaiAdapter.setDropDownViewResource(dropdown);
        kiaAdapter.setDropDownViewResource(dropdown);
        suzukiAdapter.setDropDownViewResource(dropdown);
        chevroletAdapter.setDropDownViewResource(dropdown);
        otherAdapter.setDropDownViewResource(dropdown);
    }

    private void updateCarModelSpinner(String brand) {
        if (brand == null) return;
        switch (brand) {
            case "Toyota": spinnerCarModel.setAdapter(toyotaAdapter); break;
            case "Honda": spinnerCarModel.setAdapter(hondaAdapter); break;
            case "Mitsubishi": spinnerCarModel.setAdapter(mitsubishiAdapter); break;
            case "Ford": spinnerCarModel.setAdapter(fordAdapter); break;
            case "Nissan": spinnerCarModel.setAdapter(nissanAdapter); break;
            case "Hyundai": spinnerCarModel.setAdapter(hyundaiAdapter); break;
            case "Kia": spinnerCarModel.setAdapter(kiaAdapter); break;
            case "Suzuki": spinnerCarModel.setAdapter(suzukiAdapter); break;
            case "Chevrolet": spinnerCarModel.setAdapter(chevroletAdapter); break;
            default: spinnerCarModel.setAdapter(otherAdapter); break;
        }
    }

    private void saveVehicleData() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) return;

        String brand = spinnerCarBrand.getSelectedItem().toString();
        String model = spinnerCarModel.getSelectedItem().toString();
        String type = spinnerCarType.getSelectedItem().toString();
        String year = spinnerCarYear.getSelectedItem().toString();

        String rawPlate = editPlateNumber.getText().toString().trim().toUpperCase();

        if (!rawPlate.matches("[A-Z]{3} \\d{3,4}")) {
            editPlateNumber.setError("Invalid format. Use: ABC 1234");
            editPlateNumber.requestFocus();
            return;
        }

        Map<String, Object> vehicleData = new HashMap<>();
        vehicleData.put("carBrand", brand);
        vehicleData.put("carModel", model);
        vehicleData.put("carType", type);
        vehicleData.put("carYear", year);
        vehicleData.put("carPlateNumber", rawPlate); // Save the valid plate

        db.collection("users").document(user.getUid())
                .set(vehicleData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Setup Complete!", Toast.LENGTH_SHORT).show();
                    checkUserTypeAndRedirect(user.getUid());
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error saving data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void checkUserTypeAndRedirect(String userId) {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    String userType = "Customer";
                    if (documentSnapshot.exists()) {
                        String type = documentSnapshot.getString("userType");
                        if (type != null && (type.trim().equalsIgnoreCase("Service Provider") || type.trim().equalsIgnoreCase("driver"))) {
                            userType = "Service Provider";
                        }
                    }

                    Intent intent;
                    if (userType.equals("Service Provider")) {
                        intent = new Intent(VehicleRegistrationActivity.this, ServiceProviderHomepage.class);
                    } else {
                        intent = new Intent(VehicleRegistrationActivity.this, homepage.class);
                    }

                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Intent intent = new Intent(VehicleRegistrationActivity.this, homepage.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
    }
}