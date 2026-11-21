package org.sopt.sopkathon.domain.record.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "record")
public class Record {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "record_id")
	private Long id;

	@Column(name = "record_text", columnDefinition = "TEXT")
	private String text;

	@Column(name = "record_uuid")
	private String uuid;

	protected Record() {
	}

	public Record(String text, String uuid) {
		this.text = text;
		this.uuid = uuid;
	}

	public Long getId() {
		return id;
	}

	public String getText() {
		return text;
	}

	public String getUuid() {
		return uuid;
	}
}
