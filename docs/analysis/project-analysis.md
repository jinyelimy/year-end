# Ligg-Tax 프로젝트 전체 분석

> 작성일: 2026-04-03  
> 브랜치: codex/next-migration

---

## 1. 프로젝트 개요

**Ligg-Tax**는 한국 연말정산 시뮬레이터 웹 애플리케이션입니다.  
사용자가 홈택스 PDF를 업로드하면 공제항목을 자동으로 파싱·분류하고, 세액 계산 시뮬레이션을 거쳐 예상 환급액을 확인할 수 있는 5단계 흐름으로 구성되어 있습니다.

| 항목 | 내용 |
|------|------|
| 백엔드 | Spring Boot 3.5 + Java 21 + PostgreSQL 16 + Redis 7 |
| 프론트엔드 | Next.js 16 + React 19 + Tailwind CSS |
| 인증 | JWT + Spring Security (소셜 로그인 구조 준비됨) |
| 배포 | Docker Compose (로컬 개발 환경) |

---

## 2. 전체 디렉토리 구조

```
year-end/
├── backend/                          # Spring Boot 백엔드
│   ├── src/main/java/com/example/yearend/
│   │   ├── YearendApplication.java
│   │   ├── admin/                    # 관리자 검토 도메인
│   │   ├── calculation/              # 세액 계산 / 시뮬레이션 도메인
│   │   ├── common/                   # 공통 (API응답, 예외, JPA설정, Money VO)
│   │   ├── deduction/                # 공제 항목 도메인 (핵심)
│   │   ├── document/                 # 증빙 서류 체크리스트 도메인
│   │   ├── security/                 # JWT 인증, Spring Security 설정
│   │   ├── taxsession/               # 세션/부양가족/소득 도메인
│   │   └── user/                     # 사용자/인증 도메인
│   └── src/test/java/               # 단위 테스트 10개
├── frontend/                         # Next.js 프론트엔드
│   ├── app/                          # 페이지 라우트 (10개)
│   ├── components/                   # 공유 컴포넌트 (3개)
│   └── lib/                          # 유틸리티/API 모듈 (4개)
├── docs/                             # 분석/설계/가이드/참고 자료
│   ├── analysis/                     # 현재 프로젝트 상태 분석
│   ├── architecture/                 # 핵심 설계 문서
│   ├── guides/                       # 구현/운영 가이드
│   ├── notes/                        # 날짜별 조사 및 메모
│   ├── references/                   # 외부 원문 자료
│   ├── samples/                      # 샘플 입력 파일
│   └── images/                       # 화면 스크린샷
├── scripts/                          # 하네스/동기화 스크립트
├── docker-compose.yml
├── AGENTS.md                         # AI 에이전트 작업 가이드
└── README.md
```

---

## 3. 백엔드 도메인 구조

### 3-1. 패키지별 역할

| 패키지 | 역할 | 주요 구성 |
|--------|------|-----------|
| `user` | 사용자 등록/로그인, 소셜 OAuth (카카오/네이버) | api 4, application 3, domain 3, infra 1 |
| `security` | JWT 발급/검증, Spring Security 필터 체인 | 6개 클래스 |
| `taxsession` | 연말정산 세션, 부양가족, 소득항목 CRUD | api 6, application 3, domain 7, infra 3 |
| `deduction` | 공제항목 CRUD, 홈택스 PDF 파싱, 공제 정책 엔진 | api 2, application 6, domain 10, infra 7 |
| `calculation` | 세액 계산, 시뮬레이션 실행/결과 저장 | api 2, application 1, domain 4, infra 1 |
| `document` | 증빙 서류 체크리스트 자동 생성/동기화 | api 2, application 1, domain 3, infra 1 |
| `admin` | 관리자 검토 (세션 목록, 체크리스트 검토) | api 2, application 1 |
| `common` | 공통 응답 래퍼, 예외 처리, JPA 설정, Money VO | 7개 클래스 |

### 3-2. 핵심 클래스 및 인터페이스

