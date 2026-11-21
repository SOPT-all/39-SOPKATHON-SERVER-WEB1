# Proposal

## Why

녹음에서 생성된 세계관과 그에 속하는 채팅·맥락을 저장할 도메인 모델이 필요하다. 제공된 ERD를 JPA 매핑으로 옮겨 이후 API 구현에서 동일한 데이터 구조를 사용한다.

## What Changes

- 녹음, 세계관, 채팅, 맥락의 식별자·본문·enum 필드를 저장한다.
- 녹음 하나에 최대 하나의 세계관이 연결되고, 세계관 하나에 여러 채팅·맥락이 속하며 각 하위 레코드는 필수 상위 FK를 가진다.
- ERD의 문자열 길이, TEXT, nullable 조건과 사용자 확인 카테고리 6종을 반영한다.
- 갱신된 ERD에 따라 세계관 배경 요약(`story_summary`, 200자)과 이미지 URL(`image_url`, 300자)을 추가하고 카테고리 컬럼명을 `story_category`로 수정한다.
- PK 자동 생성과 문자열 enum 영속화를 검증한다. 내용이 없는 Untitled 테이블은 제외한다.

## Capabilities

### New Capabilities

- `story-persistence`: 녹음·세계관·채팅·맥락 데이터와 필수 연관관계의 영속성.

### Modified Capabilities

없음.

## Impact

- 도메인별 entity 패키지, JPA 및 MySQL 런타임 의존성, H2 테스트 의존성을 추가한다.
- DB 프로파일은 이슈 #13과 PR #14에서 관리한다. 테스트는 별도 프로파일로 실행한다.
- API 원본: https://app.notion.com/p/API-41f14af65e0680929a7aeb0ff586991f (현재 연결에서 접근 불가). 이번 변경은 API를 추가하지 않는다.
- 사용자 확인 타입은 채팅 STUDENT/STORY/FEEDBACK, 맥락 CHARACTER/EVENT이다. 나머지 컬럼의 원본은 이슈 #15에 제공된 ERD이며, 녹음–세계관 관계는 사용자 확인에 따라 일대일로 제한한다.
