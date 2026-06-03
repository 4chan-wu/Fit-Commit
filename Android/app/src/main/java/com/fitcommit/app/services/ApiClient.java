package com.fitcommit.app.services;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {
    public static final String BASE_URL = "http://10.0.2.2:8000";
    public static final String BACKEND_CLOTHES_USER_ID = "45482f0f-42d0-401e-a18a-77abeb0492cc";
    public static final boolean ENABLE_AI_ANALYSIS = false;

    private static Retrofit retrofit;

    private ApiClient() {
    }

    public static Retrofit getRetrofit() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL + "/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}
