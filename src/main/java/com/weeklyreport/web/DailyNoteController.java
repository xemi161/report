package com.weeklyreport.web;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.weeklyreport.domain.DailyNote;
import com.weeklyreport.service.DailyNoteService;
import com.weeklyreport.service.WeekLabelService;
import com.weeklyreport.service.WeekPeriod;
import com.weeklyreport.web.dto.DailyNoteForm;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 일일 기록("오늘 한 일")의 추가/수정/삭제. <b>세 화면이 이 컨트롤러 하나를 공유한다</b> —
 * 대시보드 카드, 작성 화면 좌측 패널, 히스토리 탭의 "한 일 기록" 서브뷰.
 * 화면마다 다르게 그려야 하므로 어느 화면에서 왔는지를 {@code view} 쿼리 파라미터로 받아
 * 그 화면에 맞는 프래그먼트만 돌려준다(기존 {@code week} 쿼리스트링 관례와 같은 방식).
 *
 * <p>돌려주는 것은 <b>기록 블록 프래그먼트뿐</b>이다. 기록은 보고서 데이터에 아무 영향이 없으므로
 * 작성 화면 전체({@code entry :: writeView})를 다시 그릴 이유가 없다.
 *
 * <p>htmx 배선 규약 그대로: 구조가 바뀌는 추가/삭제는 해당 블록을 통째로 다시 렌더링해
 * {@code hx-swap="outerHTML"}로 갈아끼우고, 인라인 텍스트·시간 수정은 저장만 하고
 * 빈 응답을 준다({@code hx-swap="none"}, 포커스 유지).
 */
@Controller
public class DailyNoteController {

    /** 대시보드 카드에 펼쳐둘 날 수(오늘 + 기록이 있는 직전 3일). */
    static final int DASHBOARD_RECENT_DAYS = 4;

    private static final DateTimeFormatter MONTH_PARAM = DateTimeFormatter.ofPattern("yyyy-MM");

    private final DailyNoteService dailyNoteService;

    public DailyNoteController(DailyNoteService dailyNoteService) {
        this.dailyNoteService = dailyNoteService;
    }

    /**
     * 기록 추가. 텍스트가 비어 있으면 아무것도 만들지 않고 화면만 그대로 다시 그린다.
     *
     * @param view  "dashboard" | "entry" | "records" — 어느 화면에서 호출됐는지
     * @param week  view=dashboard/entry일 때 그 주의 시작(금요일). 없으면 이번 주
     * @param month view=records일 때 보고 있는 달("yyyy-MM"). 없으면 이번 달
     * @param q     view=records일 때 걸려 있는 검색어. 다시 그릴 때 그대로 유지한다
     */
    @PostMapping("/daily-notes")
    public String add(@ModelAttribute DailyNoteForm form,
                      @RequestParam(required = false) String view,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                      @RequestParam(required = false) String month,
                      @RequestParam(required = false) String q,
                      Model model) {
        dailyNoteService.add(form.getWorkDate(), form.getText(), form.getHours());
        return renderView(view, week, month, q, model);
    }

    /**
     * 인라인 수정(텍스트/시간). 화면을 다시 그리면 입력 포커스가 날아가므로 저장만 한다.
     *
     * <p>시간 칸은 비우면 "0시간"이 아니라 <b>미입력(null)</b>이 되어야 하므로,
     * 값이 비었을 때와 아예 안 보냈을 때를 파라미터 존재 여부로 구분한다.
     */
    @PostMapping("/daily-notes/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute DailyNoteForm form,
                         HttpServletRequest request) {
        boolean hoursProvided = request.getParameterMap().containsKey("hours");
        dailyNoteService.update(id, form.getText(), form.getHours(), hoursProvided);
        return "fragments-entry :: noop";
    }

    @PostMapping("/daily-notes/{id}/delete")
    public String delete(@PathVariable Long id,
                         @RequestParam(required = false) String view,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                         @RequestParam(required = false) String month,
                         @RequestParam(required = false) String q,
                         Model model) {
        dailyNoteService.delete(id);
        return renderView(view, week, month, q, model);
    }

    // ---------- 화면별 재렌더링 ----------

