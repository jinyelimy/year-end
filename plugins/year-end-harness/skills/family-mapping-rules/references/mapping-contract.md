# Mapping Contract Summary

상세 계약은 `plugins/year-end-harness/contracts/family-mapping-contract.md`를 따른다. 이 파일은 실무 요약본이다.

## Identification Order

1. 이름 + 생년월일 완전 일치
2. 주민번호 앞자리 또는 관계 문구 보조 확인
3. 중복 후보가 남으면 `needs_review`

## Minimum Output Fields

- `person`
- `page`
- `rawLine`
- `ownerPersonKey`
- `claimantDependentId`
- `mappingConfidence`
- `mappingReason`
- `claimability`
- `claimabilityReason`
- `evidenceRequirementCode`
- `evidenceStatus`
