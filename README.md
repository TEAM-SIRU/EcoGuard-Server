# EcoGuard Server

환경지킴이(EcoGuard)의 서버 애플리케이션입니다.

학생이 학교 내 청소 활동에 참여하고 활동을 인증할 수 있으며, 교사가 청소 구역과 활동을 관리할 수 있도록 서비스를 제공합니다.

## Project

환경지킴이는 학교 구성원의 환경 보호 활동을 체계적으로 관리하기 위한 서비스입니다.

학생은 청소 활동에 신청하고 배정된 구역에서 활동한 후 사진을 통해 활동을 인증할 수 있습니다. 교사는 청소 구역과 학생 배치를 관리하고 제출된 인증을 확인할 수 있습니다.

## Features

### Authentication

* DataGSM OAuth를 이용한 로그인 / 로그아웃
* JWT 기반 인증 및 인가 (`STUDENT`, `TEACHER`)
* 액세스 토큰 2시간 / 리프레시 토큰 7일 (사용할 때마다 갱신되는 sliding 방식), 리프레시 토큰으로 재발급
* 로그아웃 시 이전에 발급된 모든 기기의 토큰 무효화
* 학생은 앱, 교사는 웹에서 이용

### Recruitment

* 학기별 · 학년/반별 모집 (반별 최대 6명, 선착순)
* 학생은 본인 반 모집만 조회 및 신청 가능
* 신청 즉시 선착순으로 승인(`APPROVED`)되며, 정원이 차면 신청 불가
* 신청 순서(N번째) 및 승인 상태 확인 (응답의 `recruitmentId`로 학기 구분)
* 서버 시간 기준은 서버 OS 시간대와 무관하게 항상 `Asia/Seoul`(KST)

### Cleaning Area

* 학교 도면을 기반으로 미리 나눠진 청소 구역 관리
* 확정된 18개 구역: 1·2학기 기존 구역을 합친 목록 (공통 12 / 1학기 4 / 2학기 2, `application.yaml`의 `cleaning-area-seed`)
* `model-ready: true`인 구역만 활성화 가능 (현재 Gemini 검수 중이라 전 구역 `true`, 자체 AI 서버로 전환하면 모델이 준비된 구역만 `true`)
* 청소 구역 활성화 및 비활성화
* 활성 구역에 학생 배정 (한 구역에 여러 명, 학생당 한 구역)
* 담당 구역과 함께 배정된 학생 확인

### Verification

* 평일 지정 시간(07:20 ~ 08:10)에 배정된 구역의 청소 사진 제출 (당일 1회, 주말과 방학 기간에는 제출 불가, 공휴일은 아직 반영하지 않음)
* 업로드하는 사진은 **JPG, PNG만** 받습니다(AI 검수에 사진 형식을 정확히 알려 주기 위해). 사진 한 장은 10MB까지, 한 번에 보내는 요청 전체는 32MB까지이며, 넘으면 `400 INVALID_IMAGE`로 응답합니다.
* 방학 기간은 NEIS 학사일정의 방학식 다음 날 ~ 개학일 전날로 판단 (조회 결과는 6시간 캐시, NEIS 장애 시에는 제출을 막지 않음)
* 활동 기록의 필요 일수와 미제출 집계에서도 주말과 방학 기간은 제외 (공휴일은 아직 반영하지 않음)
* 사진을 비동기로 검수 (`PROCESSING` / `APPROVED` / `REJECTED` / `MANUAL_REVIEW`)
* 검수 방식은 `AI_REVIEW_PROVIDER`로 선택
  * `gemini`(기본): 자체 AI 모델이 준비되기 전까지 Gemini API로 검수. 통과만 자동 승인하고, 통과하지 못한 인증은 자동 반려하지 않고 교사 수동 검토로 전환 (`AI_FAILED`, AI가 지적한 사유는 `failReasons`로 함께 전달)
  * `ai-server`: 자체 AI 서버로 검수 (AI 모델이 준비되면 전환). 어느 쪽이든 통과로 확실히 판정된 경우만 자동 승인하고, 그 외(불통과, 판정 누락, 통과인데 사유가 있는 모순된 응답)는 자동 반려하지 않고 교사 수동 검토로 보냅니다. `REJECTED`는 교사가 반려할 때만 생깁니다.
