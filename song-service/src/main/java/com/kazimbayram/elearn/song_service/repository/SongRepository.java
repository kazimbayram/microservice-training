package com.kazimbayram.elearn.song_service.repository;

import com.kazimbayram.elearn.song_service.entity.Song;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SongRepository extends JpaRepository<Song, Long> {
}
