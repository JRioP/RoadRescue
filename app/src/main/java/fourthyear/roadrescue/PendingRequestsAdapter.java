package fourthyear.roadrescue;

// ADDED IMPORTS
import android.location.Location;
import java.util.Locale;
// END ADDED IMPORTS

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Map;

public class PendingRequestsAdapter extends RecyclerView.Adapter<PendingRequestsAdapter.ViewHolder> {

    private final List<Map<String, Object>> pendingRequests;
    private final OnAcceptClickListener acceptClickListener;
    private final OnItemClickListener itemClickListener;

    public interface OnAcceptClickListener {
        void onAcceptClick(String requestId, Map<String, Object> requestData);
    }

    public interface OnItemClickListener {
        // Correctly receives the full request data map
        void onItemClick(Map<String, Object> requestData);
    }

    public PendingRequestsAdapter(List<Map<String, Object>> pendingRequests,
                                  OnAcceptClickListener acceptListener,
                                  OnItemClickListener itemListener) {
        this.pendingRequests = pendingRequests;
        this.acceptClickListener = acceptListener;
        this.itemClickListener = itemListener;
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
        holder.bind(requestData, acceptClickListener, itemClickListener);
    }

    @Override
    public int getItemCount() {
        return pendingRequests.size();
    }

    // --- VIEW HOLDER UPDATED ---
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView requestInfoText;
        TextView requestIdText;
        Button acceptButton;
        TextView requestDistanceText; // 1. Find the new TextView

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            requestInfoText = itemView.findViewById(R.id.request_info_text);
            requestIdText = itemView.findViewById(R.id.request_id_text);
            acceptButton = itemView.findViewById(R.id.accept_request_button);
            requestDistanceText = itemView.findViewById(R.id.request_distance_text); // 2. Assign it
        }

        // --- BIND METHOD REPLACED ---
        void bind(final Map<String, Object> requestData,
                  final OnAcceptClickListener acceptListener,
                  final OnItemClickListener itemListener) {

            // ... (existing code to get requestId, pickupLat, pickupLng, pickupAddress) ...
            String requestId = (String) requestData.get("requestId");
            Double pickupLat = (Double) requestData.get("pickupLat");
            Double pickupLng = (Double) requestData.get("pickupLng");
            String pickupAddress = (String) requestData.get("pickupAddress");

            // 3. Add logic to get destination and calculate distance
            Double destLat = (Double) requestData.get("destinationLat");
            Double destLng = (Double) requestData.get("destinationLng");

            String locationInfo;
            if (pickupAddress != null && !pickupAddress.isEmpty()) {
                locationInfo = pickupAddress;
            } else if (pickupLat != null && pickupLng != null) {
                locationInfo = String.format("Request at (%.4f, %.4f)", pickupLat, pickupLng);
            } else {
                locationInfo = "Unknown Location";
            }

            // Calculate distance
            String distanceText = "Distance: N/A";
            if (pickupLat != null && pickupLng != null && destLat != null && destLng != null) {
                float[] results = new float[1];
                Location.distanceBetween(pickupLat, pickupLng, destLat, destLng, results);
                float distanceInMeters = results[0];

                if (distanceInMeters > 1000) {
                    float distanceInKm = distanceInMeters / 1000;
                    distanceText = String.format(Locale.getDefault(), "Distance: %.2f km", distanceInKm);
                } else {
                    distanceText = String.format(Locale.getDefault(), "Distance: %.0f m", distanceInMeters);
                }
            }

            // 4. Set all text fields
            requestInfoText.setText(locationInfo);
            requestIdText.setText("ID: " + (requestId != null ? requestId.substring(0, Math.min(requestId.length(), 8)) + "..." : "N/A"));
            requestDistanceText.setText(distanceText); // Set the distance

            // ... (existing click listeners) ...
            // Accept button listener
            acceptButton.setOnClickListener(v -> {
                if (acceptListener != null && requestId != null) {
                    acceptListener.onAcceptClick(requestId, requestData);
                }
            });

            // Item click listener: pass the full map data
            itemView.setOnClickListener(v -> {
                if (itemListener != null) {
                    itemListener.onItemClick(requestData);
                }
            });
        }
    }
    // --- END OF MERGE ---
}