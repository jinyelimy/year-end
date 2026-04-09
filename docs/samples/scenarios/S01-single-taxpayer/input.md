# S01 Single Taxpayer

## Source

- Raw PDF: `docs/samples/raw/고길동(750101)-2025년도자료.pdf`

## Scenario Goal

- 단독 납세자 기준으로 파서, 자동 매핑, 기본 공제 판단 흐름을 검증한다.

## Expected Focus

- 납세자 본인 명의 지출은 `high` 신뢰도로 자동 매핑된다.
- 계산 지원 공제는 simulation candidate가 된다.
- 미지원 공제 타입은 `review-only` 또는 `excluded` 상태를 유지한다.
