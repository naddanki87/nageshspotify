package com.nagesh.spotify.library;

import java.util.List;

public record PlaylistDto(String id, String name, String description, List<String> colors, List<String> tracks) {

    static PlaylistDto from(Playlist p) {
        return new PlaylistDto(p.getId(), p.getName(), p.getDescription(), List.copyOf(p.getColors()),
                List.copyOf(p.getTrackIds()));
    }
}
