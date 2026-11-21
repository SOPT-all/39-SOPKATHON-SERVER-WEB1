# Spec Delta

## Purpose

학생이 어느 기기에서든 등록된 이야기의 공유 대화를 이어 읽고, 이전 기록을 불러오거나 전송 결과가 불확실할 때 마지막으로 저장된 메시지를 확인할 수 있게 한다.

## ADDED Requirements

### Requirement: 공개 이야기의 공유 대화 읽기

서버는 인증 없이 `GET /api/v1/stories/{storyId}/messages`로 해당 공개 이야기의 저장된 메시지만 읽어야 한다(SHALL). 성공 응답은 HTTP 200, `code: SUCCESS`, `message: 요청에 성공했습니다.`와 `data.messages`를 담아야 한다(SHALL).

#### Scenario: 학생 발화 전 다른 기기에서 재입장

- **WHEN** 첫 장면과 질문을 담은 첫 STORY 한 건만 있는 이야기에서 두 기기가 차례로 채팅 내역을 조회한다
- **THEN** 두 응답 모두 같은 ID와 본문의 STORY 한 건을 반환하고 저장된 메시지 수는 한 건이다

### Requirement: 전송 결과와 일치하는 메시지 내역

각 항목은 저장된 `messageId`(int64), `type`(STUDENT, FEEDBACK, STORY), `text`(문자열)를 반환해야 한다(SHALL). 같은 대화에서 학생 원문이 그에 대한 답변보다 먼저 나타나며 재전송한 문장도 독립된 메시지로 보존해야 한다(SHALL).

#### Scenario: 교정 후 고친 문장으로 이야기를 진행함

- **WHEN** 첫 장면 뒤에 6번 API가 교정 턴과 수정 후 진행 턴을 차례로 저장하고 학생이 최신 내역을 조회한다
- **THEN** 첫 STORY, STUDENT 원문, FEEDBACK, 수정한 STUDENT 원문, 다음 STORY가 각각 한 번씩 나타난다. 뒤의 네 항목은 두 전송 성공 응답의 메시지와 ID·타입·본문이 각각 같다

#### Scenario: 조회 페이지에 불완전한 메시지가 저장됨

- **WHEN** 반환할 메시지 중 하나라도 타입 또는 본문이 null이다
- **THEN** HTTP 500과 `INTERNAL_SERVER_ERROR`, `data: null`을 반환한다. 이전 메시지를 최신 ID로 노출하는 부분 성공 응답을 방지한다

#### Scenario: 충돌이나 응답 유실 후 최신 대화 확인

- **WHEN** 전송 충돌이나 응답 유실 후 커서 없이 다시 조회한다
- **THEN** 조회 시점에 저장이 완료된 메시지만 보인다. 응답을 잃었어도 커밋된 전송의 원문과 답변은 보이고, 거절되거나 롤백된 전송의 메시지는 보이지 않는다

### Requirement: 메시지 ID 기준 이전 페이지 조회

서버는 같은 이야기에서 커서 생략 시 최신 메시지, `beforeMessageId` 지정 시 그 값보다 ID가 작은 메시지 중 최신 메시지를 최대 30건 골라 ID 오름차순으로 반환해야 한다(SHALL). 이전 기록이 소진되면 빈 배열을 반환해야 한다(SHALL).

#### Scenario: 첫 페이지 이후 새 메시지가 저장됨

- **WHEN** 같은 이야기의 메시지 31건 중 최신 30건을 읽고, 새 전송이 저장된 뒤 첫 페이지의 가장 오래된 ID를 커서로 조회한다
- **THEN** 두 번째 페이지에는 처음에 읽지 않은 가장 오래된 한 건만 담긴다. 두 페이지를 이어 붙이면 원래 31건이 순서대로 한 번씩 나타난다. 중간에 다른 이야기에 저장된 메시지는 어느 페이지에도 포함되지 않는다

#### Scenario: 정확히 30건에서 첫 기록에 도달함

- **WHEN** 저장된 메시지 30건을 받은 뒤 첫 항목의 ID로 이전 페이지를 요청한다
- **THEN** HTTP 200과 `data.messages: []`를 반환한다

### Requirement: 잘못된 식별자와 공개되지 않은 이야기 처리

경로 ID와 전달된 커서는 ASCII 숫자(0–9)만으로 표기된 양의 10진수 int64로 해석할 수 있어야 한다(MUST). 잘못된 값은 HTTP 400 `INVALID_PARAMETER`, 조회할 공개 이야기가 없으면 HTTP 404 `STORY_NOT_FOUND`를 반환해야 한다(SHALL). 오류 응답의 `data`는 null이다.

#### Scenario: 식별자의 형식 또는 범위 오류

- **WHEN** 경로 ID 또는 전달된 커서가 문자, 0, 음수 또는 int64 범위를 넘는 값이다
- **THEN** HTTP 400과 `INVALID_PARAMETER`를 반환한다

#### Scenario: 없는 이야기 또는 등록 전 초안

- **WHEN** 유효한 ID가 존재하지 않거나 아직 공개 등록되지 않은 이야기의 ID로 조회한다
- **THEN** 두 경우 모두 HTTP 404와 `STORY_NOT_FOUND`를 반환한다
