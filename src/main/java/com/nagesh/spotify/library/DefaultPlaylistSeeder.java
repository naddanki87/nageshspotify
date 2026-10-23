package com.nagesh.spotify.library;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Creates the starter playlists the first time the app runs against an empty database. */
@Component
public class DefaultPlaylistSeeder implements ApplicationRunner {

    private final PlaylistRepository playlists;

    public DefaultPlaylistSeeder(PlaylistRepository playlists) {
        this.playlists = playlists;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (playlists.count() > 0) {
            return;
        }
        playlists.saveAll(List.of(
                new Playlist("p-daily", "Daily Mix 1", "Neon Harbor, Tidal Echo and more", 0,
                        List.of("#509bf5", "#1e3264"), List.of("t1", "t5", "t9", "t13", "t15")),
                new Playlist("p-chill", "Chill Vibes", "Kick back and unwind", 1,
                        List.of("#8d67ab", "#27856a"), List.of("t9", "t10", "t7", "t11", "t12")),
                new Playlist("p-drive", "Road Trip", "Windows down, volume up", 2,
                        List.of("#f59b23", "#e8115b"), List.of("t3", "t4", "t14", "t2", "t6"))));
    }
}
