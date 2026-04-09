# 연말정산 모듈 프로젝트

Spring Boot 백엔드와 Next.js 프론트엔드로 구성된 연말정산 시뮬레이터입니다.  
사용자는 연말정산 세션을 기준으로 기본 정보, 부양가족, 소득, 간소화 자료, 공제항목, 증빙 서류, 계산 결과를 순서대로 확인하고 입력할 수 있습니다.

## 주요 화면

### 링텍스 대시보드
![링텍스 대시보드](docs/images/dashboard-latest.png)

### 기본 정보 및 부양가족 입력
![기본 정보 및 부양가족 입력](docs/images/basic-info-dependents-latest.png)

### 소득 입력
![소득 입력](docs/images/income-latest.png)

### 간소화 자료 가져오기
![간소화 자료 가져오기](docs/images/import-data-latest.png)

### 공제항목 입력
![공제항목 입력](docs/images/deductions-latest.png)

## 프로젝트 개요

- 목표: 연말정산 입력, 공제 검토, 증빙 확인, 계산 결과 확인까지 한 흐름으로 연결
- 세션 중심 구조: 모든 입력 데이터는 `tax_session` 기준으로 관리
- 현재 구현 초점: 실제 사용 가능한 UI 흐름과 백엔드 CRUD/API 연결
- 3단계 구조: 간소화 자료 가져오기와 공제항목 입력을 묶어서 검토 및 확정 흐름 제공

## 현재 구현 범위

- 인증
  - 로그인 / 회원가입
  - JWT 기반 인증
- 세션 흐름
  - 연말정산 세션 조회
  - 대시보드 단계별 진행 상태 표시
- 기본 정보 · 부양가족
  - 기본 정보 입력
  - 부양가족 등록 / 조회 / 수정 흐름
- 소득 입력
  - 소득 항목 CRUD
  - 금액 검증
  - 소득 합계 반영
- 간소화 자료 · 공제항목
  - 홈택스 PDF 업로드 UI
  - 공제항목 조회 / 수정 / 삭제 / 직접 입력
  - 자동 반영 / 확인 필요 구분
  - 문서 체크리스트 동기화
- 계산 / 제출
  - 공제 계산 결과 확인
  - 제출 상태 확인 화면

## 간소화 자료 가져오기 현재 상태

- `POST /api/v1/tax-sessions/{sessionId}/deduction-items/imports/hometax` 업로드 API가 구현되어 있습니다.
- 현재는 PDF 업로드를 트리거로 샘플 공제항목을 생성하는 구조입니다.
- 즉 실제 PDF 텍스트 추출 / OCR / 표 파싱은 아직 붙어 있지 않습니다.
- 다만 아래 구조는 이미 준비되어 있습니다.
  - `deduction_items` 저장
  - `attributesJsonb` 메타데이터 저장
  - `document_checklists` 동기화
  - 자동 반영 / 확인 필요 UI 분기

## 저장 구조 메모

### `deduction_items`

공제항목 본체를 저장합니다.

- `deduction_type`
- `amount`
- `used_at`
- `source_name`
- `evidence_status`
- `attributesJsonb`

### `attributesJsonb`

가져오기 메타데이터를 JSONB로 저장합니다.

예시:

```json
{
  "sourceType": "HOMETAX",
  "sourceLabel": "홈택스 PDF",
  "entryChannel": "IMPORT_SYNC",
  "importBatchId": "550e8400-e29b-41d4-a716-446655440000",
  "importFileName": "고길동(750101)-2025년도자료.pdf",
  "importedAt": "2026-03-27T10:32:15+09:00",
  "importBucket": "AUTO_APPLIED",
  "reviewStatus": "APPROVED",
  "confidenceLevel": "HIGH",
  "reviewReason": "표준 의료비 항목으로 바로 매핑됨"
}
```

### `deduction_rules`

공제율, 한도, 조건 같은 세법 규칙을 담기 위한 테이블 의도로 설계되어 있습니다.

- 현재 코드 기준으로는 정책 클래스 하드코딩 계산이 먼저 사용됩니다.
- 즉 테이블은 존재하지만, 계산 엔진이 아직 직접 읽어 쓰는 단계까지는 연결되지 않았습니다.

## 기술 스택

### Frontend

- Next.js 16
- React 19
- Tailwind CSS

### Backend

- Java 21
- Spring Boot 3.5
- Spring Web
- Spring Security
- Spring Data JPA
- Spring Validation
- Springdoc OpenAPI

### Infra

- PostgreSQL
- Redis
- Docker Compose

## 디렉터리 구조

```text
year-end/
├─ backend/
│  └─ src/main/java/com/example/yearend
├─ frontend/
│  ├─ app/
│  └─ lib/
├─ docs/
│  ├─ analysis/
│  ├─ architecture/
│  ├─ guides/
│  ├─ notes/
│  ├─ references/
│  ├─ samples/
│  └─ images/
├─ scripts/
├─ AGENTS.md
├─ docker-compose.yml
└─ README.md
```

## 주요 화면 경로

- `/`
- `/dependents`
- `/income`
- `/import-data`
- `/deductions`
- `/evidence-docs`
- `/results`
- `/submit-status`

## 주요 API 예시

- `POST /api/v1/auth/signup`
- `POST /api/v1/auth/login`
- `GET /api/v1/users/me`
- `POST /api/v1/tax-sessions`
- `GET /api/v1/tax-sessions/{sessionId}/income-items`
- `POST /api/v1/tax-sessions/{sessionId}/income-items`
- `GET /api/v1/tax-sessions/{sessionId}/deduction-items`
- `POST /api/v1/tax-sessions/{sessionId}/deduction-items/imports/hometax`
- `GET /api/v1/tax-sessions/{sessionId}/document-checklists`

## 로컬 실행 방법

### 1. 인프라 실행

```bash
docker compose up -d
```

### 2. 백엔드 실행

```bash
cd backend
./gradlew bootRun
```

기본 주소:

- App: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui.html`

### 3. 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev
```

또는 배포 모드:

```bash
cd frontend
npm install
npm run build
npm run start
```

## 테스트 계정

- email: `yelingg@example.com`
- password: `08210821`

## 문서

- `docs/analysis/project-analysis.md`
- `docs/architecture/harness-engineering-design.md`
- `docs/architecture/year-end-simulator-blueprint.md`
- `docs/architecture/deduction-rule-engine-design.md`
- `docs/architecture/postgresql-erd-draft.md`
- `docs/architecture/api-spec-draft.md`

## 다음 작업 방향

- 실제 홈택스 PDF 파싱 도입
- 공제 규칙 테이블과 계산 엔진 연결
- 공제 대분류 / 세부항목 구조 확장
- 증빙 검토 흐름 고도화
