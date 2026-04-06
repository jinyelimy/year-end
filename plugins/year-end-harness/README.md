# Year-End Harness Plugin

이 디렉터리는 연말정산 프로젝트용 Codex 플러그인 하네스 본체다.

## Entry Points

- `AGENTS.md`: 저장소 작업 규칙과 수정 경계
- `docs/architecture/harness-engineering-design.md`: 전체 하네스 설계와 단계별 흐름
- `docs/analysis/project-analysis.md`: 현재 프로젝트 분석
- `plugins/year-end-harness/context/tax-year-context.json`: 날짜와 세법 컨텍스트
- `.agents/plugins/marketplace.json`: Codex 로컬 플러그인 노출 순서

## Included Components

- `agents/`: 5개 전문 역할 브리프
- `skills/year-end-a2a-orchestrator/`: 전체 오케스트레이션 스킬
- `skills/tax-law-pack/`: Agent A용 세법 팩 작성 스킬
- `skills/family-mapping-rules/`: Agent B/C용 가족 매핑 규칙 스킬
- `skills/repo-validation/`: 저장소 검증 스킬
- `skills/verification-loop/`: Agent D/E용 3-loop 검증 스킬
- `contracts/`: 산출물 단일 계약 소스
- `templates/`: 에이전트별 산출물 템플릿
- `scripts/`: Windows 친화 검증 스크립트와 아티팩트 검사기

## Routing

- 하네스 전체 흐름 설계/실행/감사는 `skills/year-end-a2a-orchestrator/`를 쓴다.
- 최신 세법 팩 작성은 `skills/tax-law-pack/`를 쓴다.
- 가족 매핑 로직 설계와 구현은 `skills/family-mapping-rules/`를 쓴다.
- 코드 검증과 빌드는 `skills/repo-validation/`를 쓴다.
- 3회 loop와 최종 QA는 `skills/verification-loop/`를 쓴다.

## Operating Principle

- 규칙 우선순위는 `AGENTS.md`가 가장 높다.
- 플러그인은 Codex 진입점을 제공하고, 실제 산출물은 `.local/harness/<date>/`에 남긴다.
- 공식 세법 값은 반드시 국세청, 국가법령정보센터, 기획재정부 자료로 검증한다.
- backend 검증은 같은 `build/` 디렉터리를 공유하므로 병렬 실행하지 않는다.
- Windows 환경에서는 `gradlew.bat`, `npm.cmd`를 사용한다.