    private String renderView(String view, LocalDate week, String month, String query, Model model) {
        if ("records".equals(view)) {
            populateRecordsView(model, dailyNoteService, parseMonth(month), query);
            return "fragments-daily :: recordsPane";
        }
        WeekPeriod period = week == null
                ? WeekLabelService.forDate(LocalDate.now())
                : WeekLabelService.forWeekStart(week);
        if ("entry".equals(view)) {
            populateWeekPanel(model, dailyNoteService, period);
            return "fragments-daily :: weekPanel";
        }
        if ("dashboard".equals(view)) {
            populateDashboardCard(model, dailyNoteService, period.weekStart(), DASHBOARD_RECENT_DAYS);
            return "fragments-daily :: dashboardCard";
        }
        // view를 안 보냈다면 화면을 갱신할 필요가 없다는 뜻으로 본다(저장만).
        return "fragments-entry :: noop";
    }

    static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month.trim(), MONTH_PARAM);
        } catch (RuntimeException e) {
            return YearMonth.now();
        }
    }

    /**
     * 대시보드 "오늘 한 일" 카드. 오늘 기록은 날짜 헤더 없이 바로 깔고, 그 아래에
     * <b>기록이 있는 직전 며칠</b>만 날짜 그룹으로 붙인다(어제 적은 걸 오늘 고치는 일이 잦아서).
     * 그 주 전체는 작성 화면 좌측 패널이 맡는다.
     */
    static void populateDashboardCard(Model model, DailyNoteService dailyNoteService,
                                       LocalDate weekStart, int recentDays) {
        LocalDate today = LocalDate.now();
        List<DailyNote> weekNotes = dailyNoteService.findByWeek(weekStart);
        List<DailyNote> todayNotes = weekNotes.stream()
                .filter(n -> today.equals(n.getWorkDate()))
                .toList();
        // 직전 날들은 최근 것부터 붙는다 — 주 조회가 오름차순이라 여기서 뒤집는다(Java 17이라 List.reversed() 없음).
        List<DailyNoteService.DayGroup> beforeToday = new ArrayList<>(dailyNoteService
                .groupByDate(weekNotes.stream().filter(n -> n.getWorkDate().isBefore(today)).toList()));
        Collections.reverse(beforeToday);

        model.addAttribute("today", today);
        model.addAttribute("week", weekStart);
        model.addAttribute("todayNotes", todayNotes);
        model.addAttribute("todayNoteCount", todayNotes.size());
        model.addAttribute("todayHoursDisplay", dailyNoteService.sumHoursDisplay(todayNotes));
        model.addAttribute("recentDayGroups",
                beforeToday.stream().limit(Math.max(0, recentDays - 1)).toList());
        model.addAttribute("weekNoteCount", weekNotes.size());
        model.addAttribute("weekHoursDisplay", dailyNoteService.sumHoursDisplay(weekNotes));
    }

    /**
     * 작성 화면 좌측 패널(그 주 7일). 보고서 유무·제출 상태와 <b>완전히 무관하게</b> 동작한다 —
     * 보고서를 만들지 않은 주에도 기록은 그대로 쓰고 고칠 수 있어야 한다.
     */
    static void populateWeekPanel(Model model, DailyNoteService dailyNoteService, WeekPeriod period) {
        LocalDate weekStart = period.weekStart();
        LocalDate today = LocalDate.now();
        List<DailyNote> weekNotes = dailyNoteService.findByWeek(weekStart);
        List<LocalDate> weekDates = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            weekDates.add(weekStart.plusDays(i));
        }
        boolean containsToday = !today.isBefore(weekStart) && !today.isAfter(period.weekEnd());

        model.addAttribute("today", today);
        model.addAttribute("week", weekStart);
        model.addAttribute("period", period);
        model.addAttribute("panelDayGroups", dailyNoteService.panelGroups(weekStart, weekNotes));
        model.addAttribute("panelDates", weekDates);
        // 지난 날 기록을 채워 넣을 수 있어야 하므로 날짜 선택이 붙은 입력을 상시 노출한다.
        model.addAttribute("panelDefaultDate", containsToday ? today : period.weekEnd());
        model.addAttribute("panelIsCurrentWeek", containsToday);
        model.addAttribute("weekNoteCount", weekNotes.size());
        model.addAttribute("weekHoursDisplay", dailyNoteService.sumHoursDisplay(weekNotes));
    }

    /** 검색 없이 그 달 전체를 보는 기본 호출. */
    static void populateRecordsView(Model model, DailyNoteService dailyNoteService, YearMonth month) {
        populateRecordsView(model, dailyNoteService, month, null);
    }

    /**
     * 히스토리 탭의 "한 일 기록" 서브뷰(그 달). <b>정렬은 최신순(내림차순)</b>으로,
     * 작성 패널의 금→목 오름차순과 방향이 반대인 것은 의도된 설계다.
     *
     * <p>검색어({@code query})가 있으면 목록뿐 아니라 <b>통계 3칸도 검색 결과 기준</b>으로 다시 계산된다 —
     * 걸러진 목록 위에 "이 달 전체" 합계가 그대로 남아 있으면 두 숫자가 서로 다른 모집단을 가리켜
     * 어느 쪽을 읽어야 하는지 알 수 없게 된다. 다만 월 페이저의 이동 가능 범위
     * ({@code hasPrevMonth}/{@code hasNextMonth})만은 <b>검색과 무관하게 전체 기록 기준</b>으로 둔다 —
     * 검색어가 안 걸리는 달이라고 해서 그 달로 넘어가지 못하면 "다른 달에도 있나" 확인이 막힌다.
     *
     * @param query 검색어(부분일치, 대소문자 무시). null/공백이면 그 달 전체
     */
    static void populateRecordsView(Model model, DailyNoteService dailyNoteService,
                                    YearMonth month, String query) {
        String q = DailyNoteService.normalizeQuery(query);
        List<DailyNote> notes = dailyNoteService.findByMonth(month, q);
        List<DailyNoteService.DayGroup> groups = dailyNoteService.groupByDate(notes);

        YearMonth current = YearMonth.now();
        YearMonth min = dailyNoteService.earliestMonth().filter(m -> m.isBefore(current)).orElse(current);
        YearMonth max = dailyNoteService.latestMonth().filter(m -> m.isAfter(current)).orElse(current);

        String hours = dailyNoteService.sumHoursDisplay(notes);

        model.addAttribute("today", LocalDate.now());
        // 검색어는 입력칸 value·월 이동 링크·htmx 재렌더링 파라미터가 전부 다시 써야 하므로 그대로 내려준다.
        // 검색 중이 아니면 빈 문자열이다(null이면 @{...(q=${recordQuery})}가 파라미터를 지워주지만,
        // 템플릿에서 th:value에 그대로 쓸 수 있게 빈 문자열로 맞춘다).
        model.addAttribute("recordQuery", q == null ? "" : q);
        // ⚠️ 입력칸(th:value)만은 정규화 전 원문을 돌려준다. 검색은 타이핑 중에 300ms마다 #recordsPane을
        // 통째로 갈아끼우므로, 정규화된 값을 돌려주면 서버가 사용자가 방금 친 글자를 지워버린다 —
        // "GTPP "까지 치고 잠깐 멈추면 응답이 value="GTPP"로 와서 공백이 사라지고,
        // 이어서 "로그인"을 치면 "GTPP로그인"이 되어 검색이 0건으로 떨어진다.
        // 링크·범위 문구는 그대로 정규화된 recordQuery를 쓴다(원문이 새 나가도 서버가 다시 정규화하므로 무해).
        model.addAttribute("recordQueryRaw", q == null ? "" : query);
        model.addAttribute("recordSearching", q != null);
        model.addAttribute("recordMonth", month.format(MONTH_PARAM));
        model.addAttribute("recordMonthLabel", month.getYear() + "년 " + month.getMonthValue() + "월");
        model.addAttribute("prevMonth", month.minusMonths(1).format(MONTH_PARAM));
        model.addAttribute("nextMonth", month.plusMonths(1).format(MONTH_PARAM));
        model.addAttribute("hasPrevMonth", month.isAfter(min));
        model.addAttribute("hasNextMonth", month.isBefore(max));
        model.addAttribute("recordDayGroups", groups);
        model.addAttribute("recordCount", notes.size());
        model.addAttribute("recordDayCount", groups.size());
        // 통계 타일은 값이 0이어도 "0"을 그려야 한다(칩처럼 숨기지 않는다).
        model.addAttribute("recordHoursDisplay", hours.isEmpty() ? "0" : hours);
        model.addAttribute("recordDefaultDate",
                month.equals(current) ? LocalDate.now() : month.atDay(1));
    }
}
