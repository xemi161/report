# 오케스트레이터 후속 조치 — qa가 보고한 "남은 이슈" 처리

qa_report.md가 판단이 필요하다고 보고한 4건 중 3건을 오케스트레이터가 직접 처리했다(전부 사용자 승인
없이 진행 가능한 범위 — 제품 결정 1건 + 경미한 정리 2건).

## 1. `activeProjectCount` 이중 계산 (제품 결정, 처리함)

**선택**: (a) 작성 탭을 대시보드의 확정 규칙에 맞춘다.

- `EntryService`에 `activeProjectsWithProgress()`/`ProjectProgress` 신설(원래 `DashboardController`에
  있던 로직을 그대로 옮김 — 판정 기준은 안 바뀜: `Project.active==true` AND 최근 진행률(완료율 평균) < 100%).
- `DashboardController.populateActiveProjects()`(사설 메서드) 삭제, `entryService.activeProjectsWithProgress()` 호출로 대체.
  이제 안 쓰는 `ProjectRepository`/`ReportItemRepository` 필드·생성자 인자·관련 import 제거.
- `EntryController.populateWriteView()`의 `activeProjectCount`도 같은 메서드 재사용으로 교체
  (`projectRepository.findByActiveTrueOrderByNameAsc().size()` → `entryService.activeProjectsWithProgress().size()`).
- **CLAUDE.md "다음에 할 일"의 `Project.active` 집계 방식 미결 항목이 이걸로 해소됨** — docs-sync가 반영할 것.
- 테스트: qa가 `DashboardControllerTest`에 리플렉션으로 짜둔 진행률 계산 테스트 5건을
  `EntryServiceTest`(신규)로 옮겨 `activeProjectsWithProgress()`를 직접(리플렉션 없이) 검증하도록 바꿨고,
  화면 간 일관성을 고정하는 회귀 테스트 1건을 추가했다. `DashboardControllerTest`는 hero/과거보고서/일일기록
  분리 검증만 남았다.
- `./gradlew test` 통과(58건 — qa의 53건 + 이번에 옮기며 늘어난 1건).

## 2. `historyCount` 죽은 모델 속성 (경미, 처리함)

`LayoutAdvice.historyCount()`가 매 요청마다 `COUNT` 쿼리를 날렸지만 어떤 템플릿도 더 이상 참조하지
않았다(frontend가 헤더 탭에서 건수 문구를 뺌). 메서드와 이제 안 쓰는 `WeeklyReportRepository`
의존성을 `LayoutAdvice`에서 제거했다.

## 3. `DailyNoteService`의 불필요한 트랜잭션 (경미, 보류)

qa가 지적한 순수 함수의 불필요한 `@Transactional` 건은 이번엔 손대지 않았다 — 동작에 영향 없는
성능상 미세 최적화라 이번 라운드 스코프에서 제외. 필요하면 별도로 처리.

## 4. "월 밖 날짜로 기록 추가 시 화면에서 사라짐" (경미, 보류)

frontend가 이미 알고 있던 known limitation — backend 계약에 "적은 날짜의 달로 따라가기"가 없어서
그대로 뒀다. 실사용에 문제되면 별도 요청으로 처리.

---

이 파일은 backend/frontend/qa 세 에이전트 산출물과 별개로, 그 세 산출물을 받은 뒤 오케스트레이터가
직접 수정한 내용의 기록이다(qa 재소집 없이 처리 — 변경 범위가 작고 `./gradlew test`로 검증됐다).
