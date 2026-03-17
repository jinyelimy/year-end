# Notion 자동 업로드 가이드

## 목표
`docs/` 아래의 Markdown 문서를 기준 문서 저장소로 두고, 변경된 문서만 Notion 데이터베이스에 자동 반영한다.

## 왜 이 방식을 쓰는가
- 로컬에 원본 문서가 남아서 재업로드와 이력 관리가 쉽다.
- Notion 업로드 실패가 나도 산출물이 사라지지 않는다.
- 포트폴리오 문서, 설계 문서, 회고 문서를 같은 방식으로 누적할 수 있다.

## 동작 방식
1. `scripts/sync-docs-to-notion.mjs`가 `docs/**/*.md`를 읽는다.
2. 파일 해시를 `.notion-sync-state.json`에 저장한다.
3. 바뀐 문서만 Notion 데이터베이스에서 같은 `Source Path`를 가진 페이지를 찾는다.
4. 있으면 업데이트하고, 없으면 새 페이지를 만든다.

## Notion 사전 준비
### 1. Integration 생성
- [Notion Integrations](https://www.notion.so/my-integrations) 에서 새 Integration 생성
- 내부용 이름 예시: `year-end-doc-sync`
- 발급된 Internal Integration Token을 보관

### 2. 데이터베이스 생성
데이터베이스 속성은 아래처럼 맞추는 것을 권장한다.

| 속성명 | 타입 | 용도 |
|---|---|---|
| Name | Title | 문서 제목 |
| Source Path | Rich text | 로컬 문서 경로 |

스크립트는 기본적으로 `Name`, `Source Path` 속성을 사용한다.
다른 이름을 쓰고 싶으면 `.env`에서 바꿀 수 있다.

### 3. 권한 연결
- Notion 데이터베이스 우측 상단 `...` 메뉴에서 `Connections` 선택
- 생성한 Integration을 연결

## 로컬 설정
### 1. 환경 변수 파일 생성
루트에 `.env` 파일을 만들고 아래 값을 채운다.

```env
NOTION_TOKEN=secret_xxx
NOTION_DATABASE_ID=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
NOTION_TITLE_PROPERTY=Name
NOTION_SOURCE_PATH_PROPERTY=Source Path
NOTION_SYNC_DOCS_DIR=docs
```

### 2. 수동 실행
```bash
npm run notion:sync
```

## 자동 실행 추천 방식
### 로컬 정기 실행
- Windows 작업 스케줄러 또는 Codex 자동화로 하루 1회 실행
- 추천 시간: 평일 오전 9시

### CI 실행
- GitHub에 push 될 때 문서 변경분을 자동 동기화할 수 있다
- 워크플로 파일: `.github/workflows/sync-notion-docs.yml`
- 단, Notion 토큰과 DB 설정은 GitHub Secrets로 관리해야 한다

## GitHub Actions 설정
GitHub 저장소의 `Settings -> Secrets and variables -> Actions` 에 아래 Secrets를 추가한다.

| 이름 | 값 |
|---|---|
| `NOTION_TOKEN` | Notion Internal Integration Token |
| `NOTION_DATABASE_ID` | 문서를 올릴 Notion 데이터베이스 ID |
| `NOTION_TITLE_PROPERTY` | 데이터베이스 제목 속성 이름 |
| `NOTION_SOURCE_PATH_PROPERTY` | 원본 경로 속성 이름 |

현재 이 프로젝트 기준 권장 값은 아래와 같다.

| 이름 | 값 |
|---|---|
| `NOTION_TITLE_PROPERTY` | `이름` |
| `NOTION_SOURCE_PATH_PROPERTY` | `Source Path` |

워크플로는 아래 파일이 push 될 때 실행된다.
- `docs/**/*.md`
- `scripts/sync-docs-to-notion.mjs`
- `package.json`
- `.github/workflows/sync-notion-docs.yml`

원한다면 GitHub Actions 화면에서 `Run workflow`로 수동 실행도 가능하다.

## 현재 스크립트의 범위
- `#`, `##`, `###` 헤더 지원
- 일반 문단 지원
- `- ` 목록, `1. ` 목록 지원
- 단순 Markdown 위주 문서 업로드에 적합

## 남아 있는 한계
- 표와 코드블록은 현재 단순 텍스트 수준으로 반영된다.
- Notion 전용 서식까지 완벽하게 매핑하지는 않는다.
- 문서가 아주 길면 표현 손실이 있을 수 있다.

포트폴리오 용도에서는 먼저 "자동 반영 파이프라인"을 만드는 것이 더 중요하고, 서식 고도화는 다음 단계로 두는 편이 좋다.
