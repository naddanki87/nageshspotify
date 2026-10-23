package com.nagesh.spotify;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nagesh.spotify.catalog.Album;
import com.nagesh.spotify.catalog.CatalogService;
import com.nagesh.spotify.catalog.Track;
import com.nagesh.spotify.library.LibraryService;
import com.nagesh.spotify.library.PlaylistDto;

@RestController
public class SearchController {

    public record SearchResults(List<Track> tracks, List<Album> albums, List<PlaylistDto> playlists) {
    }

    private final CatalogService catalog;
    private final LibraryService library;

    public SearchController(CatalogService catalog, LibraryService library) {
        this.catalog = catalog;
        this.library = library;
    }

    @GetMapping("/api/search")
    public SearchResults search(@RequestParam("q") String query) {
        if (query.isBlank()) {
            return new SearchResults(List.of(), List.of(), List.of());
        }
        return new SearchResults(catalog.searchTracks(query), catalog.searchAlbums(query),
                library.searchPlaylists(query));
    }
}
