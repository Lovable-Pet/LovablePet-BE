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

```jsonc
// 성공
{ "success": true, "data": { ... } }

// 실패
{
  "success": false,
  "error": {
    "code": "COMMON_400",
    "message": "잘못된 입력값입니다.",
    "fieldErrors": [ { "field": "name", "rejectedValue": "", "reason": "공백일 수 없습니다" } ]
  }
}
```

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

---

## 배포 (AWS EC2 + GitHub Actions)

Python 파이프라인(LovablePet-AI-AR)과 같은 EC2에 Docker로 올립니다.

- `.github/workflows/deploy.yml`: `dev`·`main`으로의 PR과 push마다 이미지를 빌드하고 prod 프로필로 뜨는지(`/actuator/health`) 확인합니다.
  `main`에 머지되면 이미지를 GHCR(`ghcr.io/lovable-pet/lovablepet-be`, 태그 sha·latest)에 올리고 EC2에서 새 이미지로 교체합니다.
- 이미지는 Actions에서 빌드합니다. EC2는 메모리를 파이프라인과 나눠 쓰므로 서버에서 빌드하지 않습니다.

서버 폴더 `~/lovablepet-spring/` (파이프라인의 `~/lovablepet-ai/`와 분리):

| 경로 | 내용 |
|---|---|
| `docker-compose.yml` | 배포가 `deploy/docker-compose.yml`을 복사 |
| `.env` | 비밀값. 서버에서만 만들고 커밋하지 않음 (변수 목록: `deploy/.env.example`) |
| `data/postgres/` | DB 데이터 |
| `data/storage/` | 업로드 파일 + `models/`(콜백으로 받은 GLB). 소유자 uid 10001 |

| 컨테이너 | 내용 |
|---|---|
| `spring` | 8080 공개. 네트워크 `lovablepet`(파이프라인과 공유)·`db`. 메모리 상한 1.5 GB (힙은 그 70%) |
| `lovablepet-postgres` | `pgvector/pgvector:pg16`. 내부 네트워크 `db`에만 붙고 포트를 열지 않음. 메모리 상한 1 GB |

파이프라인 연동: Spring은 `http://pipeline:8000`을 부르고, 파이프라인은 `http://spring:8080/internal/pet-models/callback`으로 결과를 보냅니다.
같은 docker 네트워크라 공개 주소가 필요 없습니다. `PET_PIPELINE_API_KEY`는 `~/lovablepet-ai/.env`의 `PIPELINE_API_KEY`와 같은 값이고,
파이프라인이 없어도 Spring은 정상으로 뜹니다.

서버 준비 (한 번):

1. swap 2 GB (메모리가 넘칠 때 프로세스가 강제 종료되는 대신 느려지도록):
   ```
   sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
   echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
   echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-swappiness.conf && sudo sysctl -p /etc/sysctl.d/99-swappiness.conf
   ```
2. 폴더와 `.env`:
   ```
   mkdir -p ~/lovablepet-spring/data/postgres
   sudo install -d -o 10001 -g 10001 -m 750 ~/lovablepet-spring/data/storage
   # deploy/.env.example 의 변수를 ~/lovablepet-spring/.env 에 채운다 (chmod 600)
   # 랜덤 값은 openssl rand -hex 32, JWT_SECRET 만 openssl rand -base64 32
   ```
3. Actions용 SSH 키를 따로 만들어 공개키를 서버 `~/.ssh/authorized_keys`에 추가하고, 저장소 Actions secrets에 등록:
   `SSH_HOST`(Elastic IP), `SSH_USER`(`ubuntu`), `SSH_KEY`(개인키 전체).

확인과 로그 (서버에서):

```
curl -fsS localhost:8080/actuator/health
docker logs -f spring
```

`.env`를 고친 뒤(예: 카카오 키 추가) 반영 (서버에서):

```
cd ~/lovablepet-spring && docker compose up -d
```

`DB_PASSWORD`는 DB를 처음 띄울 때만 적용됩니다. 나중에 바꾸려면 DB 안에서도 `ALTER USER`로 바꿔야 합니다.
