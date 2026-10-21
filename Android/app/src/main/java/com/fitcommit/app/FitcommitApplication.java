package com.fitcommit.app;

import android.app.Application;

import com.fitcommit.app.utils.LanguageManager;

public class FitcommitApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        LanguageManager.applySavedLanguage(this);
    }
}
