package com.nagesh.spotify;

import java.util.HashSet;
import java.util.Set;

import android.content.Context;
import android.content.SharedPreferences;

/** The user's liked songs, saved on the device. */
public final class Likes {

    private static final String PREFS = "nageshspotify";
    private static final String KEY = "liked";

    private final SharedPreferences prefs;
    private final Set<String> liked;

    public Likes(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        liked = new HashSet<>(prefs.getStringSet(KEY, new HashSet<String>()));
    }

    public boolean isLiked(Song song) {
        return song != null && liked.contains(song.key());
    }

    /** Toggles the like and returns whether the song is now liked. */
    public boolean toggle(Song song) {
        boolean now;
        if (liked.remove(song.key())) {
            now = false;
        } else {
            liked.add(song.key());
            now = true;
        }
        prefs.edit().putStringSet(KEY, new HashSet<>(liked)).apply();
        return now;
    }
}
