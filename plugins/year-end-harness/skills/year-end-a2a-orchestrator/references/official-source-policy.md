# Official Source Policy

## Default Date Context

- 기준일: 2026-04-06
- 기본 타깃: `2025 귀속 소득 / 2026 신고`

## Allowed Sources

- 국세청 연말정산 안내 자료
- 국가법령정보센터 `소득세법`, `소득세법 시행령`, `소득세법 시행규칙`
- 기획재정부 세법개정 보도자료 및 후속 자료

## Starting Links

- 국세청 `2025년 귀속 연말정산 종합 안내`
  - https://www.nts.go.kr/nts/cm/cntnts/cntntsView.do?cntntsId=238938&mi=2304
- 국가법령정보센터 `근로소득자 소득·세액공제신고서 작성방법`
  - https://law.go.kr/LSW/flDownload.do?flNm=%EC%86%8C%EB%93%9D%C2%B7%EC%84%B8%EC%95%A1+%EA%B3%B5%EC%A0%9C%EC%8B%A0%EA%B3%A0%EC%84%9C%2F%EA%B7%BC%EB%A1%9C%EC%86%8C%EB%93%9D%EC%9E%90+%EC%86%8C%EB%93%9D%C2%B7%EC%84%B8%EC%95%A1+%EA%B3%B5%EC%A0%9C%EC%8B%A0%EA%B3%A0%EC%84%9C&flSeq=113896131
- 저장소 내부 기준 문서
  - `docs/references/2025년 원천징수의무자를 위한 연말정산 신고안내.pdf`

## Working Rules

- 세율, 한도, 인적공제 요건, 가족 합산 규칙은 기억으로 확정하지 않는다.
- 자료마다 발행일과 효력일을 기록한다.
- 공식 자료 간 충돌이 있으면 더 최신 문서를 우선하고, 충돌 자체를 산출물에 남긴다.
- 계산 엔진 반영 전에는 `confirmed` 값만 코드에 사용한다.

## Minimum Items Agent A Must Verify

- 근로소득세 과세표준 구간과 세율
- 근로소득공제
- 기본공제와 추가공제
- 보험료, 의료비, 교육비, 기부금, 신용카드 등 항목별 한도와 공제율
- 가족별 합산 가능 조건
- 항목별 필수 증빙

## Family-Mapping Anchors To Re-Verify

- 기본공제 대상 부양가족은 연간 소득금액 기준을 충족해야 한다.
- 신용카드 사용액은 다른 거주자의 기본공제를 적용받지 않은 배우자와 생계를 같이하는 직계존비속의 사용액 포함 가능 여부를 다시 확인해야 한다.
- 보험료, 의료비, 교육비, 기부금은 항목별로 가족 합산 가능 조건이 다르므로 하나의 공통 규칙으로 묶지 않는다.
