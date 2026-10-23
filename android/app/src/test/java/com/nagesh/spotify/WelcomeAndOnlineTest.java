package com.nagesh.spotify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import java.util.List;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowMediaPlayer;
import org.robolectric.shadows.util.DataSource;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class WelcomeAndOnlineTest {

    private Application app;
    private PlayerService service;

    @Before
    public void setUp() {
        app = RuntimeEnvironment.getApplication();
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS);
        service = Robolectric.buildService(PlayerService.class).create().get();
        shadowOf(app).setComponentNameAndServiceForBindService(new ComponentName(app, PlayerService.class),
                service.onBind(null));
    }

    private static String text(WelcomeActivity a, int id) {
        return ((TextView) a.findViewById(id)).getText().toString();
    }

    @Test
    public void firstLaunchAsksForANameOnceThenWelcomesBack() {
        assertNull(new Profile(app).name());

        // First launch: registration form
        ActivityController<WelcomeActivity> first = Robolectric.buildActivity(WelcomeActivity.class).setup();
        WelcomeActivity a = first.get();
        assertEquals(View.VISIBLE, a.findViewById(R.id.register_form).getVisibility());
        assertEquals(View.GONE, a.findViewById(R.id.welcome_group).getVisibility());

        // An empty name is rejected
        a.findViewById(R.id.register_button).performClick();
        assertNotNull(((EditText) a.findViewById(R.id.name_input)).getError());
        assertNull(new Profile(app).name());

        ((EditText) a.findViewById(R.id.name_input)).setText("  Nagesh  ");
        a.findViewById(R.id.register_button).performClick();
        assertEquals("Nagesh", new Profile(app).name());
        assertEquals(View.VISIBLE, a.findViewById(R.id.welcome_group).getVisibility());
        assertEquals("Hello, Nagesh!", text(a, R.id.welcome_title));

        // "Start listening" opens the player
        a.findViewById(R.id.start_button).performClick();
        Intent next = shadowOf(a).getNextStartedActivity();
        assertEquals(MainActivity.class.getName(), next.getComponent().getClassName());
        assertTrue(a.isFinishing());
        first.pause().stop().destroy();

        // Reopening the app: no form, just a welcome back
        WelcomeActivity again = Robolectric.buildActivity(WelcomeActivity.class).setup().get();
        assertEquals(View.GONE, again.findViewById(R.id.register_form).getVisibility());
        assertEquals("Welcome back, Nagesh!", text(again, R.id.welcome_title));
        assertEquals("Not Nagesh? Change name", text(again, R.id.change_name));
    }

    @Test
    public void nameCanBeChanged() {
        new Profile(app).setName("Nagesh");
        WelcomeActivity a = Robolectric.buildActivity(WelcomeActivity.class).setup().get();
        a.findViewById(R.id.change_name).performClick();
        EditText input = a.findViewById(R.id.name_input);
        assertEquals("Nagesh", input.getText().toString());
        input.setText("Nageshwar");
        a.findViewById(R.id.register_button).performClick();
        assertEquals("Nageshwar", new Profile(app).name());
        assertEquals("Hello, Nageshwar!", text(a, R.id.welcome_title));
    }

    @Test
    public void onlineTabStreamsTheDemoCatalog() {
        List<Song> online = MusicLibrary.online(app);
        assertEquals(16, online.size());
        assertEquals("https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3", online.get(0).key());
        for (Song s : online) {
            ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(app, s.uri), new ShadowMediaPlayer.MediaInfo(200_000, 0));
        }

        MainActivity main = Robolectric.buildActivity(MainActivity.class).setup().get();
        shadowOf(Looper.getMainLooper()).idle();
        main.findViewById(R.id.tab_online).performClick();
        ListView list = main.findViewById(R.id.list);
        assertEquals(16, list.getCount());

        shadowOf(list).performItemClick(2);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("Sunset Freeway", service.current().title);
        assertTrue(service.isPlaying());
    }

    @Test
    public void addsAndRemovesOnlineLinks() {
        Uri link = Uri.parse("https://example.com/music/Morning%20Raga.mp3");
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(app, link), new ShadowMediaPlayer.MediaInfo(90_000, 0));
        MainActivity main = Robolectric.buildActivity(MainActivity.class).setup().get();
        shadowOf(Looper.getMainLooper()).idle();

        main.findViewById(R.id.add_link).performClick();
        shadowOf(Looper.getMainLooper()).idle(); // let the dialog finish showing
        AlertDialog dialog = (AlertDialog) ShadowAlertDialog.getLatestDialog();
        EditText input = findEditText(dialog);
        input.setText("not a link");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertNotNull(input.getError());
        assertTrue(dialog.isShowing());

        input.setText(link.toString());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(dialog.isShowing());
        assertTrue(new SavedLinks(app).contains(link.toString()));
        assertEquals("Morning Raga", service.current().title);
        assertTrue(service.isPlaying());

        ListView list = main.findViewById(R.id.list);
        assertEquals(17, list.getCount()); // the link, then the 16 demo songs
        assertEquals("Morning Raga", ((Song) list.getItemAtPosition(0)).title);

        // Long-press removes it
        assertTrue(list.getOnItemLongClickListener().onItemLongClick(list, null, 0, 0));
        ((AlertDialog) ShadowAlertDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(new SavedLinks(app).contains(link.toString()));
        assertEquals(16, list.getCount());
    }

    private static EditText findEditText(AlertDialog dialog) {
        FrameLayout content = dialog.findViewById(android.R.id.content);
        return findEditText((View) content);
    }

    private static EditText findEditText(View v) {
        if (v instanceof EditText) {
            return (EditText) v;
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                EditText found = findEditText(g.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
