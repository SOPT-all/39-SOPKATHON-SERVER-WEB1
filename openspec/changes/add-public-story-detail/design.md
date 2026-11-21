# Design

## Context

ADR 0002는 초안을 직접 ID로 지정한 공개 조회에서도 노출하지 않도록 정한다. 명세의 `setting`, `characters`는 엔티티의 `background`, `character`에 대응한다.

## Goals / Non-Goals

**Goals:**

- 공개 이야기 하나를 명세의 일곱 필드로 조회한다.
- 초안의 존재 여부를 응답으로 구분할 수 없게 한다.

**Non-Goals:**

- 채팅 내역과 공개 전환은 다루지 않는다.

## Decisions

- `findByIdAndStatus(id, PUBLISHED)`로 조회한다. 초안과 없는 ID가 같은 404가 된다.
- 1 미만 ID는 서비스에서 `INVALID_PARAMETER`로 거부한다. `@Positive`는 공통 핸들러에서 `INVALID_INPUT`으로 변환되어 명세와 달라진다.
- 숫자가 아니거나 Long 범위를 넘는 ID는 기존 타입 불일치 처리로 `INVALID_PARAMETER`를 반환한다.
- 엔티티를 직접 직렬화하지 않고 `GetStoryResponse`로 변환한다.

## Risks / Trade-offs

- [공개 이야기의 필수값 누락] → `category`가 null이면 변환에 실패한다. 공개 전 필수값은 확정 서비스가 보장해야 하며, 더미데이터도 같은 조건을 지켜야 한다.