**도메인 엔티티:**
- `TaxSession` — 연말정산 세션 (user, taxYear, sessionStatus, basicInfoJsonb, filingType, ruleVersion)
- `Dependent` — 부양가족 (이름, 생년월일, 관계유형, 거주유형)
- `IncomeItem` — 소득항목 (급여/상여/기타, 과세/비과세/원천징수세액)
- `DeductionItem` — 공제항목 (deductionType, amount, evidenceStatus, attributesJsonb)
- `CalculationResult` — 계산 결과 (입력 해시 기반 버전 관리)
- `DocumentChecklist` — 증빙 서류 체크리스트 (필수/선택 구분)
- `DeductionRule` — 공제 규칙 엔티티 (테이블 존재, 계산 엔진 미연결)

**핵심 인터페이스/추상클래스:**
- `DeductionPolicy` — `supports()`, `evaluate(TaxContext, DeductionItem)` 정의
- `AbstractDeductionPolicy` — Template Method 패턴, EligibilityChecker 체인 순회 후 `calculateEligibleAmount` → `applyLimit` → `calculateTaxCredit` 호출
- `EligibilityChecker` — 공제 자격 요건 단위 검증 인터페이스
- `TaxCalculationService` — 세액 계산 서비스 인터페이스

**핵심 서비스:**
- `DeductionItemService` — 공제항목 CRUD + 홈택스 PDF import 오케스트레이션
- `HometaxPdfImportParser` — PDFBox 기반 PDF 텍스트 추출 및 섹션별 파싱 (OCR 대체 지원)
- `DeductionEngine` — 모든 공제항목을 DeductionPolicyRegistry에서 적절한 정책으로 평가
- `DeductionPolicyRegistry` — DeductionType → DeductionPolicy 매핑 관리
- `DeductionItemReviewPolicy` — 가져온 항목의 계산 포함/제외 결정
- `SimulationService` — 시뮬레이션 오케스트레이션 (컨텍스트 구성 → 공제 평가 → 세액 계산 → 결과 저장)
- `DefaultTaxCalculationService` — 과세표준 계산 + 누진세율 적용 + 환급액 산출

---

## 4. 공제 정책 구현 현황

### 4-1. DeductionType별 구현 상태

| 공제 타입 | 구현 상태 | Policy 클래스 | 비고 |
|-----------|----------|--------------|------|
| `MEDICAL_EXPENSE` | **구현 완료** | `MedicalExpenseDeductionPolicy` | 총급여 3% 초과분 공제, `MedicalExpenseThresholdChecker` 적용 |
| `EDUCATION_EXPENSE` | **구현 완료** | `EducationExpenseDeductionPolicy` | subType별 한도 (대학 900만, 유치원/학교/학원 300만, 본인 무제한) |
| `INSURANCE` | **구현 완료** | `InsurancePremiumDeductionPolicy` | 세액공제 방식, 연 100만 한도, 공제율 12%, 최대 12만 세액공제 |
| `DONATION` | **미구현** | 없음 | enum만 정의, 시뮬레이션 시 `UNSUPPORTED_DEDUCTION_TYPE` 예외 |
| `CREDIT_CARD` | **미구현** | 없음 | PDF 파싱은 완료, `calculationSupported=false`로 review-only |

### 4-2. 홈택스 PDF 파싱 섹션별 현황

| 섹션 | 파싱 지원 | 계산 지원 |
|------|----------|----------|
| 의료비 | 인별합계금액 추출 | 지원 |
| 보장성보험 | 인별합계금액 추출 | 지원 |
| 교육비 | 합계금액 복수 행 합산, subType 자동 분류 | 지원 |
| 신용카드/직불카드/현금영수증/제로페이 | 카테고리별 합계 추출 | **미지원** |
| OCR 대체 | Tesseract 연동 구조 구현 | - |

### 4-3. 세액 계산 현황

`DefaultTaxCalculationService`는 **2구간만 구현**된 단순화 상태입니다.

| 현황 | 내용 |
|------|------|
| 구현됨 | 1400만 이하 6%, 초과 15% (2구간) |
| 미구현 | 실제 소득세법 8단계 누진세율 (5천만/8800만/1.5억/3억/5억/10억 구간) |
| 미구현 | 근로소득공제 (총급여 → 근로소득금액) |
| 미구현 | 인적공제 (기본공제 150만, 추가공제) |
| 미구현 | 국민연금/건강보험료 공제 |

---

## 5. API 엔드포인트 (총 28개)

### 인증/사용자
```
POST   /api/v1/auth/signup
POST   /api/v1/auth/login
GET    /api/v1/auth/oauth/{provider}/authorize-url
POST   /api/v1/auth/oauth/{provider}/exchange
GET    /api/v1/users/me
PATCH  /api/v1/users/me
```

