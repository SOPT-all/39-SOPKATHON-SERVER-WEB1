---
number: 5
title: "Spring에서 채팅 조회와 턴 저장의 트랜잭션 경계를 분리한다"
status: proposed
date: 2026-11-22
---

# Spring에서 채팅 조회와 턴 저장의 트랜잭션 경계를 분리한다

## Context and Problem Statement

학생의 문장과 AI 답변은 한 턴을 이룬다. 원문만 먼저 커밋하면 다른 기기의 GET이 미완성 턴을 표시할 수 있다. 반대로 AI 응답을 기다리는 동안 DB 트랜잭션까지 열어 두면 외부 호출의 지연이 DB 연결 점유 시간에 영향을 준다.

## Considered Options

- POST 처리 전체를 하나의 트랜잭션으로 감싸면 저장 흐름은 단순하지만 외부 AI 호출까지 트랜잭션 안에 들어간다.
- 학생 원문과 답변을 각각 커밋하면 첫 쓰기는 빨리 끝나지만 AI 실패나 후속 저장 실패 시 원문만 남을 수 있다.
- AI 응답이 준비된 뒤 한 턴을 짧은 쓰기 트랜잭션으로 커밋하고, GET은 독립된 읽기 트랜잭션에서 처리한다.

## Decision Outcome

세 번째 방식을 제안한다. POST 서비스는 이야기별 전송을 선점한 뒤 AI를 호출한다. 결과 저장은 별도 Spring 빈의 `@Transactional` 메서드를 호출해 처리한다. 학생 원문, 답변, 해당 턴에서 갱신할 맥락을 함께 커밋하고 저장 실패 시 함께 롤백한다. 선점은 커밋 또는 롤백이 끝난 뒤 해제한다. 컨트롤러는 커밋이 성공한 뒤 저장된 ID를 응답한다.

GET 서비스는 `@Transactional(readOnly = true)`에서 공개 여부를 검사하고 저장소의 채팅 행을 화면 순서로 반환한다. 조회는 전송 선점을 획득하지 않는다. 양쪽 응답은 같은 메시지 항목을 포함하며, 각 컨트롤러는 자신의 HTTP 응답을 구성한다. 클래스별 책임과 변환 메서드는 [OpenSpec 설계](../../openspec/changes/add-chat-history/design.md)에 둔다.

### Consequences

- Good, because AI가 응답하기 전에도 다른 학생은 직전까지 커밋된 대화를 읽을 수 있다.
- Good, because 한 턴의 저장이 실패해도 이후 GET에 학생 원문만 남지 않는다.
- Bad, because 쓰기 트랜잭션만으로는 같은 이야기에 대한 동시 전송을 조율할 수 없다. 이야기별 선점과 DB 커밋의 종료 순서를 함께 유지해야 한다.

POST 성공·실패 전후의 GET 결과로 이 경계를 검증한다. [연동 테스트 기준](../../openspec/changes/add-chat-history/tasks.md)
