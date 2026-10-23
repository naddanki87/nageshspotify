package com.nagesh.spotify;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

/**
 * Album art. Uses the artwork embedded in the music when there is some, and otherwise a gradient
 * tile with the album's initials, like the NageshSpotify web app.
 */
public final class Covers {

    /** Gradient pairs from the web app. */
    static final int[][] PALETTE = {
            {0xFFE8115B, 0xFF3A0CA3}, {0xFFF59B23, 0xFFC0392B}, {0xFF1E3264, 0xFF2D9CDB},
            {0xFF148A08, 0xFF0B3D2E}, {0xFF8D67AB, 0xFF503750}, {0xFF27856A, 0xFF1D4ED8},
            {0xFF509BF5, 0xFF1E3264}, {0xFFDC148C, 0xFFBA5D07},
    };

    private static final Uri ALBUM_ART = Uri.parse("content://media/external/audio/albumart");
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(16 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private static final Set<String> MISSING = new HashSet<>();

    private Covers() {
    }

    public static int[] colorsFor(String name) {
        return PALETTE[(name.hashCode() & 0x7fffffff) % PALETTE.length];
    }

    /** Shows the song's artwork in {@code view}, loading it in the background. */
    public static void show(final ImageView view, final Song song, final int sizePx) {
        final String key = artKey(song) + "@" + sizePx;
        view.setTag(key);
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageBitmap(placeholder(song.album, sizePx));
        synchronized (MISSING) {
            if (MISSING.contains(key)) {
                return;
            }
        }
        final Context context = view.getContext().getApplicationContext();
        EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap art = loadArt(context, song, sizePx);
                if (art == null) {
                    synchronized (MISSING) {
                        MISSING.add(key);
                    }
                    return;
                }
                CACHE.put(key, art);
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (key.equals(view.getTag())) {
                            view.setImageBitmap(art);
                        }
                    }
                });
            }
        });
    }

    /** Shows a gradient tile for a group (album, artist or folder). */
    public static void showGroup(ImageView view, MusicLibrary.Group group, int sizePx) {
        Song first = group.songs.get(0);
        if (group.subtitle != null && first.albumId >= 0) {
            show(view, first, sizePx); // albums: use their artwork
        } else {
            view.setTag(null);
            view.setImageBitmap(placeholder(group.name, sizePx));
        }
    }

    /** The song's artwork, or the gradient tile if it has none. Blocks; call off the main thread. */
    public static Bitmap artOrPlaceholder(Context context, Song song, int sizePx) {
        Bitmap art = loadArt(context, song, sizePx);
        return art != null ? art : placeholder(song.album, sizePx);
    }

    private static String artKey(Song song) {
        return song.albumId >= 0 ? "album:" + song.albumId : song.key();
    }

    private static Bitmap loadArt(Context context, Song song, int sizePx) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                return context.getContentResolver().loadThumbnail(song.uri, new Size(sizePx, sizePx), null);
            }
            if (song.albumId < 0) {
                return null;
            }
            InputStream in = context.getContentResolver()
                    .openInputStream(ContentUris.withAppendedId(ALBUM_ART, song.albumId));
            if (in == null) {
                return null;
            }
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 1;
                Bitmap full = BitmapFactory.decodeStream(in, null, opts);
                return full == null ? null : Bitmap.createScaledBitmap(full, sizePx, sizePx, true);
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return null; // no embedded artwork
        }
    }

    public static Bitmap placeholder(String name, int sizePx) {
        String key = "ph:" + name + "@" + sizePx;
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        int[] colors = colorsFor(name);
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint bg = new Paint();
        bg.setShader(new LinearGradient(0, 0, sizePx, sizePx, colors[0], colors[1], Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, sizePx, sizePx, bg);

        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(0xFFFFFFFF);
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setTextSize(sizePx * 0.32f);
        text.setShadowLayer(sizePx * 0.03f, 0, sizePx * 0.01f, 0x55000000);
        String initials = initials(name);
        Rect bounds = new Rect();
        text.getTextBounds(initials, 0, initials.length(), bounds);
        canvas.drawText(initials, (sizePx - bounds.width()) / 2f - bounds.left,
                (sizePx + bounds.height()) / 2f - bounds.bottom, text);
        CACHE.put(key, bmp);
        return bmp;
    }

    static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        for (String word : name.trim().split("\\s+")) {
            if (!word.isEmpty() && sb.length() < 2) {
                sb.appendCodePoint(word.codePointAt(0));
            }
        }
        return sb.length() == 0 ? "♪" : sb.toString().toUpperCase();
    }
}
