package com.nagesh.spotify.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

/**
 * Demo catalog. Audio is the freely distributable SoundHelix example set;
 * cover art is generated from each album's gradient, so no image assets are needed.
 */
@Service
public class CatalogService {

    private static final String SONG_URL = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-%d.mp3";

    private final List<Album> albums = List.of(
            new Album("a1", "Midnight Circuits", "Neon Harbor", 2024, List.of("#e8115b", "#3a0ca3")),
            new Album("a2", "Golden Hour Drive", "Saffron Skies", 2023, List.of("#f59b23", "#c0392b")),
            new Album("a3", "Deep Blue Static", "Tidal Echo", 2025, List.of("#1e3264", "#2d9cdb")),
            new Album("a4", "Forest Tapes", "Moss & Pine", 2022, List.of("#148a08", "#0b3d2e")),
            new Album("a5", "Chai at 2AM", "Lo-Fi Nagesh", 2025, List.of("#8d67ab", "#503750")),
            new Album("a6", "Monsoon Signals", "Rain Theory", 2024, List.of("#27856a", "#1d4ed8")));

    private final Map<String, Album> albumsById = albums.stream()
            .collect(Collectors.toMap(Album::id, Function.identity()));

    private final List<Track> tracks = List.of(
            track(1, "Voltage Bloom", "a1"),
            track(2, "Afterglow Protocol", "a1"),
            track(3, "Sunset Freeway", "a2"),
            track(4, "Copper Clouds", "a2"),
            track(5, "Undertow", "a3"),
            track(6, "Pressure Waves", "a3"),
            track(7, "Canopy", "a4"),
            track(8, "Old Cassette Trail", "a4"),
            track(9, "Steam & Static", "a5"),
            track(10, "Window Seat", "a5"),
            track(11, "First Rain", "a6"),
            track(12, "Petrichor Loop", "a6"),
            track(13, "Night Market", "a1"),
            track(14, "Saffron Road", "a2"),
            track(15, "Lighthouse", "a3"),
            track(16, "Firefly Field", "a4"));

    private final Map<String, Track> tracksById = tracks.stream()
            .collect(Collectors.toMap(Track::id, Function.identity()));

    private final List<Genre> genres = List.of(
            new Genre("Electronic", "#e8115b"),
            new Genre("Chill", "#8d67ab"),
            new Genre("Indie", "#148a08"),
            new Genre("Focus", "#1e3264"),
            new Genre("Workout", "#f59b23"),
            new Genre("Ambient", "#27856a"),
            new Genre("Bollywood", "#dc148c"),
            new Genre("Telugu Hits", "#ba5d07"));

    private Track track(int n, String title, String albumId) {
        return new Track("t" + n, title, albumId, albumsById.get(albumId).artist(), SONG_URL.formatted(n));
    }

    public Catalog catalog() {
        return new Catalog(albums, tracks, genres);
    }

    public boolean hasTrack(String id) {
        return tracksById.containsKey(id);
    }

    public Optional<Genre> genre(String name) {
        return genres.stream().filter(g -> g.name().equalsIgnoreCase(name)).findFirst();
    }

    /** Songs whose title, artist or album title contain {@code query}, ignoring case. */
    public List<Track> searchTracks(String query) {
        String q = normalize(query);
        return tracks.stream()
                .filter(t -> contains(t.title(), q) || contains(t.artist(), q)
                        || contains(albumsById.get(t.album()).title(), q))
                .toList();
    }

    /** Albums whose title or artist contain {@code query}, ignoring case. */
    public List<Album> searchAlbums(String query) {
        String q = normalize(query);
        return albums.stream().filter(a -> contains(a.title(), q) || contains(a.artist(), q)).toList();
    }

    /** A random selection of {@code size} track ids. */
    public List<String> randomMix(int size) {
        List<String> ids = new ArrayList<>(tracksById.keySet());
        Collections.shuffle(ids);
        return ids.subList(0, Math.min(size, ids.size()));
    }

    static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    static boolean contains(String haystack, String normalizedNeedle) {
        return haystack.toLowerCase(Locale.ROOT).contains(normalizedNeedle);
    }
}
