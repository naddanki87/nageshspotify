package com.nagesh.spotify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.widget.Toast;

/**
 * Plays the queue with {@link MediaPlayer} and keeps playing in the background, with a media
 * notification and lock-screen / headset controls through a {@link MediaSession}.
 */
public class PlayerService extends Service implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, AudioManager.OnAudioFocusChangeListener {

    public interface Listener {
        void onPlayerChanged();
    }

    public class LocalBinder extends Binder {
        public PlayerService getService() {
            return PlayerService.this;
        }
    }

    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;

    private static final String ACTION_TOGGLE = "com.nagesh.spotify.TOGGLE";
    private static final String ACTION_NEXT = "com.nagesh.spotify.NEXT";
    private static final String ACTION_PREV = "com.nagesh.spotify.PREV";
    private static final String ACTION_STOP = "com.nagesh.spotify.STOP";
    private static final String CHANNEL_ID = "playback";
    private static final int NOTIFICATION_ID = 1;
    private static final int ART_SIZE = 512;

    private final IBinder binder = new LocalBinder();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());

    /** Songs in play order. */
    private final List<Song> queue = new ArrayList<>();
    /** The queue before shuffling, so turning shuffle off restores it. */
    private final List<Song> original = new ArrayList<>();
    private int index = -1;
    private boolean shuffle;
    private int repeat = REPEAT_OFF;

    private MediaPlayer player;
    private boolean prepared;
    private boolean playWhenReady;
    private boolean resumeOnFocusGain;
    private boolean foreground;
    private boolean noisyRegistered;
    private Bitmap art;

    private AudioManager audioManager;
    private NotificationManager notifications;
    private MediaSession session;

    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            pause(); // headphones unplugged
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        notifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            notifications.createNotificationChannel(channel);
        }
        session = new MediaSession(this, "NageshSpotify");
        // Needed before Android 8 for headset and lock-screen buttons
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                play();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onSkipToNext() {
                next();
            }

            @Override
            public void onSkipToPrevious() {
                previous();
            }

            @Override
            public void onSeekTo(long pos) {
                seekTo((int) pos);
            }

            @Override
            public void onStop() {
                stop();
            }
        });
        session.setSessionActivity(PendingIntent.getActivity(this, 0, new Intent(this, NowPlayingActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        session.setActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_TOGGLE.equals(action)) {
            toggle();
        } else if (ACTION_NEXT.equals(action)) {
            next();
        } else if (ACTION_PREV.equals(action)) {
            previous();
        } else if (ACTION_STOP.equals(action)) {
            stop();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (!isPlaying()) {
            stop();
        }
    }

    @Override
    public void onDestroy() {
        releasePlayer();
        if (noisyRegistered) {
            unregisterReceiver(noisyReceiver);
            noisyRegistered = false;
        }
        audioManager.abandonAudioFocus(this);
        session.release();
        super.onDestroy();
    }

    // ---------- Listeners ----------

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void changed() {
        for (Listener l : listeners) {
            l.onPlayerChanged();
        }
    }

    // ---------- State ----------

    public Song current() {
        return index >= 0 && index < queue.size() ? queue.get(index) : null;
    }

    public boolean isPlaying() {
        return player != null && prepared && player.isPlaying();
    }

    public int position() {
        return player != null && prepared ? player.getCurrentPosition() : 0;
    }

    public int duration() {
        if (player != null && prepared) {
            return player.getDuration();
        }
        Song s = current();
        return s == null ? 0 : (int) s.durationMs;
    }

    public boolean isShuffle() {
        return shuffle;
    }

    public int repeatMode() {
        return repeat;
    }

    // ---------- Commands ----------

    /** Replaces the queue with {@code songs} and plays from {@code start}. */
    public void playAll(List<Song> songs, int start, boolean shuffled) {
        if (songs.isEmpty()) {
            return;
        }
        // Keep the service alive after the screen that started it goes away
        startService(new Intent(this, PlayerService.class));
        if (shuffled) {
            shuffle = true;
        }
        original.clear();
        original.addAll(songs);
        queue.clear();
        if (shuffle) {
            List<Song> rest = new ArrayList<>(songs);
            Song first = shuffled && start < 0 ? null : rest.remove(Math.max(start, 0));
            Collections.shuffle(rest);
            if (first != null) {
                queue.add(first);
            }
            queue.addAll(rest);
            index = 0;
        } else {
            queue.addAll(songs);
            index = Math.max(start, 0);
        }
        load(true);
    }

    public void toggle() {
        if (isPlaying()) {
            pause();
        } else {
            play();
        }
    }

    public void play() {
        if (current() == null) {
            return;
        }
        if (player == null) {
            load(true);
        } else if (prepared) {
            startPlayback();
        } else {
            playWhenReady = true;
        }
    }

    public void pause() {
        playWhenReady = false;
        if (isPlaying()) {
            player.pause();
        }
        if (foreground) {
            stopForeground(false);
            foreground = false;
        }
        publish();
    }

    public void next() {
        advance(false);
    }

    public void previous() {
        if (queue.isEmpty()) {
            return;
        }
        if (position() > 3000) {
            seekTo(0);
            return;
        }
        index = index > 0 ? index - 1 : queue.size() - 1;
        load(true);
    }

    public void seekTo(int ms) {
        if (player != null && prepared) {
            player.seekTo(ms);
            publish();
        }
    }

    public void setShuffle(boolean on) {
        shuffle = on;
        Song cur = current();
        if (cur != null) {
            queue.clear();
            if (on) {
                List<Song> rest = new ArrayList<>(original);
                rest.remove(cur);
                Collections.shuffle(rest);
                queue.add(cur);
                queue.addAll(rest);
                index = 0;
            } else {
                queue.addAll(original);
                index = Math.max(0, queue.indexOf(cur));
            }
        }
        changed();
    }

    public void cycleRepeat() {
        repeat = (repeat + 1) % 3;
        changed();
    }

    public void stop() {
        pause();
        releasePlayer();
        stopForeground(true);
        foreground = false;
        notifications.cancel(NOTIFICATION_ID);
        stopSelf();
        changed();
    }

    // ---------- Playback ----------

    private void advance(boolean auto) {
        if (queue.isEmpty()) {
            return;
        }
        if (auto && repeat == REPEAT_ONE) {
            player.seekTo(0);
            startPlayback();
            return;
        }
        if (index < queue.size() - 1) {
            index++;
        } else if (repeat == REPEAT_ALL || !auto) {
            index = 0;
        } else {
            pause(); // end of the queue
            seekTo(0);
            return;
        }
        load(true);
    }

    private void load(boolean autoplay) {
        final Song song = current();
        if (song == null) {
            return;
        }
        releasePlayer();
        player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
        player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        player.setOnPreparedListener(this);
        player.setOnCompletionListener(this);
        player.setOnErrorListener(this);
        prepared = false;
        playWhenReady = autoplay;
        try {
            player.setDataSource(this, song.uri);
            player.prepareAsync();
        } catch (Exception e) {
            failed(song);
            return;
        }
        art = null;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Bitmap bmp = Covers.artOrPlaceholder(PlayerService.this, song, ART_SIZE);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (song.equals(current())) {
                            art = bmp;
                            publish();
                        }
                    }
                });
            }
        }).start();
        publish();
    }

    private void startPlayback() {
        int focus = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        if (focus != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            return;
        }
        player.setVolume(1f, 1f);
        player.start();
        playWhenReady = false;
        if (!noisyRegistered) {
            registerReceiver(noisyReceiver, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
            noisyRegistered = true;
        }
        publish();
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
        prepared = false;
    }

    private void failed(Song song) {
        String message = "Can't play “" + song.title + "”";
        if (MusicLibrary.isWebLink(song.uri)) {
            message += ". Check your internet connection.";
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        releasePlayer();
        publish();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        prepared = true;
        if (playWhenReady) {
            startPlayback();
        } else {
            publish();
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        advance(true);
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Song song = current();
        if (song != null) {
            failed(song);
        }
        return true;
    }

    @Override
    public void onAudioFocusChange(int change) {
        if (player == null || !prepared) {
            return;
        }
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
                resumeOnFocusGain = false;
                pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                resumeOnFocusGain = isPlaying();
                pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                player.setVolume(0.2f, 0.2f);
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                player.setVolume(1f, 1f);
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false;
                    startPlayback();
                }
                break;
            default:
                break;
        }
    }

    // ---------- Session and notification ----------

    /** Pushes the current state to the media session, the notification and the UI. */
    private void publish() {
        Song song = current();
        boolean playing = isPlaying();
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE
                | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                | PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_STOP;
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(actions)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, position(),
                        playing ? 1f : 0f)
                .build());
        if (song != null) {
            MediaMetadata.Builder meta = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, song.title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, song.artist)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, song.album)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, duration());
            if (art != null) {
                meta.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art);
            }
            session.setMetadata(meta.build());
            updateNotification(song, playing);
        }
        changed();
    }

    private void updateNotification(Song song, boolean playing) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(song.title)
                .setContentText(song.artist)
                .setSubText(song.album)
                .setLargeIcon(art)
                .setShowWhen(false)
                .setOngoing(playing)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, NowPlayingActivity.class),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
                .setDeleteIntent(serviceIntent(ACTION_STOP))
                .addAction(action(R.drawable.ic_prev, R.string.previous, ACTION_PREV))
                .addAction(playing ? action(R.drawable.ic_pause, R.string.pause, ACTION_TOGGLE)
                        : action(R.drawable.ic_play, R.string.play, ACTION_TOGGLE))
                .addAction(action(R.drawable.ic_next, R.string.next, ACTION_NEXT))
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(session.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2));
        Notification n = b.build();
        if (playing && !foreground) {
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
                } else {
                    startForeground(NOTIFICATION_ID, n);
                }
                foreground = true;
            } catch (RuntimeException e) {
                // Android 12+ can refuse to start a foreground service from the background;
                // keep playing and just show the notification.
                notifySafely(n);
            }
        } else {
            notifySafely(n);
        }
    }

    private void notifySafely(Notification n) {
        try {
            notifications.notify(NOTIFICATION_ID, n);
        } catch (SecurityException e) {
            // Notification permission denied: playback still works without it
        }
    }

    private Notification.Action action(int icon, int title, String action) {
        return new Notification.Action.Builder(Icon.createWithResource(this, icon), getString(title),
                serviceIntent(action)).build();
    }

    private PendingIntent serviceIntent(String action) {
        Intent intent = new Intent(this, PlayerService.class).setAction(action);
        return PendingIntent.getService(this, action.hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
