package com.weeklyreport.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import com.weeklyreport.domain.DailyNote;
import com.weeklyreport.domain.Project;
import com.weeklyreport.domain.ReportItem;
import com.weeklyreport.domain.WeeklyReport;
import com.weeklyreport.domain.enums.Group;
import com.weeklyreport.domain.enums.ReportStatus;
import com.weeklyreport.repository.WeeklyReportRepository;
import com.weeklyreport.service.DailyNoteService;
import com.weeklyreport.service.EntryService;
import com.weeklyreport.service.ManWeekService;
import com.weeklyreport.service.TodoItemService;

/**
 * 대시보드가 계산해 내려보내는 값들.
 *
 * <p>"진행중인 프로젝트" 판정 로직 자체(완료율 평균, 최근 주 우선 등)는
 * {@code EntryService.activeProjectsWithProgress()}로 옮겨져 {@code EntryServiceTest}가 검증한다 —
 * 대시보드와 작성 탭이 같은 이름의 지표를 다르게 계산하던 모순을 없애기 위한 이전이다.
 * 여기서는 hero 통계가 저장된 합계가 아니라 매번 실측인지, 과거 보고서 목록이 맞는지만 본다.
 */
class DashboardControllerTest {

    private final EntryService entryService = Mockito.mock(EntryService.class);
    private final DailyNoteService dailyNoteService = Mockito.mock(DailyNoteService.class);
    private final ManWeekService manWeekService = new ManWeekService();
    private final TodoItemService todoItemService = Mockito.mock(TodoItemService.class);
    private final WeeklyReportRepository weeklyReportRepository = Mockito.mock(WeeklyReportRepository.class);

    private final DashboardController controller = new DashboardController(
            entryService, dailyNoteService, todoItemService, manWeekService, weeklyReportRepository);

