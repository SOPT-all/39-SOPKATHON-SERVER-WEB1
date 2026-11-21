# Design

## Context

PR #21이 dev에 병합되었다. Story는 DRAFT/PUBLISHED, Record는 원문·유일한 필수 UUID를 가진다. 확정 메타데이터와 잠금 조회·ChatRepository는 아직 없다. 첫 API의 OpenAI 설정·엄격한 응답 파싱·공통 오류를 재사용한다. 결정은 ADR 0003과 ADR 0002를 따른다.

## Goals / Non-Goals

**Goals:** 동일 최종 요청 재사용, 정상 동시 AI 한 번, DB 공개·첫 메시지 한 번, 짧은 트랜잭션과 중단 복구를 자동 테스트한다.

**Non-Goals:** 학생·공개 조회 구현, 이미지 생성, 키 등록·운영 DDL 실행, 분산 트랜잭션에 의한 외부 AI 호출의 장애 포함 완전 1회 보장.

## Decisions

- CreateStoryRequest는 JsonNode 필드로 JSON 타입을 엄격하게 확인하고 검증된 불변 값으로 변환한다. 원문은 codePointCount로 5,000개를 검사하고 편집값의 공백·정규화는 바꾸지 않는다. 필수 헤더는 표준 UUID를 대소문자 무관 검증한 뒤 정규 UUID 문자열로 조회한다.
- 본문 해시는 검증된 필드의 고정 순서 JSON 배열을 UTF-8로 직렬화한 SHA-256이다. 최초 transcript와 여섯 최종값을 포함한다. PUBLISHED/진행 중 해시 차이는 원문 불일치 400보다 우선하여 409이다. 새 DRAFT의 최초 원문 차이는 400이다.
- Story에 publish_request_hash(varchar64), publish_attempt_token(varchar36), publish_attempt_expires_at(timestamp)를 nullable로 추가한다. 상태는 두 값만 유지한다. 최초 UUID와 Record는 수정하지 않는다.
- PublicationCoordinator의 짧은 transactional 메서드가 UUID의 Story와 Record를 join fetch하고 PESSIMISTIC_WRITE로 잠근다. PUBLISHED면 같은 해시에 결과를 재사용한다. 유효한 토큰이 있으면 같은 해시에 WAIT, 다른 해시에 409이다. 토큰이 없거나 만료됐으면 최초 원문을 확인하고 새 토큰·해시·만료를 기록해 OWNER를 반환한다.
- OpenEntityManagerInView가 같은 HTTP 요청에서 이전 시도의 엔티티를 보관할 수 있으므로 잠금 획득 뒤 refresh로 최신 권한을 읽는다. 대기 조회는 엔티티 대신 새 DTO projection으로 읽어 다른 트랜잭션의 커밋을 관찰한다.
- StoryPublicationService는 트랜잭션 없이 OWNER만 AI를 호출한다. WAIT는 별도 read-only 트랜잭션으로 결과를 관찰하고 잠금 없이 poll한다. 기본 대기 75초, poll 100ms이다. 처리 표시가 없어지거나 토큰이 바뀌면 503으로 끝내고 자동 재생성하지 않는다. 인터럽트도 복원 후 503으로 처리한다.
- 기본 처리 유효 기간은 120초이며 실제 적용은 max(설정값, OpenAI connect+read timeout+30초)이다. Clock을 주입해 만료를 테스트한다. 만료를 회수하는 것은 새 HTTP 요청이며 대기 루프에서 자동 회수하지 않는다. 소유권은 최종 저장 시 토큰·해시·만료 모두 확인한다.
- 확정 writer는 별도 빈·트랜잭션에서 Story를 다시 잠가 소유권을 확인하고 최종 편집값·prologue=scene·Chat(STORY, scene+줄바꿈+question) 한 건·PUBLISHED를 커밋한다. 토큰·만료는 지우고 해시는 유지한다. 저장 오류는 모두 롤백한다.
- 생성·저장 실패 시 별도 짧은 release 트랜잭션으로 자신의 DRAFT 토큰만 정리한다. 정리 실패는 최초 오류를 가리지 않으며 만료 회수가 복구한다. 이전 처리자가 새 토큰을 해제할 수 없다. 생성 대기와 메인 orchestration에는 transactional을 붙이지 않는다.
- StoryOpeningGenerator는 최종값을 받아 scene/question을 반환한다. 기존 OpenAI 통신·응답 파싱을 공통 클라이언트로 추출하고 첫 API 회귀 테스트를 유지한다. 두 필드는 strict schema로 받고 scene 300자, question은 한 질문과 최대 300자로 검증한다. 질문 하나는 프롬프트와 단일 질문 표지 검증·대표 원문 검토를 함께 사용한다. API 키 없이 대역을 사용한다.
- 201 CREATED/200 SUCCESS의 data는 storyId 하나이다. ErrorCode에 고정 메시지의 IDEMPOTENCY_KEY_CONFLICT를 추가하고 기존 400/404/415/503/500을 재사용한다.

## Risks / Trade-offs

- [서버 중단 후 외부 AI가 이미 실행됐는지 모름] → 회수 시 재호출 가능성을 명시하고 토큰 fencing으로 DB 중복 공개를 막는다.
- [대기 요청 수와 DB 조회 증가] → 유한한 대기·poll 간격을 설정한다. 현재 규모에서는 Redis나 별도 작업 큐를 도입하지 않는다.
- [AI 의미 품질] → 대표 최종값으로 사건·인물 유지, 장면과 질문 하나를 실제 모델 연결 후 검토한다. 자동 대역 테스트는 의미 품질을 보장하지 않는다.
- [실제 MySQL과 H2의 잠금 차이] → H2 MySQL 모드에서 실제 병렬 트랜잭션을 검증하고 실제 MySQL 검증 여부는 PR에 별도로 밝힌다.

## Migration Plan

운영자는 중복 공개·첫 메시지 상태를 확인한 후 다음 nullable 컬럼만 추가한다. 기존 상태·원문·UUID는 변경하지 않고 자동 실행하지 않는다.

```sql
ALTER TABLE story
  ADD COLUMN publish_request_hash VARCHAR(64) NULL,
  ADD COLUMN publish_attempt_token VARCHAR(36) NULL,
  ADD COLUMN publish_attempt_expires_at TIMESTAMP(6) NULL;
```

기존 PUBLISHED에 요청 해시가 없는 경우 새 확정을 수행하지 않고 409로 거부한다. 배포 전 해당 데이터의 기존 등록 본문을 확인하여 별도로 정리한다. 롤백 시 앱만 되돌리고 컬럼·데이터를 자동 삭제하지 않는다.
