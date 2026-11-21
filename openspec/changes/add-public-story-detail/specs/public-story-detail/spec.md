# Spec Delta

## Purpose

학생이 선택한 공개 이야기의 소개, 배경, 등장인물을 비공개 정보 없이 제공한다.

## ADDED Requirements

### Requirement: 공개 이야기 상세 조회

시스템은 인증 없이 GET /api/v1/stories/{storyId} 요청을 받아 PUBLISHED 이야기를 200 SUCCESS의 data로 반환해야 한다(SHALL). 응답은 storyId, title, summary, imageUrl, category, setting, characters만 포함해야 하며(SHALL), 핵심 사건·첫 장면과 녹음 원문·UUID를 노출해서는 안 된다(MUST NOT).

#### Scenario: 공개 이야기 조회
- **WHEN** PUBLISHED 이야기의 ID로 조회한다
- **THEN** 일곱 공개 필드를 반환하고 category는 영문 대문자 코드로 반환한다

### Requirement: 조회할 수 없는 이야기

시스템은 존재하지 않거나 PUBLISHED가 아닌 이야기에 404 STORY_NOT_FOUND를 반환해야 한다(SHALL).

#### Scenario: 초안 조회
- **WHEN** DRAFT 이야기의 ID로 조회한다
- **THEN** 404 STORY_NOT_FOUND를 반환한다

#### Scenario: 없는 이야기 조회
- **WHEN** 존재하지 않는 ID로 조회한다
- **THEN** 404 STORY_NOT_FOUND를 반환한다

### Requirement: 이야기 ID 검증

시스템은 숫자가 아니거나 int64 범위를 넘거나 1 미만인 storyId에 400 INVALID_PARAMETER를 반환해야 한다(SHALL).

#### Scenario: 잘못된 ID
- **WHEN** storyId가 0, 음수, 문자, 소수 또는 int64 초과 값이다
- **THEN** 400 INVALID_PARAMETER를 반환한다
