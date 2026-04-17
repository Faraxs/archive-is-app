package com.archiver;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShareActivity extends Activity {

    private static final String ARCHIVE_BASE = "https://archive.is/";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String sharedText = getSharedText();

        if (sharedText == null || sharedText.isEmpty()) {
            toast("No URL found");
            finish();
            return;
        }

        String url = extractUrl(sharedText);

        if (url == null) {
            toast("No URL found in shared text");
            finish();
            return;
        }

        toast("Checking archive.is…");
        checkAndArchive(url);
    }

    private String getSharedText() {
        Intent intent = getIntent();
        if (intent == null) return null;
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            return intent.getStringExtra(Intent.EXTRA_TEXT);
        }
        return null;
    }

    private String extractUrl(String text) {
        if (text.startsWith("http://") || text.startsWith("https://")) {
            return text.trim();
        }
        // Extract URL from text that may contain other content
        Pattern pattern = Pattern.compile("https?://[^\\s]+");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private void checkAndArchive(String articleUrl) {
        executor.execute(() -> {
            String existingArchiveUrl = checkExisting(articleUrl);
            mainHandler.post(() -> {
                if (existingArchiveUrl != null) {
                    // Already archived — open the existing snapshot
                    toast("Already archived — opening…");
                    openUrl(existingArchiveUrl);
                } else {
                    // Not archived — submit for archiving
                    toast("Archiving now…");
                    openUrl(ARCHIVE_BASE + "?run=1&url=" + Uri.encode(articleUrl));
                }
                finish();
            });
        });
    }

    /**
     * Checks archive.is for an existing snapshot.
     * Uses the /newest/ endpoint — if it redirects to an archive URL, the page is archived.
     * Returns the archive URL if found, null if not.
     */
    private String checkExisting(String articleUrl) {
        try {
            String checkUrl = ARCHIVE_BASE + "newest/" + Uri.encode(articleUrl);
            HttpURLConnection conn = (HttpURLConnection) new URL(checkUrl).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            int responseCode = conn.getResponseCode();
            String location = conn.getHeaderField("Location");
            conn.disconnect();

            // archive.is/newest/ returns 302 to the archive if it exists
            // or 404 / redirects to submit page if it doesn't
            if ((responseCode == 301 || responseCode == 302) && location != null) {
                if (location.startsWith(ARCHIVE_BASE) && !location.contains("submit") && !location.contains("/?url=")) {
                    return location;
                }
            }
        } catch (IOException e) {
            // Network error — fall through to archive fresh
        }
        return null;
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}
