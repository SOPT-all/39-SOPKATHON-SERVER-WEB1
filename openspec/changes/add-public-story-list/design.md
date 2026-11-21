# Design

## Context

Story는 DRAFT/PUBLISHED 상태를 가지며, ADR 0002는 공개 목록이 PUBLISHED 이야기만 대상으로 하도록 정한다. 엔티티에는 학생에게 숨겨야 하는 핵심 사건과 Record 연관관계가 있다.

## Goals / Non-Goals

**Goals:**

- 공개 이야기만 최신 등록순으로 조회한다.
- 명세의 다섯 필드만 응답한다.

**Non-Goals:**

- 페이지네이션, 상세 조회, 공개 전환은 다루지 않는다.

## Decisions

- Spring Data 파생 쿼리 `findAllByStatusOrderByIdDesc(StoryStatus)`를 사용한다. 별도 JPQL 없이 상태 조건과 정렬을 표현할 수 있다.
- 엔티티를 직접 직렬화하지 않고 `GetStoriesResponse`와 중첩 `StoryItem`으로 변환한다. 비공개 필드 노출과 지연 로딩 문제를 막는다.
- `category`는 `name()`으로 변환해 표시 이름이 아닌 영문 코드를 응답한다.
- 서비스는 `@Transactional(readOnly = true)`로 조회한다.

## Risks / Trade-offs

- [전체 목록 조회] → 해커톤 범위의 데이터량을 전제로 페이지네이션을 추가하지 않는다. 확인된 병목이 생기면 재검토한다.
- [공개 이야기의 필수값 누락] → `category`가 null이면 변환에 실패한다. 공개 전 필수값은 확정 서비스가 보장해야 하며, 더미데이터도 같은 조건을 지켜야 한다.