## Why

시연 중 만든 초안, 공개 이야기와 대화가 다음 시연에도 남는다. 현재 시작 시 실행하는 시드 SQL은 기존 데이터를 보존하므로 반복 시연의 출발점을 복원할 수 없다.

## What Changes

- 배포 담당자가 GitHub Actions에서 초기화 대상을 미리 확인하고 명시적으로 시연 데이터를 복원하는 수동 작업을 제안한다.
- 배포 커밋의 시드를 원본으로 삼고, 복원 전후의 데이터와 서비스 응답을 검증한다.

## Capabilities

### New Capabilities

- `demo-data-reset`: 시연용 DB 초기 상태 미리보기 및 복원.

### Modified Capabilities

없음.

## Impact

GitHub Actions, EC2의 시연 앱과 해당 MySQL 데이터가 대상이다. 기존 API 계약은 유지한다. 초기화는 선택한 데이터의 테스트 기록을 지운다.
