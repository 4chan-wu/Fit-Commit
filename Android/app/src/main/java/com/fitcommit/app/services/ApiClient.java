package com.fitcommit.app.services;

import com.fitcommit.app.BuildConfig;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import okhttp3.OkHttpClient;

public final class ApiClient {
    public static final String SUPABASE_URL = trimTrailingSlash(BuildConfig.SUPABASE_URL);
    public static final String SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;
    public static final String SUPABASE_CLOTHING_BUCKET = BuildConfig.SUPABASE_CLOTHING_BUCKET;
    public static final String BASE_URL = SUPABASE_URL + "/rest/v1";
    public static final String AUTH_URL = SUPABASE_URL + "/auth/v1";
    public static final String STORAGE_URL = SUPABASE_URL + "/storage/v1";
    public static final String FUNCTIONS_URL = SUPABASE_URL + "/functions/v1";
    public static final boolean ENABLE_AI_ANALYSIS = false;

    private static Retrofit retrofit;
    private static OkHttpClient httpClient;

    private ApiClient() {
    }

    public static Retrofit getRetrofit() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(AUTH_URL + "/")
                    .client(getHttpClient())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    public static OkHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                    .addInterceptor(chain -> chain.proceed(
                            chain.request().newBuilder()
                                    .header("apikey", SUPABASE_ANON_KEY)
                                    .build()
                    ))
                    .build();
        }
        return httpClient;
    }

    public static boolean isConfigured() {
        return !SUPABASE_URL.isEmpty() && !SUPABASE_ANON_KEY.isEmpty();
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) return "";
        String result = value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }
}
