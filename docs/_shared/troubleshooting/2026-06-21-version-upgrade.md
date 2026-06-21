---
title: Spring Boot 3.3.5 → 3.5.9 버전업
domain: _shared
type: troubleshooting
status: resolved
author: 이정헌
created: 2026-06-21
updated: 2026-06-21
related:
  - ../../ai-chatbot/2026-06-21-proposal.md
---

# 트러블슈팅: Spring Boot 3.3.5 → 3.5.9 버전업

Spring AI 1.1.x(= Boot 3.4+ 요구) 도입을 위해 버전업하며 발생한 이슈.

## 1. mybatis-spring-boot-starter 호환
- 변경: `3.0.4 → 3.0.5` (3.0.4는 Boot 3.3 타깃). `spring-ai-project_lab`이 Boot 3.5.9 + mybatis 3.0.5로 검증된 조합.

## 2. JwtTokenService 잠복 버그 — full-context 기동 실패
- 증상: `java -jar` 기동 시 `UnsatisfiedDependencyException ... JwtTokenService ... No default constructor found`.
- 원인: `JwtTokenService`에 public 생성자가 **2개**(`@Value` DI용 + `Clock` 주입 테스트용)인데 **둘 다 `@Autowired` 없음**. Spring은 생성자를 못 골라 기본 생성자를 찾다 실패. (Spring 6.x 규칙: 생성자 다수면 하나에 `@Autowired` 필수)
- 왜 그동안 안 드러났나: 단위테스트는 생성자를 직접 호출하고, **full ApplicationContext를 띄우는 테스트가 없음**(`@MybatisTest` 슬라이스만 존재) → `mvnw test`는 통과하지만 실제 부팅에서만 실패.
- 해결: DI 생성자(`@Value`)에 `@Autowired` 추가. 코드베이스 관례와 일치(`HouseService`, `PublicDataAptTradeClient`는 이미 적용).
- 교훈: 버전업 영향 점검 시 **단위테스트 통과 ≠ 부팅 정상**. full-context 부팅 검증을 별도로 할 것.

## 3. `mvnw clean test` 연속 실행 플레이크 (Windows)
- 증상: `clean test`를 연달아 돌리면 `NoClassDefFoundError`/`ClassNotFoundException`(예: `PublicDataApiKeyProvider`) 다수 발생.
- 원인: 직전 실행의 JVM이 `target`을 잠가 `clean`이 부분적으로만 수행 → 일부 클래스 누락.
- 해결: 단독으로 한 번만 실행하면 정상(49건 전부 통과). 동일 모듈에서 빌드를 겹쳐 돌리지 말 것.

## 버전업 영향 점검 요약 (코드 실측)
- 안전: 컨트롤러/서비스 테스트(순수 Mockito + `standaloneSetup`, `@MockBean` 미사용), 웹/HTTP API, 커스텀 JWT(JDK crypto), 설정 키, Java 17 유지.
- 필수 변경: parent 버전, mybatis 3.0.5, 위 `@Autowired` 1건.
- 카나리아: `MemberMapperTest`(`@MybatisTest`+H2) 통과로 MyBatis 호환 확인.
