# Proposal

## Why

작성자가 편집한 초안을 같은 이야기 ID로 공개 확정하고 학생이 처음 읽을 장면과 질문을 준비해야 한다. 요청 유실·재시도·동시 전송이 첫 메시지와 공개 결과를 중복 생성하지 않도록 한다.

## What Changes

- 공개 POST /api/v1/stories에 필수 Idempotency-Key UUID와 transcript/title/summary/setting/characters/coreEvent/category를 받는다.
- 기존 UUID의 DRAFT에 최종값을 반영하고 OpenAI로 첫 장면·질문을 생성한다. 첫 장면·첫 STORY 메시지 한 건·PUBLISHED 전환은 원자적으로 저장하며 Record와 Story를 새로 생성하지 않는다.
- 최초 확정은 201 CREATED, 동일 최종 본문 재사용은 200 SUCCESS, 처리 중·공개된 키의 다른 본문은 409 IDEMPOTENCY_KEY_CONFLICT로 처리한다. 본문 비교에 최초 원문을 포함하고 JSON 키 순서는 무관하다.
- 요청 해시·처리 토큰·만료 시각과 짧은 DB 행 잠금으로 동시 요청을 조정한다. AI 호출·동일 요청 대기는 트랜잭션 밖에서 수행한다.
- 기존 공통 오류와 503 AI_UNAVAILABLE을 재사용한다. 없는 초안 UUID는 404, 처리 중·공개 이전 최초 원문 불일치는 400으로 명시한다.

## Capabilities

### New Capabilities

- `story-publication`: 최종 편집값에 의한 이야기 공개 확정, 첫 메시지 생성과 멱등성·동시성·실패 복구 계약.

### Modified Capabilities

없음. 현재 openspec/specs에는 capability가 없다. 병합된 generate-story-drafts의 모델과 원문 보존 규칙을 사용한다.

## Impact

- 이슈 #22, [ADR 0003](../../../docs/adr/0003-이야기-확정의-멱등성과-동시-요청을-db에서-관리한다.md), 기존 ADR 0002와 연관된다.
- 원본: [확정 API](https://app.notion.com/p/2-41f14af65e06811b8c47d915f86829c3), 사용자가 제공한 본문과 대화에서 확인한 상태 기반 확정·오류 조건.
- Story 처리 메타데이터, 잠금 repository, ChatRepository, HTTP DTO·controller, 처리 권한·대기·확정 서비스, 공통 OpenAI 통신·첫 장면 생성, 문서·테스트를 추가한다.
- 첫 API의 기존 계약을 유지한다. 공개 조회·학생 메시지 API, 이미지 생성, 실제 키 등록·운영 DDL 실행은 제외한다.
- 구현은 테스트와 함께 작은 커밋으로 나누고, 운영 DB·실제 모델 검증 여부를 PR에서 구분한다.
