---
name: verification-loop
description: 3회 SDET 루프와 최종 QA 검증을 운영할 때 사용한다. 가족 구성 시나리오를 늘려가며 결함 보고, 수정 요청, 재검증을 반복하고 마지막 승인 여부를 결정하면서 scenario fixture와 loop contract를 만족시켜야 하면 반드시 사용한다.
---

# Verification Loop

이 스킬은 Agent D와 Agent E가 테스트 루프와 최종 검증을 운영할 때 쓴다.

## Start

1. `AGENTS.md`, `docs/architecture/harness-engineering-design.md`, Agent B 설계안, Agent C 구현 결과를 읽는다.
2. `docs/samples/scenarios/README.md`와 필요한 scenario fixture를 먼저 고른다.
3. `plugins/year-end-harness/contracts/loop-report-contract.md`, `plugins/year-end-harness/contracts/final-verification-contract.md`, 해당 템플릿을 함께 연다.
4. [`references/loop-contract.md`](./references/loop-contract.md)는 보조 요약으로만 쓴다.

## Loop Rules

- Loop 1: 본인 단독, 기본 공제, 단일 지출 항목
- Loop 2: 맞벌이 부부, 자녀, 부모 포함 가족 시나리오
- Loop 3: 경계값, 동일 이름 충돌, 증빙 누락, 미지원 공제 타입

## Output

- `.local/harness/<date>/loop-1-sdet-report.md`
- `.local/harness/<date>/loop-2-sdet-report.md`
- `.local/harness/<date>/loop-3-sdet-report.md`
- `.local/harness/<date>/agent-e-final-verification.md`

## Rules

- 각 루프는 결함이 없더라도 반드시 보고서를 남긴다.
- 결함은 수정 요청과 재검증 결과까지 한 세트로 닫는다.
- 미해결 blocking defect가 있으면 최종 승인을 내리지 않는다.
