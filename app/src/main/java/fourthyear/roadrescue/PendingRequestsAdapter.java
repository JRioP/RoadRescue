package fourthyear.roadrescue;

import android.location.Location; // Import Location
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.maps.model.LatLng; // ADDED
import java.util.List;
import java.util.Locale; // Import Locale
import java.util.Map;
import javax.annotation.Nullable; // ADDED

public class PendingRequestsAdapter extends RecyclerView.Adapter<PendingRequestsAdapter.ViewHolder> {

    private final List<Map<String, Object>> pendingRequests;
    private final OnAcceptClickListener acceptClickListener;
    private final OnItemClickListener itemClickListener;
    private LatLng providerCurrentLocation; // Store provider's location

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

    // --- NEW METHOD ---
    // Call this from the activity when the provider's location changes
    public void updateProviderLocation(LatLng location) {
        this.providerCurrentLocation = location;
        notifyDataSetChanged(); // Update all visible items with new distances
    }
    // --- END NEW ---

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
        // Pass the provider's location to the binder
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
        TextView requestDistanceText; // Assuming this ID exists from previous step

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            requestInfoText = itemView.findViewById(R.id.request_info_text);
            requestIdText = itemView.findViewById(R.id.request_id_text);
            acceptButton = itemView.findViewById(R.id.accept_request_button);
            requestDistanceText = itemView.findViewById(R.id.request_distance_text); // Find the distance TextView
        }

        // --- MODIFIED bind METHOD ---
        void bind(final Map<String, Object> requestData,
                  @Nullable LatLng providerLocation, // Receive provider's location
                  final OnAcceptClickListener acceptListener,
                  final OnItemClickListener itemListener) {

            String requestId = (String) requestData.get("requestId");
            Double pickupLat = (Double) requestData.get("pickupLat");
            Double pickupLng = (Double) requestData.get("pickupLng");
            String pickupAddress = (String) requestData.get("pickupAddress");

            String locationInfo;
            if (pickupAddress != null && !pickupAddress.isEmpty()) {
                locationInfo = pickupAddress;
            } else if (pickupLat != null && pickupLng != null) {
                locationInfo = String.format("Request at (%.4f, %.4f)", pickupLat, pickupLng);
            } else {
                locationInfo = "Unknown Location";
            }

            requestInfoText.setText(locationInfo);
            requestIdText.setText("ID: " + (requestId != null ? requestId.substring(0, Math.min(requestId.length(), 8)) + "..." : "N/A"));

            // --- NEW: Calculate "Distance From You" ---
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
            requestDistanceText.setText(distanceText); // Set the text
            // --- END NEW ---

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