    private Project project(long id, String name) {
        Project p = new Project(name);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private ReportItem projectItem(Project project, WeeklyReport report, Integer completion, String hours, Integer days) {
        ReportItem item = ReportItem.forGroup(Group.PROJECT);
        item.setProject(project);
        item.setWeeklyReport(report);
        item.setCompletion(completion);
        if (hours != null) {
            item.setHours(new BigDecimal(hours));
        }
        item.setDays(days);
        return item;
    }

    private WeeklyReport report(String label, LocalDate weekStart) {
        return new WeeklyReport(label, weekStart, weekStart.plusDays(6));
    }

    // ---------- hero ----------

    @Test
    void hero_통계는_저장된_합계가_아니라_항목에서_매번_다시_계산한다() {
        // 작성중(draft)이라 totalHours/totalManWeek 필드는 아직 0인 상태 — 그래도 실측값이 나와야 한다.
        WeeklyReport draft = report("8월 3주", LocalDate.of(2026, 8, 14));
        draft.addItem(projectItem(project(1L, "GTPP"), draft, 60, "8", 2));
        draft.addItem(projectItem(project(1L, "GTPP"), draft, 40, "4", 1));
        assertThat(draft.getTotalHours()).isEqualByComparingTo("0");

        Model model = new ExtendedModelMap();
        ReflectionTestUtils.invokeMethod(controller, "populateHero", model, draft);

        assertThat(model.asMap().get("heroTotalHours")).isEqualTo("20");
        assertThat(model.asMap().get("heroManWeek")).isEqualTo(new BigDecimal("0.50"));
        assertThat(model.asMap().get("heroItemCount")).isEqualTo(2);
    }

    @Test
    void 이번_주_보고서가_없으면_hero는_0으로_채운다() {
        Model model = new ExtendedModelMap();
        ReflectionTestUtils.invokeMethod(controller, "populateHero", model, (WeeklyReport) null);

        assertThat(model.asMap().get("report")).isNull();
        assertThat(model.asMap().get("heroTotalHours")).isEqualTo("0");
        assertThat(model.asMap().get("heroItemCount")).isEqualTo(0);
    }

    // ---------- 과거 보고서 ----------

    @Test
    void 과거_보고서는_이번_주를_빼고_최근_5개만_보여주되_전체_건수는_따로_준다() {
        LocalDate thisWeek = LocalDate.of(2026, 8, 14);
        List<WeeklyReport> submitted = List.of(
                report("8월 3주", thisWeek),                    // 이번 주 → 제외
                report("8월 2주", LocalDate.of(2026, 8, 7)),
                report("8월 1주", LocalDate.of(2026, 7, 31)),
                report("7월 5주", LocalDate.of(2026, 7, 24)),
                report("7월 4주", LocalDate.of(2026, 7, 17)),
                report("7월 3주", LocalDate.of(2026, 7, 10)),
                report("7월 2주", LocalDate.of(2026, 7, 3)));
        Mockito.when(weeklyReportRepository.findByStatusOrderByWeekStartDesc(ReportStatus.SUBMITTED))
                .thenReturn(submitted);

        Model model = new ExtendedModelMap();
        ReflectionTestUtils.invokeMethod(controller, "populatePastReports", model, thisWeek);

        assertThat((List<?>) model.asMap().get("recentReports")).hasSize(5);
        assertThat(model.asMap().get("pastReportCount")).isEqualTo(6);
    }

    // ---------- 벤토: hero 요일 스트립 ----------

    /** 실제 서비스처럼 동작하도록 합계 계산만 목에 심어준다(스트립이 보는 것은 날짜별 합뿐이다). */
    private void 기록_합계를_실제처럼_계산하게_한다() {
        Mockito.when(dailyNoteService.sumHours(Mockito.anyList())).thenAnswer(i -> {
            List<DailyNote> notes = i.getArgument(0);
            return notes.stream().map(DailyNote::getHours).filter(h -> h != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        });
        Mockito.when(dailyNoteService.sumHoursDisplay(Mockito.anyList())).thenAnswer(i -> {
            List<DailyNote> notes = i.getArgument(0);
            BigDecimal sum = notes.stream().map(DailyNote::getHours).filter(h -> h != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return sum.signum() == 0 ? "" : sum.stripTrailingZeros().toPlainString();
        });
    }

    @Test
    void 요일_스트립은_기록이_하나도_없어도_금부터_목까지_7칸을_그린다() {
        // 주말이 비어 보이는 게 아니라 "주말이 이 주 안에 있다"는 사실이 보여야 한다.
        LocalDate weekStart = LocalDate.of(2026, 8, 14);
        Mockito.when(dailyNoteService.findByWeek(weekStart)).thenReturn(List.of());
        기록_합계를_실제처럼_계산하게_한다();

        Model model = new ExtendedModelMap();
        ReflectionTestUtils.invokeMethod(controller, "populateHeroDayStrip", model, weekStart);

        @SuppressWarnings("unchecked")
        List<DashboardController.HeroDay> days =
                (List<DashboardController.HeroDay>) model.asMap().get("heroDays");
        assertThat(days).hasSize(7);
        assertThat(days).extracting(DashboardController.HeroDay::dow)
                .containsExactly("금", "토", "일", "월", "화", "수", "목");
        assertThat(days).allMatch(DashboardController.HeroDay::isEmpty);
        assertThat(model.asMap().get("heroLoggedDays")).isEqualTo(0);
    }

    @Test
    void 막대는_8시간을_100퍼센트로_잡되_짧은_기록도_사라지지_않게_최소_높이를_준다() {
        LocalDate weekStart = LocalDate.of(2026, 8, 14);
        Mockito.when(dailyNoteService.findByWeek(weekStart)).thenReturn(List.of(
                new DailyNote(weekStart, "0.5시간짜리", new BigDecimal("0.5")),   // 6% → 최소 8%로 올린다
                new DailyNote(weekStart.plusDays(1), "네 시간", new BigDecimal("4")),
                new DailyNote(weekStart.plusDays(2), "열두 시간", new BigDecimal("12"))));  // 150% → 100%로 자른다
        기록_합계를_실제처럼_계산하게_한다();

        Model model = new ExtendedModelMap();
        ReflectionTestUtils.invokeMethod(controller, "populateHeroDayStrip", model, weekStart);

        @SuppressWarnings("unchecked")
        List<DashboardController.HeroDay> days =
                (List<DashboardController.HeroDay>) model.asMap().get("heroDays");
        assertThat(days).extracting(DashboardController.HeroDay::barPercent)
                .containsExactly(8, 50, 100, 0, 0, 0, 0);
        // 기록이 없는 날은 막대를 아예 그리지 않는다(0h를 "채워야 할 빈칸"으로 보이게 하지 않기 위해).
        assertThat(days.get(3).isEmpty()).isTrue();
        assertThat(model.asMap().get("heroLoggedDays")).isEqualTo(3);
    }

    // ---------- 벤토: 지표 타일 ----------

    @Test
    void 진행중_프로젝트_평균_진행률은_반올림한_정수이고_하나도_없으면_0이다() {
        Mockito.when(entryService.activeProjectsWithProgress()).thenReturn(List.of(
                new EntryService.ProjectProgress(project(1L, "GTPP"), 30, "8월 2주"),
                new EntryService.ProjectProgress(project(2L, "2차인증"), 40, "8월 2주"),
                new EntryService.ProjectProgress(project(3L, "결제"), 45, "8월 1주")));

        Model model = new ExtendedModelMap();
        ReflectionTestUtils.invokeMethod(controller, "populateActiveProjectMetric", model);

        assertThat(model.asMap().get("activeProjectCount")).isEqualTo(3);
        assertThat(model.asMap().get("activeProjectAvgProgress")).isEqualTo(38); // 115/3 = 38.33 → 38

        Mockito.when(entryService.activeProjectsWithProgress()).thenReturn(List.of());
        Model empty = new ExtendedModelMap();
        ReflectionTestUtils.invokeMethod(controller, "populateActiveProjectMetric", empty);
        assertThat(empty.asMap().get("activeProjectAvgProgress")).isEqualTo(0);
    }

    @Test
    void 상세_타일이_빠진_뒤로는_모집단_목록도_평균_맨위크도_내려보내지_않는다() {
        // dashboard.html에 이 값들을 읽는 자리가 없다. 다시 채우면 대시보드 진입마다
        // 제출본 전체를 헛조회하게 되므로, 타일을 되살릴 때 함께 되살려야 한다.
        Mockito.when(entryService.activeProjectsWithProgress()).thenReturn(List.of());
        Mockito.when(weeklyReportRepository.findByStatusOrderByWeekStartDesc(ReportStatus.SUBMITTED))
                .thenReturn(List.of());
        Mockito.when(dailyNoteService.findByWeek(Mockito.any())).thenReturn(List.of());
        기록_합계를_실제처럼_계산하게_한다();

        Model model = new ExtendedModelMap();
        controller.dashboard(model);

        assertThat(model.asMap()).doesNotContainKeys("avgManWeek", "avgWeekCount", "avgWeeks", "activeProjects");
        Mockito.verify(entryService, Mockito.never()).recentSubmittedReports(Mockito.anyInt(), Mockito.any());
        Mockito.verify(entryService, Mockito.never()).averageManWeek(Mockito.anyList());
    }

    @Test
    void 대시보드_모델은_벤토_템플릿이_참조하는_이름을_전부_채운다() {
        Mockito.when(entryService.activeProjectsWithProgress()).thenReturn(List.of());
        Mockito.when(weeklyReportRepository.findByStatusOrderByWeekStartDesc(ReportStatus.SUBMITTED))
                .thenReturn(List.of());
        Mockito.when(dailyNoteService.findByWeek(Mockito.any())).thenReturn(List.of());
        Mockito.when(dailyNoteService.groupByDate(Mockito.anyList())).thenReturn(List.of());
        기록_합계를_실제처럼_계산하게_한다();

        Model model = new ExtendedModelMap();
        assertThat(controller.dashboard(model)).isEqualTo("dashboard");

        assertThat(model.asMap()).containsKeys(
                // 헤더/hero
                "activeTab", "period", "week", "lowThreshold", "report",
                "heroTotalHours", "heroManWeek", "heroItemCount",
                "heroDays", "heroLoggedDays", "heroWeekHoursDisplay",
                // 지표 타일
                "activeProjectCount", "activeProjectAvgProgress", "todoOverdueCount", "todoDueTodayCount",
                // 아래 띠 두 카드 + 과거 보고서
                "todayNotes", "recentDayGroups", "todoDayGroups", "todoVisibleLimit",
                "recentReports", "pastReportCount");
        assertThat(model.asMap().get("activeTab")).isEqualTo("dashboard");
    }

    // ---------- 일일 기록과의 분리 ----------

    @Test
    void 일일_기록은_hero_통계에_전혀_섞이지_않는다() {
        WeeklyReport draft = report("8월 3주", LocalDate.of(2026, 8, 14));
        draft.addItem(projectItem(project(1L, "GTPP"), draft, 60, "8", 1));
        Mockito.when(entryService.findByWeekStart(Mockito.any())).thenReturn(Optional.of(draft));
        // 기록이 아무리 많아도 hero는 보고서 항목만 본다.
        Mockito.when(dailyNoteService.sumHoursDisplay(Mockito.any())).thenReturn("99");

        Model model = new ExtendedModelMap();
        ReflectionTestUtils.invokeMethod(controller, "populateHero", model, draft);

        assertThat(model.asMap().get("heroTotalHours")).isEqualTo("8");
        assertThat(model.asMap().get("heroManWeek")).isEqualTo(new BigDecimal("0.20"));
    }
}
