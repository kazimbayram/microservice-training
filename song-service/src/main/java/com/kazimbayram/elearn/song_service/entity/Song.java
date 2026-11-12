package com.kazimbayram.elearn.song_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "songs")
public class Song implements Persistable<Long> {

	@Id
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, length = 100)
	private String artist;

	@Column(nullable = false, length = 100)
	private String album;

	@Column(nullable = false, length = 5)
	private String duration;

	@Column(nullable = false, length = 4)
	private String year;

	@Transient
	private boolean newEntity = true;

	protected Song() {
	}

	public Song(Long id, String name, String artist, String album, String duration, String year) {
		this.id = id;
		this.name = name;
		this.artist = artist;
		this.album = album;
		this.duration = duration;
		this.year = year;
	}

	@Override
	public Long getId() {
		return id;
	}

	@Override
	public boolean isNew() {
		return newEntity;
	}

	@PostLoad
	@PostPersist
	void markPersisted() {
		newEntity = false;
	}

	public String getName() {
		return name;
	}

	public String getArtist() {
		return artist;
	}

	public String getAlbum() {
		return album;
	}

	public String getDuration() {
		return duration;
	}

	public String getYear() {
		return year;
	}
}