* 호출 한도 초과(`RATE_LIMITED`), AI 오류(`AI_ERROR`, `MODEL_NOT_READY`), 응답 지연(`TIMEOUT`), 구역을 알 수 없는 경우(`UNKNOWN_ZONE`), 검수가 10분 넘게 끝나지 않는 경우에도 교사 수동 검토로 전환
* 제출이 한꺼번에 몰려도 AI 호출은 `AI_REVIEW_WORKERS`개씩만 처리하고 나머지는 **크기가 제한된 대기열**에서 차례를 기다립니다. 대기열에는 사진 바이트가 아니라 파일 경로만 넣고, 차례가 오면 파일에서 읽어 보냅니다. 대기열이 가득 차면 `QUEUE_FULL`, 제출 후 `AI_REVIEW_MAX_WAIT_SECONDS`가 지나면 `TIMEOUT`으로 자동 반려 없이 수동 검토로 보냅니다.
* Gemini는 모델을 여러 개 등록할 수 있습니다(`GEMINI_MODELS`). 요청마다 **분당 한도에 여유가 있는 모델**을 우선 쓰고, 429/503/장애가 나면 그 모델을 잠시 제외(429는 60초 또는 `Retry-After`, 그 밖은 10초)한 채 **다음 모델로 다시 시도**합니다. 건당 최대 `max-attempts`(기본 3)번 시도하고, 모든 모델이 막혀 있으면 자리가 날 때까지 기다리다 대기 시간 상한을 넘기면 수동 검토로 보냅니다. 모델 사용량 집계는 서버 메모리에만 있어 서버가 한 대일 때 정확하고 재시작하면 초기화됩니다.
* `ai_reviews` 테이블에는 **AI가 실제로 한 판정**(`decision`, `is_passed`)과 응답 원문(`raw_response`)을 저장합니다. 판정을 해석하지 못한 응답과 Gemini 오류 응답(앞 2000자)도 원문을 남기며 이때 판정은 비어 있습니다. 서버가 수동 검토로 보낸 최종 처리와 이유는 인증의 `status`, `manualReviewReason`에 따로 남습니다.
* 인증 승인 시 봉사시간 10분 적립

### Appeal

* 반려된 인증에 대해 횟수 제한 없이 이의신청 (N차)
* 교사가 승인 / 반려 및 답변 작성, 승인 시 봉사시간 10분 적립
* 학생은 이의신청 내역과 처리 결과 확인

### Activity

* 월별 활동 기록 (날짜, 구역, 인증 결과, 누적 봉사시간)
* 승인 / 반려 / 미제출 횟수 집계
* 이번 주 청소 현황 (N/5일)
* 교사용 학생 검색 및 활동 현황 (출석, 인증 사진, 구역, 봉사시간)

### Notice

* 교사 공지사항 작성 / 수정 / 삭제
* 학생 공지사항 조회 (이전 / 다음 공지 이동)

## Activity Flow

```text
모집 공고
    ↓
학생 신청 (선착순, 즉시 승인)
    ↓
청소 구역 배정
    ↓
청소 활동
    ↓
사진 인증 (07:20 ~ 08:10)
    ↓
AI 검수
    ↓
승인 → 봉사시간 +10분
    │
    ↓ 반려
이의신청 → 교사 검토 → 승인 시 +10분
```

AI 검수를 통해 인증하기 어려운 경우 교사가 직접 확인할 수 있습니다. 서버 재시작이나 예외로 검수가 끝나지 못한 인증은 5분마다 점검해 수동 검토로 넘깁니다.

## API

모든 API는 `/api/v1` 하위에 있으며, 로그인을 제외하고 `Authorization: Bearer {accessToken}` 헤더가 필요합니다.

### Auth

| Method | Endpoint       | Role | Description |
| ------ | -------------- | ---- | ----------- |
| GET    | `/auth/callback` | -  | DataGSM redirect 수신 → `ecoguard://auth/callback`으로 302 (code/state/error/error_description 그대로 전달) |
| POST   | `/auth/login`  | -    | DataGSM 인가 코드로 로그인 |
| POST   | `/auth/refresh` | -    | 리프레시 토큰으로 토큰 재발급 (사용할 때마다 갱신되어 로그인 유지) |
| POST   | `/auth/logout` | 공통   | 로그아웃 (이전에 발급된 모든 기기의 토큰 무효화) |
| GET    | `/users/me`    | 공통   | 내 정보 (이름, 역할, 학번, 학년, 반) |
| DELETE | `/users/me`    | STUDENT | 회원 탈퇴. 개인정보 익명화 + 모든 토큰 무효화. 인증/이의신청/봉사 시간 기록은 남기고 청소 구역 배정·모집 신청은 삭제. 같은 dataGSM 계정으로 다시 로그인하면 새 계정으로 가입됨 |

