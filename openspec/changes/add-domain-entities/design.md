# Design

## Context

현재 Spring Boot 4.1.2, Java 21 프로젝트에는 웹 계층만 있으며 영속성 의존성과 엔티티가 없다. 동기는 proposal.md를 참고한다. 기존 ADR은 팀 결정 기록 관리에 관한 것으로 데이터 모델을 제한하지 않는다.

## Goals / Non-Goals

**Goals:** ERD의 DB 구조를 명시적으로 매핑하고 별도 MySQL 설치 없이 저장·조회와 제약조건을 검증한다.

**Non-Goals:** API·서비스·저장소 인터페이스, 삭제 전파·감사 필드, DB 프로파일과 운영 스키마 배포는 추가하지 않는다.

## Decisions

- 도메인별 `domain/{record,story,chat,context}/entity` 패키지에 엔티티를 둔다. Lombok `Getter`와 `RequiredArgsConstructor(access = PROTECTED)`로 getter와 JPA 기본 생성자를 생성한다. 엔티티 필드에 final 또는 NonNull을 추가하지 않아 기존 null 허용 조건을 유지하고, 값을 받는 public 생성자는 명시적으로 제공한다. StoryCategory는 final displayName 필드에 대한 생성자와 getter를 Lombok으로 생성한다.
- PK는 Long/IDENTITY로 매핑한다. 수동 식별자 지정 대신 DB가 각 레코드의 식별자를 생성한다.
- 사용자 확인에 따라 세계관의 녹음 참조는 단방향 `OneToOne(fetch = LAZY, optional = false)`와 `JoinColumn(nullable = false, unique = true)`로 매핑한다. 녹음만 먼저 저장할 수 있고, 같은 녹음에 두 번째 세계관을 저장하는 것은 DB 유일성 제약으로 거부한다. 채팅·맥락의 세계관 참조는 단방향 `ManyToOne(fetch = LAZY, optional = false)`와 `JoinColumn(nullable = false)`로 매핑한다. 양방향 컬렉션과 cascade는 현재 요구가 없어 추가하지 않는다.
- 문자열 길이·TEXT·nullable은 ERD대로 명시한다. `record_uuid`는 길이가 정의되지 않아 JPA 기본 255자를 사용한다. 세계관의 `record_id` 외에는 추가 unique 제약을 만들지 않는다.
- 갱신된 ERD의 카테고리 컬럼명은 `story_category`이며 Java 필드는 `category`이다. 배경 요약은 `summary`/`story_summary`(200자), 이미지 URL은 `imageUrl`/`image_url`(300자)로 매핑하고 두 필드 모두 null을 허용한다. 값을 받는 생성자에 두 필드를 포함한다. enum은 STRING으로 저장한다. 카테고리는 HORROR/ROMANCE/THRILLER/ACTION/FANTASY/ETC이며 한국어 표기를 getter로 제공한다. 사용자 확인에 따라 채팅 타입은 STUDENT/STORY/FEEDBACK, 맥락 타입은 CHARACTER/EVENT로 제한한다.
- JPA starter, MySQL connector를 운영 의존성에 추가한다. H2와 JPA test starter는 테스트에만 사용한다. 각 테스트에 `ActiveProfiles("test")`를 지정하고 `src/test/resources/application-test.yml`에 H2를 구성하여 #13의 local/prod 설정과 격리한다. 공통 application.yml을 덮어쓰지 않아 기존 애플리케이션 설정도 함께 검증한다.

## Risks / Trade-offs

- [실제 MySQL과 H2의 차이] → H2 MySQL 모드에서 FK·길이·타입·TEXT 저장을 검증하지만 실제 MySQL 검증을 대신했다고 주장하지 않는다.
- [운영 DB 설정 미포함] → 프로파일은 #13/#14에서 제공하며 운영 DDL 생성·적용은 배포 시 별도 수행한다. 테스트 밖에서 H2로 연결되지 않도록 H2를 testRuntimeOnly로 한정한다.
- [API 원본 접근 불가] → 링크와 사용자 확인 값을 proposal.md에 남기고 새 API 계약을 만들지 않는다.
