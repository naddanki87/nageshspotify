package com.nagesh.spotify.library;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;

@Entity
public class LikedTrack {

    /** Increases with each like, so it doubles as the "liked at" order. */
    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false, unique = true)
    private String trackId;

    protected LikedTrack() {
    }

    public LikedTrack(String trackId) {
        this.trackId = trackId;
    }

    public String getTrackId() {
        return trackId;
    }
}
