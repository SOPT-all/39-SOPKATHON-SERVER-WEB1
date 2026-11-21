# PLAY SOPT 솝커톤 WEB 1조 서버

## 실행과 검증

프로젝트 루트에서 JDK 21과 Gradle Wrapper를 사용한다. Windows에서는 `./gradlew` 대신 `.\gradlew.bat`를 사용한다.

| 작업 | 명령 |
| --- | --- |
| 서버 실행 | `./gradlew bootRun` |
| 테스트 | `./gradlew test` |
| PR과 동일한 빌드·테스트 | `./gradlew clean build` |

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

키나 모델이 없으면 다른 기능의 서버 기동은 허용하고 초안 요청에 503 AI_UNAVAILABLE을 반환한다. 실패 응답에서는 생성 결과나 원문을 저장하지 않는다. DB 접속은 local/prod 프로파일의 DB_URL, DB_USERNAME, DB_PASSWORD를 사용한다.

Windows PowerShell에서 JDK 21과 로컬 MySQL을 준비하고 위 환경변수를 설정한 뒤 `.\gradlew.bat bootRun`으로 실행한다. [Swagger](http://localhost:8080/swagger-ui/index.html)에서 다음 요청을 보낼 수 있다.

```http
POST /api/v1/story-drafts
Content-Type: application/json

{"transcript":"해질 무렵 소나무 숲에서 토끼가 열쇠를 이러버렸어요. 여우를 따라가 풀잎 아래서 열쇠를 찾았어요."}
```

기존 DB에는 story_status와 record_uuid 유일성 제약을 별도로 적용해야 한다. 데이터 분류·중복 점검과 DDL 적용 순서는 [초안 생성 설계의 Migration Plan](openspec/changes/generate-story-drafts/design.md#migration-plan)을 따른다. 새 개발 DB의 스키마 자동 생성은 로컬 실행에만 명시적으로 사용한다.

비어 있는 새 로컬 개발 DB에 한해 다음처럼 엔티티 기반 스키마 생성을 활성화할 수 있다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local --spring.jpa.hibernate.ddl-auto=update"
```

자동 테스트는 H2와 대역 HTTP 서버·클라이언트를 사용하므로 실제 OpenAI 키나 외부 호출 없이 `.\gradlew.bat test`로 실행할 수 있다. 실제 모델의 원문 보존·소개문 품질은 키와 모델 등록 후 대표 원문으로 별도 확인한다.

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
