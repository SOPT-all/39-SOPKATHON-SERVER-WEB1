# PLAY SOPT 솝커톤 WEB 1조 서버

## 실행과 검증

프로젝트 루트에서 JDK 21과 Gradle Wrapper를 사용한다. Windows에서는 `./gradlew` 대신 `.\gradlew.bat`를 사용한다.

| 작업 | 명령 |
| --- | --- |
| 서버 실행 | `./gradlew bootRun` |
| 테스트 | `./gradlew test` |
| PR과 동일한 빌드·테스트 | `./gradlew clean build` |

JPA는 공통으로 `open-in-view: false`를 사용한다. 엔티티의 지연 로딩과 응답 DTO 변환은 필요한 서비스 트랜잭션 안에서 처리한다.

| 프로파일 | 스키마 설정 | SQL 출력 |
| --- | --- | --- |
| local | `ddl-auto: update` — 엔티티 변경을 개발 DB에 반영 | 실행 SQL을 포맷하여 출력 |
| prod | `ddl-auto: validate` — 기존 스키마를 검사하며 자동 변경하지 않음 | 출력하지 않음 |
| test | `ddl-auto: create-drop` — H2 테스트 스키마 생성·정리 | 테스트 설정 사용 |

운영 프로파일은 필요한 테이블·컬럼·제약을 별도 적용한 뒤 실행하며 스키마 검증에 실패하면 기동하지 않는다. NOT NULL·유일성 제약도 DDL로 별도 적용·확인한다. SQL 바인딩 값 로깅은 활성화하지 않는다.

## 이야기 초안 생성

`POST /api/v1/story-drafts`는 브라우저에서 확정한 텍스트를 JSON으로 받는다. 원문은 유니코드 코드 포인트 기준 최대 5,000자이며 숫자·빈 값·공백뿐인 값은 허용하지 않는다. 성공 시 원문과 UUID를 Record에, 편집용 결과를 Story의 DRAFT 상태로 함께 저장하고 200 SUCCESS로 반환한다. 이 단계에서는 첫 장면·이미지·채팅을 생성하지 않는다.

OpenAI Responses API와 구조화 출력을 사용한다. 키는 담당자가 서버 환경변수로 등록하며 저장소에 넣지 않는다. 모델명은 구조화 출력을 지원하는 모델을 지정한다.

| 환경변수 | 기본값 | 용도 |
| --- | --- | --- |
| OPENAI_API_KEY | 없음 | OpenAI 인증 키 |
| OPENAI_MODEL | 없음 | 사용할 모델 ID |
| OPENAI_CONNECT_TIMEOUT | 5s | 연결 제한 시간 |
| OPENAI_READ_TIMEOUT | 60s | 응답 대기 제한 시간 |
| OPENAI_MAX_OUTPUT_TOKENS | 4096 | 생성 토큰 상한 |

연결·응답 제한 시간과 토큰 상한은 양수여야 한다.

키나 모델이 없으면 다른 기능의 서버 기동은 허용하고 초안 생성·이야기 확정의 AI 호출에 503 AI_UNAVAILABLE을 반환한다. 초안 생성 실패 시 생성 결과나 원문을 저장하지 않는다. DB 접속은 local/prod 프로파일의 DB_URL, DB_USERNAME, DB_PASSWORD를 사용한다.

