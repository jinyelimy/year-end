# S04 Same Name Collision

## Source

- Synthetic scenario document for conflict testing

## Scenario Goal

- 동명이인 또는 식별자 충돌 시 자동 합산이 차단되는지 검증한다.

## Expected Focus

- 이름만 같은 경우 자동 매핑하지 않는다.
- 주민번호 앞자리나 관계 힌트가 부족하면 `needs_review`로 남긴다.
- 잘못된 자동 합산은 blocking defect로 취급한다.
