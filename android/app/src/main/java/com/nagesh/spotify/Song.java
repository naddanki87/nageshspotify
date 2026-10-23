package com.nagesh.spotify;

import android.net.Uri;

/** An audio file the app can play: either from the device's media library or opened directly. */
public final class Song {

    public final long id;
    public final Uri uri;
    public final String title;
    public final String artist;
    public final String album;
    public final long albumId;
    public final String folder;
    public final long durationMs;

    public Song(long id, Uri uri, String title, String artist, String album, long albumId, String folder,
            long durationMs) {
        this.id = id;
        this.uri = uri;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.albumId = albumId;
        this.folder = folder;
        this.durationMs = durationMs;
    }

    /** Stable key used for likes and for comparing songs. */
    public String key() {
        return uri.toString();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Song && ((Song) o).key().equals(key());
    }

    @Override
    public int hashCode() {
        return key().hashCode();
    }

    public static String formatTime(long ms) {
        if (ms <= 0) {
            return "0:00";
        }
        long sec = ms / 1000;
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }
}
