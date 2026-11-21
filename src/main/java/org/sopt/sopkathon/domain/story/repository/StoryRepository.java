package org.sopt.sopkathon.domain.story.repository;

import java.util.List;
import java.util.Optional;

import org.sopt.sopkathon.domain.story.entity.Story;
import org.sopt.sopkathon.domain.story.entity.StoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryRepository extends JpaRepository<Story, Long> {

	List<Story> findAllByStatusOrderByIdDesc(StoryStatus status);

	Optional<Story> findByIdAndStatus(Long id, StoryStatus status);
}
