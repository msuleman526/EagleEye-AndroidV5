package com.suleman.eagleeye.util;

import android.util.Log;

import java.io.File;
import java.io.IOException;

public class KmzCleaner {
    /**
     * Clean the KMZ file to remove corrupt efficiencyFlightModeEnable fields
     * and unsupported gimbal actions for Mini 4 Pro
     */
    static String TAG = "com.suleman.eagleeye";
    public static void cleanKMZFile(File kmzFile, File path) throws IOException {
        Log.d(TAG, "Starting KMZ cleaning process");
        Log.d(TAG, "Original KMZ size: " + kmzFile.length() + " bytes");

        if (kmzFile.length() == 0) {
            throw new IOException("Cannot clean empty KMZ file");
        }

        // Create temporary directory
        File tempDir = new File(path, "temp_kmz_" + System.currentTimeMillis());
        if (!tempDir.mkdirs()) {
            throw new IOException("Failed to create temp directory");
        }

        try {
            // Extract KMZ
            extractZip(kmzFile, tempDir);

            // Verify extraction
            File[] extractedFiles = tempDir.listFiles();
            if (extractedFiles == null || extractedFiles.length == 0) {
                throw new IOException("No files extracted from KMZ");
            }

            Log.d(TAG, "Extracted " + extractedFiles.length + " files");

            // Clean both XML files - CHECK BOTH ROOT AND wpmz SUBDIRECTORY
            File templateKml = new File(tempDir, "template.kml");
            if (!templateKml.exists()) {
                // Try wpmz subdirectory
                templateKml = new File(tempDir, "wpmz/template.kml");
            }

            if (templateKml.exists()) {
                Log.d(TAG, "Found template.kml at: " + templateKml.getPath());
                Log.d(TAG, "Size before cleaning: " + templateKml.length() + " bytes");
                cleanXMLFile(templateKml);
                Log.d(TAG, "Size after cleaning: " + templateKml.length() + " bytes");
            } else {
                Log.w(TAG, "template.kml not found in extracted files");
            }

            File waylinesWpml = new File(tempDir, "waylines.wpml");
            if (!waylinesWpml.exists()) {
                // Try wpmz subdirectory
                waylinesWpml = new File(tempDir, "wpmz/waylines.wpml");
            }

            if (waylinesWpml.exists()) {
                Log.d(TAG, "Found waylines.wpml at: " + waylinesWpml.getPath());
                Log.d(TAG, "Size before cleaning: " + waylinesWpml.length() + " bytes");
                cleanXMLFile(waylinesWpml);

                // ✅ NEW: Remove gimbal actions from waylines.wpml
                removeGimbalActions(waylinesWpml);

                Log.d(TAG, "Size after cleaning: " + waylinesWpml.length() + " bytes");
            } else {
                Log.w(TAG, "waylines.wpml not found in extracted files");
            }

            // Recreate KMZ
            Log.d(TAG, "Recreating KMZ file");
            createZip(tempDir, kmzFile);

            Log.d(TAG, "New KMZ size: " + kmzFile.length() + " bytes");

            if (kmzFile.length() == 0) {
                throw new IOException("Cleaned KMZ file is empty - ZIP creation failed");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error during KMZ cleaning", e);
            throw new IOException("KMZ cleaning failed: " + e.getMessage());
        } finally {
            // Cleanup temp directory
            deleteRecursive(tempDir);
            Log.d(TAG, "Cleaned up temp directory");
        }
    }

    /**
     * ✅ NEW METHOD: Remove unsupported gimbal actions from waylines.wpml
     * Mini 4 Pro does not support: gimbalRotate, gimbalEvenlyRotate
     */
    private static void removeGimbalActions(File xmlFile) throws IOException {
        Log.d(TAG, "Removing unsupported gimbal actions from: " + xmlFile.getName());

        // Read all lines
        java.util.List<String> lines = new java.util.ArrayList<>();
        java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader(xmlFile)
        );
        String line;
        while ((line = reader.readLine()) != null) {
            lines.add(line);
        }
        reader.close();

        Log.d(TAG, "Processing " + lines.size() + " lines for gimbal action removal");

        // Remove gimbal action blocks
        java.util.List<String> cleanedLines = new java.util.ArrayList<>();
        int removedActionGroups = 0;
        int removedActions = 0;
        boolean insideActionGroup = false;
        boolean isGimbalActionGroup = false;
        java.util.List<String> actionGroupBuffer = new java.util.ArrayList<>();
        int actionGroupDepth = 0;

        for (String currentLine : lines) {
            // Detect start of action group
            if (currentLine.trim().equals("<wpml:actionGroup>")) {
                insideActionGroup = true;
                isGimbalActionGroup = false;
                actionGroupBuffer.clear();
                actionGroupBuffer.add(currentLine);
                actionGroupDepth = 1;
                continue;
            }

            // If inside action group, buffer the lines
            if (insideActionGroup) {
                actionGroupBuffer.add(currentLine);

                // Track nested action groups
                if (currentLine.trim().equals("<wpml:actionGroup>")) {
                    actionGroupDepth++;
                }

                // Check if this action group contains gimbal actions
                if (currentLine.contains("<wpml:actionActuatorFunc>gimbalRotate</wpml:actionActuatorFunc>") ||
                        currentLine.contains("<wpml:actionActuatorFunc>gimbalEvenlyRotate</wpml:actionActuatorFunc>")) {
                    isGimbalActionGroup = true;
                    Log.d(TAG, "Found gimbal action group to remove");
                }

                // Detect end of action group
                if (currentLine.trim().equals("</wpml:actionGroup>")) {
                    actionGroupDepth--;

                    if (actionGroupDepth == 0) {
                        // End of action group
                        if (isGimbalActionGroup) {
                            // Skip this entire action group (don't add to cleaned lines)
                            removedActionGroups++;
                            Log.d(TAG, "Removed gimbal action group (" + actionGroupBuffer.size() + " lines)");
                        } else {
                            // Keep this action group (not a gimbal action)
                            cleanedLines.addAll(actionGroupBuffer);
                        }

                        insideActionGroup = false;
                        actionGroupBuffer.clear();
                    }
                }
                continue;
            }

            // Not inside action group, keep the line
            cleanedLines.add(currentLine);
        }

        Log.d(TAG, "Removed " + removedActionGroups + " gimbal action groups");
        Log.d(TAG, "Lines before: " + lines.size() + ", after: " + cleanedLines.size());

        // Write cleaned content back
        java.io.BufferedWriter writer = new java.io.BufferedWriter(
                new java.io.FileWriter(xmlFile)
        );
        for (int i = 0; i < cleanedLines.size(); i++) {
            writer.write(cleanedLines.get(i));
            if (i < cleanedLines.size() - 1) {
                writer.newLine();
            }
        }
        writer.close();

        Log.d(TAG, "✅ Gimbal actions removed from " + xmlFile.getName());
    }

