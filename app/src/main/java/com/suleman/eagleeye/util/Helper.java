package com.suleman.eagleeye.util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.suleman.eagleeye.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper utility class - Java equivalent of Helper.kt from sample code
 * Provides common utility functions for the application
 * 
 * @author Suleman
 * @date 2025-06-26
 */
public class Helper {
    
    private static final String TAG = "Helper";

    /**
     * Convert array to ArrayList of strings
     */
    public static ArrayList<String> makeList(Object[] array) {
        ArrayList<String> list = new ArrayList<>();
        for (Object item : array) {
            list.add(item.toString());
        }
        return list;
    }

    /**
     * Convert int array to ArrayList of strings
     */
    public static ArrayList<String> makeList(int[] array) {
        ArrayList<String> list = new ArrayList<>();
        for (int item : array) {
            list.add(String.valueOf(item));
        }
        return list;
    }

    /**
     * Convert double array to ArrayList of strings
     */
    public static ArrayList<String> makeList(double[] array) {
        ArrayList<String> list = new ArrayList<>();
        for (double item : array) {
            list.add(String.valueOf(item));
        }
        return list;
    }

    /**
     * Convert long array to ArrayList of strings
     */
    public static ArrayList<String> makeList(long[] array) {
        ArrayList<String> list = new ArrayList<>();
        for (long item : array) {
            list.add(String.valueOf(item));
        }
        return list;
    }

    /**
     * Convert List to ArrayList of strings
     */
    public static ArrayList<String> makeList(List<?> list) {
        ArrayList<String> result = new ArrayList<>();
        for (Object item : list) {
            result.add(item.toString());
        }
        return result;
    }

    /**
     * Start browser with URL
     * Handles exceptions gracefully for robust operation
     */
    public static void startBrowser(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);   // Some devices need this
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // Resolve applicationContext issue
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "startBrowser failed: " + e.getMessage());
        }
    }



    /**
     * Open file chooser for specified path
     */
    public static void openFileChooser(String path, Context context) {
        try {
            Uri uri = Uri.parse("content://com.android.externalstorage.documents/document/primary:" + path);
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(Intent.createChooser(intent, "EagleEye"));
        } catch (Exception e) {
            Log.e(TAG, "openFileChooser failed: " + e.getMessage());
        }
    }

    /**
     * Show toast message safely
     */
    public static void showToast(Context context, String message) {
        if (context != null && message != null) {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Check if string is null or empty
     */
    public static boolean isStringEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * Safe string conversion
     */
    public static String safeToString(Object obj) {
        return obj != null ? obj.toString() : "null";
    }
}