### Recruitment

| Method | Endpoint                                  | Role    | Description |
| ------ | ----------------------------------------- | ------- | ----------- |
| POST   | `/recruitments`                           | TEACHER | 모집 공고 등록 (`semester`는 `"2026-2"`처럼 `연도-학기`, 활동 시간 `activityStartTime`/`activityEndTime`은 `"07:20"` 형식이며 생략하면 07:20~08:10) |
| PATCH  | `/recruitments/{recruitmentId}`           | TEACHER | 모집 기간 / 인원 수정 |
| GET    | `/recruitments`                           | TEACHER | 학년/반별 전체 모집 현황 |
| GET    | `/recruitments/current`                   | STUDENT | 내 반 모집 공고 조회 |
| POST   | `/recruitments/{recruitmentId}/applications` | STUDENT | 환경지킴이 신청 |
| GET    | `/recruitments/{recruitmentId}/applications` | TEACHER | 신청자 목록 (신청 순서대로) |
| GET    | `/applications/me`                        | STUDENT | 내 신청 결과 및 신청 순서 (`recruitmentId`로 어느 모집(학기)의 신청인지 구분) |

### Cleaning Area

| Method | Endpoint                               | Role    | Description |
| ------ | -------------------------------------- | ------- | ----------- |
| GET    | `/cleaning-areas`                      | 공통      | 학교 도면 구역 목록 (활성 여부, 배정 학생) |
| PATCH  | `/cleaning-areas/{areaId}`             | TEACHER | 구역 활성 / 비활성 |
| POST   | `/cleaning-areas/{areaId}/assignments` | TEACHER | 구역에 학생 배정 |
| GET    | `/assignments/me`                      | STUDENT | 내 청소 구역 및 함께 배정된 학생 (`zoneCode`로 앱 도면에서 내 구역 칸을 찾아 표시) |

### Verification & AI Review

| Method | Endpoint                              | Role            | Description |
| ------ | ------------------------------------- | --------------- | ----------- |
| POST   | `/verifications`                      | STUDENT         | 청소 인증 사진 제출 (`multipart/form-data`, `photo`). 재전송 대비 헤더 `Idempotency-Key`(64자 이하), `X-Submit-Started-At`(ISO-8601) 선택 |
| GET    | `/verifications/today`                | STUDENT         | 오늘 인증 정보 (배정 구역, 인증 가능 시간, 서버 시각, 제출 여부·시각, 불가 사유) |
| GET    | `/verifications/me`                   | STUDENT         | 내 인증 내역 |
| GET    | `/verifications/{verificationId}/review` | STUDENT, TEACHER | 검수 상태 및 결과 (학생은 본인 것만) |
| GET    | `/verifications`                      | TEACHER         | 수동 검토 대기 목록 |
| PATCH  | `/verifications/{verificationId}/review` | TEACHER         | 수동 검토 승인 / 반려 |

### Appeal

| Method | Endpoint                                  | Role    | Description |
| ------ | ----------------------------------------- | ------- | ----------- |
| POST   | `/verifications/{verificationId}/appeals` | STUDENT | 반려된 인증에 이의신청 (JSON `content`, 또는 `multipart/form-data`의 `content` + `photos` 최대 3장) |
| GET    | `/appeals/me`                             | STUDENT | 내 이의신청 내역 (N차, 상태, 교사 답변) |
| GET    | `/appeals?status=`                        | TEACHER | 이의신청 목록 |
| PATCH  | `/appeals/{appealId}`                     | TEACHER | 이의신청 승인 / 반려 및 답변 (`replyTitle` 제목, `reply` 본문) |

### Activity

| Method | Endpoint                                   | Role    | Description |
| ------ | ------------------------------------------ | ------- | ----------- |
| GET    | `/service-times/me?year=&month=`           | STUDENT | 월별 활동 기록 및 집계 |
| GET    | `/service-times/me/weekly`                 | STUDENT | 이번 주 청소 현황 (N/5일) |
| GET    | `/students/activities?keyword=`            | TEACHER | 학생 검색 및 활동 현황 |
| GET    | `/students/{studentId}/activities?year=&month=` | TEACHER | 학생별 월별 활동 기록 |

### Notice

