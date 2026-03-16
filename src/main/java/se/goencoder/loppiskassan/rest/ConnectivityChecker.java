package se.goencoder.loppiskassan.rest;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Proactive connectivity check for iLoppis backend.
 * Uses the shared ApiHelper OkHttpClient (with short timeouts) to verify reachability.
 */
public class ConnectivityChecker {

    private static final Logger log = Logger.getLogger(ConnectivityChecker.class.getName());

    // Timeout: 2 seconds balances responsiveness with reliability
    private static final int CHECK_TIMEOUT_MS = 2000;
    private static volatile boolean lastKnownOnline = false;

    /**
     * Check if the backend is reachable.
     * Makes a lightweight HEAD request and checks for a 2xx response.
     * Uses the shared ApiHelper client (with tighter timeouts for health checks).
     *
     * @return true if server responds within timeout
     */
    public static boolean isOnline() {
        try {
            String baseUrl = ApiHelper.INSTANCE.getBasePath();
            // Derive a short-timeout client from the shared one (inherits interceptors)
            OkHttpClient client = ApiHelper.INSTANCE.getHttpClient().newBuilder()
                    .connectTimeout(CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .readTimeout(CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .writeTimeout(CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .build();

            Request request = new Request.Builder()
                    .url(baseUrl)
                    .head()
                    .build();

            try (Response response = client.newCall(request).execute()) {
                boolean online = response.isSuccessful();
                lastKnownOnline = online;
                return online;
            }

        } catch (Exception e) {
            log.fine("Connectivity check failed: " + e.getMessage());
            lastKnownOnline = false;
            return false;
        }
    }

    /**
     * Get the last known connectivity state without making a new request.
     *
     * @return true if last check was online
     */
    public static boolean isLastKnownOnline() {
        return lastKnownOnline;
    }
}
