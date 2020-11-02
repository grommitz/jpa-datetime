package com.grommitz.jpadatetime;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "DBTHING")
public class DbThing {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;
	@Column
	private String url;
	@Column
	private LocalDateTime time;

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public LocalDateTime getTime() {
		return time;
	}

	public void setTime(LocalDateTime time) {
		this.time = time;
	}
}
