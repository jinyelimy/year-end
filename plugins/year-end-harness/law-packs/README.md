# Law Packs

이 디렉터리는 월별 공식 세법팩의 Git 관리 저장소다.

## 목적

- 공식 소스 기반 세법 스냅샷을 월별로 남긴다.
- 사람이 읽는 세법 팩과 계산용 정규화 룰팩을 함께 보존한다.
- 이전 월 버전과의 diff 를 남겨 변경 추적을 쉽게 한다.

## 구조

```text
law-packs/
  2025/
    2025.01/
      source-manifest.json
      agent-a-tax-pack.md
      normalized-rule-pack.json
      diff-from-previous.md
```

## 규칙

- `agent-a-tax-pack.md`는 사람이 읽는 근거 문서다.
- `normalized-rule-pack.json`은 review/publish 후보 데이터이며, 런타임은 `PUBLISHED` 상태의 룰셋만 읽는다.
- `source-manifest.json`에는 공식 원문 레지스트리와 확인 시각을 남긴다.
- 원문 파일 자체는 용량 문제로 `.local/harness/<date>/<run-id>/raw/`에 두고, 이 디렉터리에는 정리된 산출물만 둔다.
- 같은 월에 정정이 발생하면 `YYYY.MM.patch` 버전을 쓴다.
- 이 디렉터리로 승격되는 세법팩은 review 를 통과한 정본만 허용한다.
