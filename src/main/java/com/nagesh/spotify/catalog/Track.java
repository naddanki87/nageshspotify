package com.nagesh.spotify.catalog;

/**
 * A playable song. {@code album} holds the id of the {@link Album} it belongs to.
 */
public record Track(String id, String title, String album, String artist, String src) {
}
