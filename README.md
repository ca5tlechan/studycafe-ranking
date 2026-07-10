# 스터디카페 랭킹 앱 (StudyCafeRanking)

스터디카페 이용 시간을 기록하고 개인·학교별 공부 시간 랭킹을 보여주는 PWA.
전체 기획/도메인 규칙은 **[CLAUDE.md](./CLAUDE.md)** (단일 기준 문서) 참조.

## 구조 (모노레포)

```
StudyCafeRanking/
├─ backend/    Spring Boot 4.1 (Java 21) + JPA + Security + Validation, REST API
├─ frontend/   Vite + React 19 + TypeScript + PWA
├─ docker-compose.yml   로컬 PostgreSQL 17
├─ CLAUDE.md   기획/도메인 명세 (source of truth)
└─ .env.example
```

## 사전 요구사항

- JDK 21 (Temurin 등) — 백엔드 빌드/실행
- Node 20+ — 프론트
- Docker — 로컬 Postgres

## 로컬 실행

### 1) DB (Postgres)
```bash
docker compose up -d db
```

### 2) 백엔드 (:8080)
```bash
cd backend
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ./gradlew bootRun
```
> 시스템 기본 JDK가 25라면 위처럼 `JAVA_HOME`을 21로 지정해 실행한다.

### 3) 프론트 (:5173)
```bash
cd frontend
npm install   # 최초 1회
npm run dev
```
개발 중 프론트의 `/api` 요청은 `vite.config.ts` 프록시로 백엔드(:8080)에 전달된다.

## 시크릿 관리

- DB 접속정보·JWT 시크릿 등은 **환경변수** 또는 `.env`(gitignore됨)로 주입한다.
- 커밋되는 `application.yml` 에는 리터럴 시크릿을 넣지 않는다(`${...}` 플레이스홀더만).

## 진행 상황

- [x] **Phase 0** — 프로젝트 셋업 (백엔드/프론트 스캐폴딩, Postgres compose, 빌드 초록불)
- [ ] Phase 3(선) — 04:00 분할 + 하루/주 캡 순수 로직 + 단위 테스트
- [ ] Phase 1 — 인증(회원가입/로그인/JWT)
- [ ] Phase 2 — 체크인/체크아웃 원본 기록
- [ ] Phase 3 — 집계 로직 연결(daily_study_records)
- [ ] Phase 4~ — 통계/랭킹 API, 프론트, 마감·어뷰징 방지 (CLAUDE.md §7)
