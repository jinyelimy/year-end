# Family Mapping Contract

## Purpose

가족 매핑 판단이 설계, 구현, 테스트에서 동일한 필드와 상태값을 쓰도록 고정한다.

## Required Fields

| Field | Description |
|---|---|
| `person` | PDF 원문에 표시된 이름 또는 라벨 |
| `page` | 원문 페이지 번호 |
| `rawLine` | 원시 추출 라인 |
| `ownerPersonKey` | 지출 소유자 식별자 |
| `claimantDependentId` | 공제 청구 기준 부양가족 ID, 없으면 null |
| `mappingConfidence` | `high`, `medium`, `low` |
| `mappingReason` | 판정 근거 |
| `claimability` | `taxpayer_claimable`, `owner_only`, `excluded`, `needs_review` |
| `claimabilityReason` | 합산 가능/불가 근거 |
| `evidenceRequirementCode` | 증빙 요구 코드 |
| `evidenceStatus` | `ready`, `missing`, `needs_review` |

## Decision Rules

1. 이름 + 생년월일 완전 일치 시 자동 매핑 후보로 본다.
2. 동명이인 또는 동일 생년월일 충돌 시 추가 식별자가 없으면 `needs_review`다.
3. 합산 가능 여부는 항목별 세법 규칙으로만 판정한다.
4. 자동 매핑 실패 시 계산 엔진에 강제 포함시키지 않는다.
