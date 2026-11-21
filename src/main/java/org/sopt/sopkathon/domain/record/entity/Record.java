package org.sopt.sopkathon.domain.record.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Entity
@Table(name = "record", uniqueConstraints = @UniqueConstraint(name = "uk_record_uuid", columnNames = "record_uuid"))
@Getter
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class Record {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "record_id")
	private Long id;

	@Column(name = "record_text", columnDefinition = "TEXT")
	private String text;

	@Column(name = "record_uuid", nullable = false)
	private String uuid;

	public Record(String text, String uuid) {
		this.text = text;
		this.uuid = uuid;
	}
}
