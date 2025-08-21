package se.goencoder.loppiskassan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.*;
import se.goencoder.iloppis.model.EventFilter;
import se.goencoder.iloppis.model.FilterEventsRequest;
import se.goencoder.iloppis.model.Pagination;
import se.goencoder.iloppis.invoker.JSON;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * A simple standalone client for testing connections to the iLoppis API
 * This bypasses the auto-generated API client which has issues with content type
 */
public class SimpleApiClient {

    public static void main(String[] args) {
        System.out.println("Starting simple API client test...");

        try {
            // Create a request similar to what FilterEventsRequest would do
            FilterEventsRequest request = new FilterEventsRequest();
            EventFilter filter = new EventFilter();
            filter.setDateFrom("2025-01-01");
            request.setFilter(filter);

            Pagination pagination = new Pagination();
            pagination.setPageSize(100);
            request.setPagination(pagination);

            // Use the same JSON serializer that the API client would use
            JSON json = new JSON();
            String jsonBody = json.serialize(request);

            System.out.println("Request body: " + jsonBody);

            // Create OkHttp client manually
            OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();

            // Build the request manually with proper content type
            Request httpRequest = new Request.Builder()
                .url("http://127.0.0.1:8080/v1/events:filter")
                .post(RequestBody.create(MediaType.get("application/json"), jsonBody.getBytes(StandardCharsets.UTF_8)))
                .build();

            System.out.println("Sending request to: " + httpRequest.url());

            // Execute the request
            try (Response response = client.newCall(httpRequest).execute()) {
                if (response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "No response body";
                    System.out.println("Successful response: " + responseBody);
                } else {
                    System.out.println("Error response: " + response.code() + " - " + response.message());
                    if (response.body() != null) {
                        System.out.println(response.body().string());
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Error in API client: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
