package com.nagesh.spotify;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

/** Finds the audio files stored on the device using Android's MediaStore. */
public final class MusicLibrary {

    public static final String UNKNOWN_ARTIST = "Unknown artist";
    public static final String UNKNOWN_ALBUM = "Unknown album";

    /** Songs grouped by album, artist or folder. */
    public static final class Group {
        public final String name;
        public final String subtitle;
        public final List<Song> songs = new ArrayList<>();

        Group(String name, String subtitle) {
            this.name = name;
            this.subtitle = subtitle;
        }
    }

    private MusicLibrary() {
    }

    /** Every audio file on the device, sorted by title. Call this off the main thread. */
    public static List<Song> loadAll(Context context) {
        Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
        };
        List<Song> songs = new ArrayList<>();
        Cursor c = context.getContentResolver().query(collection, projection, null, null, null);
        if (c == null) {
            return songs;
        }
        try {
            int idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID);
            int durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
            while (c.moveToNext()) {
                long id = c.getLong(idCol);
                String path = c.getString(dataCol);
                String title = clean(c.getString(titleCol), fileName(path));
                songs.add(new Song(id, ContentUris.withAppendedId(collection, id), title,
                        clean(c.getString(artistCol), UNKNOWN_ARTIST),
                        clean(c.getString(albumCol), UNKNOWN_ALBUM),
                        c.getLong(albumIdCol), folderName(path), c.getLong(durationCol)));
            }
        } finally {
            c.close();
        }
        Collections.sort(songs, new Comparator<Song>() {
            @Override
            public int compare(Song a, Song b) {
                return a.title.compareToIgnoreCase(b.title);
            }
        });
        return songs;
    }

    /** A song for a file the user opened directly (from the file picker or another app). */
    public static Song fromUri(Context context, Uri uri) {
        String name = null;
        ContentResolver resolver = context.getContentResolver();
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            try {
                Cursor c = resolver.query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null);
                if (c != null) {
                    try {
                        if (c.moveToFirst()) {
                            name = c.getString(0);
                        }
                    } finally {
                        c.close();
                    }
                }
            } catch (RuntimeException e) {
                // Some providers don't support queries; fall back to the URI below
            }
        }
        if (name == null) {
            name = uri.getLastPathSegment();
        }
        String title = stripExtension(clean(name, "Audio file"));
        return new Song(-1, uri, title, UNKNOWN_ARTIST, "Opened files", -1, "Opened files", 0);
    }

    public static List<Group> byAlbum(List<Song> songs) {
        Map<String, Group> groups = new LinkedHashMap<>();
        for (Song s : songs) {
            String key = s.album + "\u0000" + s.albumId;
            Group g = groups.get(key);
            if (g == null) {
                g = new Group(s.album, s.artist);
                groups.put(key, g);
            }
            g.songs.add(s);
        }
        return sorted(groups);
    }

    public static List<Group> byArtist(List<Song> songs) {
        Map<String, Group> groups = new LinkedHashMap<>();
        for (Song s : songs) {
            Group g = groups.get(s.artist);
            if (g == null) {
                g = new Group(s.artist, null);
                groups.put(s.artist, g);
            }
            g.songs.add(s);
        }
        return sorted(groups);
    }

    public static List<Group> byFolder(List<Song> songs) {
        Map<String, Group> groups = new LinkedHashMap<>();
        for (Song s : songs) {
            Group g = groups.get(s.folder);
            if (g == null) {
                g = new Group(s.folder, null);
                groups.put(s.folder, g);
            }
            g.songs.add(s);
        }
        return sorted(groups);
    }

    public static boolean matches(Song s, String query) {
        return query.isEmpty() || contains(s.title, query) || contains(s.artist, query) || contains(s.album, query)
                || contains(s.folder, query);
    }

    public static boolean matches(Group g, String query) {
        return query.isEmpty() || contains(g.name, query) || (g.subtitle != null && contains(g.subtitle, query));
    }

    private static boolean contains(String haystack, String lowerNeedle) {
        return haystack.toLowerCase(Locale.ROOT).contains(lowerNeedle);
    }

    private static List<Group> sorted(Map<String, Group> groups) {
        List<Group> list = new ArrayList<>(groups.values());
        Collections.sort(list, new Comparator<Group>() {
            @Override
            public int compare(Group a, Group b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        return list;
    }

    private static String clean(String value, String fallback) {
        if (value == null || value.trim().isEmpty() || "<unknown>".equals(value)) {
            return fallback;
        }
        return value.trim();
    }

    private static String fileName(String path) {
        return path == null ? "Unknown" : stripExtension(new File(path).getName());
    }

    private static String folderName(String path) {
        if (path == null) {
            return "Music";
        }
        File parent = new File(path).getParentFile();
        return parent == null || parent.getName().isEmpty() ? "Music" : parent.getName();
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
