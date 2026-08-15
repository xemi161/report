package com.weeklyreport.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weeklyreport.domain.DailyNote;
import com.weeklyreport.repository.DailyNoteRepository;

/**
 * 일일 기록("오늘 한 일")의 조회/집계/CRUD.
 *
 * <p><b>주간보고 통계와 완전히 분리되어 있다</b> — 여기서 계산하는 시간 합계는
 * {@code ManWeekService}의 총 투입시간/맨위크와 아무 관련이 없고 서로 섞이지 않는다.
 * 기록의 시간은 선택 입력이라 언제나 부분값이므로 "기록된 시간"이라는 별도 이름으로만 노출한다.
 */
@Service
@Transactional
public class DailyNoteService {

    private final DailyNoteRepository dailyNoteRepository;

    public DailyNoteService(DailyNoteRepository dailyNoteRepository) {
        this.dailyNoteRepository = dailyNoteRepository;
    }

    // ---------- 조회 ----------

    /** 그 주(금~목) 기록을 오름차순으로. weekStart는 금요일이어야 한다. */
    @Transactional(readOnly = true)
    public List<DailyNote> findByWeek(LocalDate weekStart) {
        return dailyNoteRepository.findByWorkDateBetweenOrderByWorkDateAscIdAsc(weekStart, weekStart.plusDays(6));
    }

    /** 그 달 기록을 <b>내림차순</b>(최신 위)으로. 작성 패널과 정렬 방향이 반대인 것은 의도된 설계다. */
    @Transactional(readOnly = true)
    public List<DailyNote> findByMonth(YearMonth month) {
        return dailyNoteRepository.findByWorkDateBetweenOrderByWorkDateDescIdAsc(
                month.atDay(1), month.atEndOfMonth());
    }

    /**
     * 그 달 기록을 검색어로 걸러 <b>내림차순</b>으로. 검색어가 비어 있으면(null/공백)
     * {@link #findByMonth(YearMonth)}와 완전히 같은 결과다 — 화면 쪽에서 "검색 중인가"를
     * 따로 분기하지 않고 이 메서드 하나만 부를 수 있게 하기 위해서다.
     *
     * <p>검색 범위는 <b>그 달 안</b>으로 고정한다(전체 기간 검색이 아님) — 월 페이저가 화면의
     * 축이므로, 검색이 그 축을 무시하고 결과를 섞어 오면 페이저와 목록이 서로 다른 것을 가리키게 된다.
     */
    @Transactional(readOnly = true)
    public List<DailyNote> findByMonth(YearMonth month, String query) {
        String q = normalizeQuery(query);
        if (q == null) {
            return findByMonth(month);
        }
        return dailyNoteRepository.findByWorkDateBetweenAndTextContainingIgnoreCaseOrderByWorkDateDescIdAsc(
                month.atDay(1), month.atEndOfMonth(), q);
    }

