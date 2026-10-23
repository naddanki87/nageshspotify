package com.nagesh.spotify.library;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;

@Entity
public class Playlist {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    private String description = "";

    /** Position in the user's playlist list; lower comes first. */
    private long position;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "playlist_color", joinColumns = @JoinColumn(name = "playlist_id"))
    @OrderColumn(name = "idx")
    @Column(name = "color")
    private List<String> colors = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "playlist_track", joinColumns = @JoinColumn(name = "playlist_id"))
    @OrderColumn(name = "idx")
    @Column(name = "track_id")
    private List<String> trackIds = new ArrayList<>();

    protected Playlist() {
    }

    public Playlist(String id, String name, String description, long position, List<String> colors, List<String> trackIds) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.position = position;
        this.colors = new ArrayList<>(colors);
        this.trackIds = new ArrayList<>(trackIds);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public long getPosition() {
        return position;
    }

    public List<String> getColors() {
        return colors;
    }

    public List<String> getTrackIds() {
        return trackIds;
    }
}
