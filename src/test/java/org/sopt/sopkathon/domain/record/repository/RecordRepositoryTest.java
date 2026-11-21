package org.sopt.sopkathon.domain.record.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.sopt.sopkathon.domain.record.entity.Record;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(properties = "spring.test.database.replace=none")
@ActiveProfiles("test")
class RecordRepositoryTest {

	@Autowired
	private RecordRepository repository;

	@Test
	void rejectsDuplicateUuid() {
		repository.saveAndFlush(new Record("첫 원문", "same-uuid"));
		assertThrows(DataIntegrityViolationException.class,
			() -> repository.saveAndFlush(new Record("다른 원문", "same-uuid")));
	}

	@Test
	void allowsDistinctUuidsAndPreservesNullableAttributes() {
		repository.saveAndFlush(new Record("같은 원문", "first-uuid"));
		repository.saveAndFlush(new Record("같은 원문", "second-uuid"));
		repository.saveAndFlush(new Record(null, null));
		repository.saveAndFlush(new Record(null, null));
		assertEquals(4, repository.count());
	}
}
