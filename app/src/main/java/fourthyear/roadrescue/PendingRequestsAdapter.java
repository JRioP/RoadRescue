package fourthyear.roadrescue;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
// REMOVED: import com.google.android.gms.maps.model.LatLng; // No longer needed in adapter
import java.util.List;
import java.util.Map;
// REMOVED: import javax.annotation.Nullable; // Not used here

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

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView requestInfoText;
        TextView requestIdText;
        Button acceptButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            requestInfoText = itemView.findViewById(R.id.request_info_text);
            requestIdText = itemView.findViewById(R.id.request_id_text);
            acceptButton = itemView.findViewById(R.id.accept_request_button);
        }

        void bind(final Map<String, Object> requestData,
                  final OnAcceptClickListener acceptListener,
                  final OnItemClickListener itemListener) {

            String requestId = (String) requestData.get("requestId");
            Double pickupLat = (Double) requestData.get("pickupLat");
            Double pickupLng = (Double) requestData.get("pickupLng");

            // Extract pickup address from the map data
            String pickupAddress = (String) requestData.get("pickupAddress");
            String locationInfo;

            // Prioritize displaying the full address if available
            if (pickupAddress != null && !pickupAddress.isEmpty()) {
                locationInfo = pickupAddress;
            } else if (pickupLat != null && pickupLng != null) {
                // Fallback to coordinates if address is missing
                locationInfo = String.format("Request at (%.4f, %.4f)", pickupLat, pickupLng);
            } else {
                locationInfo = "Unknown Location";
            }

            requestInfoText.setText(locationInfo);
            requestIdText.setText("ID: " + (requestId != null ? requestId.substring(0, Math.min(requestId.length(), 8)) + "..." : "N/A"));

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
}