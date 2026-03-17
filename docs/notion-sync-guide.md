# Notion 자동 업로드 가이드

## 목표
`docs/` 아래 Markdown 문서를 원본으로 관리하고, 변경된 문서만 Notion 데이터베이스에 동기화한다.

## 동작 방식
1. `scripts/sync-docs-to-notion.mjs`가 `docs/**/*.md`를 읽는다.
2. 파일 내용의 해시를 계산해 `.notion-sync-state.json`과 비교한다.
3. 변경된 문서만 Notion 데이터베이스에서 같은 `Source Path`를 가진 페이지를 찾는다.
4. 페이지가 있으면 업데이트하고, 없으면 새로 생성한다.

## Notion 준비
### 1. Integration 생성
- [Notion Integrations](https://www.notion.so/my-integrations)에서 Internal Integration을 만든다.
- 발급된 토큰을 `NOTION_TOKEN`으로 사용한다.

### 2. 데이터베이스 생성
- Title 속성은 기본 제목 속성을 그대로 사용해도 된다.
- `Source Path`라는 `Rich text` 속성을 하나 추가한다.

| 속성명 | 타입 | 용도 |
| --- | --- | --- |
| 제목 속성 | Title | 문서 제목 |
| `Source Path` | Rich text | 로컬 문서 경로 |

### 3. 권한 연결
- 데이터베이스 우측 상단 `...` 메뉴에서 `Connections`를 연다.
- 만든 Integration을 연결한다.

## 로컬 설정
루트에 `.env` 파일을 만들고 아래 값을 넣는다.

```env
NOTION_TOKEN=secret_xxx
NOTION_DATABASE_ID=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
NOTION_SOURCE_PATH_PROPERTY=Source Path
NOTION_SYNC_DOCS_DIR=docs
```

수동 실행은 아래 명령으로 한다.

```bash
node .\scripts\sync-docs-to-notion.mjs
```

## GitHub Actions 자동 실행
워크플로 파일은 [.github/workflows/sync-notion-docs.yml](/C:/yelingg/year-end/.github/workflows/sync-notion-docs.yml)이다.

아래 파일이 push되면 자동 실행된다.
- `docs/**/*.md`
- `scripts/sync-docs-to-notion.mjs`
- `package.json`
- `.github/workflows/sync-notion-docs.yml`

GitHub 저장소 `Settings -> Secrets and variables -> Actions`에는 아래 두 개만 있으면 된다.

| 이름 | 설명 |
| --- | --- |
| `NOTION_TOKEN` | Notion Internal Integration Token |
| `NOTION_DATABASE_ID` | 업로드 대상 데이터베이스 ID |

제목 속성은 스크립트가 데이터베이스 스키마에서 자동 탐지하고, 문서 경로 속성은 `Source Path`를 사용한다.
GitHub Actions에서는 현재 push에서 변경된 Markdown 문서만 골라서 동기화한다.

## 주의할 점
- 원본은 Notion이 아니라 `docs/`이다.
- Notion에서 직접 수정한 내용은 다음 동기화 때 덮어써질 수 있다.
- 코드 블록, 표 같은 복잡한 Markdown은 현재 단순 텍스트 수준으로 반영된다.
