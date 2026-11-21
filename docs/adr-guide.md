# ADR 조회와 작성

[결정 기록](adr/)은 Markdown으로 바로 읽을 수 있다. 검색·생성·상태 변경에는 저장소의 adrs CLI 또는 MCP를 사용한다.

## 처음 사용할 때

저장소 루트에서 Python 3로 실행 파일을 설치한다. macOS·Linux에서 실행하며 Windows에서는 WSL을 사용한다.

```sh
python3 scripts/setup-adrs.py
./scripts/adrs list
```

Codex에서 사용하려면 프로젝트를 신뢰하고 새 세션을 연다. [프로젝트 MCP 설정](../.codex/config.toml)이 같은 실행 파일을 사용한다. Windows에서는 Codex도 WSL에서 실행한다.

## 결정의 생애주기

아래 명령은 저장소 루트에서 실행한다. 번호는 대상 ADR 번호로 바꾼다.

| 상황 | 할 일 |
| --- | --- |
| 관련 결정 찾기 | `./scripts/adrs search "검색어"`로 기존 결정과 대체 관계를 확인한다. |
| 새 결정 제안 | `./scripts/adrs new "결정 제목"`으로 생성한다. [MADR 템플릿](adr/template.md)의 YAML 필드와 구조 제목을 유지하고 본문은 한국어로 쓴다. 생성 날짜가 한국 날짜와 다르면 `date`를 바로잡는다. |
| 팀 합의 반영 | `./scripts/adrs status 번호 accepted`로 확정한다. |
| 기존 결정 대체 | 팀 합의 후 `./scripts/adrs new --status accepted --supersedes 이전번호 "새 결정 제목"`으로 연결하고 두 기록의 상태를 확인한다. |
| 상태·관계 변경 검증 | `./scripts/adrs doctor`를 실행한다. |
