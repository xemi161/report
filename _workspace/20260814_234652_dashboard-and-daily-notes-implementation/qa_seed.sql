-- QA E2E 전용 시드. 사용자의 실제 DB(~/.weekly-report/data/db)는 절대 건드리지 않는다 —
-- in-memory H2 + ddl-auto=create 로 띄운 임시 인스턴스에만 들어간다.
-- 기준일: 2026-08-15(토) → 이번 주 = 2026-08-14(금) ~ 2026-08-20(목) = "8월 3주"

INSERT INTO app_settings (id, name, role, ticket_prefix) VALUES (1, 'QA테스터', '파트원', 'NHNKCP-개발1팀');

INSERT INTO project (id, name, ticket, active) VALUES (1, 'GTPP 결제연동', 'NHNKCP-개발1팀/23', TRUE);
INSERT INTO project (id, name, ticket, active) VALUES (2, '정산배치 개선', 'NHNKCP-개발1팀/44', TRUE);
INSERT INTO project (id, name, ticket, active) VALUES (3, '신규 프로젝트', '', TRUE);
INSERT INTO project (id, name, ticket, active) VALUES (4, '종료된 프로젝트', 'NHNKCP-개발1팀/9', FALSE);

-- 과거 제출본 6건 → pastReportCount(6) > 5 이므로 대시보드에 "전체 6개 보기" 링크가 떠야 한다
INSERT INTO weekly_report (id, week_label, week_start, week_end, status, total_hours, total_man_week, submitted_at)
VALUES (1, '8월 2주', DATE '2026-08-07', DATE '2026-08-13', 'SUBMITTED', 34.00, 0.85, TIMESTAMP '2026-08-13 18:00:00');
INSERT INTO weekly_report (id, week_label, week_start, week_end, status, total_hours, total_man_week, submitted_at)
VALUES (2, '8월 1주', DATE '2026-07-31', DATE '2026-08-06', 'SUBMITTED', 40.00, 1.00, TIMESTAMP '2026-08-06 18:00:00');
INSERT INTO weekly_report (id, week_label, week_start, week_end, status, total_hours, total_man_week, submitted_at)
VALUES (3, '7월 5주', DATE '2026-07-24', DATE '2026-07-30', 'SUBMITTED', 28.00, 0.70, TIMESTAMP '2026-07-30 18:00:00');
INSERT INTO weekly_report (id, week_label, week_start, week_end, status, total_hours, total_man_week, submitted_at)
VALUES (4, '7월 4주', DATE '2026-07-17', DATE '2026-07-23', 'SUBMITTED', 40.00, 1.00, TIMESTAMP '2026-07-23 18:00:00');
INSERT INTO weekly_report (id, week_label, week_start, week_end, status, total_hours, total_man_week, submitted_at)
VALUES (5, '7월 3주', DATE '2026-07-10', DATE '2026-07-16', 'SUBMITTED', 36.00, 0.90, TIMESTAMP '2026-07-16 18:00:00');
INSERT INTO weekly_report (id, week_label, week_start, week_end, status, total_hours, total_man_week, submitted_at)
VALUES (6, '7월 2주', DATE '2026-07-03', DATE '2026-07-09', 'SUBMITTED', 40.00, 1.00, TIMESTAMP '2026-07-09 18:00:00');

-- 이번 주(작성중). hero = 8h*2일 + 4h*1일 = 20시간 / 맨위크 0.50 / 3건
INSERT INTO weekly_report (id, week_label, week_start, week_end, status, total_hours, total_man_week, submitted_at)
VALUES (7, '8월 3주', DATE '2026-08-14', DATE '2026-08-20', 'DRAFT', 0.00, 0.00, NULL);

-- 지난주(8월 2주) 항목 — 대시보드 "진행중인 프로젝트" 진행률의 근거가 된다
INSERT INTO report_item (id, weekly_report_id, group_type, project_id, ticket, title, phase, hours, days, completion, carried_over, sort_order)
VALUES (11, 1, 'PROJECT', 1, 'NHNKCP-개발1팀/23', '결제 API 연동', 'DEVELOPMENT', 8.00, 2, 60, FALSE, 0);
INSERT INTO report_item (id, weekly_report_id, group_type, project_id, ticket, title, phase, hours, days, completion, carried_over, sort_order)
VALUES (12, 1, 'PROJECT', 1, 'NHNKCP-개발1팀/23', '결제 API 설계', 'ANALYSIS_DESIGN', 6.00, 1, 80, FALSE, 1);
-- 진행률 100% → activeProjects 에서 빠져야 한다
INSERT INTO report_item (id, weekly_report_id, group_type, project_id, ticket, title, phase, hours, days, completion, carried_over, sort_order)
VALUES (13, 1, 'PROJECT', 2, 'NHNKCP-개발1팀/44', '정산배치 리팩터', 'DEVELOPMENT', 4.00, 1, 100, FALSE, 2);

-- 이번 주(작성중) 항목
INSERT INTO report_item (id, weekly_report_id, group_type, project_id, ticket, title, phase, hours, days, completion, carried_over, sort_order)
VALUES (21, 7, 'PROJECT', 1, 'NHNKCP-개발1팀/23', '결제 API 연동', 'DEVELOPMENT', 8.00, 2, 70, TRUE, 0);
-- ⚠️ 레이아웃 최악 케이스: 티켓 입력이 있는 dev 행 + "이월"과 "완료" 배지가 동시에 붙는다.
--    worklog §9 qa 절이 지목한 "완료+이월 동시" 행이 바로 이것 — 1536px에서도 한 줄이어야 한다.
INSERT INTO report_item (id, weekly_report_id, group_type, project_id, ticket, title, phase, hours, days, completion, carried_over, sort_order)
VALUES (22, 7, 'DEV', NULL, 'NHNKCP-개발1팀/77', '사내 위키 정리 및 온보딩 문서 개편', 'DEVELOPMENT', 4.00, 1, 100, TRUE, 1);
INSERT INTO report_item (id, weekly_report_id, group_type, project_id, title, hours, days, sort_order, carried_over)
VALUES (23, 7, 'ETC', NULL, '팀 회고 참석', NULL, NULL, 2, FALSE);

-- 일일 기록: 이번 주 2건 + 지난달(7월) 2건 → 월 페이저 이전 버튼이 활성화되어야 한다
INSERT INTO daily_note (id, work_date, note_text, hours, created_at)
VALUES (1, DATE '2026-08-14', '결제 API 연동 스펙 확인', 2.5, TIMESTAMP '2026-08-14 10:00:00');
INSERT INTO daily_note (id, work_date, note_text, hours, created_at)
VALUES (2, DATE '2026-08-14', '팀 스탠드업', 0.5, TIMESTAMP '2026-08-14 11:00:00');
INSERT INTO daily_note (id, work_date, note_text, hours, created_at)
VALUES (3, DATE '2026-07-20', '정산배치 로그 분석', 3.0, TIMESTAMP '2026-07-20 14:00:00');
INSERT INTO daily_note (id, work_date, note_text, hours, created_at)
VALUES (4, DATE '2026-07-21', '월간 회고 준비', NULL, TIMESTAMP '2026-07-21 09:00:00');

ALTER TABLE weekly_report ALTER COLUMN id RESTART WITH 100;
ALTER TABLE report_item ALTER COLUMN id RESTART WITH 100;
ALTER TABLE project ALTER COLUMN id RESTART WITH 100;
ALTER TABLE daily_note ALTER COLUMN id RESTART WITH 100;
