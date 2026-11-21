package org.sopt.sopkathon.domain.story.repository;

import org.sopt.sopkathon.domain.story.entity.Story;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryRepository extends JpaRepository<Story, Long> {
}
