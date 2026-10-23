package com.nagesh.spotify;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.SharedPreferences;

/** Song links (http/https URLs) the user added to the Online tab, in the order they were added. */
public final class SavedLinks {

    private static final String PREFS = "nageshspotify";
    private static final String KEY = "links";

    private final SharedPreferences prefs;

    public SavedLinks(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public List<String> all() {
        List<String> links = new ArrayList<>();
        for (String line : prefs.getString(KEY, "").split("\n")) {
            if (!line.trim().isEmpty()) {
                links.add(line.trim());
            }
        }
        return links;
    }

    public boolean contains(String url) {
        return all().contains(url);
    }

    public void add(String url) {
        List<String> links = all();
        if (!links.contains(url)) {
            links.add(url);
            save(links);
        }
    }

    public void remove(String url) {
        List<String> links = all();
        links.remove(url);
        save(links);
    }

    private void save(List<String> links) {
        StringBuilder sb = new StringBuilder();
        for (String link : links) {
            sb.append(link).append('\n');
        }
        prefs.edit().putString(KEY, sb.toString()).apply();
    }
}
