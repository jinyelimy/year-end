# Year-End Harness Plugin

이 디렉터리는 연말정산 프로젝트용 Codex 플러그인 하네스 본체다.

## Entry Points

- `AGENTS.md`: 저장소 작업 규칙과 수정 경계
- `docs/architecture/harness-engineering-design.md`: 전체 하네스 설계와 단계별 흐름
- `docs/analysis/project-analysis.md`: 현재 프로젝트 분석
- `plugins/year-end-harness/context/tax-year-context.json`: 날짜와 세법 컨텍스트
- `plugins/year-end-harness/law-packs/`: Git 관리 월별 세법팩
- `.agents/plugins/marketplace.json`: Codex 로컬 플러그인 노출 순서

## Included Components

- `agents/`: 5개 전문 역할 브리프
- `skills/year-end-a2a-orchestrator/`: 전체 오케스트레이션 스킬
- `skills/tax-law-pack/`: Agent A용 세법 팩 작성 스킬
- `skills/family-mapping-rules/`: Agent B/C용 가족 매핑 규칙 스킬
- `skills/repo-validation/`: 저장소 검증 스킬
- `skills/verification-loop/`: Agent D/E용 3-loop 검증 스킬
- `law-packs/`: 월별 공식 소스 스냅샷과 정규화 룰팩 저장소
- `contracts/`: 산출물 단일 계약 소스
- `templates/`: 에이전트별 산출물 템플릿
- `scripts/`: Windows 친화 검증 스크립트, phase gate, 아티팩트 검사기

## Routing

- 하네스 전체 흐름 설계/실행/감사는 `skills/year-end-a2a-orchestrator/`를 쓴다.
- 최신 세법 팩 작성과 정규화 룰팩 생성은 `skills/tax-law-pack/`를 쓴다.
- 가족 매핑 로직 설계와 구현은 `skills/family-mapping-rules/`를 쓴다.
- 코드 검증과 빌드는 `skills/repo-validation/`를 쓴다.
- 3회 loop와 최종 QA는 `skills/verification-loop/`를 쓴다.
- 메인 사용자 흐름은 `홈택스 간소화 PDF 업로드 -> 공제 후보 추출 -> 본인/부양가족 매핑 -> deduction_items 등록 -> 증빙자료 동기화 -> 계산 -> 결과확인`으로 본다.
- 직접 입력은 메인 경로가 아니라 간소화자료 누락 또는 보정용 fallback 경로로 본다.
- 세법 데이터 흐름은 `공식 소스 수집 -> 월별 세법팩 -> normalized-rule-pack.json -> review -> Git 정본 승격 -> PUBLISHED 게시 -> 계산`으로 본다.
- 구현 run은 가능한 한 아래 수직 슬라이스 하나만 선택한다.
  - `파싱/공제 항목 등록`
  - `부양가족 매칭`
  - `증빙자료`
  - `결과 계산`
  - `특정 공제 항목`
  - `세법팩/룰셋`

## Operating Principle

- 규칙 우선순위는 `AGENTS.md`가 가장 높다.
- 플러그인은 Codex 진입점을 제공하고, 실제 산출물은 `.local/harness/<date>/<run-id>/`에 남긴다.
- 같은 날짜에 재실행할 때도 새 `run-id`를 발급해 기존 run을 덮어쓰지 않는다.
- 현재 기본 제품 범위는 `환급/징수 계산 모듈`이며, 공식 제출서류 생성과 회사 제출 워크플로는 후속 확장 범위로 둔다.
- 공식 세법 값은 반드시 국세청, 홈택스, 국가법령정보센터, 법령해석 API, 기획재정부 자료로 검증한다.
- 공식 소스 원문과 계산용 정규화 데이터는 분리한다.
- Markdown 세법 팩은 사람이 읽는 근거 문서이고, `normalized-rule-pack.json`은 review/publish 후보가 된다.
- 월별 세법팩은 Git 관리 자산으로 남기고, 실행별 원문 스냅샷은 `.local/harness/`에 남긴다.
- 계산 엔진은 `PUBLISHED` 상태의 룰셋만 읽고, `READY_FOR_REVIEW` 상태의 후보 데이터는 직접 읽지 않는다.
- 자동 수집은 허용하지만 자동 publish 는 금지한다.
- backend 검증은 같은 `build/` 디렉터리를 공유하므로 병렬 실행하지 않는다.
- Windows 환경에서는 `gradlew.bat`, `npm.cmd`를 사용한다.
- 계약 검증은 `validate-artifacts.py`, phase 순서 검증은 `run-harness-gate.cmd`를 사용한다.
- Phase 0 audit는 실제 샘플 PDF 기준으로 섹션 인식, 사람 식별, 금액 추출, 공제 후보 등록 상태를 확인하는 것을 기본으로 한다.
- 특정 공제 항목을 구현 완료로 판단할 때는 `파싱`, `공제 항목 등록`, `부양가족 매칭`, `증빙자료`, `결과 계산` 중 필요한 구간이 모두 반영됐는지 함께 확인한다.
- 결과확인 화면과 계산 API는 추후 인사솔루션 연계를 위해 재사용 가능한 입력/출력 계약을 유지하는 것을 기본 원칙으로 한다.
