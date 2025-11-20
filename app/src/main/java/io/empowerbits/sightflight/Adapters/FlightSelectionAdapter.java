package io.empowerbits.sightflight.Adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import io.empowerbits.sightflight.models.FlightLog;
import io.empowerbits.sightflight.R;
import io.empowerbits.sightflight.models.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FlightSelectionAdapter extends RecyclerView.Adapter<FlightSelectionAdapter.FlightViewHolder> {

    private List<FlightLog> flightsList;
    private OnFlightClickListener clickListener;

    public interface OnFlightClickListener {
        void onFlightClick(int position);
    }

    public FlightSelectionAdapter(List<FlightLog> flightsList, OnFlightClickListener clickListener) {
        this.flightsList = flightsList;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public FlightViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_flight_selection, parent, false);
        return new FlightViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FlightViewHolder holder, int position) {
        FlightLog flight = flightsList.get(position);
        
        // Set flight number
        holder.flightNumberText.setText("Flight " + (position + 1));
        
        // Format and set flight duration
        String endedDat = flight.getEnded_at();
        if(endedDat == null || endedDat.equals("")){
            String json = flight.getLog();
            Gson gson = new Gson();
            Log log = gson.fromJson(json, Log.class);
            endedDat = log.estimateEndTime;
        }
        String durationText = formatFlightDuration(flight.getStarted_at(), endedDat);
        holder.flightDurationText.setText(durationText);
        
        // Set click listener
        holder.flightItemContainer.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onFlightClick(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return flightsList != null ? flightsList.size() : 0;
    }

    /**
     * Format flight duration for display
     */
    private String formatFlightDuration(String startedAt, String endedAt) {
        try {
            if (startedAt == null || endedAt == null) {
                return "Duration not available";
            }

            // Parse date strings - adjust format as needed based on your date format
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            SimpleDateFormat displayFormat = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
            
            Date startDate = inputFormat.parse(startedAt);
            Date endDate = inputFormat.parse(endedAt);
            
            if (startDate != null && endDate != null) {
                String startFormatted = displayFormat.format(startDate);
                String endFormatted = displayFormat.format(endDate);
                
                // Calculate duration
                long durationMs = endDate.getTime() - startDate.getTime();
                long durationMinutes = durationMs / (1000 * 60);
                
                return startFormatted + " - " + endFormatted + " (" + durationMinutes + " min)";
            }
            
        } catch (ParseException e) {
            // If parsing fails, try alternative format or return raw dates
            return formatAlternativeDate(startedAt, endedAt);
        }
        
        return "Duration not available";
    }

    /**
     * Alternative date formatting if primary format fails
     */
    private String formatAlternativeDate(String startedAt, String endedAt) {
        try {
            // Try ISO format
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            SimpleDateFormat displayFormat = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
            
            Date startDate = isoFormat.parse(startedAt);
            Date endDate = isoFormat.parse(endedAt);
            
            if (startDate != null && endDate != null) {
                String startFormatted = displayFormat.format(startDate);
                String endFormatted = displayFormat.format(endDate);
                return startFormatted + " - " + endFormatted;
            }
        } catch (ParseException e) {
            // If all parsing fails, return raw strings
            return startedAt + " - " + endedAt;
        }
        
        return "Duration not available";
    }

    static class FlightViewHolder extends RecyclerView.ViewHolder {
        LinearLayout flightItemContainer;
        TextView flightNumberText;
        TextView flightDurationText;

        public FlightViewHolder(@NonNull View itemView) {
            super(itemView);
            flightItemContainer = itemView.findViewById(R.id.flightItemContainer);
            flightNumberText = itemView.findViewById(R.id.flightNumberText);
            flightDurationText = itemView.findViewById(R.id.flightDurationText);
        }
    }
}
