package com.weeklyreport.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import com.weeklyreport.repository.WeeklyReportRepository;
import com.weeklyreport.service.DailyNoteService;

/**
 * 히스토리 탭의 서브 뷰 분기와 <b>기록 검색어(q)의 전달</b>.
 *
 * <p>{@code q}는 컨트롤러가 받아서 {@code populateRecordsView}에 그대로 넘기기만 하는 값이라
 * 빠뜨려도 자바는 컴파일된다 — 화면은 조용히 "검색했는데 그 달 전체가 나온다"로만 깨진다.
 * 그래서 전달 자체를 여기서 고정한다.
 */
class HistoryControllerTest {

    private final WeeklyReportRepository weeklyReportRepository = Mockito.mock(WeeklyReportRepository.class);
    private final DailyNoteService dailyNoteService = Mockito.mock(DailyNoteService.class);
    private final HistoryController controller =
            new HistoryController(weeklyReportRepository, dailyNoteService);

    @BeforeEach
    void 비어있는_상태를_기본으로_둔다() {
        Mockito.when(weeklyReportRepository.findByStatusOrderByWeekStartDesc(Mockito.any()))
                .thenReturn(List.of());
        Mockito.when(dailyNoteService.findByMonth(Mockito.any(), Mockito.any())).thenReturn(List.of());
        Mockito.when(dailyNoteService.groupByDate(Mockito.any())).thenReturn(List.of());
        Mockito.when(dailyNoteService.sumHoursDisplay(Mockito.any())).thenReturn("");
        Mockito.when(dailyNoteService.earliestMonth()).thenReturn(Optional.empty());
        Mockito.when(dailyNoteService.latestMonth()).thenReturn(Optional.empty());
    }

    @Test
    void 기본_뷰는_과거_보고서_목록이고_기록_모델은_채우지_않는다() {
        Model model = new ExtendedModelMap();

        String view = controller.list(null, null, null, model);

        assertThat(view).isEqualTo("history");
        assertThat(model.asMap().get("historyView")).isEqualTo("reports");
        assertThat(model.asMap()).doesNotContainKey("recordDayGroups");
    }

    @Test
    void 기록_뷰는_검색어를_그대로_기록_조회에_넘긴다() {
        Model model = new ExtendedModelMap();

        controller.list("records", "2026-08", "GTPP", model);

        assertThat(model.asMap().get("historyView")).isEqualTo("records");
        assertThat(model.asMap().get("recordSearching")).isEqualTo(true);
        assertThat(model.asMap().get("recordQuery")).isEqualTo("GTPP");
        Mockito.verify(dailyNoteService).findByMonth(YearMonth.of(2026, 8), "GTPP");
    }

    @Test
    void 검색어가_비어있으면_그_달_전체를_본다() {
        // 검색 지우기 링크와 월 이동 링크가 q=를 빈 값으로 실어 보내는 경로다(Thymeleaf는 빈 값도 렌더링한다).
        Model model = new ExtendedModelMap();

        controller.list("records", "2026-08", "", model);

        assertThat(model.asMap().get("recordSearching")).isEqualTo(false);
        Mockito.verify(dailyNoteService).findByMonth(YearMonth.of(2026, 8), null);
    }

    @Test
    void 월_파라미터가_없으면_이번_달을_본다() {
        Model model = new ExtendedModelMap();

        controller.list("records", null, null, model);

        Mockito.verify(dailyNoteService).findByMonth(YearMonth.now(), null);
    }
}
