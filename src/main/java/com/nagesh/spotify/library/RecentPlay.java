package com.nagesh.spotify.library;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class RecentPlay {

    @Id
    private String trackId;

    private Instant playedAt;

    protected RecentPlay() {
    }

    public RecentPlay(String trackId, Instant playedAt) {
        this.trackId = trackId;
        this.playedAt = playedAt;
    }

    public String getTrackId() {
        return trackId;
    }

    public void setPlayedAt(Instant playedAt) {
        this.playedAt = playedAt;
    }
}
