# 연말정산 공제 시뮬레이터 핵심 API 명세 초안

## 1. 문서 목적
이 문서는 Spring Boot + Swagger/OpenAPI 기준으로 바로 옮길 수 있는 수준의 핵심 API 초안이다.  
도메인 기준은 다음과 같다.

- 사용자 단위 인증
- 연말정산은 `tax_session` 단위로 입력/계산
- 부양가족, 소득, 지출은 모두 세션 하위 리소스
- 계산 결과는 세션 기준 조회
- 관리자 API는 별도 `/api/v1/admin` 하위로 분리

## 2. 공통 규칙

### 2-1. Base URL
`/api/v1`

### 2-2. 인증 방식
- 인증 필요 API는 `Authorization: Bearer {accessToken}` 사용
- Spring Security + JWT Access Token 기준

### 2-3. 권한 구분
- `USER`: 본인 세션만 조회/수정 가능
- `ADMIN`: 관리자 검토 API 접근 가능

### 2-4. 공통 응답 구조
1차 버전에서는 아래 구조를 권장한다.

```json
{
  "success": true,
  "data": {},
  "error": null,
  "timestamp": "2026-03-17T15:00:00+09:00"
}
```

실패 시 예시:

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "DEPENDENT_NOT_FOUND",
    "message": "부양가족 정보를 찾을 수 없습니다.",
    "fieldErrors": [
      {
        "field": "dependentId",
        "reason": "존재하지 않는 ID입니다."
      }
    ]
  },
  "timestamp": "2026-03-17T15:00:00+09:00"
}
```

### 2-5. 공통 상태 코드
- `200 OK`: 조회/수정 성공
- `201 Created`: 생성 성공
- `204 No Content`: 삭제 성공
- `400 Bad Request`: 요청값 검증 실패
- `401 Unauthorized`: 인증 실패
- `403 Forbidden`: 권한 없음
- `404 Not Found`: 리소스 없음
- `409 Conflict`: 중복 생성/상태 충돌

### 2-6. 세션 관련 전제
부양가족/소득/지출 API는 `taxSessionId`가 필요하므로, 실제 구현에서는 아래 세션 API도 함께 제공하는 것을 권장한다.

- `POST /api/v1/tax-sessions`
- `GET /api/v1/tax-sessions/{taxSessionId}`

이 문서의 핵심 범위는 요청하신 항목 위주로 작성하되, 하위 리소스 구조가 성립하도록 `taxSessionId`를 URL에 포함한다.

---

## 3. 인증 / 사용자 API

## 3-1. 회원가입

- 기능명: 회원가입
- HTTP Method: `POST`
- URL: `/api/v1/auth/signup`
- 인증 필요 여부: 아니오
- 권한: 공개

### Request JSON 예시

```json
{
  "email": "user@example.com",
  "password": "Password123!",
  "name": "홍길동"
}
```

### Response JSON 예시

```json
{
  "success": true,
  "data": {
    "userId": "1f8d2f50-7f59-4c47-b2d5-4f5b5d3d8a11",
    "email": "user@example.com",
    "name": "홍길동",
    "role": "USER",
    "createdAt": "2026-03-17T15:10:00+09:00"
  },
  "error": null,
  "timestamp": "2026-03-17T15:10:00+09:00"
}
```

### validation 포인트
- `email`: 필수, 이메일 형식, 최대 255자
- `password`: 필수, 8~20자, 영문/숫자/특수문자 조합 권장
- `name`: 필수, 공백 제외 1~100자
- 동일 이메일 중복 가입 금지

## 3-2. 로그인

- 기능명: 로그인
- HTTP Method: `POST`
- URL: `/api/v1/auth/login`
- 인증 필요 여부: 아니오
- 권한: 공개

### Request JSON 예시

```json
{
  "email": "user@example.com",
  "password": "Password123!"
}
```

### Response JSON 예시

```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "userId": "1f8d2f50-7f59-4c47-b2d5-4f5b5d3d8a11",
      "email": "user@example.com",
      "name": "홍길동",
      "role": "USER"
    }
  },
  "error": null,
  "timestamp": "2026-03-17T15:12:00+09:00"
}
```

### validation 포인트
- `email`: 필수
- `password`: 필수
- 탈퇴/잠금 상태 계정 로그인 차단

## 3-3. 내 정보 조회

- 기능명: 내 정보 조회
- HTTP Method: `GET`
- URL: `/api/v1/users/me`
- 인증 필요 여부: 예
- 권한: `USER`, `ADMIN`

### Request JSON 예시
- 없음

### Response JSON 예시

```json
{
  "success": true,
  "data": {
    "userId": "1f8d2f50-7f59-4c47-b2d5-4f5b5d3d8a11",
    "email": "user@example.com",
    "name": "홍길동",
    "role": "USER",
    "status": "ACTIVE",
    "createdAt": "2026-03-10T10:00:00+09:00",
    "updatedAt": "2026-03-17T15:12:00+09:00"
  },
  "error": null,
  "timestamp": "2026-03-17T15:13:00+09:00"
}
```

### validation 포인트
- JWT 유효성 검증
- 인증 사용자 식별 가능해야 함

## 3-4. 내 정보 수정

- 기능명: 내 정보 수정
- HTTP Method: `PATCH`
- URL: `/api/v1/users/me`
- 인증 필요 여부: 예
- 권한: `USER`, `ADMIN`

### Request JSON 예시

```json
{
  "name": "홍길동2",
  "password": "NewPassword123!"
}
```

### Response JSON 예시

```json
{
  "success": true,
  "data": {
    "userId": "1f8d2f50-7f59-4c47-b2d5-4f5b5d3d8a11",
    "email": "user@example.com",
    "name": "홍길동2",
    "updatedAt": "2026-03-17T15:15:00+09:00"
  },
  "error": null,
  "timestamp": "2026-03-17T15:15:00+09:00"
}
```

### validation 포인트
- `name`: 선택, 1~100자
- `password`: 선택, 8~20자, 정책 만족 여부
- 최소 하나 이상의 수정 필드 필요

---

## 4. 부양가족 API

## 4-1. 부양가족 등록

- 기능명: 부양가족 등록
- HTTP Method: `POST`
- URL: `/api/v1/tax-sessions/{taxSessionId}/dependents`
- 인증 필요 여부: 예
- 권한: `USER` 본인 세션만 가능, `ADMIN`은 조회 중심 권장

### Request JSON 예시

```json
{
  "name": "홍길순",
  "relationType": "CHILD",
  "birthDate": "2015-05-10",
  "annualIncomeAmount": 0,
  "residentType": "RESIDENT",
  "livesTogether": true,
  "isDisabled": false
}
```

### Response JSON 예시

```json
{
  "success": true,
  "data": {
    "dependentId": "1a3cb301-cd23-4f16-a210-3125ce8f710a",
    "taxSessionId": "a3d3f23e-ec8b-4d2a-a4d1-0c5f5d9b2e77",
    "name": "홍길순",
    "relationType": "CHILD",
    "birthDate": "2015-05-10",
    "annualIncomeAmount": 0,
    "isBasicDeductionTarget": true,
    "createdAt": "2026-03-17T15:20:00+09:00"
  },
  "error": null,
  "timestamp": "2026-03-17T15:20:00+09:00"
}
```

### validation 포인트
- `taxSessionId`: 존재 여부, 본인 세션 여부
- `name`: 필수, 1~100자
- `relationType`: enum 검증
- `birthDate`: 필수, 미래 날짜 불가
- `annualIncomeAmount`: 0 이상

## 4-2. 부양가족 목록 조회

- 기능명: 부양가족 목록 조회
- HTTP Method: `GET`
- URL: `/api/v1/tax-sessions/{taxSessionId}/dependents`
- 인증 필요 여부: 예
- 권한: `USER` 본인 세션, `ADMIN` 가능

### Request JSON 예시
- 없음

### Response JSON 예시

```json
{
  "success": true,
  "data": [
    {
      "dependentId": "1a3cb301-cd23-4f16-a210-3125ce8f710a",
      "name": "홍길순",
      "relationType": "CHILD",
      "birthDate": "2015-05-10",
      "annualIncomeAmount": 0,
      "isBasicDeductionTarget": true
    }
  ],
  "error": null,
  "timestamp": "2026-03-17T15:21:00+09:00"
}
```

### validation 포인트
- 세션 소유권 검증

## 4-3. 부양가족 단건 조회

- 기능명: 부양가족 단건 조회
- HTTP Method: `GET`
- URL: `/api/v1/tax-sessions/{taxSessionId}/dependents/{dependentId}`
- 인증 필요 여부: 예
- 권한: `USER` 본인 세션, `ADMIN` 가능

### Request JSON 예시
- 없음

### Response JSON 예시

```json
{
  "success": true,
  "data": {
    "dependentId": "1a3cb301-cd23-4f16-a210-3125ce8f710a",
    "name": "홍길순",
    "relationType": "CHILD",
    "birthDate": "2015-05-10",
    "annualIncomeAmount": 0,
    "residentType": "RESIDENT",
    "livesTogether": true,
    "isDisabled": false,
    "isBasicDeductionTarget": true
  },
  "error": null,
  "timestamp": "2026-03-17T15:22:00+09:00"
}
```

### validation 포인트
- 세션과 `dependentId`의 소속 일치 여부 확인

## 4-4. 부양가족 수정

- 기능명: 부양가족 수정
- HTTP Method: `PATCH`
- URL: `/api/v1/tax-sessions/{taxSessionId}/dependents/{dependentId}`
- 인증 필요 여부: 예
- 권한: `USER` 본인 세션만 가능

### Request JSON 예시

```json
{
  "annualIncomeAmount": 1000000,
  "livesTogether": false,
  "isDisabled": false
}
```

### Response JSON 예시

```json
{
  "success": true,
  "data": {
    "dependentId": "1a3cb301-cd23-4f16-a210-3125ce8f710a",
    "annualIncomeAmount": 1000000,
    "livesTogether": false,
    "updatedAt": "2026-03-17T15:23:00+09:00"
  },
  "error": null,
  "timestamp": "2026-03-17T15:23:00+09:00"
}
```

### validation 포인트
- 수정 가능한 필드만 허용
- `annualIncomeAmount`: 0 이상

## 4-5. 부양가족 삭제

- 기능명: 부양가족 삭제
- HTTP Method: `DELETE`
- URL: `/api/v1/tax-sessions/{taxSessionId}/dependents/{dependentId}`
- 인증 필요 여부: 예
- 권한: `USER` 본인 세션만 가능

### Request JSON 예시
- 없음

### Response JSON 예시

```json
{
  "success": true,
  "data": {
    "dependentId": "1a3cb301-cd23-4f16-a210-3125ce8f710a",
    "deleted": true
  },
  "error": null,
  "timestamp": "2026-03-17T15:24:00+09:00"
}
```

### validation 포인트
- 이미 계산된 결과가 있으면 재계산 필요 상태로 변경
- soft delete 적용 시 하위 공제 항목 영향 검토 필요

---

## 5. 소득 항목 API

## 5-1. 소득 항목 등록

- 기능명: 소득 항목 등록
- HTTP Method: `POST`
- URL: `/api/v1/tax-sessions/{taxSessionId}/income-items`
- 인증 필요 여부: 예
- 권한: `USER` 본인 세션만 가능

### Request JSON 예시

```json
{
  "incomeType": "SALARY",
  "payerName": "이수시스템",
  "grossAmount": 58000000,
  "taxableAmount": 56000000,
  "withheldTaxAmount": 2600000,
  "nonTaxableAmount": 2000000
}
```

### Response JSON 예시

```json
{
  "success": true,
  "data": {
    "incomeItemId": "fd75c2e4-7d62-43b6-a4ef-5472c55d0ad8",
    "taxSessionId": "a3d3f23e-ec8b-4d2a-a4d1-0c5f5d9b2e77",
    "incomeType": "SALARY",
    "grossAmount": 58000000,
    "taxableAmount": 56000000,
    "withheldTaxAmount": 2600000
  },
  "error": null,
  "timestamp": "2026-03-17T15:30:00+09:00"
}
```

### validation 포인트
- `incomeType`: enum 검증
- 금액 필드: 0 이상
- `taxableAmount <= grossAmount`
- `nonTaxableAmount <= grossAmount`

## 5-2. 소득 항목 목록 조회

- 기능명: 소득 항목 목록 조회
- HTTP Method: `GET`
- URL: `/api/v1/tax-sessions/{taxSessionId}/income-items`
- 인증 필요 여부: 예
- 권한: `USER` 본인 세션, `ADMIN` 가능

### Request JSON 예시
- 없음

### Response JSON 예시

```json
{
  "success": true,
  "data": [
    {
      "incomeItemId": "fd75c2e4-7d62-43b6-a4ef-5472c55d0ad8",
      "incomeType": "SALARY",
      "payerName": "이수시스템",
      "grossAmount": 58000000,
      "taxableAmount": 56000000,
      "withheldTaxAmount": 2600000
    }
  ],
  "error": null,
  "timestamp": "2026-03-17T15:31:00+09:00"
}
```

### validation 포인트
- 세션 소유권 검증

## 5-3. 소득 항목 단건 조회

- 기능명: 소득 항목 단건 조회
- HTTP Method: `GET`
- URL: `/api/v1/tax-sessions/{taxSessionId}/income-items/{incomeItemId}`
- 인증 필요 여부: 예
- 권한: `USER` 본인 세션, `ADMIN` 가능

### Request JSON 예시
- 없음

### Response JSON 예시

```json
{
  "success": true,
  "data": {
    "incomeItemId": "fd75c2e4-7d62-43b6-a4ef-5472c55d0ad8",
    "incomeType": "SALARY",
    "payerName": "이수시스템",
    "grossAmount": 58000000,
    "taxableAmount": 56000000,
    "withheldTaxAmount": 2600000,
    "nonTaxableAmount": 2000000
  },
  "error": null,
  "timestamp": "2026-03-17T15:32:00+09:00"
}
```

### validation 포인트
- 세션 소속 검증

## 5-4. 소득 항목 수정

- 기능명: 소득 항목 수정
- HTTP Method: `PATCH`
- URL: `/api/v1/tax-sessions/{taxSessionId}/income-items/{incomeItemId}`
- 인증 필요 여부: 예
- 권한: `USER` 본인 세션만 가능

### Request JSON 예시

```json
{
  "withheldTaxAmount": 2700000,
  "nonTaxableAmount": 1800000
}
```

### Response JSON 예시

```json
{
  "success": true,
  "data": {
    "incomeItemId": "fd75c2e4-7d62-43b6-a4ef-5472c55d0ad8",
    "withheldTaxAmount": 2700000,
    "nonTaxableAmount": 1800000,
    "updatedAt": "2026-03-17T15:33:00+09:00"
  },
  "error": null,
  "timestamp": "2026-03-17T15:33:00+09:00"
}
```

### validation 포인트
- 금액 음수 불가
- 총액 대비 하위 금액 초과 금지

## 5-5. 소득 항목 삭제

- 기능명: 소득 항목 삭제
- HTTP Method: `DELETE`
- URL: `/api/v1/tax-sessions/{taxSessionId}/income-items/{incomeItemId}`
- 인증 필요 여부: 예
- 권한: `USER` 본인 세션만 가능

### Request JSON 예시
- 없음

### Response JSON 예시

```json
{
  "success": true,
  "data": {
    "incomeItemId": "fd75c2e4-7d62-43b6-a4ef-5472c55d0ad8",
    "deleted": true
  },
  "error": null,
  "timestamp": "2026-03-17T15:34:00+09:00"
}
```

### validation 포인트
- 세션 소속 검증
- 삭제 후 세션 계산 상태 초기화 필요

---

## 6. 지출 항목 API

## 6-1. 지출 항목 등록

- 기능명: 지출 항목 등록
- HTTP Method: `POST`
- URL: `/api/v1/tax-sessions/{taxSessionId}/deduction-items`
- 인증 필요 여부: 예
- 권한: `USER` 본인 세션만 가능

### Request JSON 예시

```json
{
  "deductionType": "MEDICAL_EXPENSE",
  "subType": "HOSPITAL",
  "dependentId": "1a3cb301-cd23-4f16-a210-3125ce8f710a",
  "amount": 3500000,
  "usedAt": "2025-09-10",
  "sourceName": "서울병원",
  "attributes": {
    "receiptType": "CARD",
    "hospitalType": "GENERAL"
  }
}
```

### Response JSON 예시

```json
{
  "success": true,
  "data": {
    "deductionItemId": "7273ef9d-3ea1-43d8-a14a-8190a60cf8ac",
    "taxSessionId": "a3d3f23e-ec8b-4d2a-a4d1-0c5f5d9b2e77",
    "deductionType": "MEDICAL_EXPENSE",
    "amount": 3500000,
    "evidenceStatus": "PENDING"
  },
  "error": null,
  "timestamp": "2026-03-17T15:40:00+09:00"
}
```

### validation 포인트
- `deductionType`: enum 검증
- `amount`: 0 초과
- `dependentId`: 같은 세션의 부양가족인지 확인
- `usedAt`: 해당 과세연도 범위인지 확인
- `attributes`: 공제 타입별 필수 값 존재 여부 검증

## 6-2. 지출 항목 목록 조회

- 기능명: 지출 항목 목록 조회
- HTTP Method: `GET`
- URL: `/api/v1/tax-sessions/{taxSessionId}/deduction-items`
- 인증 필요 여부: 예
- 권한: `USER` 본인 세션, `ADMIN` 가능

### Request JSON 예시
- 없음

### Response JSON 예시

```json
{
  "success": true,
  "data": [
    {
      "deductionItemId": "7273ef9d-3ea1-43d8-a14a-8190a60cf8ac",
      "deductionType": "MEDICAL_EXPENSE",
      "subType": "HOSPITAL",
      "amount": 3500000,
      "usedAt": "2025-09-10",
      "sourceName": "서울병원",
      "evidenceStatus": "PENDING"
    }
  ],
  "error": null,
  "timestamp": "2026-03-17T15:41:00+09:00"
}
```

### validation 포인트
- 세션 소유권 검증

## 6-3. 지출 항목 단건 조회

- 기능명: 지출 항목 단건 조회
- HTTP Method: `GET`
- URL: `/api/v1/tax-sessions/{taxSessionId}/deduction-items/{deductionItemId}`
- 인증 필요 여부: 예
- 권한: `USER` 본인 세션, `ADMIN` 가능

### Request JSON 예시
- 없음

### Response JSON 예시

```json
{
  "success": true,
  "data": {
    "deductionItemId": "7273ef9d-3ea1-43d8-a14a-8190a60cf8ac",
    "deductionType": "MEDICAL_EXPENSE",
    "subType": "HOSPITAL",
    "dependentId": "1a3cb301-cd23-4f16-a210-3125ce8f710a",
    "amount": 3500000,
    "usedAt": "2025-09-10",
    "sourceName": "서울병원",
    "evidenceStatus": "PENDING",
    "attributes": {
      "receiptType": "CARD",
      "hospitalType": "GENERAL"
    }
  },
  "error": null,
  "timestamp": "2026-03-17T15:42:00+09:00"
}
```

### validation 포인트
- 세션 소속 검증

## 6-4. 지출 항목 수정

- 기능명: 지출 항목 수정
- HTTP Method: `PATCH`
- URL: `/api/v1/tax-sessions/{taxSessionId}/deduction-items/{deductionItemId}`
- 인증 필요 여부: 예
- 권한: `USER` 본인 세션만 가능

### Request JSON 예시

```json
{
  "amount": 4200000,
  "sourceName": "서울대학교병원",
  "attributes": {
    "receiptType": "CARD",
    "hospitalType": "TERTIARY"
  }
}
```

### Response JSON 예시

```json
{
  "success": true,
  "data": {
    "deductionItemId": "7273ef9d-3ea1-43d8-a14a-8190a60cf8ac",
    "amount": 4200000,
    "sourceName": "서울대학교병원",
    "updatedAt": "2026-03-17T15:43:00+09:00"
  },
  "error": null,
  "timestamp": "2026-03-17T15:43:00+09:00"
}
```

### validation 포인트
- 공제 타입별 필수 속성 유지 여부
- 금액 0 초과

## 6-5. 지출 항목 삭제

- 기능명: 지출 항목 삭제
- HTTP Method: `DELETE`
- URL: `/api/v1/tax-sessions/{taxSessionId}/deduction-items/{deductionItemId}`
- 인증 필요 여부: 예
- 권한: `USER` 본인 세션만 가능

### Request JSON 예시
- 없음

### Response JSON 예시

```json
{
  "success": true,
  "data": {
    "deductionItemId": "7273ef9d-3ea1-43d8-a14a-8190a60cf8ac",
    "deleted": true
  },
  "error": null,
  "timestamp": "2026-03-17T15:44:00+09:00"
}
```

### validation 포인트
- 세션 소속 검증
- 삭제 후 결과 재계산 필요 표시

---

## 7. 계산 / 결과 API

## 7-1. 연말정산 시뮬레이션 실행

- 기능명: 연말정산 시뮬레이션 실행
- HTTP Method: `POST`
- URL: `/api/v1/tax-sessions/{taxSessionId}/simulate`
- 인증 필요 여부: 예
- 권한: `USER` 본인 세션만 가능

### Request JSON 예시

```json
{
  "ruleVersion": "2025.1",
  "forceRecalculate": true
}
```

### Response JSON 예시

```json
{
  "success": true,
  "data": {
    "taxSessionId": "a3d3f23e-ec8b-4d2a-a4d1-0c5f5d9b2e77",
    "calculationResultId": "6d2c0303-0da7-4ef4-8b0a-9f0c76d87b6a",
    "calculationVersion": 3,
    "ruleVersion": "2025.1",
    "status": "CALCULATED",
    "executedAt": "2026-03-17T15:50:00+09:00"
  },
  "error": null,
  "timestamp": "2026-03-17T15:50:00+09:00"
}
```

### validation 포인트
- 세션 소유권 검증
- 소득 항목 최소 1개 이상 존재 여부
- 필수 기본정보 입력 여부
- 이미 같은 입력값/같은 규칙 버전으로 계산된 경우 멱등 처리 가능

## 7-2. 결과 조회

- 기능명: 시뮬레이션 결과 조회
- HTTP Method: `GET`
- URL: `/api/v1/tax-sessions/{taxSessionId}/results`
- 인증 필요 여부: 예
- 권한: `USER` 본인 세션, `ADMIN` 가능

### Request JSON 예시
- 없음

### Response JSON 예시

```json
{
  "success": true,
  "data": {
    "calculationResultId": "6d2c0303-0da7-4ef4-8b0a-9f0c76d87b6a",
    "taxSessionId": "a3d3f23e-ec8b-4d2a-a4d1-0c5f5d9b2e77",
    "taxYear": 2025,
    "ruleVersion": "2025.1",
    "totalIncomeAmount": 58000000,
    "totalDeductionAmount": 7200000,
    "taxableIncomeAmount": 50800000,
    "calculatedTaxAmount": 3400000,
    "taxCreditAmount": 1250000,
    "finalTaxAmount": 2150000,
    "withholdingTaxAmount": 2600000,
    "expectedRefundAmount": 450000,
    "appliedItems": [
      {
        "deductionItemId": "7273ef9d-3ea1-43d8-a14a-8190a60cf8ac",
        "deductionType": "MEDICAL_EXPENSE",
        "requestedAmount": 4200000,
        "eligibleAmount": 2460000,
        "appliedAmount": 2460000,
        "reasons": [
          "총급여 3% 초과 요건을 충족합니다.",
          "의료비 공제 인정 금액을 계산했습니다."
        ]
      }
    ]
  },
  "error": null,
  "timestamp": "2026-03-17T15:51:00+09:00"
}
```

### validation 포인트
- 최근 계산 결과 존재 여부 확인
- 세션 소유권 검증

## 7-3. 미적용 사유 조회

- 기능명: 미적용 사유 조회
- HTTP Method: `GET`
- URL: `/api/v1/tax-sessions/{taxSessionId}/rejections`
- 인증 필요 여부: 예
- 권한: `USER` 본인 세션, `ADMIN` 가능

### Request JSON 예시
- 없음

### Response JSON 예시

```json
{
  "success": true,
  "data": [
    {
      "deductionItemId": "ce2c4fb6-65a9-4142-a4f2-6f7ad76a8f11",
      "deductionType": "EDUCATION_EXPENSE",
      "requestedAmount": 12000000,
      "reasonCodes": [
        "DEPENDENT_INCOME_EXCEEDED",
        "LIMIT_EXCEEDED"
      ],
      "reasons": [
        "부양가족 연 소득 요건을 충족하지 않습니다.",
        "교육비 공제 한도를 초과했습니다."
      ]
    }
  ],
  "error": null,
  "timestamp": "2026-03-17T15:52:00+09:00"
}
```

### validation 포인트
- 계산 결과가 선행되어야 함
- 결과 trace에서 reject 항목만 추출

## 7-4. 누락 서류 체크 조회

- 기능명: 누락 서류 체크 조회
- HTTP Method: `GET`
- URL: `/api/v1/tax-sessions/{taxSessionId}/document-checklists`
- 인증 필요 여부: 예
- 권한: `USER` 본인 세션, `ADMIN` 가능

### Request JSON 예시
- 없음

### Response JSON 예시

```json
{
  "success": true,
  "data": [
    {
      "checklistId": "0f8cbacc-fb0f-4ab7-b238-8f9c33191b34",
      "documentType": "MEDICAL_RECEIPT",
      "requiredYn": true,
      "submittedYn": false,
      "reviewStatus": "PENDING",
      "comment": "의료비 영수증 누락"
    }
  ],
  "error": null,
  "timestamp": "2026-03-17T15:53:00+09:00"
}
```

### validation 포인트
- 세션 소유권 검증
- 관리자 미검토 상태도 사용자에게 노출할 범위 정의 필요

---

## 8. 관리자 검토 API

## 8-1. 검토 대상 세션 목록 조회

- 기능명: 관리자 검토 대상 세션 목록 조회
- HTTP Method: `GET`
- URL: `/api/v1/admin/tax-sessions`
- 인증 필요 여부: 예
- 권한: `ADMIN`

### Query Parameter 예시
- `status=SUBMITTED`
- `taxYear=2025`
- `page=0`
- `size=20`

### Request JSON 예시
- 없음

### Response JSON 예시

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "taxSessionId": "a3d3f23e-ec8b-4d2a-a4d1-0c5f5d9b2e77",
        "userId": "1f8d2f50-7f59-4c47-b2d5-4f5b5d3d8a11",
        "userName": "홍길동",
        "taxYear": 2025,
        "sessionStatus": "SUBMITTED",
        "latestCalculationVersion": 3,
        "submittedAt": "2026-03-17T15:55:00+09:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  },
  "error": null,
  "timestamp": "2026-03-17T15:56:00+09:00"
}
```

