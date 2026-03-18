package se.goencoder.loppiskassan.rest;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

/**
 * OkHttp interceptor that injects the Authorization header on every request.
 * Reads the current API key from {@link ApiHelper} at request time, so
 * re-authentication is picked up automatically without any header manipulation.
 */
final class AuthInterceptor implements Interceptor {

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();

        // If the request already carries an Authorization header, don't overwrite it.
        if (original.header("Authorization") != null) {
            return chain.proceed(original);
        }

        String apiKey = ApiHelper.INSTANCE.getCurrentApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return chain.proceed(original);
        }

        Request authenticated = original.newBuilder()
                .header("Authorization", "Bearer " + apiKey)
                .build();
        return chain.proceed(authenticated);
    }
}
