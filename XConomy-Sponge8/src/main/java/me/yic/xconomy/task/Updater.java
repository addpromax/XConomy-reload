/*
 *  This file (Updater.java) is a part of project XConomy
 *  Copyright (C) YiC and contributors
 *
 *  This program is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the
 *  Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 */
package me.yic.xconomy.task;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.yic.xconomy.XConomy;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class Updater implements Runnable {
    private static final String PROJECT_ID = "UdMN9hv5";
    private static final String PROJECT_URL = "https://modrinth.com/plugin/xconomy-reload";
    private static final String VERSION_PREFIX = "XConomy-Sponge8-";

    public static boolean old = false;
    public static String newVersion = "none";
    public static String downloadUrl = PROJECT_URL;

    @Override
    public void run() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://api.modrinth.com/v2/project/" + PROJECT_ID + "/version");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "XConomy/" + XConomy.PVersion
                    + " (https://github.com/addpromax/XConomy)");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(60000);

            try (InputStream input = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                ReleaseInfo latestRelease = findLatestRelease(response.toString());
                newVersion = latestRelease == null ? null : latestRelease.version;
                if (latestRelease != null) {
                    downloadUrl = latestRelease.downloadUrl;
                }
            }

            if (newVersion == null || !isNewer(newVersion, XConomy.PVersion)) {
                XConomy.getInstance().logger("已是最新版本", 0, null);
                return;
            }

            old = true;
            XConomy.getInstance().logger("发现新版本 ", 0, newVersion);
            XConomy.getInstance().logger(null, 0, downloadUrl);
        } catch (Exception exception) {
            XConomy.getInstance().logger("检查更新失败", 0, null);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static ReleaseInfo findLatestRelease(String response) {
        JsonArray versions = JsonParser.parseString(response).getAsJsonArray();
        ReleaseInfo latestRelease = null;
        for (JsonElement element : versions) {
            JsonObject version = element.getAsJsonObject();
            if (!"release".equalsIgnoreCase(version.get("version_type").getAsString())) {
                continue;
            }
            String versionNumber = version.get("version_number").getAsString();
            if (!versionNumber.startsWith(VERSION_PREFIX)) {
                continue;
            }
            String pluginVersion = versionNumber.substring(VERSION_PREFIX.length());
            if (latestRelease == null || isNewer(pluginVersion, latestRelease.version)) {
                latestRelease = new ReleaseInfo(pluginVersion, getDownloadUrl(version));
            }
        }
        return latestRelease;
    }

    private static String getDownloadUrl(JsonObject version) {
        JsonArray files = version.getAsJsonArray("files");
        for (JsonElement element : files) {
            JsonObject file = element.getAsJsonObject();
            if (file.get("primary").getAsBoolean()) {
                return file.get("url").getAsString();
            }
        }
        return files.size() == 0 ? PROJECT_URL : files.get(0).getAsJsonObject().get("url").getAsString();
    }

    private static boolean isNewer(String candidate, String current) {
        String[] candidateParts = candidate.replaceFirst("^[^0-9]*", "").split("[.-]");
        String[] currentParts = current.replaceFirst("^[^0-9]*", "").split("[.-]");
        int length = Math.max(candidateParts.length, currentParts.length);
        for (int index = 0; index < length; index++) {
            int candidatePart = parseVersionPart(candidateParts, index);
            int currentPart = parseVersionPart(currentParts, index);
            if (candidatePart != currentPart) {
                return candidatePart > currentPart;
            }
        }
        return false;
    }

    private static int parseVersionPart(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index].replaceAll("[^0-9].*$", ""));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private static final class ReleaseInfo {
        private final String version;
        private final String downloadUrl;

        private ReleaseInfo(String version, String downloadUrl) {
            this.version = version;
            this.downloadUrl = downloadUrl;
        }
    }
}
