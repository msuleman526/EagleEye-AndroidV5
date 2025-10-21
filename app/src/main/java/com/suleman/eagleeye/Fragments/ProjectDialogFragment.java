package com.suleman.eagleeye.Fragments;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.suleman.eagleeye.Activities.AddProject.ProjectInfoActivity;
import com.suleman.eagleeye.Adapters.OnProjectClickListener;
import com.suleman.eagleeye.Adapters.OnProjectEditClickListener;
import com.suleman.eagleeye.Adapters.ProjectAdapter;
import com.suleman.eagleeye.ApiResponse.ProjectsResponse;
import com.suleman.eagleeye.R;
import com.suleman.eagleeye.Retrofit.ApiClient;
import com.suleman.eagleeye.models.Project;
import com.suleman.eagleeye.util.UserSessionManager;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProjectDialogFragment extends DialogFragment {

    private static final String TAG = "ProjectDialogFragment";
    private static final String ARG_UPLOAD_PHOTOS_MODE = "upload_photos_mode";

    List<Project> projectList = new ArrayList<>();
    List<Project> filteredProjectList = new ArrayList<>();
    ProjectAdapter adapter;
    RecyclerView recyclerView;
    EditText searchEditText;
    View loaderLayout;
    UserSessionManager sessionManager;

    // Flag to determine if this is for upload photos functionality
    private boolean isUploadPhotosMode = false;

    // Callback interface for MediaManagerActivity project selection
    public interface ProjectSelectionListener {
        void onProjectSelected(Project project);
        void onProjectSelectionCancelled();
    }

    private ProjectSelectionListener projectSelectionListener;

    public ProjectDialogFragment() {}

    /**
     * Create instance for upload photos functionality
     */
    public static ProjectDialogFragment newInstanceForUploadPhotos() {
        ProjectDialogFragment fragment = new ProjectDialogFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_UPLOAD_PHOTOS_MODE, true);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Set project selection listener for MediaManagerActivity
     */
    public void setProjectSelectionListener(ProjectSelectionListener listener) {
        this.projectSelectionListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.sidebar_projects, container, false);

        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawableResource(R.drawable.secondary_gradient_background);
        }

        // Check if this is upload photos mode
        if (getArguments() != null) {
            isUploadPhotosMode = getArguments().getBoolean(ARG_UPLOAD_PHOTOS_MODE, false);
        }

        Log.d(TAG, "ProjectDialogFragment created. Upload Photos Mode: " + isUploadPhotosMode);

        sessionManager = new UserSessionManager(requireContext());

        loaderLayout = view.findViewById(R.id.loader);
        recyclerView = view.findViewById(R.id.projectRecyclerView);
        searchEditText = view.findViewById(R.id.searchEdt);

        setupSearch();

        view.findViewById(R.id.newProjectButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(getContext(), ProjectInfoActivity.class));
            }
        });
        view.findViewById(R.id.backBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ProjectAdapter(filteredProjectList, requireContext(), new OnProjectClickListener() {
            @Override
            public void onProjectClick(Project project) {
                if (isUploadPhotosMode) {
                    handleUploadPhotosProjectSelection(project);
                } else if (projectSelectionListener != null) {
                    // Handle MediaManager project selection for submit functionality
                    handleMediaManagerProjectSelection(project);
                } else {
                    // Original behavior for mission planner
                    handleMissionPlannerProjectSelection(project);
                }
            }
        }, new OnProjectEditClickListener() {
            @Override
            public void onProjectEditClick(Project project) {
                Intent intent = new Intent(requireContext(), ProjectInfoActivity.class);
                intent.putExtra("project", project);
                startActivity(intent);
                dismiss();
            }
        });
        recyclerView.setAdapter(adapter);
        loadProjectsFromApi();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Handle back button press
        view.setFocusableInTouchMode(true);
        view.requestFocus();
        view.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    dismiss();
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Reconfigure dialog when orientation changes
        resizeDialog();

        Log.d(TAG, "Orientation changed - reconfiguring dialog");
    }

    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not needed
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterProjects(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Not needed
            }
        });
    }

    private void filterProjects(String query) {
        filteredProjectList.clear();

        if (query.isEmpty()) {
            filteredProjectList.addAll(projectList);
        } else {
            String lowerCaseQuery = query.toLowerCase().trim();
            for (Project project : projectList) {
                boolean matches = false;

                // Search by project name (first_name field)
                if (project.first_name != null &&
                        project.first_name.toLowerCase().contains(lowerCaseQuery)) {
                    matches = true;
                }

                // Search by project address
                if (!matches && project.address != null &&
                        project.address.toLowerCase().contains(lowerCaseQuery)) {
                    matches = true;
                }

                // Search by project name field (if different from first_name)
                if (!matches && project.name != null &&
                        project.name.toLowerCase().contains(lowerCaseQuery)) {
                    matches = true;
                }

                if (matches) {
                    filteredProjectList.add(project);
                }
            }
        }

        adapter.notifyDataSetChanged();

        // Log search results for debugging
        Log.d("ProjectDialog", "Search query: '" + query + "', Results: " + filteredProjectList.size());
    }

    private void loadProjectsFromApi() {
        showLoader(true);
        ApiClient.getApiService().projects("Bearer " + sessionManager.getToken()).enqueue(new Callback<ProjectsResponse>() {
            @Override
            public void onResponse(Call<ProjectsResponse> call, Response<ProjectsResponse> response) {
                showLoader(false);
                if (response.isSuccessful()) {
                    projectList.clear();
                    projectList.addAll(response.body().data);
                    filteredProjectList.clear();
                    filteredProjectList.addAll(projectList);
                    adapter.notifyDataSetChanged();
                } else {
                    String errorBody = "";
                    try {
                        errorBody = response.errorBody() != null ? response.errorBody().string() : "null";
                    } catch (Exception e) {
                        Log.e("ProjectDialogFragment", "Error reading errorBody: " + e.getMessage());
                    }

                    Log.e("ProjectDialogFragment", "API call failed. Code: " + response.code() + ", Error: " + errorBody);
                    Toast.makeText(requireContext(), "Unable to show projects.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ProjectsResponse> call, Throwable t) {
                showLoader(false);
                Log.e("ProjectDialogFragment", "API call failed. Message: " + t.getMessage());
                Toast.makeText(requireContext(), "There is some issue.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showLoader(boolean isLoading) {
        loaderLayout.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(isLoading ? View.GONE : View.VISIBLE);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);

        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            // Remove dialog default style for full screen in portrait
            if (dialog.getWindow() != null) {
                dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
            }
        }

        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        resizeDialog();
    }

    public void resizeDialog() {
        if (getDialog() != null && getDialog().getWindow() != null) {
            Window window = getDialog().getWindow();
            int orientation = getResources().getConfiguration().orientation;

            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // Landscape mode: Sidebar from right with 540dp width
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 540, getResources().getDisplayMetrics());
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

    /**
     * Handle project selection for upload photos functionality
     */
    private void handleUploadPhotosProjectSelection(Project project) {
        Log.d(TAG, "Upload Photos - Project selected: " + project.name);

        try {
            // Check if project has flights
            if (project.flights == null || project.flights.isEmpty()) {
                Toast.makeText(requireContext(), "No Flight exists with this project", Toast.LENGTH_SHORT).show();
                return;
            }

            Log.d(TAG, "Project has " + project.flights.size() + " flight(s)");

            if (project.flights.size() == 1) {
                // Only one flight - pass project to MediaManager with index 0
                openMediaManagerWithProject(project, 0);
            } else {
                // Multiple flights - show flight selection dialog
                showFlightSelectionDialog(project);
            }

            dismiss(); // Dismiss current dialog

        } catch (Exception e) {
            Log.e(TAG, "Error handling upload photos project selection: " + e.getMessage());
            Toast.makeText(requireContext(), "Error processing project flights", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Handle project selection for MediaManager submit functionality
     */
    private void handleMediaManagerProjectSelection(Project project) {
        Log.d(TAG, "MediaManager - Project selected for submission: " + project.name);

        try {
            if (projectSelectionListener != null) {
                projectSelectionListener.onProjectSelected(project);
            }
            dismiss();
        } catch (Exception e) {
            Log.e(TAG, "Error handling MediaManager project selection: " + e.getMessage());
            Toast.makeText(requireContext(), "Error processing project selection", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Handle project selection for mission planner (original behavior)
     */
    private void handleMissionPlannerProjectSelection(Project project) {
//        Context context = getContext();
//        if (context instanceof MissionPlanner) {
//            MissionPlanner missionPlanner = (MissionPlanner) context;
//            missionPlanner.onProjectSelectedFromList(project);
//            Log.d(TAG, "Mission Planner - Project selected: " + project.name);
//        } else {
//            Log.e(TAG, "Context is not MissionPlanner, starting new activity");
//            Intent intent = new Intent(requireContext(), MissionPlanner.class);
//            intent.putExtra("project", project);
//            startActivity(intent);
//        }
//        dismiss();
    }

    /**
     * Open MediaManager with project and flight index
     */
    private void openMediaManagerWithProject(Project project, int flightIndex) {
        try {
//            Intent intent = new Intent(requireContext(), MediaManagerActivity.class);
//            intent.putExtra("project", project);
//            intent.putExtra("flight_index", flightIndex);
//            intent.putExtra("upload_photos_mode", true);
//
//            Log.d(TAG, "Opening MediaManager with project: " + project.name + ", flight index: " + flightIndex);
//            startActivity(intent);

        } catch (Exception e) {
            Log.e(TAG, "Error opening MediaManager: " + e.getMessage());
            Toast.makeText(requireContext(), "Error opening Media Manager", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Show flight selection dialog for multiple flights
     */
    private void showFlightSelectionDialog(Project project) {
//        try {
//            FlightSelectionDialogFragment dialog = FlightSelectionDialogFragment.newInstance(project);
//            dialog.setFlightSelectionListener(new FlightSelectionDialogFragment.FlightSelectionListener() {
//                @Override
//                public void onFlightSelected(Project selectedProject, int flightIndex) {
//                    openMediaManagerWithProject(selectedProject, flightIndex);
//                }
//            });
//
//            dialog.show(getParentFragmentManager(), "FlightSelectionDialog");
//            Log.d(TAG, "Flight selection dialog shown for project: " + project.name);
//
//        } catch (Exception e) {
//            Log.e(TAG, "Error showing flight selection dialog: " + e.getMessage());
//            Toast.makeText(requireContext(), "Error showing flight selection", Toast.LENGTH_SHORT).show();
//        }
    }

    @Override
    public void onCancel(@NonNull android.content.DialogInterface dialog) {
        super.onCancel(dialog);

        // Notify listener that selection was cancelled
        if (projectSelectionListener != null) {
            try {
                projectSelectionListener.onProjectSelectionCancelled();
            } catch (Exception e) {
                Log.e(TAG, "Error calling onProjectSelectionCancelled: " + e.getMessage());
            }
        }
    }
}