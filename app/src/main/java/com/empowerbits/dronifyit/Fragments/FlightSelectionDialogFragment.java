package com.empowerbits.dronifyit.Fragments;

import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.empowerbits.dronifyit.Adapters.FlightSelectionAdapter;
import com.empowerbits.dronifyit.models.FlightLog;
import com.empowerbits.dronifyit.models.Project;
import com.empowerbits.dronifyit.R;

import java.util.ArrayList;
import java.util.List;

public class FlightSelectionDialogFragment extends DialogFragment {

    private static final String TAG = "FlightSelectionDialog";
    private static final String ARG_PROJECT = "project";

    private Project project;
    private List<FlightLog> flightsList = new ArrayList<>();
    private FlightSelectionAdapter adapter;
    private RecyclerView recyclerView;
    private TextView titleText;
    private FlightSelectionListener listener;

    public interface FlightSelectionListener {
        void onFlightSelected(Project project, int flightIndex);
    }

    public static FlightSelectionDialogFragment newInstance(Project project) {
        FlightSelectionDialogFragment fragment = new FlightSelectionDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_PROJECT, project);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            project = (Project) getArguments().getSerializable(ARG_PROJECT);
            if (project != null && project.flights != null) {
                flightsList.addAll(project.flights);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_flight_selection, container, false);

        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawableResource(R.drawable.secondary_gradient_background);
            getDialog().setTitle("Select Flight");
        }

        initializeViews(view);
        setupRecyclerView();
        updateTitle();

        Log.d(TAG, "FlightSelectionDialog created with " + flightsList.size() + " flights");

        return view;
    }

    private void initializeViews(View view) {
        recyclerView = view.findViewById(R.id.flightRecyclerView);
        titleText = view.findViewById(R.id.titleText);
        view.findViewById(R.id.backBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });
    }

    public void resizeDialog() {
        if (getDialog() != null && getDialog().getWindow() != null) {
            Window window = getDialog().getWindow();
            int orientation = getResources().getConfiguration().orientation;

            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // Landscape mode: Sidebar from right with 540dp width
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 440, getResources().getDisplayMetrics());
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                params.gravity = Gravity.END; // Align to right
                window.setAttributes(params);

                // Apply slide in animation from right
                window.setWindowAnimations(R.style.DialogAnimationFromRight);

                Log.d(TAG, "Dialog configured for LANDSCAPE - Width: 540dp, Height: MATCH_PARENT, Gravity: END");
            } else {
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                params.gravity = Gravity.CENTER; // Center for portrait
                window.setAttributes(params);
            }
        }
    }

    private void setupRecyclerView() {
        adapter = new FlightSelectionAdapter(flightsList, new FlightSelectionAdapter.OnFlightClickListener() {
            @Override
            public void onFlightClick(int position) {
                Log.d(TAG, "Flight selected at position: " + position);
                
                if (listener != null) {
                    listener.onFlightSelected(project, position);
                }
                
                dismiss();
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
    }

    private void updateTitle() {
        if (titleText != null && project != null) {
            String title = (project.name != null ? project.name + " Flights" : "Project Flights");
            titleText.setText(title);
        }
    }

    public void setFlightSelectionListener(FlightSelectionListener listener) {
        this.listener = listener;
    }

    @Override
    public void onStart() {
        super.onStart();
        resizeDialog();
    }
}
