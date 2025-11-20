package io.empowerbits.sightflight.Fragments;

import android.app.Dialog;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import io.empowerbits.sightflight.R;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

/**
 * DroneSettingFragment - Main settings dialog fragment with tabs
 * Similar to ProjectDialogFragment behavior:
 * - Landscape: Right sidebar (540dp width)
 * - Portrait: Full screen
 */
public class DroneSettingFragment extends DialogFragment {

    private static final String TAG = "DroneSettingFragment";

    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private View fragmentContainer;
    private SettingsTabAdapter adapter;

    // Tab titles
    private final String[] tabTitles = {"Safety", "Control", "Camera", "Transmission", "About"};

    // Track if we're showing a sub-screen
    private boolean isShowingSubScreen = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_drone_settings, container, false);

        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawableResource(R.drawable.secondary_gradient_background);
        }

        tabLayout = view.findViewById(R.id.tabLayout);
        viewPager = view.findViewById(R.id.viewPager);
        fragmentContainer = view.findViewById(R.id.fragmentContainer);

        // Setup ViewPager with adapter
        adapter = new SettingsTabAdapter(this);
        viewPager.setAdapter(adapter);

        // Link TabLayout with ViewPager2
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(tabTitles[position])
        ).attach();

        // Back button
        view.findViewById(R.id.backBtn).setOnClickListener(v -> {
            if (isShowingSubScreen) {
                hideSubScreen();
            } else {
                dismiss();
            }
        });

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Handle back button press
        view.setFocusableInTouchMode(true);
        view.requestFocus();
        view.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                if (isShowingSubScreen) {
                    hideSubScreen();
                    return true;
                } else {
                    dismiss();
                    return true;
                }
            }
            return false;
        });
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        resizeDialog();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);

        int orientation = getResources().getConfiguration().orientation;
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
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

    private void resizeDialog() {
        if (getDialog() != null && getDialog().getWindow() != null) {
            Window window = getDialog().getWindow();
            int orientation = getResources().getConfiguration().orientation;

            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                // Landscape mode: Sidebar from right with 540dp width
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 540, getResources().getDisplayMetrics());
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                params.gravity = Gravity.END;
                window.setAttributes(params);
                window.setWindowAnimations(R.style.DialogAnimationFromRight);
            } else {
                // Portrait mode: Full screen
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                params.height = ViewGroup.LayoutParams.MATCH_PARENT;
                params.gravity = Gravity.CENTER;
                window.setAttributes(params);
            }
        }
    }

    /**
     * Show a sub-screen fragment (AR Settings, Battery Info, etc.)
     */
    public void showSubScreen(Fragment fragment) {
        isShowingSubScreen = true;

        // Hide tabs and viewpager
        tabLayout.setVisibility(View.GONE);
        viewPager.setVisibility(View.GONE);

        // Show fragment container
        fragmentContainer.setVisibility(View.VISIBLE);

        // Replace fragment
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.replace(R.id.fragmentContainer, fragment);
        transaction.commit();
    }

    /**
     * Hide sub-screen and return to tabs
     */
    public void hideSubScreen() {
        isShowingSubScreen = false;

        // Show tabs and viewpager
        tabLayout.setVisibility(View.VISIBLE);
        viewPager.setVisibility(View.VISIBLE);

        // Hide fragment container
        fragmentContainer.setVisibility(View.GONE);

        // Clear fragment
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (fragment != null) {
            FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
            transaction.remove(fragment);
            transaction.commit();
        }
    }

    /**
     * ViewPager2 Adapter for tabs
     */
    private class SettingsTabAdapter extends FragmentStateAdapter {

        public SettingsTabAdapter(@NonNull Fragment fragment) {
            super(fragment);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return new SafetyTabFragment();
                case 1:
                    return new ControlsTabFragment();
                case 2:
                    return new CameraTabFragment();
                case 3:
                    return EmptyTabFragment.newInstance("Transmission");
                case 4:
                    return EmptyTabFragment.newInstance("About");
                default:
                    return new SafetyTabFragment();
            }
        }

        @Override
        public int getItemCount() {
            return tabTitles.length;
        }
    }
}
