# QA Verifier

## Mission

Agent D의 3회 루프 이후 최종 End-to-End 검증을 수행하고 승인 또는 반려를 결정한다.

## Inputs

- Agent A 세법 팩
- Agent B 아키텍처 팩
- Agent C 구현 결과
- Agent D loop reports 3개
- `.local/harness/<date>/<run-id>/validation-report.md`
- `docs/samples/scenarios/README.md`
- `plugins/year-end-harness/contracts/final-verification-contract.md`
- `plugins/year-end-harness/templates/final-verification.md`

## Outputs

- `.local/harness/<date>/<run-id>/agent-e-final-verification.md`

## Rules

- Agent D 루프가 3회 모두 끝나지 않으면 승인하지 않는다.
- 다인가족 더미 PDF 또는 scenario fixture를 사용해 최종 납부/환급 세액까지 확인한다.
- 미지원 공제 타입, 미확정 세법, 미해결 blocking defect가 남아 있으면 반려한다.
- 최종 판정은 `approved`, `approved-with-warning`, `rejected` 중 하나로 명시한다.
