package com.weeklyreport.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import com.weeklyreport.domain.DailyNote;
import com.weeklyreport.service.DailyNoteService;
import com.weeklyreport.web.dto.DailyNoteForm;

import jakarta.servlet.http.HttpServletRequest;

/**
 * {@code view} 파라미터 → 반환 프래그먼트 / 모델 속성명 계약.
 *
 * <p>이 계약이 깨져도 자바는 컴파일된다 — 프래그먼트 이름과 모델 속성명은 문자열이라
 * 오타가 나면 런타임에 템플릿이 조용히 비거나 EL1007E로만 드러난다. 그래서 여기서 문자열째 고정한다.
 */
class DailyNoteControllerViewTest {

    private final DailyNoteService dailyNoteService = Mockito.mock(DailyNoteService.class);
    private final DailyNoteController controller = new DailyNoteController(dailyNoteService);

    private static final LocalDate WEEK_START = LocalDate.of(2026, 8, 14);

    /**
     * 실제 서비스는 절대 null을 주지 않는다(합계 0이면 빈 문자열). 목이 기본 null을 주면
     * 컨트롤러가 NPE로 죽어 "프래그먼트 이름"이라는 검증 대상에 닿지도 못하므로 기본값을 깔아둔다.
     */
    @BeforeEach
    void 기록없는_상태를_기본으로_둔다() {
        Mockito.when(dailyNoteService.findByWeek(Mockito.any())).thenReturn(List.of());
        // 기록 화면은 검색 여부와 무관하게 2-인자 오버로드만 부른다(1-인자는 서비스 내부에서만 쓴다).
        Mockito.when(dailyNoteService.findByMonth(Mockito.any(), Mockito.any())).thenReturn(List.of());
        Mockito.when(dailyNoteService.groupByDate(Mockito.any())).thenReturn(List.of());
        Mockito.when(dailyNoteService.panelGroups(Mockito.any(), Mockito.any())).thenReturn(List.of());
        Mockito.when(dailyNoteService.sumHoursDisplay(Mockito.any())).thenReturn("");
        Mockito.when(dailyNoteService.earliestMonth()).thenReturn(Optional.empty());
        Mockito.when(dailyNoteService.latestMonth()).thenReturn(Optional.empty());
    }

    private DailyNoteForm form() {
        DailyNoteForm form = new DailyNoteForm();
        form.setWorkDate(WEEK_START);
        form.setText("한 일");
        return form;
    }

    // ---------- view → 프래그먼트 ----------

    @Test
    void view가_dashboard면_대시보드_카드_프래그먼트를_돌려준다() {
        String view = controller.add(form(), "dashboard", WEEK_START, null, null, new ExtendedModelMap());

        assertThat(view).isEqualTo("fragments-daily :: dashboardCard");
    }

    @Test
    void view가_entry면_작성패널_프래그먼트를_돌려준다() {
        String view = controller.add(form(), "entry", WEEK_START, null, null, new ExtendedModelMap());

        assertThat(view).isEqualTo("fragments-daily :: weekPanel");
    }

    @Test
    void view가_records면_기록화면_프래그먼트를_돌려준다() {
        String view = controller.add(form(), "records", null, "2026-08", null, new ExtendedModelMap());

        assertThat(view).isEqualTo("fragments-daily :: recordsPane");
    }

    @Test
    void view를_안_보내면_저장만_하고_빈_응답을_준다() {
        String view = controller.add(form(), null, null, null, null, new ExtendedModelMap());

        assertThat(view).isEqualTo("fragments-entry :: noop");
        Mockito.verify(dailyNoteService).add(WEEK_START, "한 일", null);
    }

    @Test
    void 삭제도_view에_맞는_프래그먼트를_돌려준다() {
        assertThat(controller.delete(1L, "records", null, "2026-08", null, new ExtendedModelMap()))
                .isEqualTo("fragments-daily :: recordsPane");
        Mockito.verify(dailyNoteService).delete(1L);
    }

    // ---------- 인라인 수정의 hours 존재 여부 ----------

