package com.weeklyreport.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.weeklyreport.domain.WeeklyReport;
import com.weeklyreport.domain.enums.ReportStatus;
import com.weeklyreport.repository.WeeklyReportRepository;
import com.weeklyreport.service.DailyNoteService;
import com.weeklyreport.service.EntryService;
import com.weeklyreport.service.ManWeekService;
import com.weeklyreport.service.WeekLabelService;
import com.weeklyreport.service.WeekPeriod;

/**
 * "대시보드" 탭 — 첫 진입 화면({@code /}).
 *
 * <p>서버가 전부 계산해 한 번에 렌더링하는 거의 정적인 화면이라 htmx 배선이 없다.
 * 유일한 동적 영역은 "오늘 한 일" 카드이고, 그 CRUD는 {@link DailyNoteController}가 담당한다.
 *
 * <p>주차 페이저는 작성 탭 전용이므로 여기서는 prev/next week을 채우지 않는다.
 */
@Controller
public class DashboardController {

    /** 대시보드가 쓰는 평균 기간. 작성 화면의 4주 평균과 <b>다른 지표</b>다. */
    private static final int RECENT_WEEKS_FOR_AVERAGE = 2;

    /** 대시보드에 노출할 과거(제출) 보고서 개수. 전체는 히스토리 탭이 맡는다. */
    private static final int RECENT_REPORTS_ON_DASHBOARD = 5;

    /** 이 맨위크 미만이면 낮음으로 표시(히스토리 카드와 같은 기준). */
    private static final BigDecimal LOW_MAN_WEEK_THRESHOLD = new BigDecimal("0.80");

    private final EntryService entryService;
    private final DailyNoteService dailyNoteService;
    private final ManWeekService manWeekService;
    private final WeeklyReportRepository weeklyReportRepository;

    public DashboardController(EntryService entryService,
                                DailyNoteService dailyNoteService,
                                ManWeekService manWeekService,
                                WeeklyReportRepository weeklyReportRepository) {
        this.entryService = entryService;
        this.dailyNoteService = dailyNoteService;
        this.manWeekService = manWeekService;
        this.weeklyReportRepository = weeklyReportRepository;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        WeekPeriod period = WeekLabelService.forDate(LocalDate.now());
        WeeklyReport report = entryService.findByWeekStart(period.weekStart()).orElse(null);

        model.addAttribute("activeTab", "dashboard");
        model.addAttribute("period", period);
        model.addAttribute("week", period.weekStart());
        model.addAttribute("lowThreshold", LOW_MAN_WEEK_THRESHOLD);

        populateHero(model, report);
        populateManWeekAverage(model, period.weekStart());
        List<EntryService.ProjectProgress> activeProjects = entryService.activeProjectsWithProgress();
        model.addAttribute("activeProjects", activeProjects);
        model.addAttribute("activeProjectCount", activeProjects.size());
        populatePastReports(model, period.weekStart());
        // "오늘 한 일" 카드의 모델은 DailyNoteController가 소유한다 —
        // 기록을 추가/삭제하면 그쪽이 같은 프래그먼트를 같은 속성으로 다시 그려야 하기 때문.
        DailyNoteController.populateDashboardCard(model, dailyNoteService,
                period.weekStart(), DailyNoteController.DASHBOARD_RECENT_DAYS);

        return "dashboard";
    }

    /**
     * "이번 주" 카드. 상태(미작성/작성중/제출됨)와 그 주의 실측 통계를 함께 준다.
     *
     * <p>합계를 {@code report.totalHours}에서 읽지 않고 매번 다시 계산하는 이유:
     * 그 필드들은 <b>제출 시점에만</b> 채워지므로 작성중(draft) 주에서는 0으로 남아 있다.
     */
    private void populateHero(Model model, WeeklyReport report) {
        model.addAttribute("report", report);
        if (report == null) {
            model.addAttribute("heroTotalHours", "0");
            model.addAttribute("heroManWeek", BigDecimal.ZERO.setScale(2));
            model.addAttribute("heroItemCount", 0);
            return;
        }
        BigDecimal totalHours = manWeekService.totalHours(report.getItems());
        model.addAttribute("heroTotalHours", stripZeros(totalHours));
        model.addAttribute("heroManWeek", manWeekService.totalManWeek(report.getItems()));
        model.addAttribute("heroItemCount", report.getItems().size());
    }

    /** 최근 2주 평균 맨위크 + 그 평균을 낸 주들(이번 주 제외, 제출본만). */
    private void populateManWeekAverage(Model model, LocalDate currentWeekStart) {
        List<WeeklyReport> avgWeeks =
                entryService.recentSubmittedReports(RECENT_WEEKS_FOR_AVERAGE, currentWeekStart);
        model.addAttribute("avgWeeks", avgWeeks);
        model.addAttribute("avgManWeek", entryService.averageManWeek(avgWeeks));
        model.addAttribute("avgWeekCount", RECENT_WEEKS_FOR_AVERAGE);
    }

    /**
     * 과거 보고서 = 이번 주를 뺀 <b>제출본만</b>(사용자 확정). 최신 5개만 보여주고
     * 그보다 많으면 히스토리 탭으로 넘긴다.
     */
    private void populatePastReports(Model model, LocalDate currentWeekStart) {
        List<WeeklyReport> past = weeklyReportRepository
                .findByStatusOrderByWeekStartDesc(ReportStatus.SUBMITTED).stream()
                .filter(r -> !currentWeekStart.equals(r.getWeekStart()))
                .toList();
        model.addAttribute("recentReports", past.stream().limit(RECENT_REPORTS_ON_DASHBOARD).toList());
        model.addAttribute("pastReportCount", past.size());
    }

    private String stripZeros(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() < 0) {
            stripped = stripped.setScale(0);
        }
        return stripped.toPlainString();
    }

}
