package com.nagesh.spotify.library;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlaylistRepository extends JpaRepository<Playlist, String> {

    List<Playlist> findAllByOrderByPositionAsc();

    List<Playlist> findByNameContainingIgnoreCaseOrderByPositionAsc(String name);

    @Query("select coalesce(max(p.position), -1) from Playlist p")
    long maxPosition();
}