### validation 포인트
- `status`, `taxYear` 필터 값 검증
- 관리자 권한 확인

## 8-2. 관리자 세션 상세 조회

- 기능명: 관리자 세션 상세 조회
- HTTP Method: `GET`
- URL: `/api/v1/admin/tax-sessions/{taxSessionId}`
- 인증 필요 여부: 예
- 권한: `ADMIN`

### Request JSON 예시
- 없음

### Response JSON 예시

```json
{
  "success": true,
  "data": {
    "taxSessionId": "a3d3f23e-ec8b-4d2a-a4d1-0c5f5d9b2e77",
    "user": {
      "userId": "1f8d2f50-7f59-4c47-b2d5-4f5b5d3d8a11",
      "name": "홍길동",
      "email": "user@example.com"
    },
    "taxYear": 2025,
    "sessionStatus": "SUBMITTED",
    "dependentsCount": 2,
    "incomeItemCount": 1,
    "deductionItemCount": 5,
    "latestResult": {
      "finalTaxAmount": 2150000,
      "expectedRefundAmount": 450000
    }
  },
  "error": null,
  "timestamp": "2026-03-17T15:57:00+09:00"
}
```

### validation 포인트
- 세션 존재 여부
- 관리자 권한 검증

## 8-3. 관리자 검토 상태 변경

