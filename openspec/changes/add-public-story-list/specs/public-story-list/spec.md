# Spec Delta

## Purpose

학생이 탐색 화면에서 공개된 이야기를 고를 수 있도록, 공개 이야기만 필요한 필드로 제공한다.

## ADDED Requirements

### Requirement: 공개 이야기 목록 조회

시스템은 인증 없이 GET /api/v1/stories 요청을 받아 PUBLISHED 상태의 이야기만 id 내림차순으로 200 SUCCESS의 data.items에 반환해야 한다(SHALL).

#### Scenario: 공개 이야기와 초안이 함께 있음
- **WHEN** PUBLISHED 이야기와 DRAFT 이야기가 저장된 상태에서 목록을 조회한다
- **THEN** PUBLISHED 이야기만 최신 등록순으로 반환한다

#### Scenario: 공개 이야기가 없음
- **WHEN** 저장된 이야기가 없거나 모두 DRAFT 상태에서 목록을 조회한다
- **THEN** 200 SUCCESS와 빈 배열 items를 반환한다

### Requirement: 공개 필드 제한

시스템은 목록 항목에 storyId, title, summary, imageUrl, category만 포함해야 하며(SHALL), 핵심 사건·배경·인물·첫 장면과 녹음 원문·UUID를 노출해서는 안 된다(MUST NOT). category는 영문 대문자 코드 문자열이어야 한다(SHALL).

#### Scenario: 목록 항목 응답
- **WHEN** PUBLISHED 이야기가 목록에 포함된다
- **THEN** 항목은 다섯 공개 필드만 가지고 category는 FANTASY 같은 코드로 반환된다
