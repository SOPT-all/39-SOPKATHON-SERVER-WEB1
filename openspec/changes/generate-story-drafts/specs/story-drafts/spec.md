# Spec Delta

## Purpose

브라우저가 확정한 한국어 원문에서 작성자가 편집할 이야기 초안을 만들고, 최초 원문과 등록용 UUID를 함께 보관한다. 초안 생성과 공개 확정의 단계를 구분하여 후속 등록에서 동일한 이야기와 원문을 유지하도록 한다.

## ADDED Requirements

### Requirement: 공개 JSON 요청

시스템은 인증 없이 POST /api/v1/story-drafts에서 application/json 요청을 받아야 한다(SHALL). 본문은 transcript 필드를 가진 JSON 객체이며 잘못된 JSON이나 문자열이 아닌 transcript에는 400 INVALID_REQUEST_BODY, 지원하지 않는 미디어 타입에는 415 UNSUPPORTED_MEDIA_TYPE을 반환해야 한다(SHALL).

#### Scenario: 인증 없는 원문 요청
- **WHEN** 인증 정보 없이 JSON 객체에 유효한 문자열 transcript를 보낸다
- **THEN** 원문 검증과 초안 생성을 진행한다

#### Scenario: JSON 형식 또는 필드 타입 오류
- **WHEN** 잘못된 JSON 또는 숫자·boolean·배열·객체 타입의 transcript를 보낸다
- **THEN** 400 INVALID_REQUEST_BODY를 반환하고 AI 호출과 DB 저장을 수행하지 않는다

#### Scenario: 음성 파일이나 일반 텍스트 요청
- **WHEN** multipart/form-data 또는 text/plain으로 요청한다
- **THEN** 415 UNSUPPORTED_MEDIA_TYPE을 반환한다

### Requirement: 원문 필수값과 길이 검증

시스템은 transcript 누락·null·빈 문자열·공백만 있는 값과 유니코드 코드 포인트 5,000개 초과를 400 INVALID_INPUT으로 거부해야 한다(SHALL). 유효한 원문은 공백 제거·맞춤법 교정·유니코드 정규화 없이 보존해야 한다(MUST).

#### Scenario: 원문이 비어 있음
- **WHEN** transcript가 누락되거나 null, 빈 문자열, 줄바꿈·탭을 포함한 공백뿐이다
- **THEN** 400 INVALID_INPUT을 반환하고 AI 호출과 저장을 수행하지 않는다

#### Scenario: 코드 포인트 최대 길이
- **WHEN** 보조 평면 문자를 포함한 비어 있지 않은 원문의 코드 포인트 수가 5,000개이다
- **THEN** UTF-16 길이가 더 길더라도 원문 길이를 이유로 거부하지 않는다

#### Scenario: 코드 포인트 제한 초과
- **WHEN** 원문의 코드 포인트 수가 5,001개이다
- **THEN** 400 INVALID_INPUT을 반환하고 AI 호출과 저장을 수행하지 않는다

### Requirement: 편집 필드 생성과 검증

시스템은 원문에서 title/summary/setting/characters/category/coreEvent 여섯 값을 생성하고 모두 공백이 아닌 문자열인지 검증해야 한다(SHALL). 카테고리는 HORROR/ROMANCE/THRILLER/ACTION/FANTASY/ETC 중 하나여야 하며 제목 20자, 소개문·배경 각 200자, 인물 100자, 핵심 사건 500자의 저장 제한을 만족해야 한다(MUST).

#### Scenario: 유효한 생성 결과
- **WHEN** 여섯 필드가 필수값·카테고리·길이 조건을 만족한다
- **THEN** 검증된 필드값으로 초안 저장을 진행한다

#### Scenario: 필드 누락이나 잘못된 추천 코드
- **WHEN** 생성 결과에 필드 누락·공백·잘못된 타입·허용하지 않는 카테고리·길이 초과 중 하나가 발생한다
- **THEN** 결과를 임의로 잘라 저장하지 않고 503 AI_UNAVAILABLE을 반환하며 DB에 저장하지 않는다

### Requirement: 원문에 근거한 편집 내용

시스템은 문맥상 명백한 오인식·띄어쓰기를 생성 내용에서 다듬고, 불분명한 이름의 원문 표기와 핵심 사건을 보존하도록 생성해야 한다(SHALL). 소개문은 사건 결과를 숨기고 인물 설명의 첫 문장은 주인공을 소개해야 한다(SHALL).

