# Proposal

## Why

학생이 고른 이야기의 소개, 배경, 등장인물을 확인한 뒤 대화를 시작할 수 있도록 상세 조회 API가 필요하다. 작성자의 입력 원문과 핵심 사건, 공개 전 초안은 학생에게 노출하지 않아야 한다.

## What Changes

- 인증 없는 `GET /api/v1/stories/{storyId}`를 추가한다.
- `PUBLISHED` 이야기의 `storyId`, `title`, `summary`, `imageUrl`, `category`, `setting`, `characters`만 반환한다.
- 없는 이야기와 공개되지 않은 이야기는 404 `STORY_NOT_FOUND`로 응답한다.
- 형식이 잘못되었거나 1 미만인 ID는 400 `INVALID_PARAMETER`로 응답한다.

## Capabilities

### New Capabilities

- `public-story-detail`: 학생에게 공개된 이야기 하나를 공개 필드만으로 조회하는 계약.

### Modified Capabilities

없음.

## Impact

- 대상: 이슈 #23, Story의 repository·service·controller, 상세 응답 DTO, 공통 `ErrorCode`와 관련 테스트.
- 공개 범위는 [ADR 0002](../../../docs/adr/0002-이야기-초안과-공개-상태를-story의-생명주기로-관리한다.md)의 PUBLISHED 규칙을 따른다.
- API 원본: [팀 API 명세](https://app.notion.com/p/API-450fa71b42ef82218dd981f6ec53fa8e), [공통 응답 계약](https://app.notion.com/p/41dfa71b42ef80baa568cee9dbaf6002).
- 완료 기준: 공개 필드, 초안·없는 ID의 404, 잘못된 ID의 400을 자동 테스트로 증명하고 Java 21 clean build를 통과한다.
- 제외 범위: 채팅 내역 조회, 이야기 확정·공개 API.