    @Test
    void hours_파라미터가_없으면_시간을_건드리지_않는다고_서비스에_알린다() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getParameterMap()).thenReturn(Map.of("text", new String[] {"수정"}));

        DailyNoteForm form = new DailyNoteForm();
        form.setText("수정");
        String view = controller.update(1L, form, request);

        assertThat(view).isEqualTo("fragments-entry :: noop");
        Mockito.verify(dailyNoteService).update(1L, "수정", null, false);
    }

    @Test
    void hours가_빈_값으로_실려오면_지우라고_서비스에_알린다() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getParameterMap())
                .thenReturn(Map.of("text", new String[] {"수정"}, "hours", new String[] {""}));

        DailyNoteForm form = new DailyNoteForm();
        form.setText("수정");
        controller.update(1L, form, request);

        Mockito.verify(dailyNoteService).update(1L, "수정", null, true);
    }

    // ---------- 모델 속성명 ----------

    @Test
    void 기록화면_모델은_템플릿이_참조하는_이름을_전부_채운다() {
        Model model = new ExtendedModelMap();
        DailyNoteController.populateRecordsView(model, dailyNoteService, YearMonth.of(2026, 8));

        assertThat(model.asMap()).containsKeys("today", "recordMonth", "recordMonthLabel", "prevMonth",
                "nextMonth", "hasPrevMonth", "hasNextMonth", "recordDayGroups", "recordCount",
                "recordDayCount", "recordHoursDisplay", "recordDefaultDate",
                "recordQuery", "recordQueryRaw", "recordSearching");
        assertThat(model.asMap().get("recordMonth")).isEqualTo("2026-08");
        assertThat(model.asMap().get("recordMonthLabel")).isEqualTo("2026년 8월");
        assertThat(model.asMap().get("prevMonth")).isEqualTo("2026-07");
        assertThat(model.asMap().get("nextMonth")).isEqualTo("2026-09");
    }

    @Test
    void 통계타일의_기록된_시간은_0일_때_빈문자가_아니라_0이다() {
        // 날짜 헤더 칩은 0이면 아예 안 그리지만, 통계 타일은 항상 그려야 해서 규칙이 반대다.
        Model model = new ExtendedModelMap();
        DailyNoteController.populateRecordsView(model, dailyNoteService, YearMonth.of(2026, 8));

        assertThat(model.asMap().get("recordHoursDisplay")).isEqualTo("0");
    }

    // ---------- 기록 검색(q) ----------

    @Test
    void 검색어가_없으면_검색중이_아니고_검색어는_빈_문자열이다() {
        Model model = new ExtendedModelMap();
        DailyNoteController.populateRecordsView(model, dailyNoteService, YearMonth.of(2026, 8), null);

        assertThat(model.asMap().get("recordSearching")).isEqualTo(false);
        assertThat(model.asMap().get("recordQuery")).isEqualTo("");
        assertThat(model.asMap().get("recordQueryRaw")).isEqualTo("");
        Mockito.verify(dailyNoteService).findByMonth(YearMonth.of(2026, 8), null);
    }

    @Test
    void 공백만_친_검색어는_검색으로_치지_않는다() {
        // 전각 공백(U+3000)까지 포함 — 한글 IME에서 나오는데 trim으로는 안 떨어진다.
        Model model = new ExtendedModelMap();
        DailyNoteController.populateRecordsView(model, dailyNoteService, YearMonth.of(2026, 8), "  　 ");

        assertThat(model.asMap().get("recordSearching")).isEqualTo(false);
        assertThat(model.asMap().get("recordQuery")).isEqualTo("");
        assertThat(model.asMap().get("recordQueryRaw")).isEqualTo("");
    }

    @Test
    void 검색중이면_통계도_검색_결과_기준으로_다시_계산된다() {
        DailyNote hit = new DailyNote(LocalDate.of(2026, 8, 3), "GTPP 로그인", null);
        Mockito.when(dailyNoteService.findByMonth(YearMonth.of(2026, 8), "GTPP")).thenReturn(List.of(hit));
        Mockito.when(dailyNoteService.groupByDate(List.of(hit)))
                .thenReturn(List.of(new DailyNoteService.DayGroup(hit.getWorkDate(), List.of(hit))));

        Model model = new ExtendedModelMap();
        DailyNoteController.populateRecordsView(model, dailyNoteService, YearMonth.of(2026, 8), "GTPP");

        assertThat(model.asMap().get("recordSearching")).isEqualTo(true);
        assertThat(model.asMap().get("recordQuery")).isEqualTo("GTPP");
        assertThat(model.asMap().get("recordCount")).isEqualTo(1);
        assertThat(model.asMap().get("recordDayCount")).isEqualTo(1);
    }

    @Test
    void 월_페이저_범위는_검색과_무관하게_전체_기록_기준이다() {
        // 검색어가 안 걸리는 달이라고 이동을 막으면 "다른 달엔 있나" 확인 자체가 불가능해진다.
        Mockito.when(dailyNoteService.earliestMonth()).thenReturn(Optional.of(YearMonth.of(2026, 5)));
        Mockito.when(dailyNoteService.latestMonth()).thenReturn(Optional.of(YearMonth.now().plusMonths(2)));

        Model model = new ExtendedModelMap();
        DailyNoteController.populateRecordsView(model, dailyNoteService, YearMonth.now(), "걸리지않는검색어");

        assertThat(model.asMap().get("recordCount")).isEqualTo(0);
        assertThat(model.asMap().get("hasPrevMonth")).isEqualTo(true);
        assertThat(model.asMap().get("hasNextMonth")).isEqualTo(true);
    }

    @Test
    void 입력칸에_돌려주는_검색어는_정규화_전_원문이다() {
        // 타이핑 중 300ms마다 입력칸이 서버 값으로 교체되므로, 정규화된 값을 돌려주면
        // "GTPP "까지 치고 멈춘 사용자의 공백이 지워져 이어 친 글자가 "GTPP로그인"으로 붙어버린다.
        Model model = new ExtendedModelMap();
        DailyNoteController.populateRecordsView(model, dailyNoteService, YearMonth.of(2026, 8), "GTPP ");

        assertThat(model.asMap().get("recordQueryRaw")).isEqualTo("GTPP ");
        assertThat(model.asMap().get("recordQuery")).isEqualTo("GTPP");
    }

    @Test
    void 추가와_삭제는_검색어를_그대로_들고_다시_그린다() {
        // q를 흘리지 않으면 검색 중에 한 건 지우는 순간 필터가 풀려 그 달 전체가 튀어나온다.
        controller.add(form(), "records", null, "2026-08", "GTPP", new ExtendedModelMap());
        controller.delete(1L, "records", null, "2026-08", "GTPP", new ExtendedModelMap());

        Mockito.verify(dailyNoteService, Mockito.times(2)).findByMonth(YearMonth.of(2026, 8), "GTPP");
    }

    @Test
    void 대시보드_카드_모델은_템플릿이_참조하는_이름을_전부_채운다() {
        Mockito.when(dailyNoteService.findByWeek(WEEK_START)).thenReturn(List.of());
        Mockito.when(dailyNoteService.groupByDate(Mockito.any())).thenReturn(List.of());
        Mockito.when(dailyNoteService.sumHoursDisplay(Mockito.any())).thenReturn("");

        Model model = new ExtendedModelMap();
        DailyNoteController.populateDashboardCard(model, dailyNoteService, WEEK_START, 4);

        assertThat(model.asMap()).containsKeys("today", "week", "todayNotes", "todayNoteCount",
                "todayHoursDisplay", "recentDayGroups", "weekNoteCount", "weekHoursDisplay");
        assertThat(model.asMap().get("week")).isEqualTo(WEEK_START);
    }

    @Test
    void 대시보드_카드는_오늘_이전_날짜를_최신순_최대_3개만_펼친다() {
        LocalDate today = LocalDate.now();
        List<DailyNote> notes = List.of(
                new DailyNote(today.minusDays(4), "4일 전", null),
                new DailyNote(today.minusDays(3), "3일 전", null),
                new DailyNote(today.minusDays(2), "2일 전", null),
                new DailyNote(today.minusDays(1), "1일 전", null));
        Mockito.when(dailyNoteService.findByWeek(Mockito.any())).thenReturn(notes);
        Mockito.when(dailyNoteService.groupByDate(Mockito.any())).thenAnswer(i ->
                ((List<DailyNote>) i.getArgument(0)).stream()
                        .map(n -> new DailyNoteService.DayGroup(n.getWorkDate(), List.of(n)))
                        .toList());
        Mockito.when(dailyNoteService.sumHoursDisplay(Mockito.any())).thenReturn("");

        Model model = new ExtendedModelMap();
        DailyNoteController.populateDashboardCard(model, dailyNoteService, WEEK_START, 4);

        @SuppressWarnings("unchecked")
        List<DailyNoteService.DayGroup> groups =
                (List<DailyNoteService.DayGroup>) model.asMap().get("recentDayGroups");
        assertThat(groups).hasSize(3);
        assertThat(groups.get(0).date()).isEqualTo(today.minusDays(1));
        assertThat(groups.get(2).date()).isEqualTo(today.minusDays(3));
    }

    // ---------- 월 파싱 ----------

    @Test
    void 월_파라미터가_없거나_형식이_틀리면_이번_달로_본다() {
        YearMonth now = YearMonth.now();

        assertThat(DailyNoteController.parseMonth(null)).isEqualTo(now);
        assertThat(DailyNoteController.parseMonth("  ")).isEqualTo(now);
        assertThat(DailyNoteController.parseMonth("2026-13")).isEqualTo(now);
        assertThat(DailyNoteController.parseMonth("헛소리")).isEqualTo(now);
        assertThat(DailyNoteController.parseMonth(" 2026-08 ")).isEqualTo(YearMonth.of(2026, 8));
    }
}
