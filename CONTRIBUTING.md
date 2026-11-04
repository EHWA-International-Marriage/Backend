# 브랜치 · PR 규칙

이 저장소는 이화국제결혼 홈페이지의 백엔드(API 서버)입니다. 상담 신청, 회원, 게시판처럼 **개인정보와 실제 데이터를 다루는 코드**가 들어 있습니다. 그래서 규칙은 하나로 모입니다. **develop과 main에는 검증된 변경만, PR로만 들어갑니다.**

## 브랜치

`develop`(개발 통합)과 `main`(배포본) 두 개를 둡니다. 프론트와 달리 서버는 DB 구조 변경, 환경변수 추가처럼 배포 전에 한 번 더 모아서 확인할 일이 많아 중간 브랜치를 둡니다.

| 브랜치 | 용도 | 예시 |
|---|---|---|
| `main` | 실제 서버 배포본. 직접 push 금지 | |
| `develop` | 개발 통합. 기본 브랜치. 직접 push 금지 | |
| `feat/…` | 새 API · 새 기능 | `feat/consult-api` |
| `fix/…` | 버그 수정 | `fix/login-lock-count` |
| `refactor/…` | 동작은 그대로, 구조만 개선 | `refactor/file-upload-service` |
| `test/…` | 테스트 추가 · 수정 | `test/board-permission` |
| `chore/…` | 설정 · 의존성 · 문서 · CI | `chore/github-actions-ci` |
| `hotfix/…` | 실제 서버에 급히 고쳐야 할 문제 | `hotfix/consult-masking` |

- 항상 최신 develop에서 만듭니다: `git switch develop && git pull && git switch -c feat/…`
- `hotfix/…`만 main에서 만듭니다.
- 이름은 영어 소문자와 `-`만 씁니다.
- 한 브랜치에 한 가지 일만 하고, 며칠 안에 PR로 합칩니다.
- PR 전에 `./gradlew spotlessApply && ./gradlew build`를 실행해 테스트까지 오류 없이 끝나는지 확인합니다. CI도 같은 명령으로 검사합니다.

## 커밋 메시지

`종류: 무엇을 바꿨는지` 형식으로 한 줄 요약을 씁니다. 한국어로 써도 됩니다.

```
feat: 상담 신청 API 추가
fix: 로그인 실패 횟수가 초기화되지 않는 문제 수정
refactor: S3 업로드 로직을 FileService로 통합
```

종류는 브랜치와 같이 `feat` · `fix` · `refactor` · `test` · `chore` · `hotfix`를 씁니다.

## PR

1. 작업 브랜치를 push하고 **develop을 향해** PR을 엽니다. PR 양식이 자동으로 채워집니다.
2. 본문 첫 줄에 `closes #이슈번호`를 적으면 합칠 때 이슈가 자동으로 닫힙니다.
3. API가 생기거나 바뀌었다면 PR 본문 표에 정리합니다. 프론트는 이 표와 REST Docs를 보고 작업합니다.
4. GitHub Actions(빌드·테스트)와 Claude 리뷰 봇이 자동으로 검사합니다. 리뷰 봇의 지적은 반영하거나, 반영하지 않는 이유를 댓글로 남깁니다.
5. 리뷰어 1명 이상이 승인하면 **Squash and merge**로 합칩니다.
6. 합친 뒤 작업 브랜치는 지웁니다.
7. 배포할 때는 `develop → main` PR을 열어 합칩니다.

혼자 작업할 때도 PR을 거칩니다. CI와 리뷰 봇이 한 번 더 보는 것만으로도 개인정보 노출이나 권한 누락이 실제 서버에 나가는 일을 막을 수 있습니다.

## 꼭 확인할 것

- **개인정보:** 고객 이름, 연락처, 상담 내용이 로그나 응답에 그대로 나가지 않는지 확인합니다. 테스트 데이터에도 실제 고객 정보를 쓰지 않습니다.
- **권한:** 새 API가 공개 / `/auth` / `/admin` 중 맞는 경로에 있는지, 작성자·비밀글 검사가 빠지지 않았는지 확인합니다.
- **비밀값:** 비밀번호, API 키, 토큰, `.env` 파일은 저장소에 절대 넣지 않습니다. 새 환경변수는 `.env.example`에 이름만 추가합니다.
- **DB 변경:** 테이블이나 컬럼을 바꿨다면 PR 본문에 적습니다. 배포할 때 운영 DB에 반영해야 합니다.

## 급한 수정 (hotfix)

실제 서버에서 개인정보가 노출되거나 상담 신청이 안 되는 등 급한 문제가 생겼을 때 씁니다.

1. main에서 `hotfix/…` 브랜치를 만들고 고칩니다.
2. main을 향해 PR을 열고, CI가 통과하면 바로 합칩니다. 리뷰는 합친 뒤에 받아도 됩니다.
3. 같은 수정을 develop에도 반영합니다 (`main → develop` PR).

## 되돌리기

- GitHub에서 문제의 PR을 열고 **Revert** 버튼으로 되돌리는 PR을 만들어 합칩니다.
- DB 구조를 바꾼 PR은 코드만 되돌리면 안 됩니다. DB도 이전 상태로 돌릴 수 있는지 먼저 확인합니다.

## 저장소 설정 (관리자가 한 번만)

GitHub 저장소 **Settings**에서:

- **Rules → Rulesets → New branch ruleset**, 대상 `main`, `develop`:
  - Restrict deletions, Block force pushes
  - Require a pull request before merging (승인 1명)
  - Require status checks to pass: GitHub Actions의 빌드 체크 (CI를 추가한 뒤 PR을 한 번 열어야 목록에 나타납니다)
- **General → Pull Requests:** Allow squash merging만 켜고, Automatically delete head branches를 켭니다.
- **Code security:** Dependabot alerts · security updates, Code scanning(CodeQL)을 켭니다.
