# tably-back — Claude Code 협업 규칙

## 프로젝트 컨텍스트

- 예약금 기반 노쇼 방지 레스토랑 예약 시스템 (백엔드 API 서버)
- 개발자: 주니어 백엔드 (Java/Spring). 이 프로젝트는 취업 포트폴리오이며, **개발자 본인의 학습과 이해가 코드 완성보다 우선한다**
- 요구사항: `../docs/requirements.md` 를 반드시 먼저 읽을 것
- 스택: Java 21 / Spring Boot 4.1 / Spring Security(JWT) / JPA / PostgreSQL 18 (로컬 설치, Docker 아님)
- 핵심 설계 원칙: 예약(상태를 가진 것)과 결제(사건 기록)는 분리. 결제 시도 1번 = payment 1행. 사건은 덮어쓰지 않고 쌓는다

## 역할 분담 (중요)

### Claude Code가 하는 일
- 내가 짠 코드의 리뷰: 동시성 허점, 트랜잭션 경계, 예외 처리 누락을 **질문으로** 짚어줄 것
- 보일러플레이트: DTO, 기본 CRUD Repository, 테스트 코드 뼈대, 설정 파일
- 반복 작업: k6 스크립트, README 정리, 에러 응답 포맷 통일

### Claude Code가 하지 않는 일
- 핵심 도메인 로직의 완성 코드 제공 금지: 슬롯 선점(락), 결제 멱등성, 환불 계산, 상태 전이, 노쇼 배치
  - 이 영역에서는 정답 대신 **접근 방법 2~3가지와 트레이드오프**를 제시하고, 선택은 내가 한다
  - 내가 구현한 뒤 리뷰를 요청하면 그때 문제점을 질문으로 짚어줄 것
- 요청하지 않은 리팩터링, 라이브러리 추가, 구조 변경 금지 (필요하면 먼저 제안만)

## 코드 규칙

- 패키지: com.app.tably 하위에 도메인별 구성 (member, restaurant, reservation, payment, waiting)
- 패키지: com.app.tably.{도메인} 하위를 entity / repository / service / controller / dto 로 구분
- enum은 소속 엔티티와 같은 entity 패키지에 둔다 (별도 패키지 금지)
- 응답: 통일된 ApiResponse 포맷 사용 — `{ "success": bool, "data": ..., "error": { "code", "message" } }` (성공 시 error 생략, 실패 시 data 생략. com.app.tably.common.response.ApiResponse)
- 예외: 커스텀 예외 + @RestControllerAdvice 전역 처리
- 주석은 "왜"만 남긴다. "무엇"을 설명하는 주석 금지
- 기술 용어는 영어 그대로 (한글 번역하지 않음)

## 커밋 규칙

- 형식: `타입: 한글 요약` (예: feat: Member 엔티티 및 Role enum 추가)
- 타입: feat(기능) / fix(수정) / docs(문서) / chore(설정·잡무) / refactor(구조 개선) / test(테스트)
- 커밋은 내가 승인한 뒤에만 실행할 것

## 대화 규칙

- 존댓말 사용
- 작업 후에는 변경 파일 목록과 변경 이유를 3줄 이내로 요약
- 내 코드에서 배울 점이 아니라 고칠 점을 우선 지적할 것. 칭찬으로 시작하지 말 것
