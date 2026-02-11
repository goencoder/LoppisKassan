package se.goencoder.loppiskassan.tools;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client for polling MailHog API to extract magic link API keys.
 */
public final class MailHogClient {
    private static final Pattern API_KEY_PATTERN = Pattern.compile("api_key=([a-f0-9-]+)");
    
    private final String baseUrl;
    private final HttpClient httpClient;
    private final Gson gson;
    
    public MailHogClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
    }
    
    /**
     * Poll MailHog for a magic link email sent to the specified address.
     * 
     * @param email Target email address
     * @param maxAttempts Maximum number of polling attempts
     * @param delayMs Delay between attempts in milliseconds
     * @return Extracted API key from magic link
     * @throws Exception if email not found or parsing fails
     */
    public String pollForMagicLink(String email, int maxAttempts, int delayMs) throws Exception {
        System.out.printf("Polling MailHog for magic link to %s (max %d attempts, %dms delay)%n", 
                email, maxAttempts, delayMs);
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String apiKey = tryFetchApiKey(email);
                if (apiKey != null) {
                    System.out.printf("✓ Found magic link for %s (attempt %d/%d)%n", email, attempt, maxAttempts);
                    return apiKey;
                }
            } catch (IOException e) {
                System.err.printf("Warning: MailHog request failed (attempt %d/%d): %s%n", 
                        attempt, maxAttempts, e.getMessage());
            }
            
            if (attempt < maxAttempts) {
                Thread.sleep(delayMs);
            }
        }
        
        throw new RuntimeException(String.format(
                "Failed to find magic link email for %s after %d attempts. " +
                "Check that backend sent email and MailHog is running at %s",
                email, maxAttempts, baseUrl));
    }
    
    private String tryFetchApiKey(String email) throws IOException, InterruptedException {
        String url = String.format("%s/api/v2/messages?limit=50", baseUrl);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new IOException("MailHog returned status " + response.statusCode());
        }
        
        JsonObject root = gson.fromJson(response.body(), JsonObject.class);
        JsonArray items = root.getAsJsonArray("items");
        
        if (items == null || items.isEmpty()) {
            return null;
        }
        
        // Search for most recent email to target address
        for (int i = 0; i < items.size(); i++) {
            JsonObject msg = items.get(i).getAsJsonObject();
            JsonObject raw = msg.getAsJsonObject("Raw");
            if (raw == null) continue;
            
            JsonArray to = raw.getAsJsonArray("To");
            if (to == null) continue;
            
            // Check if email matches
            boolean found = false;
            for (int j = 0; j < to.size(); j++) {
                String recipient = to.get(j).getAsString();
                if (recipient.contains(email)) {
                    found = true;
                    break;
                }
            }
            
            if (!found) continue;
            
            // Extract body and find API key
            JsonObject content = msg.getAsJsonObject("Content");
            if (content == null) continue;
            
            String body = content.get("Body").getAsString();
            Matcher matcher = API_KEY_PATTERN.matcher(body);
            
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        
        return null;
    }
}