#### Scenario: 명백한 오인식과 불분명한 이름
- **WHEN** 대표 검토 원문에 문맥상 명백한 오인식과 확인할 수 없는 고유 이름이 포함된다
- **THEN** 편집 결과는 명백한 오류만 교정하고 이름 표기와 사건을 유지하며 최초 저장 원문은 바뀌지 않는다

#### Scenario: 소개문과 인물 설명
- **WHEN** 열쇠를 잃었다가 찾는 토끼와 안내하는 여우의 이야기를 생성한다
- **THEN** 소개문은 열쇠를 찾는 결말을 드러내지 않고 인물 설명은 토끼를 주인공으로 소개하는 문장으로 시작한다

### Requirement: 원문과 미공개 초안의 원자적 저장

시스템은 서버 발급 UUID와 최초 원문을 가진 녹음 및 이에 1:1로 연결된 DRAFT 이야기를 함께 저장해야 한다(SHALL). UUID는 null을 허용하지 않고 유일해야 하며 첫 장면·이미지 URL은 비워두고 질문·채팅 메시지를 생성하지 않아야 한다(MUST). 두 저장 중 하나라도 실패하면 새 녹음과 이야기를 모두 롤백해야 한다(MUST).

#### Scenario: 초안 저장 성공
- **WHEN** 생성 결과 검증과 DB 커밋이 성공한다
- **THEN** 최초 원문·UUID와 여섯 편집 값이 보존되고 연결된 이야기는 DRAFT이며 첫 장면·이미지 URL·채팅은 생성되지 않는다

#### Scenario: 저장 실패
- **WHEN** 녹음 저장 뒤 이야기 저장이나 최종 커밋이 실패한다
- **THEN** 새 녹음·이야기가 모두 남지 않고 500 INTERNAL_SERVER_ERROR를 반환한다

#### Scenario: UUID 중복 저장
- **WHEN** 이미 사용 중인 UUID로 새 녹음을 저장하려 한다
- **THEN** DB 유일성 제약으로 저장을 거부하고 이 요청의 부분 저장을 롤백한다

#### Scenario: UUID 누락 저장
- **WHEN** UUID가 null인 녹음을 저장하려 한다
- **THEN** NOT NULL 제약으로 저장을 거부하고 이 요청의 부분 저장을 남기지 않는다

### Requirement: 편집용 성공 응답

시스템은 DB 커밋 성공 후 HTTP 200, code SUCCESS, message 요청에 성공했습니다.와 함께 data에 recordUuid/title/summary/setting/characters/category/coreEvent를 반환해야 한다(SHALL). recordUuid는 서버가 발급해 저장한 표준 UUID 문자열이어야 하며 편집 필드는 저장된 값과 일치해야 한다(MUST).

#### Scenario: 응답으로 편집 화면 구성
- **WHEN** 초안 생성·저장이 성공한다
- **THEN** 프론트가 최초 원문과 반환 UUID를 보관하고 여섯 필드로 편집 폼을 구성할 수 있는 200 응답을 받는다

### Requirement: AI 실패 처리

시스템은 AI 응답 시간 초과·요청 실패·생성 거부·불완전 응답·해석 불가능한 결과를 503 AI_UNAVAILABLE로 변환해야 한다(SHALL). 응답 message는 이야기 AI에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.이며 data는 null이어야 하고 이 요청의 녹음·이야기를 저장하지 않아야 한다(MUST).

#### Scenario: AI 응답을 얻지 못함
- **WHEN** AI 호출이 실패하거나 유효한 초안으로 변환할 수 없는 결과를 받는다
- **THEN** 503 AI_UNAVAILABLE과 null data를 반환하고 DB에 새 녹음·이야기를 저장하지 않는다

### Requirement: 초안 생성 재시도의 독립성

시스템은 초안 생성에 Idempotency-Key를 요구하지 않아야 한다(SHALL). 동일 원문의 별도 성공 요청은 새 UUID와 별도 초안을 생성하며, 최종 등록의 멱등성 처리는 이 API에서 수행하지 않아야 한다(SHALL).

#### Scenario: 같은 원문으로 다시 생성
- **WHEN** 같은 원문으로 초안 생성을 두 번 요청하고 두 요청 모두 성공한다
- **THEN** 서로 다른 UUID에 연결된 두 초안을 반환하고 기존 초안을 확정하거나 덮어쓰지 않는다
