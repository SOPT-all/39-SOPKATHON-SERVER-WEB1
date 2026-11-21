package org.sopt.sopkathon.domain.record.repository;

import org.sopt.sopkathon.domain.record.entity.Record;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordRepository extends JpaRepository<Record, Long> {
}
