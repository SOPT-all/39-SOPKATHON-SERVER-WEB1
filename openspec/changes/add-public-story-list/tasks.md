# Tasks

## 1. 목록 조회 구현

- [x] 1.1 StoryRepository에 PUBLISHED 조건과 최신순 정렬을 적용하는 조회 메서드를 추가한다.
- [x] 1.2 GetStoriesResponse와 StoryItem으로 공개 필드만 변환하고 Swagger 설명을 작성한다.
- [x] 1.3 StoryService와 공개 GET /api/v1/stories controller를 구현한다.

## 2. 검증

- [x] 2.1 통합 테스트로 DRAFT 제외, 최신순, 빈 목록, 공개 필드 제한을 확인한다.
- [x] 2.2 OpenSpec strict 검증과 Java 21 clean build를 실행하고 PR에 결과를 기록한다.

## Workflow follow-up

- 팀 리뷰 후 변경 명세를 아카이브한다.
