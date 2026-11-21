# Tasks

이 목록은 첫 번째 초안 생성 API의 구현 작업이다. 해당 기능과 검증이 완료된 항목만 완료 처리하고 각 기능·검증 단위로 작은 커밋을 유지한다.

## 1. 초안 도메인과 저장 제약

- [x] 1.1 StoryStatus(DRAFT/PUBLISHED), 필수 story_status 컬럼과 초안 생성 팩토리를 추가하고 기존 생성자 사용처를 정리한다. 도메인·영속성 테스트로 DRAFT 기본값, 1:1 관계, 첫 장면·이미지 URL의 null 및 기존 매핑 호환성을 확인한다.
- [x] 1.2 RecordRepository·StoryRepository와 record_uuid 유일성 제약을 추가하되 기존 길이·nullable 조건을 유지한다. DB 테스트로 동일 UUID 거부와 다른 UUID 저장을 검증하고, 기존 데이터가 있는 경우의 상태 분류·중복 점검 및 스키마 적용 절차를 design.md에 구체화한다.

## 2. OpenAI 초안 생성

- [x] 2.1 StoryDraftGenerator와 RestClient 기반 OpenAI Responses API 구현, 키·모델·유한 timeout 설정 및 strict JSON Schema를 추가한다. 대역 HTTP 테스트로 원문·여섯 필드 스키마·인증 헤더와 응답 파싱을 확인하고, 키·모델 미설정 시 네트워크 호출 없이 AI_UNAVAILABLE을 반환함을 검증한다. README에 키 값 없이 환경변수와 로컬 실행 방법을 기록한다.
- [x] 2.2 오인식 교정·불분명한 이름 유지·핵심 사건 보존·결말을 숨긴 소개문·주인공 첫 문장의 프롬프트와 생성 결과 검증을 구현한다. 여섯 필드 누락·공백·타입·카테고리·각 길이 경계 테스트와 원문 무변경 검증을 추가하고, 의미 품질은 대표 원문과 기대 조건의 검토 사례로 기록한다.
- [x] 2.3 ErrorCode에 AI_UNAVAILABLE(503)을 추가하고 OpenAI timeout·401/429/5xx·refusal·불완전·비정상 응답을 변환한다. 대역 테스트로 고정 오류 코드·메시지와 실패 후 저장 경로 미호출을 확인하며 DB 예외는 500으로 구분한다.

## 3. HTTP 계약과 원자적 생성

- [x] 3.1 CreateStoryDraftRequest/Response와 공개 POST /api/v1/story-drafts controller를 구현하고 Swagger 계약을 작성한다. MVC 테스트로 인증 없는 JSON, 잘못된 JSON·transcript 타입의 400, 누락·null·공백의 400, 이모지 포함 5,000/5,001 코드 포인트 경계와 415를 확인한다. 잘못된 요청은 AI와 저장을 호출하지 않아야 한다.
- [x] 3.2 StoryDraftService와 별도 transactional StoryDraftWriter를 구현해 UUID 생성, 트랜잭션 밖 LLM 호출·검증, 최초 원문·UUID와 Story(DRAFT) 저장을 연결한다. 통합 테스트로 원문 그대로 저장, 한 Record·한 Story만 커밋, Chat/Context 무생성, LLM 호출 중 DB 트랜잭션 부재, 이야기 저장 실패 시 Record까지 롤백함을 확인한다.
- [x] 3.3 커밋 후 저장된 값으로 200 SUCCESS와 recordUuid·여섯 편집 필드를 반환한다. HTTP·DB 통합 테스트로 응답 UUID/각 필드와 저장값 일치, AI 실패 시 무저장, 같은 원문의 별도 성공 요청마다 새 UUID와 DRAFT 생성, 저장 실패 시 성공 응답 부재를 확인한다.

## 4. 통합 확인

- [x] 4.1 Java 21에서 .\gradlew.bat clean build, openspec validate generate-story-drafts --strict와 git diff --check를 실행한다. PR에 자동 테스트 결과와 실제 OpenAI/MySQL 검증 여부를 구분해 적고, 사용자 로컬 설정과 키가 커밋에 포함되지 않았는지 확인한다.

## Workflow follow-up

- API 키 담당자가 OPENAI_API_KEY와 구조화 출력을 지원하는 OPENAI_MODEL을 등록한 뒤 대표 원문으로 실제 출력 품질·응답 시간을 확인한다. 자동 대역 테스트 통과를 실제 모델의 의미 보존 검증으로 대체하지 않는다.
- 공개 목록(#18)·학생 메시지(#17)와 후속 공개 상세 API에 PUBLISHED 조건을 반영한 뒤 공개 기능과 함께 배포한다.
- POST /api/v1/stories의 기존 Story 갱신·첫 메시지·공개 전환·최종 본문 기반 멱등성과 동시성 처리는 별도 변경으로 구현한다.
- 팀 리뷰 후 ADR 상태를 결정하고 완료된 변경 명세를 아카이브한다.
