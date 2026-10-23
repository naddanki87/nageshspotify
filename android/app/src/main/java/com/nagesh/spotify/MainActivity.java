package com.nagesh.spotify;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Browse the music on the device by song, album, artist or folder, plus liked songs. */
public class MainActivity extends Activity implements PlayerService.Listener {

    private static final int REQUEST_PERMISSIONS = 1;
    private static final int REQUEST_OPEN_FILES = 2;

    private enum Tab { SONGS, ONLINE, ALBUMS, ARTISTS, FOLDERS, LIKED }

    private static final int[] TAB_IDS = {
            R.id.tab_songs, R.id.tab_online, R.id.tab_albums, R.id.tab_artists, R.id.tab_folders, R.id.tab_liked,
    };

    private final Handler main = new Handler(Looper.getMainLooper());
    private PlayerService player;
    private Likes likes;

    private List<Song> allSongs = new ArrayList<>();
    private List<Song> onlineSongs = new ArrayList<>();
    private SavedLinks links;
    private boolean loaded;
    private Tab tab = Tab.SONGS;
    /** The album, artist or folder being shown, or null for the tab's top level. */
    private MusicLibrary.Group openGroup;
    private String query = "";

    /** What the list currently shows: either songs or groups. */
    private final List<Song> shownSongs = new ArrayList<>();
    private final List<MusicLibrary.Group> shownGroups = new ArrayList<>();
    private boolean showingGroups;