| Method | Endpoint              | Role    | Description |
| ------ | --------------------- | ------- | ----------- |
| GET    | `/notices`            | 공통      | 공지 목록 (최신순, 본문 미리보기와 내 읽음 여부 `isRead` 포함) |
| GET    | `/notices/{noticeId}` | 공통      | 공지 상세 (이전 / 다음 공지 ID 포함, 열면 읽음으로 기록) |
| POST   | `/notices`            | TEACHER | 공지 작성 |
| PATCH  | `/notices/{noticeId}` | TEACHER | 공지 수정 |
| DELETE | `/notices/{noticeId}` | TEACHER | 공지 삭제 |

## Tech Stack

| Category       | Technology         |
| -------------- | ------------------ |
| Language       | Kotlin 2.3.21      |
| Framework      | Spring Boot 4.1.1  |
| JDK            | Java 21            |
| ORM            | Spring Data JPA    |
| Security       | Spring Security    |
| Authentication | JWT, DataGSM OAuth |
| Database       | MySQL              |
| Test Database  | H2                 |
| Build Tool     | Gradle             |

## Architecture

```text
src/main/kotlin/team/siru/ecoguard
├── auth
├── user
├── recruitment
├── cleaningarea
├── verification
├── aireview
├── appeal
├── activity
├── notice
└── common
```

도메인을 기준으로 패키지를 분리하여 각 기능의 책임을 관리합니다.

| Package        | Description |
| -------------- | ----------- |
| `auth`         | DataGSM OAuth 로그인 및 JWT 발급 |
| `user`         | 사용자 관리 |
| `recruitment`  | 환경지킴이 모집 및 신청 |
| `cleaningarea` | 청소 구역 및 학생 배정 |
| `verification` | 청소 인증 사진 제출 |
| `aireview`     | Gemini / 자체 AI 서버 연동 검수, 교사 수동 검토, 지연된 검수 복구 |
| `appeal`       | 이의신청 |
| `activity`     | 봉사시간 적립 및 활동 기록 |
| `notice`       | 공지사항 |
| `common`       | 보안, 예외 처리, 파일 저장 등 공통 설정 |

## Authentication Flow

DataGSM OAuth를 이용하여 사용자를 인증합니다.

```text
Client
  ↓
DataGSM OAuth
  ↓
Authorization Code
  ↓
EcoGuard Server
  ↓
사용자 정보 조회
  ↓
JWT 발급
  ↓
API 요청
```

발급된 액세스 토큰을 이용하여 인증이 필요한 API에 접근합니다. 액세스 토큰이 만료되면 `/auth/refresh`로 재발급하고, 로그아웃하면 이전에 발급된 토큰이 모두 무효화됩니다.

리프레시 토큰은 7일 유효하며 사용할 때마다 새로 발급됩니다(sliding). 서버는 세션을 저장하지 않으므로 리프레시할 때 이전 리프레시 토큰이 폐기되지는 않고, 로그아웃 시 해당 사용자의 모든 토큰이 한꺼번에 무효화됩니다(기기 단위 차단 불가).

### 401 응답 규칙

앱은 응답 바디 유무로 401의 종류를 구분하므로 아래 규칙을 API 계약으로 고정합니다.

| 상황 | 상태 | 응답 바디 |
| ---- | ---- | --------- |
| 로그인 실패 (`/auth/login`, `OAUTH_FAILED`) | 401 | 있음 (`{"code": "OAUTH_FAILED", "message": "..."}`) |
| 액세스 토큰 없음 / 만료 / 무효 | 401 | 없음 |
| 리프레시 토큰 만료 / 무효 / 로그아웃으로 폐기됨 (`/auth/refresh`) | 401 | 없음 |

401로 응답하는 에러 코드는 `UNAUTHORIZED`(바디 없음)와 `OAUTH_FAILED`(바디 있음) 두 가지뿐이며, 새 401용 에러 코드를 추가하면 이 계약이 깨집니다.

앱 스토어 심사용으로 고정된 데모 학생 계정을 켤 수 있습니다 (아래 환경 변수 참고).

## Requirements

* JDK 21
* MySQL
* Git
* Gemini API 키 (선택) — 없으면 인증 사진이 모두 교사 수동 검토로 넘어갑니다
* NEIS Open API 키 (선택) — 학사일정으로 방학 기간 인증을 막습니다. 없으면 방학 제한을 적용하지 않습니다
* 자체 AI 검수 서버 (선택) — `AI_REVIEW_PROVIDER=ai-server`일 때만 필요합니다

## Getting Started

### 1. Clone

```bash
git clone https://github.com/TEAM-SIRU/EcoGuard-Server.git
cd EcoGuard-Server
```

### 2. Environment Variables

