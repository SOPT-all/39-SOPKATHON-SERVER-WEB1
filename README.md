# PLAY SOPT 솝커톤 WEB 1조 서버

## 실행과 검증

프로젝트 루트에서 JDK 21과 Gradle Wrapper를 사용한다. Windows에서는 `./gradlew` 대신 `.\gradlew.bat`를 사용한다.

| 작업 | 명령 |
| --- | --- |
| 서버 실행 | `./gradlew bootRun` |
| 테스트 | `./gradlew test` |
| PR과 동일한 빌드·테스트 | `./gradlew clean build` |

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