    private static void createZip(File sourceDir, File zipFile) throws IOException {
        java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                new java.io.FileOutputStream(zipFile)
        );

        zipDirectory(sourceDir, sourceDir, zos);

        zos.close();
    }

    /**
     * Recursively add directory contents to ZIP
     */
    private static void zipDirectory(File rootDir, File currentDir, java.util.zip.ZipOutputStream zos)
            throws IOException {
        File[] files = currentDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                // Recursively zip subdirectories
                zipDirectory(rootDir, file, zos);
            } else {
                // Add file to ZIP
                // Get relative path from root
                String relativePath = rootDir.toURI().relativize(file.toURI()).getPath();

                Log.d(TAG, "Adding to ZIP: " + relativePath);

                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(relativePath);
                zos.putNextEntry(entry);

                java.io.FileInputStream fis = new java.io.FileInputStream(file);
                byte[] buffer = new byte[4096];
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, length);
                }
                fis.close();
                zos.closeEntry();
            }
        }
    }

    /**
     * Clean XML file by removing corrupt tags - LINE BY LINE approach
     */
    private static void cleanXMLFile(File xmlFile) throws IOException {
        Log.d(TAG, "Cleaning XML file: " + xmlFile.getName());

        // Read all lines
        java.util.List<String> lines = new java.util.ArrayList<>();
        java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader(xmlFile)
        );
        String line;
        while ((line = reader.readLine()) != null) {
            lines.add(line);
        }
        reader.close();

        Log.d(TAG, "Read " + lines.size() + " lines from " + xmlFile.getName());

        // Filter out bad lines and fix namespace
        java.util.List<String> cleanedLines = new java.util.ArrayList<>();
        int removedCount = 0;

        for (String currentLine : lines) {
            // Skip lines containing efficiencyFlightModeEnable
            if (currentLine.contains("efficiencyFlightModeEnable")) {
                removedCount++;
                Log.d(TAG, "Removing line: " + currentLine.trim());
                continue; // Skip this line
            }

            if (currentLine.contains("<wpml:waypointHeadingAngle>")) {
                removedCount++;
                Log.d(TAG, "Removing gimbalPitchMode line: " + currentLine.trim());
                continue;
            }

            if (currentLine.contains("<wpml:waypointHeadingAngleEnable>")) {
                removedCount++;
                Log.d(TAG, "Removing gimbalPitchMode line: " + currentLine.trim());
                continue;
            }

            if (currentLine.contains("<wpml:waypointHeadingPoiIndex>")) {
                removedCount++;
                Log.d(TAG, "Removing gimbalPitchMode line: " + currentLine.trim());
                continue;
            }

            if (currentLine.contains("<wpml:waypointPoiPoint>")) {
                removedCount++;
                Log.d(TAG, "Removing gimbalPitchMode line: " + currentLine.trim());
                continue;
            }

            // Fix namespace version
            if (currentLine.contains("xmlns:wpml=\"http://www.dji.com/wpmz/1.0.6\"")) {
                currentLine = currentLine.replace(
                        "xmlns:wpml=\"http://www.dji.com/wpmz/1.0.6\"",
                        "xmlns:wpml=\"http://www.dji.com/wpmz/1.0.2\""
                );
                Log.d(TAG, "Fixed namespace version to 1.0.2");
            }

            cleanedLines.add(currentLine);
        }

        Log.d(TAG, "Removed " + removedCount + " corrupt lines from " + xmlFile.getName());

        // Write cleaned content back
        java.io.BufferedWriter writer = new java.io.BufferedWriter(
                new java.io.FileWriter(xmlFile)
        );
        for (int i = 0; i < cleanedLines.size(); i++) {
            writer.write(cleanedLines.get(i));
            if (i < cleanedLines.size() - 1) {
                writer.newLine();
            }
        }
        writer.close();

        Log.d(TAG, "Cleaned " + xmlFile.getName() + " - wrote " + cleanedLines.size() + " lines");
    }

    private static void extractZip(File zipFile, File outputDir) throws IOException {
        Log.d(TAG, "Extracting ZIP: " + zipFile.getName() + " (" + zipFile.length() + " bytes)");

        if (!zipFile.exists() || zipFile.length() == 0) {
            throw new IOException("ZIP file is empty or doesn't exist");
        }

        java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new java.io.FileInputStream(zipFile)
        );
        java.util.zip.ZipEntry entry;

        int fileCount = 0;
        while ((entry = zis.getNextEntry()) != null) {
            File file = new File(outputDir, entry.getName());

            Log.d(TAG, "Extracting: " + entry.getName());

            if (entry.isDirectory()) {
                file.mkdirs();
            } else {
                // Create parent directories
                file.getParentFile().mkdirs();

                // Write file
                java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
                byte[] buffer = new byte[4096];
                int length;
                while ((length = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
                fos.close();
                fileCount++;
            }
            zis.closeEntry();
        }
        zis.close();

        Log.d(TAG, "Extracted " + fileCount + " files from ZIP");
    }

    /**
     * Delete directory recursively
     */
    public static void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }
}