    private ListView list;
    private RowAdapter adapter;
    private View empty;
    private TextView emptyText;
    private View emptyButton;
    private View groupHeader;
    private TextView groupTitle;
    private TextView groupSub;
    private EditText search;
    private View searchClear;
    private View miniPlayer;
    private ImageView miniCover;
    private TextView miniTitle;
    private TextView miniArtist;
    private ImageButton miniLike;
    private ImageButton miniPlay;
    private ProgressBar miniProgress;
    private int rowCoverPx;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            player = ((PlayerService.LocalBinder) service).getService();
            player.addListener(MainActivity.this);
            handleViewIntent(getIntent());
            onPlayerChanged();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            player = null;
        }
    };

    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            if (player != null && player.duration() > 0) {
                miniProgress.setProgress((int) (1000L * player.position() / player.duration()));
            }
            main.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        likes = new Likes(this);
        links = new SavedLinks(this);
        onlineSongs = MusicLibrary.online(this);
        rowCoverPx = Math.round(48 * getResources().getDisplayMetrics().density);

        String name = new Profile(this).name();
        ((TextView) findViewById(R.id.greeting)).setText(greeting() + ", " + (name != null ? name : "there"));
        list = findViewById(R.id.list);
        empty = findViewById(R.id.empty);
        emptyText = findViewById(R.id.empty_text);
        emptyButton = findViewById(R.id.empty_button);
        groupHeader = findViewById(R.id.group_header);
        groupTitle = findViewById(R.id.group_title);
        groupSub = findViewById(R.id.group_sub);
        search = findViewById(R.id.search);
        searchClear = findViewById(R.id.search_clear);
        miniPlayer = findViewById(R.id.mini_player);
        miniCover = findViewById(R.id.mini_cover);
        miniTitle = findViewById(R.id.mini_title);
        miniArtist = findViewById(R.id.mini_artist);
        miniLike = findViewById(R.id.mini_like);
        miniPlay = findViewById(R.id.mini_play);
        miniProgress = findViewById(R.id.mini_progress);

        adapter = new RowAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (showingGroups) {
                    openGroup(shownGroups.get(position));
                } else if (player != null) {
                    player.playAll(new ArrayList<>(shownSongs), position, false);
                }
            }
        });

        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (showingGroups || !links.contains(shownSongs.get(position).key())) {
                    return false;
                }
                confirmRemoveLink(shownSongs.get(position));
                return true;
            }
        });

        bindTab(R.id.tab_songs, Tab.SONGS);
        bindTab(R.id.tab_online, Tab.ONLINE);
        bindTab(R.id.tab_albums, Tab.ALBUMS);
        bindTab(R.id.tab_artists, Tab.ARTISTS);
        bindTab(R.id.tab_folders, Tab.FOLDERS);
        bindTab(R.id.tab_liked, Tab.LIKED);

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                searchClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                refreshList();
            }
        });
        searchClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                search.setText("");
                hideKeyboard();
            }
        });

        findViewById(R.id.group_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeGroup();
            }
        });
        findViewById(R.id.group_play).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (player != null && !shownSongs.isEmpty()) {
                    player.playAll(new ArrayList<>(shownSongs), 0, false);
                }
            }
        });
        findViewById(R.id.group_shuffle).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (player != null && !shownSongs.isEmpty()) {
                    player.playAll(new ArrayList<>(shownSongs), -1, true);
                }
            }
        });
        findViewById(R.id.open_file).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("audio/*")
                        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(intent, REQUEST_OPEN_FILES);
            }
        });
        findViewById(R.id.add_link).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddLinkDialog();
            }
        });
        findViewById(R.id.refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadMusic();
            }
        });
        emptyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestPermissions(neededPermissions(), REQUEST_PERMISSIONS);
            }
        });

        miniPlayer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, NowPlayingActivity.class));
            }
        });
        miniPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (player != null) {
                    player.toggle();
                }
            }
        });
        miniLike.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (player != null && player.current() != null) {
                    likes.toggle(player.current());
                    onPlayerChanged();
                    refreshList();
                }
            }
        });

        selectTab(Tab.SONGS);
        if (hasAudioPermission()) {
            loadMusic();
        } else {
            showEmpty(getString(R.string.permission_needed), true);
            requestPermissions(neededPermissions(), REQUEST_PERMISSIONS);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, PlayerService.class), connection, Context.BIND_AUTO_CREATE);
        main.post(progressTicker);
    }

    @Override
    protected void onResume() {
        super.onResume();
        likes = new Likes(this); // likes may have changed on the Now Playing screen
        refreshList();
    }

    @Override
    protected void onStop() {
        main.removeCallbacks(progressTicker);
        if (player != null) {
            player.removeListener(this);
            player = null;
        }
        unbindService(connection);
        super.onStop();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleViewIntent(intent);
    }

    @Override
    public void onBackPressed() {
        if (openGroup != null) {
            closeGroup();
        } else if (query.length() > 0) {
            search.setText("");
        } else {
            super.onBackPressed();
        }
    }

    // ---------- Permissions and loading ----------

    private static String[] neededPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            return new String[] {Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS};
        }
        return new String[] {Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    private boolean hasAudioPermission() {
        String permission = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS && hasAudioPermission()) {
            loadMusic();
        }
    }

    private void loadMusic() {
        if (!hasAudioPermission()) {
            requestPermissions(neededPermissions(), REQUEST_PERMISSIONS);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Song> songs = MusicLibrary.loadAll(getApplicationContext());
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        allSongs = songs;
                        loaded = true;
                        if (openGroup != null) {
                            closeGroup(); // its songs may have changed
                        } else {
                            refreshList();
                        }
                    }
                });
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_OPEN_FILES || resultCode != RESULT_OK || data == null || player == null) {
            return;
        }
        List<Song> opened = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                opened.add(MusicLibrary.fromUri(this, data.getClipData().getItemAt(i).getUri()));
            }
        } else if (data.getData() != null) {
            opened.add(MusicLibrary.fromUri(this, data.getData()));
        }
        if (!opened.isEmpty()) {
            player.playAll(opened, 0, false);
            startActivity(new Intent(this, NowPlayingActivity.class));
        }
    }

    /** Plays an audio file opened from another app ("Open with NageshSpotify"). */
    private void handleViewIntent(Intent intent) {
        if (player == null || intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) {
            return;
        }
        Uri uri = intent.getData();
        if (uri != null) {
            player.playAll(Collections.singletonList(MusicLibrary.fromUri(this, uri)), 0, false);
            setIntent(new Intent(this, MainActivity.class)); // don't replay it on rotation
            startActivity(new Intent(this, NowPlayingActivity.class));
        }
    }

    // ---------- Online links ----------

    private void showAddLinkDialog() {
        final EditText input = new EditText(this);
        input.setHint(R.string.add_link_hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input);

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.add_link)
                .setMessage(R.string.add_link_message)
                .setView(box)
                .setPositiveButton(R.string.add, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface d) {
                // Validate before closing, so a typo doesn't lose what was typed
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Uri uri = Uri.parse(input.getText().toString().trim());
                        if (!MusicLibrary.isWebLink(uri)) {
                            input.setError(getString(R.string.invalid_link));
                            return;
                        }
                        dialog.dismiss();
                        addLink(uri);
                    }
                });
            }
        });
        dialog.show();
    }

    private void addLink(Uri uri) {
        links.add(uri.toString());
        onlineSongs = MusicLibrary.online(this);
        selectTab(Tab.ONLINE);
        if (player != null) {
            player.playAll(Collections.singletonList(MusicLibrary.fromLink(uri)), 0, false);
        }
    }

    private void confirmRemoveLink(final Song song) {
        new AlertDialog.Builder(this)
                .setTitle(song.title)
                .setMessage(R.string.remove_link)
                .setPositiveButton(R.string.remove, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        links.remove(song.key());
                        onlineSongs = MusicLibrary.online(MainActivity.this);
                        refreshList();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---------- Tabs and groups ----------

    private void bindTab(int id, final Tab t) {
        findViewById(id).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectTab(t);
            }
        });
    }

    private void selectTab(Tab t) {
        tab = t;
        openGroup = null;
        for (int i = 0; i < TAB_IDS.length; i++) {
            TextView chip = findViewById(TAB_IDS[i]);
            chip.setSelected(Tab.values()[i] == t);
            chip.setTextColor(chip.isSelected() ? 0xFF000000 : 0xFFFFFFFF);
        }
        refreshList();
        list.setSelection(0);
    }

    private void openGroup(MusicLibrary.Group g) {
        openGroup = g;
        search.setText("");
        hideKeyboard();
        refreshList();
        list.setSelection(0);
    }

    private void closeGroup() {
        openGroup = null;
        refreshList();
    }

    private void refreshList() {
        shownSongs.clear();
        shownGroups.clear();
        if (!loaded && tab != Tab.ONLINE) {
            // The device's music isn't available yet (still loading, or no permission)
            showingGroups = false;
            groupHeader.setVisibility(View.GONE);
            adapter.notifyDataSetChanged();
            if (!hasAudioPermission()) {
                showEmpty(getString(R.string.permission_needed), true);
            }
            return;
        }
        List<Song> base;
        if (openGroup != null) {
            base = openGroup.songs;
        } else if (tab == Tab.ONLINE) {
            base = onlineSongs;
        } else if (tab == Tab.LIKED) {
            base = new ArrayList<>();
            for (Song s : allSongs) {
                if (likes.isLiked(s)) {
                    base.add(s);
                }
            }
            for (Song s : onlineSongs) {
                if (likes.isLiked(s)) {
                    base.add(s);
                }
            }
        } else {
            base = allSongs;
        }

        showingGroups = openGroup == null && (tab == Tab.ALBUMS || tab == Tab.ARTISTS || tab == Tab.FOLDERS);
        if (showingGroups) {
            List<MusicLibrary.Group> groups = tab == Tab.ALBUMS ? MusicLibrary.byAlbum(base)
                    : tab == Tab.ARTISTS ? MusicLibrary.byArtist(base) : MusicLibrary.byFolder(base);
            for (MusicLibrary.Group g : groups) {
                if (MusicLibrary.matches(g, query)) {
                    shownGroups.add(g);
                }
            }
        } else {
            for (Song s : base) {
                if (MusicLibrary.matches(s, query)) {
                    shownSongs.add(s);
                }
            }
        }

        groupHeader.setVisibility(openGroup != null ? View.VISIBLE : View.GONE);
        if (openGroup != null) {
            groupTitle.setText(openGroup.name);
            String count = songCount(openGroup.songs.size());
            groupSub.setText(openGroup.subtitle != null ? openGroup.subtitle + " • " + count : count);
        }
        adapter.notifyDataSetChanged();

        boolean nothing = showingGroups ? shownGroups.isEmpty() : shownSongs.isEmpty();
        if (!nothing) {
            empty.setVisibility(View.GONE);
        } else if (!query.isEmpty()) {
            showEmpty(getString(R.string.no_results), false);
        } else if (tab == Tab.LIKED && openGroup == null) {
            showEmpty(getString(R.string.no_liked), false);
        } else {
            showEmpty(getString(R.string.no_music), false);
        }
    }

    private void showEmpty(String message, boolean withButton) {
        empty.setVisibility(View.VISIBLE);
        emptyText.setText(message);
        emptyButton.setVisibility(withButton ? View.VISIBLE : View.GONE);
    }

    // ---------- Player ----------

    @Override
    public void onPlayerChanged() {
        Song song = player == null ? null : player.current();
        if (song == null) {
            miniPlayer.setVisibility(View.GONE);
        } else {
            miniPlayer.setVisibility(View.VISIBLE);
            miniTitle.setText(song.title);
            miniArtist.setText(song.artist);
            Covers.show(miniCover, song, rowCoverPx);
            boolean liked = likes.isLiked(song);
            miniLike.setImageResource(liked ? R.drawable.ic_heart : R.drawable.ic_heart_outline);
            miniLike.setColorFilter(liked ? getColor(R.color.accent) : getColor(R.color.text));
            miniPlay.setImageResource(player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
        }
        adapter.notifyDataSetChanged(); // highlight the playing song
    }

    // ---------- Helpers ----------

    private static String greeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
    }

    static String songCount(int n) {
        return n + (n == 1 ? " song" : " songs");
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus();
    }

    private final class RowAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return showingGroups ? shownGroups.size() : shownSongs.size();
        }

        @Override
        public Object getItem(int position) {
            return showingGroups ? shownGroups.get(position) : shownSongs.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView != null ? convertView
                    : LayoutInflater.from(MainActivity.this).inflate(R.layout.item_row, parent, false);
            ImageView cover = row.findViewById(R.id.cover);
            TextView title = row.findViewById(R.id.title);
            TextView subtitle = row.findViewById(R.id.subtitle);
            TextView duration = row.findViewById(R.id.duration);
            ImageButton like = row.findViewById(R.id.like);

            if (showingGroups) {
                MusicLibrary.Group g = shownGroups.get(position);
                Covers.showGroup(cover, g, rowCoverPx);
                title.setText(g.name);
                title.setTextColor(getColor(R.color.text));
                String count = songCount(g.songs.size());
                subtitle.setText(g.subtitle != null ? g.subtitle + " • " + count : count);
                duration.setVisibility(View.GONE);
                like.setVisibility(View.GONE);
                return row;
            }

            final Song s = shownSongs.get(position);
            Covers.show(cover, s, rowCoverPx);
            title.setText(s.title);
            boolean playing = player != null && s.equals(player.current());
            title.setTextColor(playing ? getColor(R.color.accent) : getColor(R.color.text));
            subtitle.setText(s.artist + " • " + s.album);
            duration.setVisibility(View.VISIBLE);
            duration.setText(s.durationMs > 0 ? Song.formatTime(s.durationMs) : ""); // unknown for streams
            like.setVisibility(View.VISIBLE);
            boolean liked = likes.isLiked(s);
            like.setImageResource(liked ? R.drawable.ic_heart : R.drawable.ic_heart_outline);
            like.setColorFilter(liked ? getColor(R.color.accent) : getColor(R.color.muted));
            like.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    likes.toggle(s);
                    if (player != null) {
                        onPlayerChanged();
                    }
                    refreshList();
                }
            });
            return row;
        }
    }
}
