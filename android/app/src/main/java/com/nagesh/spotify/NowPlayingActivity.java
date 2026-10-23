package com.nagesh.spotify;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

/** Full-screen player: artwork, seek bar and all the playback controls. */
public class NowPlayingActivity extends Activity implements PlayerService.Listener {

    private final Handler main = new Handler(Looper.getMainLooper());
    private PlayerService player;
    private Likes likes;
    private boolean seeking;
    private String shownKey;

    private View root;
    private FrameLayout coverFrame;
    private ImageView cover;
    private TextView title;
    private TextView artist;
    private ImageButton like;
    private SeekBar seek;
    private TextView position;
    private TextView duration;
    private ImageButton play;
    private ImageButton shuffle;
    private ImageButton repeat;
    private int coverPx;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            player = ((PlayerService.LocalBinder) service).getService();
            player.addListener(NowPlayingActivity.this);
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
            updateProgress();
            main.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_now_playing);
        likes = new Likes(this);
        root = findViewById(R.id.np_root);
        coverFrame = findViewById(R.id.np_cover_frame);
        cover = findViewById(R.id.np_cover);
        title = findViewById(R.id.np_title);
        artist = findViewById(R.id.np_artist);
        like = findViewById(R.id.np_like);
        seek = findViewById(R.id.np_seek);
        position = findViewById(R.id.np_position);
        duration = findViewById(R.id.np_duration);
        play = findViewById(R.id.np_play);
        shuffle = findViewById(R.id.np_shuffle);
        repeat = findViewById(R.id.np_repeat);
        title.setSelected(true); // start the marquee for long titles
        coverPx = Math.min(getResources().getDisplayMetrics().widthPixels, 1024);

        // Keep the artwork square and as large as the space allows
        coverFrame.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                int size = Math.min(r - l, b - t);
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) cover.getLayoutParams();
                if (lp.width != size) {
                    lp.width = size;
                    lp.height = size;
                    lp.gravity = Gravity.CENTER;
                    cover.setLayoutParams(lp);
                }
            }
        });

        findViewById(R.id.np_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (player != null) {
                    player.toggle();
                }
            }
        });
        findViewById(R.id.np_next).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (player != null) {
                    player.next();
                }
            }
        });
        findViewById(R.id.np_prev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (player != null) {
                    player.previous();
                }
            }
        });
        shuffle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (player != null) {
                    player.setShuffle(!player.isShuffle());
                }
            }
        });
        repeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (player != null) {
                    player.cycleRepeat();
                }
            }
        });
        like.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (player != null && player.current() != null) {
                    likes.toggle(player.current());
                    onPlayerChanged();
                }
            }
        });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && player != null) {
                    position.setText(Song.formatTime((long) player.duration() * progress / 1000));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                seeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                seeking = false;
                if (player != null) {
                    player.seekTo((int) ((long) player.duration() * bar.getProgress() / 1000));
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, PlayerService.class), connection, Context.BIND_AUTO_CREATE);
        main.post(progressTicker);
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
    public void onPlayerChanged() {
        Song song = player == null ? null : player.current();
        if (song == null) {
            if (player != null) {
                finish(); // nothing to show
            }
            return;
        }
        if (!song.key().equals(shownKey)) {
            shownKey = song.key();
            title.setText(song.title);
            artist.setText(song.artist);
            Covers.show(cover, song, coverPx);
            int[] colors = Covers.colorsFor(song.album);
            root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {colors[0], getColor(R.color.panel), getColor(R.color.panel)}));
        }
        boolean liked = likes.isLiked(song);
        like.setImageResource(liked ? R.drawable.ic_heart : R.drawable.ic_heart_outline);
        like.setColorFilter(liked ? getColor(R.color.accent) : getColor(R.color.text));
        play.setImageResource(player.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
        shuffle.setColorFilter(player.isShuffle() ? getColor(R.color.accent) : getColor(R.color.text));
        int mode = player.repeatMode();
        repeat.setImageResource(mode == PlayerService.REPEAT_ONE ? R.drawable.ic_repeat_one : R.drawable.ic_repeat);
        repeat.setColorFilter(mode == PlayerService.REPEAT_OFF ? getColor(R.color.text) : getColor(R.color.accent));
        updateProgress();
    }

    private void updateProgress() {
        if (player == null || seeking) {
            return;
        }
        int dur = player.duration();
        int pos = player.position();
        seek.setProgress(dur > 0 ? (int) (1000L * pos / dur) : 0);
        position.setText(Song.formatTime(pos));
        duration.setText(Song.formatTime(dur));
    }
}
