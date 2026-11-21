# Proposal

## Why

브라우저에서 확정한 음성 인식 텍스트를 편집 폼에 채울 이야기 초안으로 정리할 서버 API가 필요하다. 최초 원문은 보존하고, 작성자가 수정할 수 있는 AI 추천값을 미공개 이야기로 저장하여 후속 확정 API에 연결한다.

## What Changes

- 공개 `POST /api/v1/story-drafts`에서 JSON의 필수 문자열 transcript를 받는다. 공백뿐인 입력과 유니코드 코드 포인트 5,000개 초과를 거부한다.
- OpenAI로 title/summary/setting/characters/category/coreEvent를 생성하고 필수값·카테고리·저장 길이를 검증한다. 원문 보존, 명백한 오인식 교정, 불분명한 이름 유지와 핵심 사건 보존을 프롬프트에 반영한다.
- 서버 UUID와 최초 원문을 Record에, 생성 결과를 1:1 Story(DRAFT)에 원자적으로 저장한다. 별도 초안 테이블과 첫 장면·Chat은 생성하지 않는다.
- 200 SUCCESS의 data에 recordUuid와 여섯 편집 필드를 반환한다. 400/415 공통 오류를 재사용하고 AI 실패에는 503 AI_UNAVAILABLE을 추가한다. AI 실패 시 저장하지 않고 DB 저장 실패 시 두 엔티티를 모두 롤백한다.
- 상태와 UUID의 NOT NULL·유일성에 필요한 엔티티·스키마 변경을 포함한다. 첫 번째 API의 응답은 후속 확정 작업의 키이며, 초안 생성 자체에 멱등성 보장은 추가하지 않는다.

## Capabilities

### New Capabilities

- `story-drafts`: 인식 원문에서 편집용 초안을 생성하고 UUID와 함께 미공개 상태로 저장·반환하는 계약.

### Modified Capabilities

없음. 현재 openspec/specs에는 등록된 capability가 없으며, 기존 add-domain-entities 변경의 영속성 모델을 기반으로 새 API를 추가한다.

## Impact

- 대상: 이슈 #19, Story·Record 엔티티와 새 controller/DTO/service/repository/OpenAI 클라이언트, 공통 ErrorCode, 서버의 AI 설정과 관련 테스트.
- 선택 이유와 후속 기능의 상태 규칙은 [ADR 0002](../../../docs/adr/0002-이야기-초안과-공개-상태를-story의-생명주기로-관리한다.md)에 기록한다.
- API 원본: [팀 API 명세](https://app.notion.com/p/API-41f14af65e0680929a7aeb0ff586991f), [공통 응답 계약](https://app.notion.com/p/41f14af65e0680469f52e39d31580fa2). 현재 Notion 연결에서 열리지 않아 사용자가 제공한 본문과 이후 대화에서 확인한 상태 기반 저장 흐름을 기준으로 작성한다.
- OpenAI API 키는 다른 팀원이 등록한다. 키와 모델은 설정으로 주입하며 키가 없는 자동 테스트는 대역 클라이언트를 사용한다.
- 완료 기준: 요청·응답 계약, 원문 보존, UUID 유일성, DRAFT 원자적 저장과 오류별 무저장을 자동 테스트로 증명하고 Java 21 clean build를 통과한다.
- 제외 범위: POST /api/v1/stories 확정·멱등성·동시 요청 처리, 중간 편집 저장, 공개 조회·학생 메시지 API, 이미지 생성, 프론트 STT 구현. 공개 API 담당 작업(#18/#17)은 ADR의 PUBLISHED 조건을 별도 반영해야 한다.
- 구현과 검증은 tasks.md의 작업 단위로 진행하고 커밋을 작게 나눈다. 확정 API는 후속 범위로 유지한다.
