package com.nagesh.spotify.library;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class LibraryController {

    public record CreatePlaylistRequest(@NotBlank @Size(max = 100) String name, String trackId) {
    }

    public record RenamePlaylistRequest(@NotBlank @Size(max = 100) String name) {
    }

    public record TrackRequest(@NotBlank String trackId) {
    }

    private final LibraryService library;

    public LibraryController(LibraryService library) {
        this.library = library;
    }

    @GetMapping("/library")
    public LibraryDto library() {
        return library.library();
    }

    @PutMapping("/liked/{trackId}")
    public List<String> like(@PathVariable String trackId) {
        return library.like(trackId);
    }

    @DeleteMapping("/liked/{trackId}")
    public List<String> unlike(@PathVariable String trackId) {
        return library.unlike(trackId);
    }

    @PostMapping("/playlists")
    @ResponseStatus(HttpStatus.CREATED)
    public PlaylistDto createPlaylist(@Valid @RequestBody CreatePlaylistRequest req) {
        return library.createPlaylist(req.name(), req.trackId());
    }

    @PatchMapping("/playlists/{id}")
    public PlaylistDto renamePlaylist(@PathVariable String id, @Valid @RequestBody RenamePlaylistRequest req) {
        return library.renamePlaylist(id, req.name());
    }

    @DeleteMapping("/playlists/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlaylist(@PathVariable String id) {
        library.deletePlaylist(id);
    }

    @PostMapping("/playlists/{id}/tracks")
    public PlaylistDto addTrack(@PathVariable String id, @Valid @RequestBody TrackRequest req) {
        return library.addTrack(id, req.trackId());
    }

    @DeleteMapping("/playlists/{id}/tracks/{trackId}")
    public PlaylistDto removeTrack(@PathVariable String id, @PathVariable String trackId) {
        return library.removeTrack(id, trackId);
    }

    @PostMapping("/recent")
    public List<String> recordPlay(@Valid @RequestBody TrackRequest req) {
        return library.recordPlay(req.trackId());
    }
}