실행에 필요한 환경 변수를 설정합니다.

```properties
DB_URL=jdbc:mysql://localhost:3306/ecoguard   # 선택 (기본값 있음)
DB_USERNAME=YOUR_USERNAME                      # 필수
DB_PASSWORD=YOUR_PASSWORD                      # 필수
JWT_SECRET=YOUR_JWT_SECRET                     # 필수, 32바이트 이상

JWT_ACCESS_VALIDITY=7200                       # 선택, 액세스 토큰 유효 시간(초)
JWT_REFRESH_VALIDITY=604800                    # 선택, 리프레시 토큰 유효 시간(초, 기본 7일)

GSM_OAUTH_MOCK=false                           # 기본 false. 로컬 개발에서만 true
GSM_OAUTH_CLIENT_ID=                           # mock=false일 때 필수 (datagsm.kr/clients에서 발급)
GSM_OAUTH_CLIENT_SECRET=                       # mock=false일 때 필수
GSM_OAUTH_REDIRECT_URI=                        # mock=false일 때 필수. 서버 callback 주소(https://<서버>/api/v1/auth/callback). dataGSM 등록값·앱 인가 요청값과 정확히 일치해야 함

AI_REVIEW_PROVIDER=gemini                      # 선택, gemini(기본) 또는 ai-server
GEMINI_API_KEY=                                # gemini일 때 필요. 비어 있으면 모두 수동 검토
GEMINI_MODEL=gemini-2.5-flash-lite             # 선택, AI Studio에서 쓸 수 있는 모델과 한도 확인
GEMINI_MODELS=                               # 선택, 우선순위 순서의 모델 목록(쉼표 구분, `이름` 또는 `이름:분당요청한도`). 예) gemini-2.5-flash-lite:15,gemini-2.5-flash:10. 비우면 GEMINI_MODEL 하나만 사용
GEMINI_RPM_LIMIT=0                             # 선택, 목록에서 한도를 안 쓴 모델의 분당 요청 한도. 0이면 한도 없이 429 응답으로만 조절
AI_REVIEW_WORKERS=2                            # 선택, 동시에 AI 검수를 처리하는 작업 수
AI_REVIEW_QUEUE_CAPACITY=200                   # 선택, 대기열 최대 건수. 넘으면 수동 검토(QUEUE_FULL)
AI_REVIEW_MAX_WAIT_SECONDS=300                 # 선택, 제출 후 이 시간(초) 안에 AI 응답이 없으면 수동 검토(TIMEOUT)
NEIS_API_KEY=                                  # 선택, open.neis.go.kr에서 발급. 비어 있으면 방학 제한 없음
NEIS_EDUCATION_OFFICE_CODE=                    # NEIS 키를 쓸 때 필수, 시도교육청 코드 (예: F10)
NEIS_SCHOOL_CODE=                              # NEIS 키를 쓸 때 필수, 표준학교코드
AI_SERVER_BASE_URL=http://localhost:9000       # ai-server일 때만 사용, 자체 AI 검수 서버 주소
FILE_STORAGE_PATH=uploads                      # 선택, 인증 사진 저장 경로 (/files 로 공개)
SERVER_PORT=8080                               # 선택
CORS_ALLOWED_ORIGINS=https://your-web-domain   # 쉼표로 구분, 기본은 localhost
DDL_AUTO=validate                              # 기본 validate. 빈 DB 최초 1회만 update

# 앱 스토어 심사용 데모 학생 계정 (심사 기간에만 켠다)
DEMO_ACCOUNT_ENABLED=false                     # 기본 false
DEMO_ACCOUNT_AUTH_CODE=                        # 켤 때 필수, 16자 이상의 추측 불가능한 값
```

`GSM_OAUTH_MOCK=true`는 인가 코드 문자열만으로 교사 계정 포함 누구로든 로그인되므로 운영에서 절대 켜지 않습니다.

심사용 데모 계정을 켜면 `DEMO_ACCOUNT_AUTH_CODE` 값을 `authCode`로 로그인했을 때 고정된 데모 학생 계정(역할 `STUDENT`)으로 들어옵니다. 교사 권한은 부여되지 않으며, 심사가 끝나면 `DEMO_ACCOUNT_ENABLED=false`로 되돌립니다.

실제 인증 정보 및 비밀키는 저장소에 포함하지 않습니다.

#### 기존 DB 마이그레이션 (청소구역 좌표 제거)