- 기능명: 관리자 검토 상태 변경
- HTTP Method: `PATCH`
- URL: `/api/v1/admin/tax-sessions/{taxSessionId}/review-status`
- 인증 필요 여부: 예
- 권한: `ADMIN`

### Request JSON 예시

```json
{
  "reviewStatus": "REJECTED",
  "comment": "의료비 증빙이 누락되어 보완이 필요합니다."
}
```

### Response JSON 예시

```json
{
  "success": true,
  "data": {
    "taxSessionId": "a3d3f23e-ec8b-4d2a-a4d1-0c5f5d9b2e77",
    "reviewStatus": "REJECTED",
    "comment": "의료비 증빙이 누락되어 보완이 필요합니다.",
    "reviewedBy": "2f9fef00-e8ac-47c5-b819-7ab8c6ef0d66",
    "reviewedAt": "2026-03-17T15:58:00+09:00"
  },
  "error": null,
  "timestamp": "2026-03-17T15:58:00+09:00"
}
```

### validation 포인트
- `reviewStatus`: enum 검증
- 반려 시 `comment` 필수 권장
- 상태 전이 규칙 검증  
  예: `SUBMITTED -> REVIEWED`, `SUBMITTED -> REJECTED`

## 8-4. 누락 서류 체크리스트 등록

