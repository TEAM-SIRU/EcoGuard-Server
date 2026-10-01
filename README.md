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
* 학생은 앱, 교사는 웹에서 이용

### Recruitment

* 학기별 · 학년/반별 모집 (반별 최대 6명, 선착순)
* 학생은 본인 반 모집만 조회 및 신청 가능
* 신청 순서(N번째) 및 신청 결과(`PENDING` / `APPROVED` / `REJECTED`) 확인
* 교사가 신청 순서 기준으로 모집 인원 확정

### Cleaning Area

* 학교 도면을 기반으로 미리 나눠진 청소 구역 관리
* 1차 운영 범위: 1·2학기 기존 구역을 합친 18개 구역 (공통 12 / 1학기 4 / 2학기 2, `application.yaml`의 `cleaning-area-seed`)
* AI 모델이 준비된 구역(`model-ready: true`)만 활성화 가능
* 청소 구역 활성화 및 비활성화
* 활성 구역에 학생 배정 (한 구역에 여러 명, 학생당 한 구역)
* 담당 구역과 함께 배정된 학생 확인

### Verification

* 지정 시간(08:00 ~ 08:10)에 배정된 구역의 청소 사진 제출 (당일 1회)
* AI 기반 사진 검수 (`PROCESSING` / `APPROVED` / `REJECTED` / `MANUAL_REVIEW`)
* AI 검수 실패 시 교사 수동 검토
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
학생 신청 (선착순)
    ↓
교사 모집 인원 확정
    ↓
청소 구역 배정
    ↓
청소 활동
    ↓
사진 인증 (08:00 ~ 08:10)
    ↓
AI 검수
    ↓
승인 → 봉사시간 +10분
    │
    ↓ 반려
이의신청 → 교사 검토 → 승인 시 +10분
```

AI 검수를 통해 인증하기 어려운 경우 교사가 직접 확인할 수 있습니다.

## API

모든 API는 `/api/v1` 하위에 있으며, 로그인을 제외하고 `Authorization: Bearer {accessToken}` 헤더가 필요합니다.

### Auth

| Method | Endpoint       | Role | Description |
| ------ | -------------- | ---- | ----------- |
| POST   | `/auth/login`  | -    | DataGSM 인가 코드로 로그인 |
| POST   | `/auth/logout` | 공통   | 로그아웃 |

### Recruitment

| Method | Endpoint                                  | Role    | Description |
| ------ | ----------------------------------------- | ------- | ----------- |
| POST   | `/recruitments`                           | TEACHER | 모집 공고 등록 |
| PATCH  | `/recruitments/{recruitmentId}`           | TEACHER | 모집 기간 / 인원 수정 |
| GET    | `/recruitments`                           | TEACHER | 학년/반별 전체 모집 현황 |
| GET    | `/recruitments/current`                   | STUDENT | 내 반 모집 공고 조회 |
| POST   | `/recruitments/{recruitmentId}/applications` | STUDENT | 환경지킴이 신청 |
| GET    | `/recruitments/{recruitmentId}/applications` | TEACHER | 신청자 목록 (신청 순서대로) |
| POST   | `/recruitments/{recruitmentId}/confirm`   | TEACHER | 선착순으로 모집 인원 확정 |
| GET    | `/applications/me`                        | STUDENT | 내 신청 결과 및 신청 순서 |

### Cleaning Area

| Method | Endpoint                               | Role    | Description |
| ------ | -------------------------------------- | ------- | ----------- |
| GET    | `/cleaning-areas`                      | 공통      | 학교 도면 구역 목록 (활성 여부, 배정 학생) |
| PATCH  | `/cleaning-areas/{areaId}`             | TEACHER | 구역 활성 / 비활성 |
| POST   | `/cleaning-areas/{areaId}/assignments` | TEACHER | 구역에 학생 배정 |
| GET    | `/assignments/me`                      | STUDENT | 내 청소 구역 및 함께 배정된 학생 |

### Verification & AI Review

| Method | Endpoint                              | Role            | Description |
| ------ | ------------------------------------- | --------------- | ----------- |
| POST   | `/verifications`                      | STUDENT         | 청소 인증 사진 제출 (`multipart/form-data`, `photo`) |
| GET    | `/verifications/me`                   | STUDENT         | 내 인증 내역 |
| GET    | `/verifications/{verificationId}/review` | STUDENT, TEACHER | 검수 상태 및 결과 (학생은 본인 것만) |
| GET    | `/verifications`                      | TEACHER         | 수동 검토 대기 목록 |
| PATCH  | `/verifications/{verificationId}/review` | TEACHER         | 수동 검토 승인 / 반려 |

### Appeal

| Method | Endpoint                                  | Role    | Description |
| ------ | ----------------------------------------- | ------- | ----------- |
| POST   | `/verifications/{verificationId}/appeals` | STUDENT | 반려된 인증에 이의신청 |
| GET    | `/appeals/me`                             | STUDENT | 내 이의신청 내역 (N차, 상태, 교사 답변) |
| GET    | `/appeals?status=`                        | TEACHER | 이의신청 목록 |
| PATCH  | `/appeals/{appealId}`                     | TEACHER | 이의신청 승인 / 반려 및 답변 |

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
| GET    | `/notices`            | 공통      | 공지 목록 (최신순) |
| GET    | `/notices/{noticeId}` | 공통      | 공지 상세 (이전 / 다음 공지 ID 포함) |
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
| `aireview`     | AI 검수 및 교사 수동 검토 |
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

발급된 JWT를 이용하여 인증이 필요한 API에 접근합니다.

## Requirements

* JDK 21
* MySQL
* Git

## Getting Started

### 1. Clone

```bash
git clone https://github.com/TEAM-SIRU/EcoGuard-Server.git
cd EcoGuard-Server
```

### 2. Environment Variables

실행에 필요한 환경 변수를 설정합니다.

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/ecoguard
spring.datasource.username=YOUR_USERNAME
spring.datasource.password=YOUR_PASSWORD

jwt.secret=YOUR_JWT_SECRET
```

실제 인증 정보 및 비밀키는 저장소에 포함하지 않습니다.

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

## Development

### Build

```bash
./gradlew build
```

### Test

```bash
./gradlew test
```

### Run

```bash
./gradlew bootRun
```

## Team

TEAM SIRU
