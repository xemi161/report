package com.weeklyreport.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.weeklyreport.domain.DailyNote;
import com.weeklyreport.repository.DailyNoteRepository;

/**
 * 일일 기록의 묶음/집계/CRUD 규칙.
 *
 * <p>여기서 검증하는 것은 전부 "화면이 이 값을 그대로 믿고 그린다"는 계약이다 —
 * 특히 시간 합계가 0일 때 빈 문자열을 주는 규칙은 템플릿이 {@code #strings.isEmpty}로
 * 렌더 여부를 가르는 신호라서, 여기서 "0"으로 바뀌면 화면에 "0h"가 새로 생긴다.
 */
class DailyNoteServiceTest {

    private final DailyNoteRepository dailyNoteRepository = Mockito.mock(DailyNoteRepository.class);
    private final DailyNoteService service = new DailyNoteService(dailyNoteRepository);

    /** 이번 주 = 2026-08-14(금) ~ 2026-08-20(목). */
    private static final LocalDate WEEK_START = LocalDate.of(2026, 8, 14);

    private DailyNote note(LocalDate date, String text, String hours) {
        return new DailyNote(date, text, hours == null ? null : new BigDecimal(hours));
    }

    // ---------- 집계 ----------

    @Test
    void 시간이_비어있는_기록은_합계에서_0으로_취급된다() {
        List<DailyNote> notes = List.of(
                note(WEEK_START, "스탠드업", "0.5"),
                note(WEEK_START, "시간 안 적은 일", null),
                note(WEEK_START, "설계 검토", "2"));

        assertThat(service.sumHours(notes)).isEqualByComparingTo("2.5");
    }

    @Test
    void 시간_합계가_0이면_빈_문자열을_준다() {
        assertThat(service.sumHoursDisplay(List.of(note(WEEK_START, "시간 미기재", null)))).isEmpty();
        assertThat(service.sumHoursDisplay(List.of())).isEmpty();
    }

    @Test
    void 시간_합계_표기는_뒷자리_0을_뗀다() {
        assertThat(service.sumHoursDisplay(List.of(note(WEEK_START, "a", "2.00")))).isEqualTo("2");
        assertThat(service.sumHoursDisplay(List.of(note(WEEK_START, "a", "8.50")))).isEqualTo("8.5");
    }