- 기능명: 관리자 누락 서류 체크리스트 등록
- HTTP Method: `POST`
- URL: `/api/v1/admin/tax-sessions/{taxSessionId}/document-checklists`
- 인증 필요 여부: 예
- 권한: `ADMIN`

### Request JSON 예시

```json
{
  "documentType": "MEDICAL_RECEIPT",
  "deductionItemId": "7273ef9d-3ea1-43d8-a14a-8190a60cf8ac",
  "requiredYn": true,
  "submittedYn": false,
  "reviewStatus": "PENDING",
  "comment": "의료비 영수증 업로드 필요"
}
```

### Response JSON 예시

```json
{
  "success": true,
  "data": {
    "checklistId": "0f8cbacc-fb0f-4ab7-b238-8f9c33191b34",
    "taxSessionId": "a3d3f23e-ec8b-4d2a-a4d1-0c5f5d9b2e77",
    "documentType": "MEDICAL_RECEIPT",
    "requiredYn": true,
    "submittedYn": false,
    "reviewStatus": "PENDING"
  },
  "error": null,
  "timestamp": "2026-03-17T15:59:00+09:00"
}
```

### validation 포인트
- 같은 세션/문서타입/항목으로 중복 생성 방지 검토
- `deductionItemId`가 해당 세션 소속인지 확인

