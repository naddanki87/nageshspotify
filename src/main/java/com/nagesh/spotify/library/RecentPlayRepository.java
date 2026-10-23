package com.nagesh.spotify.library;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecentPlayRepository extends JpaRepository<RecentPlay, String> {

    List<RecentPlay> findAllByOrderByPlayedAtDesc();
}
