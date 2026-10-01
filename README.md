# Lovable Pet 🐾

> 유기동물 입양 매칭 + AR 서비스

보호소 유기동물의 사진으로 3D 모델을 생성해 AR로 미리 만나보고, 입양 희망자의 설문을 바탕으로 적합도가 높은 동물을 추천하는 서비스입니다.
이 저장소는 **Spring Boot 백엔드** 저장소입니다. (프론트엔드, Python AI 서버는 별도 저장소)

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| Language / Runtime | Java 21 |
| Framework | Spring Boot 4.1 (Web MVC, Validation, Data JPA, Actuator) |
| Build | Gradle 9 (Wrapper 포함 — 별도 설치 불필요) |
| DB | PostgreSQL 16 + pgvector |
| Migration | Flyway |
| API 문서 | springdoc-openapi 3.x (Swagger UI) |
| 외부 연동 | Python AI 서버 (3D 생성) — Spring `RestClient` |
| Infra | Docker, Docker Compose, GitHub Actions |


## 백엔드 패키지 구조 (도메인형)

```
com.lovablepet
├── domain/             # 비즈니스 도메인
│   ├── pet/            # 동물 기록, 사진
│   │   ├── controller/
│   │   ├── service/
│   │   ├── repository/
│   │   ├── entity/
│   │   └── dto/
│   ├── generation/     # 3D 생성 작업, Python 클라이언트, 웹훅
│   ├── model/          # 완성된 3D 결과 조회
│   ├── match/          # 적합도 매칭, 재랭킹
│   └── survey/         # 설문
└── global/             # 전역 설정 / 공통 모듈
    ├── config/         # RestClient, OpenAPI, CORS 설정
    ├── exception/      # ErrorCode, BusinessException, GlobalExceptionHandler
    └── response/       # 공통 응답 포맷 (ApiResponse, ErrorResponse)
```

- 새 도메인은 `domain/{도메인명}/` 아래에 `pet`과 같은 하위 구조(controller / service / repository / entity / dto)로 추가합니다.
- 빈 패키지는 git에 올라가지 않으므로, 첫 클래스를 추가할 때 패키지를 만듭니다.

## 공통 응답 포맷

모든 API는 아래 형식으로 응답합니다. 값이 없는 필드도 `null`로 항상 포함합니다.

```jsonc
// 성공
{
  "resultType": "SUCCESS",
  "success": { "data": { ... } },   // 반환할 데이터가 없으면 { "data": null }
  "error": null,
  "meta": { "timestamp": "2026-06-30T22:10:00", "path": "/api/auth/login" }
}

// 실패
{
  "resultType": "FAIL",
  "success": null,
  "error": {
    "code": "COMMON_400",
    "message": "잘못된 입력값입니다.",
    // 입력값 검증 실패일 때만 항목별 오류 목록, 그 외에는 null
    "details": [ { "field": "name", "rejectedValue": "", "reason": "공백일 수 없습니다" } ]
  },
  "meta": { "timestamp": "2026-06-30T22:10:00", "path": "/api/pets" }
}
```

- `meta.timestamp`는 항상 한국 시간(KST), `meta.path`는 요청 경로(쿼리스트링 제외)입니다.
- 컨트롤러는 `ApiResponse.ok(data)`만 반환하면 됩니다. `meta`는 `ApiResponseMetaAdvice`가 자동으로 채웁니다.

도메인 예외는 `BusinessException`을 상속하고, 에러 코드는 `ErrorCode` enum에 추가합니다.

---


## 브랜치 전략

```
feat/*, fix/*, refactor/*, docs/*, chore/*  ──PR──▶  dev  ──PR──▶  main
                                                     (개발 통합)     (배포)
hotfix/*  ──PR──▶  main (긴급 수정, dev에도 반영)
```

| 브랜치 | 역할 | 규칙 |
|---|---|---|
| `main` | 배포용 | 직접 push 금지. **`dev` 또는 `hotfix/*`에서 오는 PR만** 머지 |
| `dev` | 개발 통합 (기본 브랜치) | 작업 브랜치 PR이 머지되는 곳 |
| `feat/*` | 기능 개발 | `dev`에서 분기 → 작업 → `dev`로 PR |
| `fix/*` | 버그 수정 | `dev`에서 분기 → 수정 → `dev`로 PR |
| `refactor/*` | 동작 변경 없는 코드 개선 | `dev`에서 분기 → 작업 → `dev`로 PR |
| `docs/*` | 문서 수정 (README 등) | `dev`에서 분기 → 작업 → `dev`로 PR |
| `chore/*` | 빌드·설정·의존성·CI 변경 | `dev`에서 분기 → 작업 → `dev`로 PR |
| `hotfix/*` | 배포 중 긴급 버그 수정 | **`main`에서 분기** → `main`으로 PR → 머지 후 `dev`에도 반영 |

브랜치 이름 예시: `feat/pet-upload`, `fix/match-rerank`, `refactor/pet-service`, `docs/readme-api`, `chore/gradle-update`, `hotfix/login-500`

커밋 메시지 권장 형식: `type(scope): 설명`
(`feat`, `fix`, `refactor`, `docs`, `test`, `chore`, `ci` 등)