## 8-5. 누락 서류 체크리스트 수정

- 기능명: 관리자 누락 서류 체크리스트 수정
- HTTP Method: `PATCH`
- URL: `/api/v1/admin/document-checklists/{checklistId}`
- 인증 필요 여부: 예
- 권한: `ADMIN`

### Request JSON 예시

```json
{
  "submittedYn": true,
  "reviewStatus": "APPROVED",
  "comment": "영수증 확인 완료"
}
```

### Response JSON 예시

```json
{
  "success": true,
  "data": {
    "checklistId": "0f8cbacc-fb0f-4ab7-b238-8f9c33191b34",
    "submittedYn": true,
    "reviewStatus": "APPROVED",
    "comment": "영수증 확인 완료",
    "reviewedBy": "2f9fef00-e8ac-47c5-b819-7ab8c6ef0d66",
    "reviewedAt": "2026-03-17T16:00:00+09:00"
  },
  "error": null,
  "timestamp": "2026-03-17T16:00:00+09:00"
}
```

### validation 포인트
- `reviewStatus` enum 검증
- 승인/반려 상태와 `submittedYn`의 논리 일관성 확인

---

## 9. Swagger/OpenAPI 작성 시 DTO 분리 권장안

### 요청 DTO 예시
- `SignUpRequest`
- `LoginRequest`
- `UpdateMyProfileRequest`
- `CreateDependentRequest`
- `UpdateDependentRequest`
- `CreateIncomeItemRequest`
- `UpdateIncomeItemRequest`
- `CreateDeductionItemRequest`
- `UpdateDeductionItemRequest`
- `RunSimulationRequest`
- `UpdateReviewStatusRequest`
- `CreateDocumentChecklistRequest`
- `UpdateDocumentChecklistRequest`

