# Tasks

## 1. 조회 메서드와 공개 판정

- [x] 1.1 `ChatHistoryRepository.findRecentMessages`를 구현한다. H2에 이야기 A의 메시지 31건과 B의 메시지를 번갈아 저장한다. A의 첫 조회가 A의 최신 30건을 ID 내림차순으로 반환하고, 새 전송 뒤에도 첫 페이지의 최소 ID를 커서로 지정하면 A의 맨 처음 한 건만 남는지 확인한다.
- [x] 1.2 `ChatHistoryService.getChatHistory`에서 #19의 공개 상태 모델을 연동한다. PUBLISHED 이야기의 첫 기록 이전은 빈 목록으로 성공하고, DRAFT와 없는 ID는 모두 `STORY_NOT_FOUND`가 되는지 검증한다. 저장소가 내림차순으로 반환한 여러 건이 응답에서는 ID 오름차순인지 확인한다.

## 2. 5번 GET 계약 검증

- [x] 2.1 `ChatHistoryController.getChatHistory`를 공통 응답에 연결한다. MVC 테스트에서 경로와 커서 각각에 문자·0·음수·int64 범위 초과를 넣어 400 `INVALID_PARAMETER`를 확인한다. DRAFT와 없는 이야기는 404 `STORY_NOT_FOUND`이며 오류 응답의 `data`는 null이어야 한다.
- [x] 2.2 `GetChatHistoryResponse`와 `MessageItem.from(Chat)`을 연결한다. 6번 코드 없이 저장한 첫 STORY를 인증 없이 두 번 GET해 같은 ID·타입·본문, 저장 건수 1건, 200 `SUCCESS`를 확인한다. 원문·피드백·수정한 원문·다음 장면을 차례로 저장한 뒤 GET의 다섯 항목이 저장값과 순서까지 일치하는지 확인한다.
- [x] 2.3 30건을 저장해 커서 없이 GET한 뒤 첫 항목의 ID로 이전 페이지를 요청한다. 첫 응답은 오래된 순서의 30건, 다음 응답은 200과 `data.messages: []`여야 한다.

## 3. 6번 병합 후 POST·GET 연동 검증

- [ ] 3.1 두 응답에 공통 `MessageItem`을 적용한다. 6번의 `MessageFlowTest`에서 사용하는 AI 테스트 대역과 실제 MVC·DB 경로를 활용해 첫 STORY의 ID로 학생 원문을 POST한다. `NEEDS_REVISION` 응답의 FEEDBACK ID를 `lastSeenMessageId`로 보내 수정한 문장을 POST하고 `STORY_ADVANCED`를 확인한다. 커서 없는 GET은 첫 STORY 뒤에 두 POST의 메시지 배열을 이은 결과와 정확히 같아야 한다. 네 항목의 ID·타입·본문과 순서, 전체 다섯 ID의 유일성을 비교한다.
- [ ] 3.2 성공한 POST 응답을 클라이언트가 사용하지 않은 상황에서 재전송 없이 GET을 반복한다. 성공한 한 쌍이 매번 같은 ID로 한 번씩 보여야 한다. 오래된 ID로 보낸 요청(409), AI 실패(503), 저장 중 롤백은 각각 요청 전후 GET 배열을 비교해 어느 쪽 메시지도 추가되지 않았는지 확인한다.
- [ ] 3.3 AI 테스트 대역에서 첫 전송을 대기시킨 동안 두 번째 전송의 409 `TURN_IN_PROGRESS`와 GET의 기존 내역을 확인한다. 첫 전송을 완료한 뒤 GET하면 성공한 한 쌍만 추가되어야 한다. 프로젝트 루트에서 `./gradlew clean build`로 연동 테스트를 포함한 전체 빌드를 검증한다.
