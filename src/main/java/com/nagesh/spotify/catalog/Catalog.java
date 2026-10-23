package com.nagesh.spotify.catalog;

import java.util.List;

public record Catalog(List<Album> albums, List<Track> tracks, List<Genre> genres) {
}
