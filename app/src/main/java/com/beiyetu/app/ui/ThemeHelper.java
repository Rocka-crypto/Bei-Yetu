package com.beiyetu.app.ui;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/** Remembers the user's choice of System, Light or Dark and applies it. */
public final class ThemeHelper {

    public static final int SYSTEM = 0;
    public static final int LIGHT = 1;
    public static final int DARK = 2;

    private static final String PREFS = "beiyetu_prefs";
    private static final String KEY = "theme_mode";

    private ThemeHelper() { }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int get(Context c) {
        return prefs(c).getInt(KEY, SYSTEM);
    }

    /** Saves the choice and applies it straight away (the screen redraws itself). */
    public static void set(Context c, int mode) {
        prefs(c).edit().putInt(KEY, mode).apply();
        apply(c);
    }

    /** Call once at start-up, before the screen is created. */
    public static void apply(Context c) {
        switch (get(c)) {
            case LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}