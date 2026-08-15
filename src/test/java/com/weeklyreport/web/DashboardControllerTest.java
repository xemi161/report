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

import com.weeklyreport.domain.Project;
import com.weeklyreport.domain.ReportItem;
import com.weeklyreport.domain.WeeklyReport;
import com.weeklyreport.domain.enums.Group;
import com.weeklyreport.domain.enums.ReportStatus;
import com.weeklyreport.repository.WeeklyReportRepository;
import com.weeklyreport.service.DailyNoteService;
import com.weeklyreport.service.EntryService;
import com.weeklyreport.service.ManWeekService;

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
    private final WeeklyReportRepository weeklyReportRepository = Mockito.mock(WeeklyReportRepository.class);

    private final DashboardController controller = new DashboardController(
            entryService, dailyNoteService, manWeekService, weeklyReportRepository);

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
