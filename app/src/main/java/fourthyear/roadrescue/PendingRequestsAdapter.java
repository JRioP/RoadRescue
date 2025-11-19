package fourthyear.roadrescue;

import android.annotation.SuppressLint;
import android.location.Location;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.model.LatLng;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PendingRequestsAdapter extends RecyclerView.Adapter<PendingRequestsAdapter.RequestViewHolder> {

    private final List<Map<String, Object>> requests;
    private final OnAcceptClickListener acceptClickListener;
    private final OnItemClickListener itemClickListener;
    private LatLng providerLocation;

    public interface OnAcceptClickListener {
        void onAcceptClick(String requestId, Map<String, Object> requestData);
    }

    public interface OnItemClickListener {
        void onItemClick(Map<String, Object> requestData);
    }

    public PendingRequestsAdapter(List<Map<String, Object>> requests,
                                  OnAcceptClickListener acceptClickListener,
                                  OnItemClickListener itemClickListener) {
        this.requests = requests;
        this.acceptClickListener = acceptClickListener;
        this.itemClickListener = itemClickListener;
    }

    @NonNull
    @Override
    public RequestViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pending_request, parent, false);
        return new RequestViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RequestViewHolder holder, int position) {
        Map<String, Object> request = requests.get(position);

        String requestId = (String) request.get("requestId");
        String requestType = (String) request.get("requestType");
        String pickupAddress = (String) request.get("pickupAddress");

        // --- FIX: Add "Vehicle: " Prefix ---
        String carType = (String) request.get("carType");
        String carModel = (String) request.get("carModel");
        String carBrand = (String) request.get("carBrand");

        StringBuilder sb = new StringBuilder();

        // Build string like: "Sedan - Toyota Vios"
        if (carType != null) sb.append(carType);

        if (carBrand != null || carModel != null) {
            if (sb.length() > 0) sb.append(" - ");
            if (carBrand != null) sb.append(carBrand).append(" ");
            if (carModel != null) sb.append(carModel);
        }

        String vehicleInfo = sb.toString();

        if (vehicleInfo.isEmpty()) {
            holder.vehicleTextView.setText("Vehicle info not available");
        } else {
            // HERE IS THE CHANGE: Added "Vehicle: "
            holder.vehicleTextView.setText("Vehicle: " + vehicleInfo);
        }
        // -----------------------------------

        holder.requestTypeTextView.setText(requestType != null ? requestType + " Request" : "Service Request");
        holder.pickupAddressTextView.setText(pickupAddress != null ? "at " + pickupAddress : "at unknown location");
        holder.requestIdTextView.setText("ID: " + (requestId != null ? requestId.substring(0, Math.min(requestId.length(), 8)) : "N/A"));

        Double amount = (Double) request.get("amount");
        String paymentMethod = (String) request.get("paymentMethod");
        String amountStr = (amount != null) ? String.format(Locale.getDefault(), " (PHP %.2f)", amount) : "";
        String paymentStr = (paymentMethod != null) ? paymentMethod : "N/A";
        holder.paymentTextView.setText("Payment: " + paymentStr + amountStr);

        // --- Distance Calculation ---
        if (providerLocation != null) {
            Double lat = (Double) request.get("pickupLat");
            Double lng = (Double) request.get("pickupLng");
            if (lat != null && lng != null) {
                float[] results = new float[1];
                Location.distanceBetween(
                        providerLocation.latitude, providerLocation.longitude,
                        lat, lng,
                        results
                );
                float distanceInKm = results[0] / 1000;
                holder.distanceTextView.setText(String.format(Locale.getDefault(), "%.1f km away", distanceInKm));
            } else {
                holder.distanceTextView.setText("Distance unknown");
            }
        } else {
            holder.distanceTextView.setText("Calculating distance...");
        }

        // --- Active Job Logic ---
        boolean isMyActiveJob = false;
        if (request.containsKey("isMyActiveJob") && request.get("isMyActiveJob") != null) {
            isMyActiveJob = (Boolean) request.get("isMyActiveJob");
        }

        if (isMyActiveJob) {
            holder.acceptButton.setVisibility(View.GONE);
            holder.acceptedStatusTextView.setVisibility(View.VISIBLE);
        } else {
            holder.acceptButton.setVisibility(View.VISIBLE);
            holder.acceptedStatusTextView.setVisibility(View.GONE);
            holder.acceptButton.setOnClickListener(v -> acceptClickListener.onAcceptClick(requestId, request));
        }

        holder.itemView.setOnClickListener(v -> itemClickListener.onItemClick(request));
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void updateProviderLocation(LatLng location) {
        this.providerLocation = location;
        notifyDataSetChanged();
    }

    public static class RequestViewHolder extends RecyclerView.ViewHolder {
        TextView requestTypeTextView;
        TextView vehicleTextView;
        TextView pickupAddressTextView;
        TextView requestIdTextView;
        TextView paymentTextView;
        TextView distanceTextView;
        TextView acceptedStatusTextView;
        Button acceptButton;

        public RequestViewHolder(@NonNull View itemView) {
            super(itemView);
            requestTypeTextView = itemView.findViewById(R.id.request_type_text);
            vehicleTextView = itemView.findViewById(R.id.request_vehicle_text);
            pickupAddressTextView = itemView.findViewById(R.id.request_address_text);
            requestIdTextView = itemView.findViewById(R.id.request_id_text);
            paymentTextView = itemView.findViewById(R.id.request_payment_text);
            distanceTextView = itemView.findViewById(R.id.request_distance_text);
            acceptButton = itemView.findViewById(R.id.btn_accept);
            acceptedStatusTextView = itemView.findViewById(R.id.text_accepted_status);
        }
    }
}