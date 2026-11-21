package org.sopt.sopkathon.domain.story.entity;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.sopt.sopkathon.domain.record.entity.Record;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(properties = "spring.test.database.replace=none")
@ActiveProfiles("test")
class StoryTest {

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	void savesAndReloadsRecordAndStoryAtColumnLimits() {
		String original = "녹음 원문".repeat(1000);
		Record record = new Record(original, "550e8400-e29b-41d4-a716-446655440000");
		entityManager.persist(record);
		Story story = new Story("제".repeat(20), StoryCategory.FANTASY, "배".repeat(200),
			"요".repeat(200), "인".repeat(100), "사".repeat(500), "첫".repeat(300),
			"https://example.com/" + "a".repeat(280), record);
		entityManager.persist(story);
		entityManager.flush();
		entityManager.clear();

		Story found = entityManager.find(Story.class, story.getId());
		assertAll(
			() -> assertNotNull(found.getId()),
			() -> assertEquals(story.getTitle(), found.getTitle()),
			() -> assertEquals(story.getCategory(), found.getCategory()),
			() -> assertEquals(story.getBackground(), found.getBackground()),
			() -> assertEquals(story.getSummary(), found.getSummary()),
			() -> assertEquals(story.getCharacter(), found.getCharacter()),
			() -> assertEquals(story.getEvent(), found.getEvent()),
			() -> assertEquals(story.getPrologue(), found.getPrologue()),
			() -> assertEquals(story.getImageUrl(), found.getImageUrl()),
			() -> assertEquals(record.getId(), found.getRecord().getId()),
			() -> assertEquals(original, found.getRecord().getText()),
			() -> assertEquals(record.getUuid(), found.getRecord().getUuid())
		);
	}

	@Test
	void allowsRecordBeforeStoryAndNullableAttributes() {
		Record record = new Record(null, null);
		entityManager.persist(record);
		entityManager.flush();
		entityManager.clear();
		Record foundRecord = entityManager.find(Record.class, record.getId());
		assertNull(foundRecord.getText());
		assertNull(foundRecord.getUuid());

		Story story = new Story(null, null, null, null, null, null, null, null, foundRecord);
		entityManager.persist(story);
		entityManager.flush();
		entityManager.clear();
		Story found = entityManager.find(Story.class, story.getId());
		assertAll(
			() -> assertNull(found.getTitle()),
			() -> assertNull(found.getCategory()),
			() -> assertNull(found.getBackground()),
			() -> assertNull(found.getSummary()),
			() -> assertNull(found.getCharacter()),
			() -> assertNull(found.getEvent()),
			() -> assertNull(found.getPrologue()),
			() -> assertNull(found.getImageUrl())
		);
	}

	@Test
	void rejectsSecondStoryForSameRecord() {
		Record record = new Record("원문", "record-1");
		entityManager.persist(record);
		entityManager.persist(new Story("첫 세계관", null, null, null, null, null, null, null, record));
		entityManager.flush();
		entityManager.clear();
		Record existing = entityManager.getReference(Record.class, record.getId());

		assertThrows(ConstraintViolationException.class, () -> {
			entityManager.persist(new Story("중복 세계관", null, null, null, null, null, null, null, existing));
			entityManager.flush();
		});
	}

	@Test
	void rejectsStoryWithoutRecord() {
		assertThrows(PersistenceException.class, () -> {
			entityManager.persist(new Story("세계관", null, null, null, null, null, null, null, null));
			entityManager.flush();
		});
	}

	@Test
	void rejectsStoryWithNonexistentRecord() {
		Record missing = entityManager.getReference(Record.class, Long.MAX_VALUE);
		assertThrows(ConstraintViolationException.class, () -> {
			entityManager.persist(new Story("세계관", null, null, null, null, null, null, null, missing));
			entityManager.flush();
		});
	}

	@ParameterizedTest
	@CsvSource({"21,200,200,100,500,300,300", "20,201,200,100,500,300,300",
		"20,200,201,100,500,300,300", "20,200,200,101,500,300,300",
		"20,200,200,100,501,300,300", "20,200,200,100,500,301,300",
		"20,200,200,100,500,300,301"})
	void rejectsOverlongStoryFields(int title, int background, int summary, int character,
		int event, int prologue, int imageUrl) {
		Record record = new Record(null, null);
		entityManager.persist(record);
		assertThrows(PersistenceException.class, () -> {
			entityManager.persist(new Story("제".repeat(title), null, "배".repeat(background),
				"요".repeat(summary), "인".repeat(character), "사".repeat(event),
				"첫".repeat(prologue), "u".repeat(imageUrl), record));
			entityManager.flush();
		});
	}

	@ParameterizedTest
	@EnumSource(StoryCategory.class)
	void storesCategoryAsString(StoryCategory category) {
		Record record = new Record(null, null);
		entityManager.persist(record);
		Story story = new Story(null, category, null, null, null, null, null, null, record);
		entityManager.persist(story);
		entityManager.flush();
		entityManager.clear();

		assertEquals(category, entityManager.find(Story.class, story.getId()).getCategory());
		assertEquals(category.name(), entityManager.createNativeQuery(
			"select story_category from story where story_id = :id", String.class)
			.setParameter("id", story.getId()).getSingleResult());
	}
}
