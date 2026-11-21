# Proposal

## Why

학생의 탐색 화면에서 공개된 이야기를 고를 수 있도록 목록 조회 API가 필요하다. 작성 중인 초안과 핵심 사건·원문은 학생에게 노출하지 않아야 한다.

## What Changes

- 인증 없는 `GET /api/v1/stories`를 추가한다.
- `PUBLISHED` 이야기만 id 내림차순(최신 등록순)으로 반환하고, 대상이 없으면 `items`를 빈 배열로 반환한다.
- 응답 항목은 `storyId`, `title`, `summary`, `imageUrl`, `category`로 제한한다. `category`는 영문 대문자 코드 문자열이다.

## Capabilities

### New Capabilities

- `public-story-list`: 학생에게 공개된 이야기 목록을 공개 필드만으로 조회하는 계약.

### Modified Capabilities

없음.

## Impact

- 대상: 이슈 #18, Story의 repository·service·controller, 목록 응답 DTO와 관련 테스트.
- 공개 범위는 [ADR 0002](../../../docs/adr/0002-이야기-초안과-공개-상태를-story의-생명주기로-관리한다.md)의 PUBLISHED 규칙을 따른다.
- API 원본: [팀 API 명세](https://app.notion.com/p/API-450fa71b42ef82218dd981f6ec53fa8e), [공통 응답 계약](https://app.notion.com/p/41dfa71b42ef80baa568cee9dbaf6002).
- 완료 기준: 상태 필터, 정렬, 빈 목록, 공개 필드 제한을 자동 테스트로 증명하고 Java 21 clean build를 통과한다.
- 제외 범위: 공개 이야기 상세 조회, 이야기 확정·공개 API, 페이지네이션, 배포 DB 스키마 적용.
