package com.weeklyreport.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.weeklyreport.domain.DailyNote;
import com.weeklyreport.domain.WeeklyReport;
import com.weeklyreport.domain.enums.ReportStatus;
import com.weeklyreport.repository.WeeklyReportRepository;
import com.weeklyreport.service.DailyNoteService;
import com.weeklyreport.service.EntryService;
import com.weeklyreport.service.ManWeekService;
import com.weeklyreport.service.TodoItemService;
import com.weeklyreport.service.WeekLabelService;
import com.weeklyreport.service.WeekPeriod;

/**
 * "대시보드" 탭 — 첫 진입 화면({@code /}).
 *
 * <p>서버가 전부 계산해 한 번에 렌더링하는 거의 정적인 화면이다. 동적 영역은 둘뿐이고
 * 각각 자기 컨트롤러가 CRUD와 모델을 소유한다 — "오늘 한 일" 카드는 {@link DailyNoteController},
 * "TODO 리스트" 카드는 {@link TodoItemController}.
 *
 * <p>화면 구성은 승인된 벤토 목업 기준이다: hero(6×4) + 지표 타일 4개(3×2) + 오늘 한 일(8×6)
 * + TODO 리스트(4×6) + 과거 보고서(전폭). <b>"최근 2주 평균 맨위크"·"진행중인 프로젝트" 상세 타일은
 * 2026-08-15에 빠졌고</b>, 남은 것은 지표 타일의 숫자뿐이다 — 그래서 여기서는
 * 프로젝트 건수·평균 진행률만 계산하고 모집단 목록은 더 이상 내려보내지 않는다.
 *
 * <p><b>평균 맨위크는 아예 계산하지 않는다</b> — 그 숫자를 놓을 타일이 사라져 아무도 읽지 않게 된
 * {@code avgManWeek}/{@code avgWeekCount}를 2026-08-16에 지웠다(대시보드에 들어올 때마다 제출본
 * 전체를 한 번 더 조회하던 비용도 함께 사라진다). 작성 탭에도 {@code avgManWeek}가 있지만 그것은
 * {@code EntryController}가 주는 <b>4주 지표</b>라 이것과 무관하다.
 *
 * <p>주차 페이저는 작성 탭 전용이므로 여기서는 prev/next week을 채우지 않는다.
 */
@Controller
public class DashboardController {

    /** 대시보드에 노출할 과거(제출) 보고서 개수. 전체는 히스토리 탭이 맡는다. */
    private static final int RECENT_REPORTS_ON_DASHBOARD = 5;

    /** 이 맨위크 미만이면 낮음으로 표시(히스토리 카드와 같은 기준). */
    private static final BigDecimal LOW_MAN_WEEK_THRESHOLD = new BigDecimal("0.80");

    /** hero 요일 막대를 꽉 채우는 기준(하루 8시간). 목업의 {@code HERO_DAY_MAX_HOURS}와 같은 값. */
    private static final BigDecimal HERO_DAY_MAX_HOURS = new BigDecimal("8");

    /** 기록이 있는 날은 막대가 아무리 짧아도 이만큼은 보이게 한다(0.5h짜리가 사라지지 않도록). */
    private static final int HERO_BAR_MIN_PERCENT = 8;

    private final EntryService entryService;
    private final DailyNoteService dailyNoteService;
    private final TodoItemService todoItemService;
    private final ManWeekService manWeekService;
    private final WeeklyReportRepository weeklyReportRepository;

