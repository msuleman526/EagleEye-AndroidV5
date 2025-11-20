package io.empowerbits.sightflight.Fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import io.empowerbits.sightflight.R;

/**
 * Empty Tab Fragment - Placeholder for unimplemented tabs
 */
public class EmptyTabFragment extends Fragment {

    private static final String ARG_TAB_NAME = "tab_name";
    private String tabName;

    public static EmptyTabFragment newInstance(String tabName) {
        EmptyTabFragment fragment = new EmptyTabFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TAB_NAME, tabName);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            tabName = getArguments().getString(ARG_TAB_NAME, "");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_empty_tab, container, false);

        TextView titleText = view.findViewById(R.id.emptyTabTitle);
        TextView messageText = view.findViewById(R.id.emptyTabMessage);

        titleText.setText(tabName + " Settings");
        messageText.setText("The " + tabName + " tab will be available in a future update");

        return view;
    }
}
