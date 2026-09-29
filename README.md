# EcoGuard Server

환경지킴이(EcoGuard)의 서버 애플리케이션입니다.

학생이 학교 내 청소 활동에 참여하고 활동을 인증할 수 있으며, 교사가 청소 구역과 활동을 관리할 수 있도록 서비스를 제공합니다.

## Project

환경지킴이는 학교 구성원의 환경 보호 활동을 체계적으로 관리하기 위한 서비스입니다.

학생은 청소 활동에 신청하고 배정된 구역에서 활동한 후 사진을 통해 활동을 인증할 수 있습니다. 교사는 청소 구역과 학생 배치를 관리하고 제출된 인증을 확인할 수 있습니다.

## Features

### Authentication

* DataGSM OAuth를 이용한 사용자 인증
* JWT 기반 인증 및 인가
* 학생 및 교사 사용자 관리

### Activity

* 청소 활동 모집
* 청소 활동 신청
* 청소 구역 배정
* 활동 인증
* 활동 기록 조회

### Cleaning Zone

* 학교 도면을 기반으로 한 청소 구역 관리
* 청소 구역 활성화 및 비활성화
* 구역별 학생 배치 인원 설정
* 하나의 구역에 여러 학생 배치

### Verification

* 청소 활동 사진 제출
* AI 기반 활동 인증
* 인증 실패 시 교사 수동 검토
* 인증 결과에 대한 이의 신청

### Notice

* 교사 공지사항 관리
* 학생 공지사항 조회

## Activity Flow

```text
활동 모집
    ↓
학생 신청
    ↓
청소 구역 배정
    ↓
청소 활동
    ↓
사진 인증
    ↓
AI 검토
    ↓
인증 완료
```

AI 검토를 통해 인증하기 어려운 경우 교사가 직접 확인할 수 있습니다.

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
src/main/kotlin
└── ...
    ├── auth
    ├── user
    ├── activity
    └── application
```

도메인을 기준으로 패키지를 분리하여 각 기능의 책임을 관리합니다.

| Package       | Description |
| ------------- | ----------- |
| `auth`        | 인증 및 인가     |
| `user`        | 사용자 관리      |
| `activity`    | 청소 활동 및 인증  |
| `application` | 활동 신청 및 배정  |

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

## License

This project is developed for educational purposes.
