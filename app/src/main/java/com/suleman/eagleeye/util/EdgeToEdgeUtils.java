package com.suleman.eagleeye.util;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class EdgeToEdgeUtils {

    private EdgeToEdgeUtils() {}

    /** Fullscreen immersive with transparent bars and cutout support */
    public static void applyFullscreen(Activity activity) {
        // Let content lay out behind system bars
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);

        // Make bars transparent (avoid black)
        activity.getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        activity.getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);

        // On Android 10+ disable forced nav-bar contrast (prevents opaque black nav bar)
        if (Build.VERSION.SDK_INT >= 29) {
            activity.getWindow().setNavigationBarContrastEnforced(false);
        }

        // Allow drawing into display cutout (notch)
        if (Build.VERSION.SDK_INT >= 28) {
            activity.getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        // Hide both status & nav bars with swipe-to-show behavior
        View decor = activity.getWindow().getDecorView();
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(activity.getWindow(), decor);
        if (controller != null) {
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            controller.hide(WindowInsetsCompat.Type.systemBars());
        }

        // Ensure your root view actually draws a background behind the bars
        View contentParent = activity.findViewById(android.R.id.content);
        View root = (contentParent instanceof ViewGroup && ((ViewGroup) contentParent).getChildCount() > 0)
                ? ((ViewGroup) contentParent).getChildAt(0)
                : contentParent;

        if (root != null) {
            // NO extra padding — fill the whole screen
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> WindowInsetsCompat.CONSUMED);
            // Make sure the root has a non-transparent background (e.g., your app background)
            if (root.getBackground() == null) {
                root.setBackgroundColor(0xFF000000); // replace with your app background color
            }
            ViewCompat.requestApplyInsets(root);
        }

        // Keep re-applying on window focus (prevents bars from sticking after user gestures)
        decor.setOnSystemUiVisibilityChangeListener(visibility -> {
            // no-op; required only for legacy flags, but harmless here
        });
    }

    /** Call from Activity.onWindowFocusChanged to keep immersive */
    public static void reapplyOnFocus(Activity activity, boolean hasFocus) {
        if (hasFocus) applyFullscreen(activity);
    }
}