### 세션
```
POST   /api/v1/tax-sessions
GET    /api/v1/tax-sessions
GET    /api/v1/tax-sessions/{sessionId}
PATCH  /api/v1/tax-sessions/{sessionId}/basic-info
POST   /api/v1/tax-sessions/{sessionId}/submit
```

### 부양가족 / 소득 / 공제
```
POST/GET/PUT/DELETE  /api/v1/tax-sessions/{sessionId}/dependents[/{dependentId}]
POST/GET/PUT/DELETE  /api/v1/tax-sessions/{sessionId}/income-items[/{incomeItemId}]
POST/GET/PUT/DELETE  /api/v1/tax-sessions/{sessionId}/deduction-items[/{deductionItemId}]
POST   /api/v1/tax-sessions/{sessionId}/deduction-items/imports/hometax
```

### 시뮬레이션 / 증빙 / 관리자
```
POST   /api/v1/tax-sessions/{sessionId}/simulation
GET    /api/v1/tax-sessions/{sessionId}/results/latest
GET    /api/v1/tax-sessions/{sessionId}/results/latest/rejections
GET    /api/v1/tax-sessions/{sessionId}/document-checklists
GET    /api/v1/admin/tax-sessions
GET    /api/v1/admin/tax-sessions/{sessionId}/checklists
POST   /api/v1/admin/checklists/{checklistId}/review
```

---

## 6. 프론트엔드 구조

### 6-1. 5단계 흐름

`basicInfoJsonb` 안의 불리언 플래그(`dependentsConfirmed`, `incomeConfirmed`, `deductionsConfirmed`)로 단계 완료를 추적합니다.

| 단계 | 경로 | 주요 동작 |
|------|------|----------|
| 1단계 | `/basic-info` → `/dependents` | 기본정보 입력, 부양가족 CRUD, `dependentsConfirmed=true` |
| 2단계 | `/income` | 소득항목 CRUD, `incomeConfirmed=true` |
| 3단계 | `/import-data` → `/deductions` | 홈택스 PDF 업로드, 자동반영/확인필요 분류, 공제항목 수정, `deductionsConfirmed=true` |
| 4단계 | `/evidence-docs` | 증빙 서류 체크리스트 확인 |
| 5단계 | `/results` → `/submit-status` | 계산 결과 확인, 최종 제출 |

대시보드(`/`)는 전체 진행률을 20% 단위(5단계)로 표시합니다.

### 6-2. 주요 lib 모듈

| 모듈 | 역할 |
|------|------|
| `yearEndApi.js` | 28개 API 래퍼, localStorage JWT 관리, `ensureCurrentSession`, 진행률/단계 판정 |
| `yearEndView.js` | 통화/날짜 포맷, 타입 라벨 매핑, `calculateFinancialSummary` |
| `deductionImport.js` | import bucket 분류, 신뢰도/리뷰 메타, 계산 포함 여부, 추천 힌트 생성 |

---

## 7. 테스트 현황

총 **10개 테스트 파일** (전부 백엔드, 프론트엔드 테스트 없음)

| 테스트 파일 | 대상 | 유형 |
|------------|------|------|
| `MedicalExpenseDeductionPolicyTest` | 의료비 공제 정책 | 단위 |
| `EducationExpenseDeductionPolicyTest` | 교육비 공제 정책 | 단위 |
| `InsurancePremiumDeductionPolicyTest` | 보험료 공제 정책 | 단위 |
| `DefaultTaxCalculationServiceTest` | 세액 계산 서비스 | 단위 |
| `SimulationServiceTest` | 시뮬레이션 서비스 | 단위 (Mockito) |
| `DeductionItemReviewPolicyTest` | 가져오기 항목 리뷰 정책 | 단위 |
| `HometaxPdfImportParserTest` | PDF 파싱 로직 | 단위 |
| `HometaxPdfImportParserRealFileProbeTest` | 실제 PDF 파일 파싱 | 프로브 |
| `HometaxParsingDtosTest` | 파싱 DTO 불변성/직렬화 | 단위 |
| `DocumentChecklistServiceTest` | 체크리스트 동기화 | 단위 |

