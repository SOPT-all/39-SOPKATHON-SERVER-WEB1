# Design

## Context

현재 5번 브랜치의 `Chat`에는 DB가 발급하는 ID, 이야기 FK, 타입, 본문이 있다. 6번 작업의 `MessageStore.savePair`는 같은 트랜잭션에서 학생 원문과 답변 순으로 저장한다. `SendMessageResponse.Message`도 같은 순서로 배열에 담기며 각 항목은 저장된 ID·타입·본문에서 만들어진다. [학생 메시지 전송 작업 #17](https://github.com/SOPT-all/39-SOPKATHON-SERVER-WEB1/issues/17)

## Goals / Non-Goals

5번은 읽기 전용 경로를 소유한다. 첫 STORY 생성은 최종 등록 경로가, 이후 원문과 답변의 원자적 저장 및 전송 선점은 6번이 소유한다.

## Decisions

GET과 POST가 같은 메시지 ID를 쓰는 이유와 응답 유실 시 복원 흐름은 [공유 채팅 ADR 초안](../../../docs/adr/0004-채팅-조회와-전송은-저장된-메시지-id로-대화를-이어간다.md)에 기록한다.

### 메서드별 책임

5번의 조회 클래스는 6번과 같은 `domain.chat` 패키지에 둔다. 컨트롤러와 서비스의 의존성은 생성자로 주입한다.

| 메서드 | 책임 |
| --- | --- |
| `ChatHistoryController.getChatHistory(storyId, beforeMessageId)` | 경로 ID와 선택 커서를 양의 int64로 검증하고 서비스 결과를 `ApiResponse.success`로 감싼다. 두 값에 동일한 10진수 파서를 적용한다. 형식·범위 오류는 `INVALID_PARAMETER`로 응답한다. |
| `ChatHistoryService.getChatHistory(storyId, beforeMessageId)` | 읽기 전용 트랜잭션에서 공개 상태를 먼저 확인한다. 공개 이야기가 없으면 `STORY_NOT_FOUND`로 종료한다. 공개 이야기이면 저장소가 반환한 목록을 역순으로 순회해 `GetChatHistoryResponse`를 만든다. 이전 페이지가 비어 있어도 조회는 성공한다. |
| `ChatHistoryRepository.findRecentMessages(storyId, beforeMessageId)` | 해당 이야기의 채팅에서 커서보다 작은 ID만 남기고, ID 내림차순으로 최대 30행을 읽는다. 커서가 생략되면 ID 상한 조건만 생략한다. |
| `MessageItem.from(Chat)` | 저장된 ID·타입·본문을 각각 `messageId`, `type`, `text`로 옮긴다. 메시지 한 건의 변환을 맡고 페이지 정렬은 서비스에 둔다. |

### 조회 조건과 공개 판정

커서는 ID의 배타적 상한이다. 두 조회의 차이는 조건 하나이므로 저장소 메서드 한 개에서 처리한다. 커서별 전략 클래스나 별도 커서 행 조회를 추가할 이유가 없다. 오프셋을 사용하면 이전 페이지를 요청하는 사이에 들어온 전송이 페이지 경계를 옮긴다. ID 경계는 이야기 사이에 ID가 비거나 새 턴이 추가되어도 안정적이다.

서비스가 이야기의 공개 상태를 직접 확인해야 등록 전 초안과 공개 이야기의 빈 페이지를 구분할 수 있다. 다른 공개 API도 같은 판정을 사용하게 되면 이야기 도메인에서 그 판정을 공유한다.

### 6번과 공유할 메시지 표현

GET의 `GetChatHistoryResponse`는 `messages`를, POST의 `SendMessageResponse`는 `result`와 `messages`를 갖는 별도의 record로 둔다. 두 응답이 같은 메시지 항목을 포함하도록 합성하면 각 API가 바뀔 때 공통 상위 응답까지 수정할 필요가 없다. 5번에서는 `MessageItem`을 정의하고, 두 기능을 합칠 때 6번의 중첩 `Message`를 같은 항목과 변환으로 통합한다. 통합 전에는 6번 워크트리의 클래스에 의존하지 않는다. 두 경로의 `STORY_NOT_FOUND`도 공통 오류 열거값 하나로 유지한다.

5번의 원본은 저장된 채팅 행이다. 타입이나 본문이 null인 행을 건너뛰면 GET과 POST가 서로 다른 마지막 ID를 볼 수 있으므로, 해당 페이지 전체를 서버 오류로 처리하고 서버 로그에 문제의 메시지 ID를 남긴다. 첫 STORY에 질문이 포함되므로 별도 프롤로그를 추가하지 않는다. 6번의 AI 입력용 조회는 교정 기록을 제외하지만, 화면 내역에는 교정 전 원문과 피드백도 필요하다. 페이지 크기는 화면에 표시할 메시지 수로 계산한다.

## Risks / Trade-offs

- **초안 공개 위험.** #19의 `Story.status` 모델을 사용해 `PUBLISHED`만 조회하고 초안과 없는 ID는 같은 404로 처리한다. 상태 모델의 소유권은 #19에 둔다. 첫 메시지 유무를 공개 판정에 사용하면 등록 상태와 저장 이력을 혼동하게 된다. [이야기 초안 작업 #19](https://github.com/SOPT-all/39-SOPKATHON-SERVER-WEB1/issues/19)
- **전송 도중의 새로고침.** 조회는 전송 선점을 기다리지 않고 커밋된 내역을 읽는다. 충돌 직후에는 이전 장면이 최신일 수 있고, 전송 커밋 뒤 커서 없이 다시 읽으면 새 답변을 포함한 최신 페이지를 받는다.
