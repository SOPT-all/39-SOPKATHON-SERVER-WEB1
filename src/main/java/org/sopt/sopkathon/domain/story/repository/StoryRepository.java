package org.sopt.sopkathon.domain.story.repository;

import org.sopt.sopkathon.domain.story.entity.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.sopt.sopkathon.domain.story.service.PublicationSnapshot;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoryRepository extends JpaRepository<Story, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from Story s join fetch s.record r where r.uuid = :uuid")
	Optional<Story> lockByRecordUuid(@Param("uuid") String uuid);

	@Query("""
		select new org.sopt.sopkathon.domain.story.service.PublicationSnapshot(
		 s.id, s.status, s.publishRequestHash, s.publishAttemptToken, s.publishAttemptExpiresAt)
		from Story s join s.record r where r.uuid = :uuid
		""")
	Optional<PublicationSnapshot> publicationSnapshot(@Param("uuid") String uuid);
}
