package io.empowerbits.sightflight.util;

import android.app.Activity;

import java.util.ArrayList;
import java.util.List;

public class ActivityCollector {
    public static List<Activity> activities = new ArrayList<>();

    public static void addActivity(Activity activity) {
        activities.add(activity);
    }

    public static void removeActivity(Activity activity) {
        activities.remove(activity);
    }

    public static void finishLastN(int n) {
        for (int i = 0; i < n; i++) {
            int index = activities.size() - 1;
            if (index >= 0) {
                Activity activity = activities.get(index);
                activity.finish();
                activities.remove(index);
            }
        }
    }
}
