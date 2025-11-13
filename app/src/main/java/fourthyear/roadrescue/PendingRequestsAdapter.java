package fourthyear.roadrescue;

import android.location.Location;
import android.os.Build;
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
import javax.annotation.Nullable;

public class PendingRequestsAdapter extends RecyclerView.Adapter<PendingRequestsAdapter.ViewHolder> {

    private final List<Map<String, Object>> pendingRequests;
    private final OnAcceptClickListener acceptClickListener;
    private final OnItemClickListener itemClickListener;
    private LatLng providerCurrentLocation;

    public interface OnAcceptClickListener {
        void onAcceptClick(String requestId, Map<String, Object> requestData);
    }

    public interface OnItemClickListener {
        void onItemClick(Map<String, Object> requestData);
    }

    public PendingRequestsAdapter(List<Map<String, Object>> pendingRequests,
                                  OnAcceptClickListener acceptListener,
                                  OnItemClickListener itemListener) {
        this.pendingRequests = pendingRequests;
        this.acceptClickListener = acceptListener;
        this.itemClickListener = itemListener;
    }

    public void updateProviderLocation(LatLng location) {
        this.providerCurrentLocation = location;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pending_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> requestData = pendingRequests.get(position);
        holder.bind(requestData, providerCurrentLocation, acceptClickListener, itemClickListener);
    }

    @Override
    public int getItemCount() {
        return pendingRequests.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView requestInfoText;
        TextView requestIdText;
        Button acceptButton;
        TextView requestDistanceText;
        TextView requestPaymentModeText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            requestInfoText = itemView.findViewById(R.id.request_info_text);
            requestIdText = itemView.findViewById(R.id.request_id_text);
            acceptButton = itemView.findViewById(R.id.accept_request_button);
            requestDistanceText = itemView.findViewById(R.id.request_distance_text);
            requestPaymentModeText = itemView.findViewById(R.id.request_payment_mode_text);
        }

        void bind(final Map<String, Object> requestData,
                  @Nullable LatLng providerLocation,
                  final OnAcceptClickListener acceptListener,
                  final OnItemClickListener itemListener) {

            String requestId = (String) requestData.get("requestId");
            Double pickupLat = (Double) requestData.get("pickupLat");
            Double pickupLng = (Double) requestData.get("pickupLng");
            String pickupAddress = (String) requestData.get("pickupAddress");

            String requestType = (String) requestData.get("requestType");
            String paymentMethod = (String) requestData.get("paymentMethod");

            Double amount = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                amount = (Double) requestData.getOrDefault("amount", 0.0);
            }

            String locationAddress;
            if (pickupAddress != null && !pickupAddress.isEmpty()) {
                locationAddress = pickupAddress;
            } else if (pickupLat != null && pickupLng != null) {
                locationAddress = String.format(Locale.getDefault(), "Lat: %.4f, Lng: %.4f", pickupLat, pickupLng);
            } else {
                locationAddress = "Unknown Location";
            }

            requestInfoText.setText(String.format("%s Request at %s",
                    requestType != null ? requestType : "Service",
                    locationAddress));

            requestIdText.setText("ID: " + (requestId != null ? requestId.substring(0, Math.min(requestId.length(), 8)) : "N/A"));

            if (paymentMethod != null) {
                String amountDisplay = String.format(Locale.getDefault(), " (PHP %.2f)", amount);
                requestPaymentModeText.setText("Payment: " + paymentMethod + amountDisplay);
            } else {
                requestPaymentModeText.setText("Payment: N/A");
            }

            String distanceText = "Calculating distance...";
            if (providerLocation != null && pickupLat != null && pickupLng != null) {
                float[] results = new float[1];
                Location.distanceBetween(
                        providerLocation.latitude, providerLocation.longitude,
                        pickupLat, pickupLng,
                        results);

                float distanceInMeters = results[0];
                if (distanceInMeters > 1000) {
                    float distanceInKm = distanceInMeters / 1000;
                    distanceText = String.format(Locale.getDefault(), "%.1f km away", distanceInKm);
                } else {
                    distanceText = String.format(Locale.getDefault(), "%.0f m away", distanceInMeters);
                }
            } else if (providerLocation == null) {
                distanceText = "Getting your location...";
            }
            requestDistanceText.setText(distanceText);

            acceptButton.setOnClickListener(v -> {
                if (acceptListener != null && requestId != null) {
                    acceptListener.onAcceptClick(requestId, requestData);
                }
            });

            itemView.setOnClickListener(v -> {
                if (itemListener != null) {
                    itemListener.onItemClick(requestData);
                }
            });
        }
    }
}