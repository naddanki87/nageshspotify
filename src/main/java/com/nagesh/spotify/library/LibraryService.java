package com.nagesh.spotify.library;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.nagesh.spotify.catalog.CatalogService;

@Service
@Transactional
public class LibraryService {

    static final int RECENT_LIMIT = 6;

    private static final List<List<String>> PALETTE = List.of(
            List.of("#e8115b", "#3a0ca3"),
            List.of("#1e3264", "#2d9cdb"),
            List.of("#148a08", "#0b3d2e"),
            List.of("#8d67ab", "#503750"),
            List.of("#f59b23", "#c0392b"),
            List.of("#27856a", "#1d4ed8"));

    private final CatalogService catalog;
    private final PlaylistRepository playlists;
    private final LikedTrackRepository liked;
    private final RecentPlayRepository recent;

    public LibraryService(CatalogService catalog, PlaylistRepository playlists, LikedTrackRepository liked,
            RecentPlayRepository recent) {
        this.catalog = catalog;
        this.playlists = playlists;
        this.liked = liked;
        this.recent = recent;
    }

    @Transactional(readOnly = true)
    public LibraryDto library() {
        return new LibraryDto(likedIds(), playlists(), recentIds());
    }

    // ---------- Liked songs ----------

    @Transactional(readOnly = true)
    public List<String> likedIds() {
        return liked.findAllByOrderByIdAsc().stream().map(LikedTrack::getTrackId).toList();
    }

    public List<String> like(String trackId) {
        requireTrack(trackId);
        if (!liked.existsByTrackId(trackId)) {
            liked.save(new LikedTrack(trackId));
        }
        return likedIds();
    }

    public List<String> unlike(String trackId) {
        liked.deleteByTrackId(trackId);
        return likedIds();
    }

    // ---------- Playlists ----------

    @Transactional(readOnly = true)
    public List<PlaylistDto> playlists() {
        return playlists.findAllByOrderByPositionAsc().stream().map(PlaylistDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PlaylistDto> searchPlaylists(String query) {
        return playlists.findByNameContainingIgnoreCaseOrderByPositionAsc(query.trim()).stream()
                .map(PlaylistDto::from).toList();
    }

    public PlaylistDto createPlaylist(String name, String trackId) {
        List<String> tracks = List.of();
        if (trackId != null) {
            requireTrack(trackId);
            tracks = List.of(trackId);
        }
        long count = playlists.count();
        Playlist p = new Playlist("p-" + UUID.randomUUID(), name.trim(), "", playlists.maxPosition() + 1,
                PALETTE.get((int) (count % PALETTE.size())), tracks);
        return PlaylistDto.from(playlists.save(p));
    }

    public PlaylistDto renamePlaylist(String id, String name) {
        Playlist p = requirePlaylist(id);
        p.setName(name.trim());
        return PlaylistDto.from(p);
    }

    public void deletePlaylist(String id) {
        playlists.delete(requirePlaylist(id));
    }

    public PlaylistDto addTrack(String playlistId, String trackId) {
        Playlist p = requirePlaylist(playlistId);
        requireTrack(trackId);
        if (p.getTrackIds().contains(trackId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Already in “" + p.getName() + "”");
        }
        p.getTrackIds().add(trackId);
        return PlaylistDto.from(p);
    }

    public PlaylistDto removeTrack(String playlistId, String trackId) {
        Playlist p = requirePlaylist(playlistId);
        p.getTrackIds().remove(trackId);
        return PlaylistDto.from(p);
    }

    // ---------- Recently played ----------

    @Transactional(readOnly = true)
    public List<String> recentIds() {
        return recent.findAllByOrderByPlayedAtDesc().stream().map(RecentPlay::getTrackId).toList();
    }

    public List<String> recordPlay(String trackId) {
        requireTrack(trackId);
        recent.findById(trackId).ifPresentOrElse(
                r -> r.setPlayedAt(Instant.now()),
                () -> recent.save(new RecentPlay(trackId, Instant.now())));
        recent.flush();
        List<RecentPlay> all = recent.findAllByOrderByPlayedAtDesc();
        if (all.size() > RECENT_LIMIT) {
            recent.deleteAll(all.subList(RECENT_LIMIT, all.size()));
        }
        return all.stream().limit(RECENT_LIMIT).map(RecentPlay::getTrackId).toList();
    }

    // ---------- Helpers ----------

    private void requireTrack(String trackId) {
        if (!catalog.hasTrack(trackId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown track: " + trackId);
        }
    }

    private Playlist requirePlaylist(String id) {
        return playlists.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown playlist: " + id));
    }
}
