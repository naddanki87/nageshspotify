package com.nagesh.spotify.library;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LikedTrackRepository extends JpaRepository<LikedTrack, Long> {

    List<LikedTrack> findAllByOrderByIdAsc();

    boolean existsByTrackId(String trackId);

    void deleteByTrackId(String trackId);
}
