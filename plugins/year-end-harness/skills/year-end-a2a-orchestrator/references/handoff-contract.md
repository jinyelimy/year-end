# Handoff Contract

모든 단계 산출물은 아래 순서를 따른다.

## Required Sections

- `Context`
- `Inputs`
- `Decisions`
- `Open Questions`
- `Files`
- `Validation`

## Result Block

```text
=== HARNESS RESULT ===
STATUS   : success | warning | error
SUMMARY  : <한 줄 요약>
ARTIFACTS: <파일 경로>
NEXT     : <다음 권고 액션>
======================
```

## Minimum Handoffs

- Agent A -> Agent B: 세법 팩, 가족공제 판단표, 증빙 매트릭스
- Agent B -> Agent C: 스키마 제안, 로직 트리, review 분기
- Agent C -> Repo Validation: 구현 노트, 변경 파일
- Repo Validation -> Agent D: validation report, 실행 결과
- Agent D -> Agent C: 루프별 defect report
- Agent D -> Agent E: loop-1/2/3 report
