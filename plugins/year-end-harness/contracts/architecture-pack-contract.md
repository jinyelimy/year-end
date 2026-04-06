# Architecture Pack Contract

## Purpose

Agent B 아키텍처 팩의 필수 구조와 가족 매핑/계산 흐름의 최소 계약을 고정한다.

## Required Sections

- `## Context`
- `## Inputs`
- `## Decisions`
- `## Open Questions`
- `## Files`
- `## Validation`

## Required Subsections Under Decisions

- `### Entity and Field Proposal`
- `### Family Mapping Logic Tree`
- `### Claimability Flow`
- `### Evidence Verification Flow`
- `### API and DTO Impact`
- `### Implementation Priority`

## Required Entity Fields

- `ownerPersonKey`
- `claimantDependentId`
- `mappingConfidence`
- `mappingReason`
- `claimability`
- `claimabilityReason`
- `evidenceRequirementCode`
- `evidenceStatus`

## Output File

- `.local/harness/<date>/agent-b-architecture-pack.md`
