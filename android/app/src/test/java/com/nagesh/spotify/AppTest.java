package com.nagesh.spotify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.Manifest;
import android.app.Application;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowMediaPlayer;
import org.robolectric.shadows.util.DataSource;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AppTest {

    private Application app;
    /** The service the activities bind to. */
    private PlayerService service;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS);
        service = Robolectric.buildService(PlayerService.class).create().get();
        shadowOf(app).setComponentNameAndServiceForBindService(new ComponentName(app, PlayerService.class),
                service.onBind(null));
    }

    private static Song song(int n, String title, String artist, String album, long albumId, String folder) {
        Uri uri = Uri.parse("content://media/external/audio/media/" + n);
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(RuntimeEnvironment.getApplication(), uri),
                new ShadowMediaPlayer.MediaInfo(180_000, 0));
        return new Song(n, uri, title, artist, album, albumId, folder, 180_000);
    }

    private List<Song> songs() {
        return new ArrayList<>(Arrays.asList(
                song(1, "Voltage Bloom", "Neon Harbor", "Midnight Circuits", 1, "Music"),
                song(2, "Afterglow Protocol", "Neon Harbor", "Midnight Circuits", 1, "Music"),
                song(3, "Sunset Freeway", "Saffron Skies", "Golden Hour Drive", 2, "Download")));
    }

    @Test
    public void mainScreenStartsAndShowsTheEmptyState() {
        new Profile(app).setName("Nagesh");
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        TextView greeting = activity.findViewById(R.id.greeting);
        assertTrue(greeting.getText().toString().endsWith(", Nagesh"));
        assertNotNull(activity.findViewById(R.id.list));
        // No media provider in the test environment, so the library is empty
        activity.findViewById(R.id.tab_albums).performClick();
        activity.findViewById(R.id.tab_liked).performClick();
        assertEquals(0, ((ListView) activity.findViewById(R.id.list)).getCount());
        assertEquals(View.GONE, activity.findViewById(R.id.mini_player).getVisibility());

        // Starting playback shows the mini player
        service.playAll(songs(), 0, false);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(View.VISIBLE, activity.findViewById(R.id.mini_player).getVisibility());
        assertEquals("Voltage Bloom", ((TextView) activity.findViewById(R.id.mini_title)).getText().toString());
        activity.findViewById(R.id.mini_play).performClick();
        assertFalse(service.isPlaying());
    }

    @Test
    public void nowPlayingScreenShowsTheCurrentSong() {
        service.playAll(songs(), 2, false);
        shadowOf(Looper.getMainLooper()).idle();
        NowPlayingActivity activity = Robolectric.buildActivity(NowPlayingActivity.class).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("Sunset Freeway", ((TextView) activity.findViewById(R.id.np_title)).getText().toString());
        assertEquals("3:00", ((TextView) activity.findViewById(R.id.np_duration)).getText().toString());
        activity.findViewById(R.id.np_next).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("Voltage Bloom", ((TextView) activity.findViewById(R.id.np_title)).getText().toString());
        activity.findViewById(R.id.np_shuffle).performClick();
        assertTrue(service.isShuffle());
        activity.findViewById(R.id.np_like).performClick();
        assertTrue(new Likes(app).isLiked(service.current()));
    }

    @Test
    public void playsTheQueueWithShuffleAndRepeat() {
        PlayerService player = service;
        List<Song> songs = songs();

        player.playAll(songs, 1, false);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("Afterglow Protocol", player.current().title);
        assertTrue(player.isPlaying());
        NotificationManager nm = (NotificationManager) app.getSystemService(Application.NOTIFICATION_SERVICE);
        assertEquals(1, shadowOf(nm).getAllNotifications().size());

        player.next();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("Sunset Freeway", player.current().title);
        player.next(); // wraps around when skipping manually
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("Voltage Bloom", player.current().title);
        player.previous();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("Sunset Freeway", player.current().title);

        player.pause();
        assertFalse(player.isPlaying());
        player.toggle();
        assertTrue(player.isPlaying());

        player.setShuffle(true);
        assertEquals("Sunset Freeway", player.current().title); // current song stays first
        player.setShuffle(false);
        assertEquals("Sunset Freeway", player.current().title);

        player.cycleRepeat();
        assertEquals(PlayerService.REPEAT_ALL, player.repeatMode());
        player.cycleRepeat();
        assertEquals(PlayerService.REPEAT_ONE, player.repeatMode());

        player.stop();
        assertFalse(player.isPlaying());
    }

    @Test
    public void groupsSongsByAlbumArtistAndFolder() {
        List<Song> songs = songs();
        List<MusicLibrary.Group> albums = MusicLibrary.byAlbum(songs);
        assertEquals(2, albums.size());
        assertEquals("Golden Hour Drive", albums.get(0).name);
        assertEquals(2, albums.get(1).songs.size());
        assertEquals(2, MusicLibrary.byArtist(songs).size());
        assertEquals("Download", MusicLibrary.byFolder(songs).get(0).name);
        assertTrue(MusicLibrary.matches(songs.get(2), "saffron"));
        assertFalse(MusicLibrary.matches(songs.get(0), "saffron"));
    }

    @Test
    public void likesAreSaved() {
        Song s = songs().get(0);
        Likes likes = new Likes(app);
        assertFalse(likes.isLiked(s));
        assertTrue(likes.toggle(s));
        assertTrue(new Likes(app).isLiked(s));
        assertFalse(likes.toggle(s));
        assertFalse(new Likes(app).isLiked(s));
    }

    @Test
    public void opensAudioFilesSharedFromOtherApps() {
        Uri uri = Uri.parse("file:///sdcard/Download/Morning%20Raga.mp3");
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(app, uri), new ShadowMediaPlayer.MediaInfo(60_000, 0));
        Song s = MusicLibrary.fromUri(app, uri);
        assertEquals("Morning Raga", s.title);
        Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "audio/mpeg");
        Robolectric.buildActivity(MainActivity.class, view).setup();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("Morning Raga", service.current().title);
        assertTrue(service.isPlaying());
    }

    @Test
    public void formatsTimes() {
        assertEquals("0:00", Song.formatTime(0));
        assertEquals("3:05", Song.formatTime(185_000));
        assertEquals("1:01:01", Song.formatTime(3_661_000));
        assertEquals("NH", Covers.initials("Neon Harbor"));
    }
}
