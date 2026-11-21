# Proposal

## Why

학생은 어느 기기에서 입장해도 같은 이야기의 마지막 대화부터 이어갈 수 있어야 한다. 전송 응답을 잃었을 때도 입력을 다시 보내기 전에 실제 저장된 기록을 확인할 수 있어야 한다. [기능 명세서 §2–3](https://app.notion.com/p/41f14af65e0680aa8c4ddbadf98ee2ff)

## What Changes

- 공개 조회 경로 `GET /api/v1/stories/{storyId}/messages`를 추가한다. 등록할 때 저장한 첫 장면과 질문은 하나의 `STORY`로 읽는다. 재입장하거나 새로고침해도 같은 기록을 반환한다. [5번 API 명세](https://app.notion.com/p/41f14af65e0681368214c6dac260425a)
- 최근 메시지를 최대 30건 반환한다. 이전 기록을 요청하면 `beforeMessageId`를 제외한 이전 30건까지 반환한다. 각 페이지 안에서는 오래된 메시지가 먼저다.
- 6번이 저장한 학생 원문과 답변의 ID·타입·본문을 보존한다. 교정 후 다시 보낸 대화에는 원문, 교정 피드백, 다시 보낸 문장, 다음 장면이 차례대로 한 번씩 나타나야 한다. [6번 API 명세](https://app.notion.com/p/41f14af65e06812aba4bc3b79540d8bb)

## Capabilities

### New Capabilities

- `chat-history` — 공개 이야기의 저장된 대화를 메시지 ID 기준으로 나누어 읽는다. [요구사항과 시나리오](specs/chat-history/spec.md)

### Modified Capabilities

## Impact

채팅 도메인에 읽기 경로를 추가한다. 전송 충돌이나 응답 유실 후에는 클라이언트가 이 경로를 커서 없이 호출해 마지막으로 저장된 메시지를 확인할 수 있다. 6번과의 연동 지점과 공개 상태 의존성은 설계에 기록한다. [설계](design.md) · [구현 작업](tasks.md) · [이슈 #20](https://github.com/SOPT-all/39-SOPKATHON-SERVER-WEB1/issues/20)
