package com.nagesh.spotify;

import android.content.Context;
import android.content.SharedPreferences;

/** The listener's name, asked for once on first launch and saved on the device. */
public final class Profile {

    private static final String PREFS = "nageshspotify";
    private static final String KEY_NAME = "user_name";

    private final SharedPreferences prefs;

    public Profile(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The saved name, or null if the user hasn't registered yet. */
    public String name() {
        String name = prefs.getString(KEY_NAME, null);
        return name == null || name.trim().isEmpty() ? null : name;
    }

    public boolean isRegistered() {
        return name() != null;
    }

    public void setName(String name) {
        prefs.edit().putString(KEY_NAME, name.trim()).apply();
    }
}