**테스트 공백:**
- Controller 계층 통합 테스트 없음
- Spring Security 테스트 없음
- Service 계층 대부분 미테스트 (SimulationService만 Mockito 존재)
- 프론트엔드 테스트 전무 (Jest/Testing Library 미설정)

---

## 8. 기능 구현 현황 요약

### 구현 완료

| 기능 | 상세 |
|------|------|
| 인증/인가 | JWT 기반 회원가입/로그인, Spring Security 필터 체인 |
| 세션 관리 | CRUD, basicInfo(JSONB) 저장, 상태 관리 (DRAFT/CALCULATED/SUBMITTED/REVIEWED/REJECTED) |
| 부양가족 | CRUD (이름/생년월일/관계유형/거주유형) |
| 소득 입력 | 소득항목 CRUD (급여/상여/기타, 과세/비과세/원천징수세액) |
| 공제항목 CRUD | 직접 입력/수정/삭제 |
| 홈택스 PDF 파싱 | PDFBox 텍스트 추출, 4개 섹션 파싱, OCR 대체 경로, 부양가족 자동 매칭 |
| 가져오기 리뷰 | AUTO_APPLIED/NEEDS_REVIEW 분류, 신뢰도 레벨, 승인/제외 UI |
| 의료비 공제 | 총급여 3% 초과분 소득공제 |
| 교육비 공제 | subType별 한도 적용, 부양가족 소득 요건 + 교육기관 요건 |
| 보험료 공제 | 세액공제 방식, 연 100만 한도, 12% 공제율 |
| 세액 계산 | 소득공제 반영 → 과세표준 → 산출세액 → 세액공제 → 결정세액 → 환급액 |
| 시뮬레이션 | 계산 실행/결과 저장/버전 관리/입력 해시, 미적용 사유 조회 |
| 증빙 체크리스트 | 공제항목 기반 자동 생성/동기화, 필수/선택 구분 |
| 관리자 검토 | 세션 목록 조회, 체크리스트 검토 처리 |
| 5단계 UI 흐름 | 대시보드 진행률, 단계 잠금/해제, 확정/확정해제, 제출 |

### 미구현 / 부분 구현

| 기능 | 우선순위 | 상태 |
|------|---------|------|
| **기부금 공제 정책** | 높음 | enum만 존재, Policy 클래스 없음, 시뮬레이션 오류 발생 |
| **신용카드 공제 정책** | 높음 | PDF 파싱 완료, 계산 Policy 없음 (`calculationSupported=false`) |
| **실제 소득세법 누진세율 (8단계)** | 높음 | 2구간으로 단순화됨 |
| **근로소득공제** | 높음 | 총급여 → 근로소득금액 계산 없음 |
| **인적공제** | 높음 | 기본공제 150만, 추가공제(경로우대/장애인/한부모) 미구현 |
| 국민연금/건강보험료 공제 | 중간 | 사회보험료 공제 체계 없음 |
| 주택관련 공제 | 중간 | 주택자금/월세/주택마련저축 등 미구현 |
| 연금저축/IRP 세액공제 | 중간 | 연금계좌 세액공제 미구현 |
| 표준공제 | 중간 | 항목별 공제 합계 기준 미달 시 표준공제 적용 없음 |
| deduction_rules 테이블 연동 | 낮음 | 엔티티 존재, 계산 엔진 미연결 (하드코딩 정책만 사용) |
| 토큰 자동 갱신 | 낮음 | refreshToken 저장만, 자동 갱신 로직 없음 |
| 소셜 로그인 실제 연동 | 낮음 | 구조만 구현, 카카오/네이버 클라이언트 ID 미설정 |
| Controller 통합 테스트 | 낮음 | MockMvc 테스트 전무 |
| 프론트엔드 테스트 | 낮음 | Jest/Testing Library 미설정 |

---

## 9. 다음 개발 우선순위 권고

1. **세액 계산 엔진 정상화** — 근로소득공제 + 인적공제 + 8단계 누진세율 구현 (핵심 기능 정확도에 직결)
2. **신용카드 공제 정책** — PDF 파싱이 이미 완료되어 Policy 클래스만 추가하면 됨
3. **기부금 공제 정책** — enum이 이미 등록되어 시뮬레이션 시 오류 방지 필요
4. **사회보험료 공제** — 국민연금/건강보험료 (거의 모든 사용자에게 해당)
5. **통합 테스트 추가** — Controller 계층 MockMvc, SimulationService E2E