청소구역의 `x`, `y` 좌표를 제거하고 앱이 `zoneCode`로 도면 칸을 찾도록 바꿨습니다. 마이그레이션 도구가 없고 운영은 `DDL_AUTO=validate`라서 컬럼이 자동으로 지워지지 않습니다. `x`, `y`가 `NOT NULL`(기본값 없음)이면 새 구역 INSERT가 실패하므로, 이 버전을 배포하기 **전에** 기존 DB에서 한 번 실행합니다.

```sql
ALTER TABLE cleaning_areas DROP COLUMN x, DROP COLUMN y;
```

(빈 DB를 `DDL_AUTO=update`로 새로 만들 때는 필요 없습니다.) 응답에서 `coordinates`(`GET /cleaning-areas`), `mapCoordinates`(`GET /assignments/me`)가 사라졌으므로 앱도 `zoneCode` 기준으로 맞춰야 합니다.

#### 기존 DB 마이그레이션 (이의신청 적립 시간)

`GET /appeals/me`의 `awardedMinutes`가 승인 여부가 아니라 **실제로 적립된 시간**을 보여 주도록 `appeals.awarded_minutes` 컬럼을 추가했습니다. 이미 승인된 인증에 이의신청이 승인되면 적립이 없으므로 `null`입니다. 운영은 `DDL_AUTO=validate`라서 이 버전을 배포하기 **전에** 기존 DB에서 한 번 실행합니다.

```sql
ALTER TABLE appeals ADD COLUMN awarded_minutes INT NULL;
UPDATE appeals SET awarded_minutes = 10 WHERE status = 'APPROVED';
```

(`UPDATE`는 기존 승인 건을 예전 동작대로 10분으로 채우는 용도입니다. 이미 승인된 인증이었던 과거 건은 구분할 수 없습니다.)

### 3. Build

Windows:

```bash
.\gradlew.bat build
```

macOS / Linux:

```bash
./gradlew build
```

### 4. Run

```bash
./gradlew bootRun
```

Windows:

```bash
.\gradlew.bat bootRun
```

## Test

```bash
./gradlew test
```

Windows:

```bash
.\gradlew.bat test
```

테스트 환경에서는 H2를 사용합니다.

## CI/CD

GitHub Actions로 테스트와 배포를 자동화합니다 (`.github/workflows`).

| 워크플로 | 시점 | 동작 |
| -------- | ---- | ---- |
| `ci.yml` | `main` 대상 PR | `./gradlew test` |
| `deploy.yml` | `main`에 머지(push), 수동 실행 가능 | 테스트/빌드 → SSH로 `/opt/ecoguard/app.jar.new` 업로드 → 기존 jar를 `app.jar.bak`으로 백업하고 교체 → `systemctl restart ecoguard` → `/auth/callback` 302 헬스체크(최대 3분). 실패하면 `app.jar.bak`으로 자동 롤백 |

**엔티티(컬럼/테이블)를 바꾸는 PR은 머지하기 전에 운영 DB에 먼저 ALTER를 적용합니다.** 운영은 `DDL_AUTO=validate`라서 컬럼이 없으면 새 버전이 기동하지 못합니다. 이 경우 배포는 실패로 끝나고 이전 버전으로 자동 롤백되지만, DB를 고친 뒤 Actions에서 해당 배포를 다시 실행(Re-run)해야 새 버전이 반영됩니다.

배포에 필요한 설정은 다음과 같습니다.

* GitHub Secrets: `SSH_HOST`, `SSH_PORT`, `SSH_USER`, `SSH_PRIVATE_KEY` (배포 전용 키), `SSH_KNOWN_HOSTS` (서버 호스트 키 고정)
  * `SSH_KNOWN_HOSTS`는 서버에서 호스트 키를 확인해 만듭니다. 포트가 22가 아니면 `[호스트]:포트` 형식이어야 하며, 로컬에서 `ssh-keyscan -p <포트> <호스트>` 로 받은 결과를 서버의 `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub` 지문과 비교한 뒤 그대로 등록합니다.
  * 이 시크릿이 없으면 배포는 되지만, 접속할 때 받은 호스트 키를 그대로 신뢰한다는 경고가 나옵니다.
* 서버: 배포 전용 공개키를 `~/.ssh/authorized_keys`에 등록하고, `sudo systemctl restart ecoguard`를 비밀번호 없이 실행하도록 sudoers에 허용
* 서버 환경변수 파일(`ecoguard.env`)은 배포가 건드리지 않으므로 새 환경변수가 생기면 서버에서 직접 추가한 뒤 배포합니다.

## Team

TEAM SIRU