Windows PowerShell에서 JDK 21과 로컬 MySQL을 준비하고 위 환경변수를 설정한 뒤 `.\gradlew.bat bootRun`으로 실행한다. [Swagger](http://localhost:8080/swagger-ui/index.html)에서 다음 요청을 보낼 수 있다.

```http
POST /api/v1/story-drafts
Content-Type: application/json

{"transcript":"해질 무렵 소나무 숲에서 토끼가 열쇠를 이러버렸어요. 여우를 따라가 풀잎 아래서 열쇠를 찾았어요."}
```

기존 DB에는 story_status와 record_uuid의 NOT NULL·유일성 제약을 별도로 적용해야 한다. 데이터 분류·UUID 누락·중복 점검과 DDL 적용 순서는 [초안 생성 설계의 Migration Plan](openspec/changes/generate-story-drafts/design.md#migration-plan)을 따른다. 로컬의 `update`도 기존 데이터 분류나 UUID 누락·중복 해결을 대신하지 않는다.

로컬은 `update`가 기본 적용되므로 다음처럼 실행한다. 운영 DB를 DB_URL에 지정한 상태로 local 프로파일을 실행하지 않는다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local"
```

자동 테스트는 H2와 대역 HTTP 서버·클라이언트를 사용하므로 실제 OpenAI 키나 외부 호출 없이 `.\gradlew.bat test`로 실행할 수 있다. 실제 모델의 원문 보존·소개문 품질은 키와 모델 등록 후 대표 원문으로 별도 확인한다.

## 학생 메시지 API 실행 설정

`POST /api/v1/stories/{storyId}/messages`는 Spring Boot 실행 환경의 `OPENAI_API_KEY`를 사용한다. 로컬에서는 프로젝트 루트의 `.env`에 `OPENAI_API_KEY=값`을 입력하거나 실행 환경변수로 지정한다. EC2 배포에서는 GitHub Actions의 별도 `OPENAI_API_KEY` 시크릿을 컨테이너에 전달한다. [교정 지침](src/main/resources/prompts/correction.yml)과 [장면 생성 지침](src/main/resources/prompts/scene.yml)은 같은 학생 원문으로 Luna에 병렬로 전달한다. 교정이 필요하면 장면 생성을 기다리지 않고 피드백만 반환하며, 수정 후 전송은 새 입력으로 처리한다. 기존 환경 변수 `TERRA_INSTRUCTIONS`(교정)와 `ASTRA_INSTRUCTIONS`(장면)를 지정하면 각 기본 지침을 교체한다. 테스트는 로컬 HTTP 대역으로 실행한다. 호출 분리와 기억 저장 방식의 배경은 [ADR 0003](docs/adr/0003-공유-이야기의-교정과-장면-생성을-분리한다.md)를 참고한다.
## 편집한 이야기 확정

`POST /api/v1/stories`는 초안 응답의 UUID를 `Idempotency-Key` 헤더로 받고 기존 Story를 공개 확정한다. 최초 원문과 최종 편집값을 보내면 첫 장면과 학생 질문 하나를 생성하고, 최종값·첫 STORY 메시지·PUBLISHED 전환을 한 트랜잭션에 저장한다. Record와 Story ID, 최초 원문은 유지한다. 작성자의 추가 확인 단계는 없다.

```http
POST /api/v1/stories
Content-Type: application/json
Idempotency-Key: e91ce7a5-f15b-434d-988c-8c704ed41693

{
  "transcript": "해질 무렵 소나무 숲에서 토끼가 열쇠를 이러버렸어요. 여우를 따라가 풀잎 아래서 열쇠를 찾았어요.",
  "title": "토끼와 여우의 열쇠 찾기",
  "summary": "열쇠를 잃어버린 토끼가 여우를 만나요.",
  "setting": "해 질 무렵의 소나무 숲",
  "characters": "주인공은 토끼다. 여우는 토끼에게 길을 안내한다.",
  "coreEvent": "토끼가 숲에서 잃어버린 열쇠를 찾는다.",
  "category": "FANTASY"
}
```

예시 UUID는 실제 초안 응답 값으로 교체한다. 원문은 5,000 코드 포인트, 제목은 20자, 소개문·배경은 200자, 인물은 100자, 사건은 500자까지이며 모두 공백이 아닌 문자열이어야 한다. 카테고리는 HORROR, ROMANCE, THRILLER, ACTION, FANTASY, ETC 중 하나이다.

최초 확정은 201 CREATED, 같은 키·필드값의 재시도는 200 SUCCESS와 `data.storyId`만 반환한다. JSON 키 순서는 무관하며 최초 transcript도 비교한다. 동일 동시 요청은 생성 결과를 공유하고 첫 메시지를 한 번 저장한다. 처리 중이거나 확정된 키에 다른 값을 보내면 409 IDEMPOTENCY_KEY_CONFLICT이다. 없는 UUID는 404 RESOURCE_NOT_FOUND, 첫 확정의 원문 불일치는 400 INVALID_INPUT, 키 누락·형식 오류는 400 INVALID_PARAMETER이다.

생성 실패는 503 AI_UNAVAILABLE, DB 저장 실패는 500 INTERNAL_SERVER_ERROR이다. 실패 시 초안의 기존 필드를 유지하고 첫 메시지를 남기지 않는다. 대기 시간 초과나 처리자 실패를 기다린 요청도 503으로 끝나며, 프론트는 같은 키·본문으로 별도 재시도한다. 실패한 시도를 기다리던 요청이 자동으로 새 AI 생성을 시작하지 않는다.

| 환경변수 | 기본값 | 용도 |
| --- | --- | --- |
| STORY_PUBLICATION_LEASE | 120s | DB 처리 권한 유효 기간 |
| STORY_PUBLICATION_WAIT_TIMEOUT | 75s | 동일 요청의 결과 대기 상한 |
| STORY_PUBLICATION_POLL_INTERVAL | 100ms | 대기 중 DB 조회 간격 |

세 값은 양수여야 한다. 실제 유효 기간은 설정값과 `OPENAI_CONNECT_TIMEOUT + OPENAI_READ_TIMEOUT + 30s` 중 큰 값이다. 생성과 대기는 DB 트랜잭션 밖에서 수행하고, 짧은 잠금으로 권한 획득과 최종 저장을 조정한다. 중단된 처리 권한은 만료 후 새 요청이 회수하며 이전 토큰은 저장하거나 새 토큰을 해제할 수 없다. 서버 중단·응답 유실 후 외부 AI 호출은 반복될 수 있고, DB 공개와 첫 메시지 저장의 중복을 방지한다.

기존 DB는 배포 전에 [확정 설계의 Migration Plan](openspec/changes/publish-stories/design.md#migration-plan)에 있는 nullable 컬럼 세 개를 적용한다. 기존 PUBLISHED에 요청 해시가 없으면 재확정을 409로 거부하므로 기존 데이터도 확인한다. 결정의 이유는 [ADR 0003](docs/adr/0003-이야기-확정의-멱등성과-동시-요청을-db에서-관리한다.md)을 참조한다. 실제 OpenAI의 문장 품질과 MySQL의 잠금 동작은 해당 환경에서 별도 확인하며 자동 테스트는 H2 MySQL 모드와 HTTP 대역을 사용한다.

## 팀 협업

1. 담당자와 진행을 공유할 기능·버그는 이슈에 작업 내용, 완료 기준과 담당자를 남긴다. 버그는 재현 방법과 기대 동작도 적는다. 리뷰 반영과 오탈자는 진행 중인 PR에서 처리한다.
2. 최신 `dev`에서 `feature/<이슈번호>-<영문-작업명>` 브랜치를 만든다. 커밋과 작업 PR 제목은 `<타입>: <한글 설명>`으로 쓴다. 타입은 `init`, `feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `ci` 중 작업에 맞게 고른다.
3. 작업 PR은 `dev`를 대상으로 [PR 양식](.github/PULL_REQUEST_TEMPLATE.md)을 채운다. 리뷰 1명과 빌드 통과를 확인한 뒤 Squash merge하고 작업 브랜치를 삭제한다.
4. 배포할 기능과 연동을 확인한 뒤 `dev → main` PR을 Merge commit으로 병합한다. 배포 담당자는 서버 기동과 주요 API 응답을 확인한다.

팀 합의를 변경할 때는 [브랜치·커밋 규칙](https://app.notion.com/p/41dfa71b42ef80aca0efcbe615b8f97c), [이슈·PR 양식](https://app.notion.com/p/PR-41dfa71b42ef802686e7e03eee30c2df)과 관련 저장소 템플릿·워크플로를 함께 확인한다.

## 설계 결정

기존 선택의 이유는 [결정 기록](docs/adr/)에서 확인한다. 결정 검색·제안·대체 또는 Codex MCP 설정이 필요하면 [ADR 사용법](docs/adr-guide.md)을 참고한다.

## 변경 명세

OpenSpec을 쓰는 팀원은 Node.js 20.19 이상에서 `npm install -g @fission-ai/openspec@1.14.3`을 한 번 실행한다. 프로젝트 루트에서 `openspec list`로 진행 중인 변경을 확인한다. [Codex Skills](.agents/skills/)와 [한국어 산출물 설정](openspec/config.yaml)은 저장소에서 공유한다.

1. **제안** — Codex에 새 기능이나 계약 변경을 설명하면 `$openspec-propose`로 변경 범위·시나리오·작업 목록을 작성한다. 팀 노션의 API 명세는 링크하고 이번 변경 사항만 적는다.
2. **구현** — 제안을 확인한 후 `$openspec-apply-change`로 구현을 요청한다. `openspec validate <변경명> --strict`로 명세 형식을 검사하고, 동작은 Gradle 테스트로 검증한다.
3. **마무리** — 검증한 변경은 `$openspec-archive-change`로 현재 명세에 반영하고 보관한다. 구현 전 범위가 바뀌면 `$openspec-update-change`로 제안을 갱신한다.