    /**
     * 검색어 정규화: 앞뒤 공백을 떼고, 비면 null(=검색 안 함)로 접는다.
     *
     * <p>{@code trim()}이 아니라 {@code strip()}인 이유: trim은 U+0020 이하만 떼므로
     * 한글 IME의 전각 공백(U+3000)이 그대로 남아 "공백만 친 검색"이 검색 중으로 잡힌다
     * (검색칸은 비어 보이는데 "검색 결과가 없습니다."만 뜬다). strip은 유니코드 공백을 전부 뗀다.
     */
    public static String normalizeQuery(String query) {
        if (query == null) {
            return null;
        }
        String stripped = query.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    /** 하루치. */
    @Transactional(readOnly = true)
    public List<DailyNote> findByDate(LocalDate date) {
        return dailyNoteRepository.findByWorkDateBetweenOrderByWorkDateAscIdAsc(date, date);
    }

    /** 기록이 존재하는 가장 이른 달(없으면 비어 있음) — 월 페이저의 이전 버튼 한계. */
    @Transactional(readOnly = true)
    public Optional<YearMonth> earliestMonth() {
        return dailyNoteRepository.findTopByOrderByWorkDateAsc().map(n -> YearMonth.from(n.getWorkDate()));
    }

    /** 기록이 존재하는 가장 늦은 달(없으면 비어 있음) — 월 페이저의 다음 버튼 한계. */
    @Transactional(readOnly = true)
    public Optional<YearMonth> latestMonth() {
        return dailyNoteRepository.findTopByOrderByWorkDateDesc().map(n -> YearMonth.from(n.getWorkDate()));
    }

    @Transactional(readOnly = true)
    public long count() {
        return dailyNoteRepository.count();
    }

    // ---------- 집계 ----------

    /**
     * 이미 정렬된 목록을 날짜별로 묶는다(입력 순서를 그대로 보존하므로 주 조회는 오름차순,
     * 월 조회는 내림차순 그룹이 나온다). 화면 세 곳이 전부 이 묶음을 렌더링한다.
     */
    public List<DayGroup> groupByDate(List<DailyNote> notes) {
        Map<LocalDate, List<DailyNote>> byDate = new LinkedHashMap<>();
        for (DailyNote note : notes) {
            byDate.computeIfAbsent(note.getWorkDate(), d -> new ArrayList<>()).add(note);
        }
        return byDate.entrySet().stream()
                .map(e -> new DayGroup(e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * 작성 화면 좌측 패널용 날짜 그룹. 그 주 7일 중 <b>기록이 있는 날 + (이번 주라면) 오늘</b>만 남긴다 —
     * 빈 주말 행으로 목록을 늘리지 않기 위해서다. 오늘 그룹은 기록이 없어도 항상 나오며 이때 notes가 비어 있다
     * (화면에서 "기록 없음" 안내를 그리는 자리).
     *
     * @param weekNotes {@link #findByWeek(LocalDate)}로 이미 오름차순 조회해둔 그 주 기록
     */
    public List<DayGroup> panelGroups(LocalDate weekStart, List<DailyNote> weekNotes) {
        LocalDate today = LocalDate.now();
        Map<LocalDate, List<DailyNote>> byDate = new LinkedHashMap<>();
        for (DailyNote note : weekNotes) {
            byDate.computeIfAbsent(note.getWorkDate(), d -> new ArrayList<>()).add(note);
        }
        List<DayGroup> groups = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            List<DailyNote> notes = byDate.get(date);
            if (notes == null && !date.equals(today)) {
                continue;
            }
            groups.add(new DayGroup(date, notes == null ? List.of() : notes));
        }
        return groups;
    }

    /** 시간이 비어 있는 기록(=선택 입력 미기재)은 0으로 취급해 더한다. */
    public BigDecimal sumHours(List<DailyNote> notes) {
        return notes.stream()
                .map(DailyNote::getHours)
                .filter(h -> h != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 합계 표기. <b>합이 0이면 빈 문자열</b>을 돌려준다 — "0h"를 들이밀면 채워야 할 빈칸으로 읽혀
     * "시간은 선택 입력"이라는 성격과 충돌한다(화면에서 아예 렌더링하지 않기 위한 신호).
     */
    public String sumHoursDisplay(List<DailyNote> notes) {
        BigDecimal sum = sumHours(notes);
        return sum.signum() == 0 ? "" : strip(sum);
    }

    // ---------- CRUD ----------

    /**
     * 기록 추가. 텍스트가 비면 아무것도 만들지 않고 빈 Optional을 돌려준다
     * (Enter 연타로 빈 줄이 쌓이지 않게).
     */
    public Optional<DailyNote> add(LocalDate workDate, String text, BigDecimal hours) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        LocalDate date = workDate == null ? LocalDate.now() : workDate;
        return Optional.of(dailyNoteRepository.save(new DailyNote(date, text.trim(), hours)));
    }

    /**
     * 인라인 수정. 각 칸이 따로 change 이벤트를 쏘므로 <b>전달된 값만</b> 반영한다 —
     * text가 null이면 텍스트를 건드리지 않고, hours는 빈 값이 곧 "지움(null)"이라
     * {@code hoursProvided}로 전달 여부를 따로 받는다.
     */
    public void update(Long id, String text, BigDecimal hours, boolean hoursProvided) {
        DailyNote note = requireNote(id);
        if (text != null) {
            note.setText(text.trim());
        }
        if (hoursProvided) {
            note.setHours(hours);
        }
    }

    public void delete(Long id) {
        dailyNoteRepository.deleteById(id);
    }

    private DailyNote requireNote(Long id) {
        return dailyNoteRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 기록입니다: " + id));
    }

    // ---------- 표기 헬퍼 ----------

    /** "8.50" → "8.5", "2.00" → "2". ReportItem.hoursDisplay()와 같은 규칙. */
    static String strip(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() < 0) {
            stripped = stripped.setScale(0);
        }
        return stripped.toPlainString();
    }

    /**
     * 하루치 기록 묶음. 날짜 헤더(라벨/오늘 칩/합계)와 그 아래 기록 줄들을 한 덩어리로 넘긴다 —
     * 대시보드 카드·작성 패널·기록 화면이 전부 같은 컴포넌트를 쓴다.
     */
    public record DayGroup(LocalDate date, List<DailyNote> notes) {

        public int count() {
            return notes.size();
        }

        /** "08.13 (목)" — 주/월 화면의 기본 날짜 헤더. */
        public String label() {
            return String.format("%02d.%02d (%s)", date.getMonthValue(), date.getDayOfMonth(), dow());
        }

        /** "2026.08.13 (목)" — 연도가 필요한 자리(월을 넘나드는 목록 등)용. */
        public String longLabel() {
            return String.format("%d.%02d.%02d (%s)",
                    date.getYear(), date.getMonthValue(), date.getDayOfMonth(), dow());
        }

        public boolean isToday() {
            return date.equals(LocalDate.now());
        }

        public BigDecimal totalHours() {
            return notes.stream()
                    .map(DailyNote::getHours)
                    .filter(h -> h != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        /** 합이 0이면 빈 문자열(날짜 헤더에 합계를 아예 그리지 않기 위한 신호). */
        public String hoursDisplay() {
            BigDecimal sum = totalHours();
            return sum.signum() == 0 ? "" : strip(sum);
        }

        private String dow() {
            return date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN);
        }
    }
}
