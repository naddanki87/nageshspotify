package com.nagesh.spotify.library;

import java.util.List;

/** Everything the user has saved: liked track ids, playlists and recently played track ids (newest first). */
public record LibraryDto(List<String> liked, List<PlaylistDto> playlists, List<String> recent) {
}