    public DashboardController(EntryService entryService,
                                DailyNoteService dailyNoteService,
                                TodoItemService todoItemService,
                                ManWeekService manWeekService,
                                WeeklyReportRepository weeklyReportRepository) {
        this.entryService = entryService;
        this.dailyNoteService = dailyNoteService;
        this.todoItemService = todoItemService;
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
        populateHeroDayStrip(model, period.weekStart());
        populateActiveProjectMetric(model);
        populatePastReports(model, period.weekStart());
        // "오늘 한 일"·"TODO 리스트" 카드의 모델은 각자의 컨트롤러가 소유한다 —
        // 조작 후 그쪽이 같은 프래그먼트를 같은 속성으로 다시 그려야 하기 때문.
        DailyNoteController.populateDashboardCard(model, dailyNoteService,
                period.weekStart(), DailyNoteController.DASHBOARD_RECENT_DAYS);
        TodoItemController.populateTodoCard(model, todoItemService);

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

    /**
     * hero 타일 하단의 요일 스트립(금~목 7칸). 주말이 비어 보이는 게 아니라
     * <b>주말이 이 앱의 한 주 안에 있다</b>는 사실이 보여야 하므로 기록이 없는 날도 칸을 그린다.
     *
     * <p>새 데이터가 아니라 기존 일일 기록의 날짜별 시간 합이다. "오늘 한 일" 카드와 조회가 겹치지만
     * 그쪽 모델은 {@link DailyNoteController}가 소유하고 이 스트립은 hero 타일 소유라 각자 조회한다
     * (한 주치 기록은 많아야 수십 건이다). ⚠️ 기록을 추가/삭제하면 htmx가 "오늘 한 일" 카드만
     * 갈아끼우므로 <b>스트립은 그 자리에서 갱신되지 않는다</b> — 대시보드 하단 "이번 주 기록 N건"과
     * 같은 성격의 의도된 절충이다(다음 페이지 로드에서 맞는다).
     */
    private void populateHeroDayStrip(Model model, LocalDate weekStart) {
        LocalDate today = LocalDate.now();
        List<DailyNote> weekNotes = dailyNoteService.findByWeek(weekStart);
        List<HeroDay> days = new ArrayList<>();
        int loggedDays = 0;
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            List<DailyNote> ofDay = weekNotes.stream()
                    .filter(n -> date.equals(n.getWorkDate()))
                    .toList();
            BigDecimal hours = dailyNoteService.sumHours(ofDay);
            if (!ofDay.isEmpty()) {
                loggedDays++;
            }
            days.add(new HeroDay(date,
                    date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN),
                    dailyNoteService.sumHoursDisplay(ofDay),
                    barPercent(hours),
                    date.equals(today)));
        }
        model.addAttribute("heroDays", days);
        model.addAttribute("heroLoggedDays", loggedDays);
        model.addAttribute("heroWeekHoursDisplay", dailyNoteService.sumHoursDisplay(weekNotes));
    }

    /** 0h면 0(막대를 아예 그리지 않는다), 그 외에는 8h를 100%로 잡되 최소 8%는 보이게 한다. */
    private int barPercent(BigDecimal hours) {
        if (hours.signum() == 0) {
            return 0;
        }
        int pct = hours.multiply(BigDecimal.valueOf(100))
                .divide(HERO_DAY_MAX_HOURS, 0, RoundingMode.HALF_UP)
                .intValue();
        return Math.max(HERO_BAR_MIN_PERCENT, Math.min(100, pct));
    }

    /**
     * "진행중 프로젝트" 지표 타일 — <b>건수와 평균 진행률만</b>. 프로젝트별 상세 목록 타일이 빠져서
     * 목록 자체({@code activeProjects})는 더 이상 내려보내지 않는다.
     *
     * <p>판정 기준은 {@code EntryService.activeProjectsWithProgress()}가 그대로 소유한다 —
     * 작성 탭과 공유하는 메서드라 두 화면의 건수가 항상 같아야 하므로 여기서 다시 세지 않는다.
     */
    private void populateActiveProjectMetric(Model model) {
        List<EntryService.ProjectProgress> activeProjects = entryService.activeProjectsWithProgress();
        model.addAttribute("activeProjectCount", activeProjects.size());
        model.addAttribute("activeProjectAvgProgress", averageProgress(activeProjects));
    }

    /** 진행중 프로젝트들의 평균 진행률(%). 하나도 없으면 0. */
    private int averageProgress(List<EntryService.ProjectProgress> projects) {
        if (projects.isEmpty()) {
            return 0;
        }
        int sum = projects.stream().mapToInt(EntryService.ProjectProgress::completion).sum();
        return Math.round((float) sum / projects.size());
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

    /**
     * hero 요일 스트립의 한 칸. 화면 전용 묶음이라 엔티티가 아니다.
     *
     * @param dow          요일 한 글자("금")
     * @param hoursDisplay 그 날 기록된 시간(0이면 빈 문자열 — 화면은 "·"를 대신 찍는다)
     * @param barPercent   막대 높이 %(0이면 막대를 그리지 않는다)
     * @param today        오늘 칸인지(테두리 강조)
     */
    public record HeroDay(LocalDate date, String dow, String hoursDisplay, int barPercent, boolean today) {

        /** 기록이 하나도 없는 날. */
        public boolean isEmpty() {
            return barPercent == 0;
        }
    }
}
