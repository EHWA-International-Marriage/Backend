# CLAUDE.md

이화국제결혼 홈페이지의 백엔드(API 서버). 국제결혼 상담·회원·게시판 데이터를 다루므로 **개인정보 보호와 보안이 최우선**이고, 프론트의 **SEO(검색 상위 노출)를 뒷받침**하는 것이 목표다.

Git 규칙(브랜치·커밋·PR)은 `README.md`의 "작업 규칙"을, SEO 관련 기능(slug, 사이트맵, IndexNow, 메타데이터 등)은 `README.md`의 "SEO 전략"을 따른다.

## 로컬 참고 자료 (레포에 없음, 있으면 사용)

- `.local/HANDOFF.md` — 진행 상황과 남은 작업. "다음 작업"을 물으면 이 파일을 본다.
- `.local/CONVENTION.md` — 공통 코드를 처음 만들 때 참고하는 상세 설계 메모. 평소에는 열지 않는다.
- `../seoulSeek` — 이전 프로젝트. 공통 코드(응답 포맷, 예외, JWT, 테스트 유틸)를 가져올 원본. 카카오·외부 API 코드는 제외하고, 아래 규칙과 다른 부분은 고쳐서 가져온다.

공통 코드가 생긴 뒤에는 **기존 코드의 패턴을 따르는 것**이 최우선이다. 새 도메인은 이미 있는 도메인을 보고 같은 구조로 만든다.

## 기술 스택

Java 17 · Spring Boot 3 · Gradle **Groovy**(`build.gradle`) · Spring Security(JWT, HttpOnly 쿠키, Stateless) · JPA + QueryDSL 5(jakarta) · **MariaDB** · Redis · S3(AWS SDK v2) · REST Docs · JUnit5 + Testcontainers(mariadb)

## 명령어

```bash
docker compose up -d          # 로컬 MariaDB + Redis
./gradlew bootRun             # 로컬 실행
./gradlew spotlessApply       # 포맷 적용
./gradlew build               # 테스트 + spotlessCheck (PR 전 필수)
```

## 패키지 구조

```
com.ewha.backend
├── _core       # 공통: config, dto(ApiResponse), exception, infrastructure(filter·handler), constraint, util
├── api/{domain}      # controller, service, dto/request(XxxReq), dto/response(XxxRes)
└── entity/{domain}   # entity, repository(XxxRepository, XxxQueryRepository), exception
```

- 게시판 종류가 여러 개여도 `board` 도메인 하나 + `BoardType` enum으로 처리한다.
- 국가는 공통 `Country` enum 하나를 모든 도메인이 쓴다.

## 코드 규칙

**API**
- 경로 권한: 공개 / `/auth/**`(USER·ADMIN) / `/admin/**`(ADMIN). 유저 ID는 `JwtAuthentication.getUserId()` / `findUserId()`로만 얻는다.
- URL은 소문자 kebab-case, 복수형 리소스(`/boards`, `/consults`), 끝에 `/` 없음.
- 응답은 항상 `ApiResponse.ok(...)` / `ApiResponse.create(id)`로. Controller는 서비스 호출 → 반환만 하고 로직을 두지 않는다.

**예외**
- 모든 비즈니스 예외는 `ApiException`(HttpStatus + 메시지) 상속. 메시지는 사용자에게 보여줘도 되는 한국어 문장.
- 예외 → 응답 변환은 `ApiExceptionHandler` 한 곳에서만. 500 응답에 내부 예외 메시지를 노출하지 않는다.

**Entity / Repository / Service**
- Entity: `BaseTimeEntity` 상속, `@Setter`·`@Data` 금지, 기본 생성자 `protected`, 생성은 정적 팩토리 `of()`, 연관관계 `LAZY`, enum은 `EnumType.STRING`.
- Repository: 없으면 예외인 조회는 `default getById()`로 모은다. 동적 조건·페이징은 `XxxQueryRepository`(QueryDSL).
- Service: `@Transactional(readOnly = true)` 클래스 기본, 쓰기 메서드만 `@Transactional`. 패키지는 `org.springframework.transaction.annotation`만.
- DTO는 `record` + Bean Validation, Controller에서 `@Valid`. Entity를 응답으로 내보내지 않는다.

**설정**
- 설정값은 `@ConfigurationProperties` + record. 비밀값·URL·CORS origin은 하드코딩하지 않고 `.env` → `application-{profile}.yml`로 주입한다.

**테스트**
- 새 API = Controller 테스트 + REST Docs 스니펫. 테스트 데이터는 `{Domain}Fixture`로 만든다.

## 보안 · 개인정보

- 비밀번호는 BCrypt, 연락처는 `AESConverter`로 암호화 저장.
- 로그와 응답에 이름·연락처·토큰을 남기지 않는다. `System.out.println`, `printStackTrace` 금지 → `@Slf4j`.
- 상담·비밀글은 작성자 본인과 관리자만 조회. 공개 목록에서는 서버에서 마스킹한다.
- slug: 개인정보 금지, NFC 정규화, 허용 문자 화이트리스트, 최대 100자, DB unique. slug로 조회해도 공개 상태·권한을 검사한다. 리다이렉트는 서버가 DB 값으로 만든 내부 경로만.
- 게시판 HTML은 jsoup 화이트리스트로 정화. 파일 업로드는 확장자·MIME·용량 검사 + UUID 파일명.

## 절대 하지 않을 것

- `.env`, 비밀키, 토큰, 실제 고객 데이터를 커밋하지 않는다. `.local/`, `.mcp.json`도 커밋하지 않는다.
- `develop` / `main`에 직접 push하지 않는다.
- 요청받지 않은 대규모 리팩토링이나 의존성 추가를 하지 않는다. 필요하면 먼저 제안한다.
