package com.empowerbits.dronifyit.Adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.empowerbits.dronifyit.models.Project;
import com.empowerbits.dronifyit.R;

import java.util.List;

public class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.ProjectViewHolder> {

    private final List<Project> projectList;
    Context appContext;
    private final OnProjectClickListener listener;
    private final OnProjectEditClickListener listener1;

    public ProjectAdapter(List<Project> projectList, Context context, OnProjectClickListener listener, OnProjectEditClickListener listener1) {
        this.projectList = projectList;
        this.appContext = context;
        this.listener = listener;
        this.listener1 = listener1;
    }

    @NonNull
    @Override
    public ProjectViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_project, parent, false);
        return new ProjectViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProjectViewHolder holder, int position) {
        Project project = projectList.get(position);

        holder.nameText.setText(project.name);
        holder.addressText.setText(project.address);
        holder.dateText.setText(project.survey_date);
        holder.dateText.setText(project.survey_date);

        Glide.with(appContext)
                .load(project.address_image)
                .into(holder.projectImage);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onProjectClick(project);
            }
        });

        holder.pencil.setOnClickListener(v -> {
            if (listener != null) {
                listener1.onProjectEditClick(project);
            }
        });
    }

    @Override
    public int getItemCount() {
        return projectList.size();
    }

    static class ProjectViewHolder extends RecyclerView.ViewHolder {

        ImageView projectImage, pencil;
        TextView nameText, addressText, dateText;

        public ProjectViewHolder(@NonNull View itemView) {
            super(itemView);
            projectImage = itemView.findViewById(R.id.projectImage);
            nameText = itemView.findViewById(R.id.projectName);
            addressText = itemView.findViewById(R.id.projectAddress);
            dateText = itemView.findViewById(R.id.projectDate);
            pencil = itemView.findViewById(R.id.pencil);
        }
    }
}