    @Test
    void 날짜별_묶음은_주어진_정렬_순서를_그대로_보존한다() {
        // 월 조회(내림차순)로 들어온 목록이 오름차순으로 뒤집히면 기록 화면이 조용히 뒤바뀐다.
        List<DailyNote> descending = List.of(
                note(LocalDate.of(2026, 8, 16), "나중 날", null),
                note(LocalDate.of(2026, 8, 15), "중간 날", null),
                note(LocalDate.of(2026, 8, 15), "중간 날 두 번째", null),
                note(LocalDate.of(2026, 8, 14), "이른 날", null));

        List<DailyNoteService.DayGroup> groups = service.groupByDate(descending);

        assertThat(groups).extracting(DailyNoteService.DayGroup::date)
                .containsExactly(LocalDate.of(2026, 8, 16), LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 14));
        assertThat(groups.get(1).count()).isEqualTo(2);
    }

    // ---------- 작성 패널 그룹 ----------

    @Test
    void 작성패널은_기록이_있는_날만_남기고_오름차순을_유지한다() {
        LocalDate pastWeekStart = LocalDate.of(2026, 5, 1);
        List<DailyNote> weekNotes = List.of(
                note(pastWeekStart, "금요일 일", "1"),
                note(pastWeekStart.plusDays(3), "월요일 일", "2"));

        List<DailyNoteService.DayGroup> groups = service.panelGroups(pastWeekStart, weekNotes);

        assertThat(groups).extracting(DailyNoteService.DayGroup::date)
                .containsExactly(pastWeekStart, pastWeekStart.plusDays(3));
    }

    @Test
    void 이번_주_패널에는_기록이_없어도_오늘_그룹이_빈_채로_나온다() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = WeekLabelService.forDate(today).weekStart();

        List<DailyNoteService.DayGroup> groups = service.panelGroups(weekStart, List.of());

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).date()).isEqualTo(today);
        assertThat(groups.get(0).notes()).isEmpty();
        assertThat(groups.get(0).isToday()).isTrue();
    }

    // ---------- DayGroup 표기 ----------

    @Test
    void 날짜그룹_라벨은_두자리_월일과_요일을_붙인다() {
        DailyNoteService.DayGroup group =
                new DailyNoteService.DayGroup(LocalDate.of(2026, 8, 14), List.of());

        assertThat(group.label()).isEqualTo("08.14 (금)");
        assertThat(group.longLabel()).isEqualTo("2026.08.14 (금)");
    }

    @Test
    void 날짜그룹_시간합계가_0이면_빈_문자열을_준다() {
        DailyNoteService.DayGroup empty = new DailyNoteService.DayGroup(
                LocalDate.of(2026, 8, 14), List.of(note(WEEK_START, "시간 미기재", null)));
        DailyNoteService.DayGroup filled = new DailyNoteService.DayGroup(
                LocalDate.of(2026, 8, 14), List.of(note(WEEK_START, "a", "1.5"), note(WEEK_START, "b", "2")));

        assertThat(empty.hoursDisplay()).isEmpty();
        assertThat(filled.hoursDisplay()).isEqualTo("3.5");
    }

    // ---------- CRUD ----------

    @Test
    void 텍스트가_비면_기록을_만들지_않는다() {
        assertThat(service.add(WEEK_START, "   ", new BigDecimal("2"))).isEmpty();
        assertThat(service.add(WEEK_START, null, null)).isEmpty();
        Mockito.verify(dailyNoteRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void 날짜를_안_보내면_오늘로_기록한다() {
        Mockito.when(dailyNoteRepository.save(Mockito.any())).thenAnswer(i -> i.getArgument(0));

        service.add(null, "  캡처한 일  ", null);

        ArgumentCaptor<DailyNote> saved = ArgumentCaptor.forClass(DailyNote.class);
        Mockito.verify(dailyNoteRepository).save(saved.capture());
        assertThat(saved.getValue().getWorkDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getValue().getText()).isEqualTo("캡처한 일");
    }

    @Test
    void 시간칸을_비우고_보내면_0이_아니라_미입력으로_지워진다() {
        DailyNote existing = note(WEEK_START, "기존", "3");
        Mockito.when(dailyNoteRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.update(1L, "기존", null, true);

        assertThat(existing.getHours()).isNull();
        assertThat(existing.hoursDisplay()).isEmpty();
    }

    @Test
    void 시간_파라미터를_아예_안_보내면_기존_시간이_유지된다() {
        DailyNote existing = note(WEEK_START, "기존", "3");
        Mockito.when(dailyNoteRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.update(1L, "제목만 수정", null, false);

        assertThat(existing.getText()).isEqualTo("제목만 수정");
        assertThat(existing.getHours()).isEqualByComparingTo("3");
    }

    @Test
    void 텍스트를_안_보내면_텍스트를_건드리지_않는다() {
        DailyNote existing = note(WEEK_START, "원래 텍스트", "3");
        Mockito.when(dailyNoteRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.update(1L, null, new BigDecimal("5"), true);

        assertThat(existing.getText()).isEqualTo("원래 텍스트");
        assertThat(existing.getHours()).isEqualByComparingTo("5");
    }

    @Test
    void 주_조회는_금요일부터_목요일까지_7일_범위다() {
        service.findByWeek(WEEK_START);

        Mockito.verify(dailyNoteRepository)
                .findByWorkDateBetweenOrderByWorkDateAscIdAsc(WEEK_START, LocalDate.of(2026, 8, 20));
    }

    // ---------- 검색 ----------

    @Test
    void 검색어_정규화는_앞뒤_공백을_떼고_비면_null로_접는다() {
        assertThat(DailyNoteService.normalizeQuery(null)).isNull();
        assertThat(DailyNoteService.normalizeQuery("")).isNull();
        assertThat(DailyNoteService.normalizeQuery("   ")).isNull();
        // 전각 공백(U+3000)은 한글 IME에서 나오는데 trim()으로는 안 떨어진다 —
        // 남으면 "검색 중"으로 잡혀 검색칸이 비어 보이는데 "검색 결과가 없습니다."만 뜬다.
        assertThat(DailyNoteService.normalizeQuery("　")).isNull();
        assertThat(DailyNoteService.normalizeQuery("  GTPP  ")).isEqualTo("GTPP");
    }

    @Test
    void 검색어가_비면_그_달_전체_조회와_같은_쿼리를_쓴다() {
        service.findByMonth(YearMonth.of(2026, 8), "   ");

        Mockito.verify(dailyNoteRepository).findByWorkDateBetweenOrderByWorkDateDescIdAsc(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        Mockito.verify(dailyNoteRepository, Mockito.never())
                .findByWorkDateBetweenAndTextContainingIgnoreCaseOrderByWorkDateDescIdAsc(
                        Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void 검색은_그_달_안에서만_거른다() {
        // 전체 기간 검색이 아니다 — 월 페이저가 화면의 축이라 검색이 축을 무시하면 안 된다.
        service.findByMonth(YearMonth.of(2026, 8), " GTPP ");

        Mockito.verify(dailyNoteRepository).findByWorkDateBetweenAndTextContainingIgnoreCaseOrderByWorkDateDescIdAsc(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "GTPP");
    }
}