### 응답 DTO 예시
- `UserMeResponse`
- `DependentResponse`
- `IncomeItemResponse`
- `DeductionItemResponse`
- `SimulationResultResponse`
- `RejectedDeductionResponse`
- `DocumentChecklistResponse`
- `AdminTaxSessionSummaryResponse`

## 10. 실무적으로 추가 고려하면 좋은 API

이번 요청 범위에는 없지만 실제 구현 시 같이 있으면 좋은 API다.

- `POST /api/v1/tax-sessions`
- `GET /api/v1/tax-sessions/{taxSessionId}`
- `PATCH /api/v1/tax-sessions/{taxSessionId}/basic-info`
- `GET /api/v1/tax-sessions`
- `GET /api/v1/tax-sessions/{taxSessionId}/calculation-results/history`

특히 계산 이력을 남기는 구조라면 결과 히스토리 조회 API는 면접 포인트로도 좋다.

## 11. 1년차 개발자 관점 정리

### 11-1. 이번 작업에서 새로 배우게 되는 개념
- RESTful 하위 리소스 설계
- 인증/인가와 리소스 소유권 검증 분리
- 요청 DTO와 응답 DTO 분리
- 상태 전이 검증
- Swagger/OpenAPI 명세 중심 설계

### 11-2. 왜 이런 구조를 쓰는지
- 세션 아래에 부양가족/소득/지출을 배치해야 도메인 경계가 명확해진다.
- 사용자 API와 관리자 API를 분리해야 권한 정책이 단순해진다.
- 계산 실행 API와 결과 조회 API를 나눠야 책임이 분리되고 캐시/이력 관리가 쉬워진다.

### 11-3. 실무에서 자주 쓰는 이유
- 프론트와 백엔드가 동시에 개발할 때 명세 문서가 계약 역할을 한다.
- Swagger 기준으로 DTO와 validation을 미리 정하면 구현 중 충돌이 줄어든다.
- 관리자 화면과 사용자 화면이 다른 요구사항을 가지기 때문에 API도 역할별로 나누는 경우가 많다.

### 11-4. 놓치기 쉬운 포인트
- 인증만으로 충분하지 않고 "본인 세션인지" 소유권 검증이 꼭 필요하다.
- `PATCH`는 전체 수정이 아니라 부분 수정이므로 nullable 처리 기준을 분명히 해야 한다.
- 삭제 후 재계산 필요 상태를 어떻게 관리할지 설계해야 한다.
- 결과 조회 API와 계산 실행 API를 섞으면 멱등성과 캐시 전략이 꼬인다.
- 지출 항목의 `attributes`처럼 타입별 필드가 다를 때 validation 전략을 미리 정해야 한다.

### 11-5. 더 공부할 키워드
- OpenAPI 3.0
- Bean Validation
- Idempotency
- Resource Ownership
- Problem Details for HTTP APIs
