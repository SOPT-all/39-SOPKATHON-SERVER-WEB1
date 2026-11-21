# Tasks

## 1. 상세 조회 구현

- [x] 1.1 StoryRepository에 ID와 PUBLISHED 상태로 조회하는 메서드를 추가한다.
- [x] 1.2 STORY_NOT_FOUND와 공개 필드만 담는 GetStoryResponse, Swagger 설명을 추가한다.
- [x] 1.3 StoryService와 공개 GET /api/v1/stories/{storyId} controller를 구현한다.

## 2. 검증

- [x] 2.1 통합 테스트로 공개 필드, 초안·없는 ID의 404, 잘못된 ID의 400을 확인한다.
- [x] 2.2 OpenSpec strict 검증과 Java 21 clean build를 실행하고 PR에 결과를 기록한다.

## Workflow follow-up

- 팀 리뷰 후 변경 명세를 아카이브한다.
