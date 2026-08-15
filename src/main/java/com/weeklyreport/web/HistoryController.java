package com.weeklyreport.web;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import com.weeklyreport.domain.WeeklyReport;
import com.weeklyreport.domain.enums.ReportStatus;
import com.weeklyreport.repository.WeeklyReportRepository;
import com.weeklyreport.service.DailyNoteService;

/**
 * "히스토리" 탭. 두 개의 서브 뷰를 담는다:
 * <ul>
 *   <li>{@code view=reports}(기본) — 제출된 과거 보고서 목록. 클릭하면 그 주의 작성 화면으로 들어가 그대로 수정할 수 있다.</li>
 *   <li>{@code view=records} — 일일 기록("한 일 기록") 월별 열람.</li>
 * </ul>
 *
 * <p>서브 뷰 상태는 새 최상위 라우트를 만들지 않고 <b>쿼리 파라미터</b>로 들고 다닌다
 * ({@code /entry?week=yyyy-MM-dd} 관례와 동일). 헤더 탭은 3개 그대로다.
 */
@Controller
public class HistoryController {

    /** 이 맨위크 미만인 주는 이상 신호로 강조 표시한다 (풀타임 1.0 기준 80% 미만). */
    private static final BigDecimal LOW_MAN_WEEK_THRESHOLD = new BigDecimal("0.80");

    private final WeeklyReportRepository weeklyReportRepository;
    private final DailyNoteService dailyNoteService;

    public HistoryController(WeeklyReportRepository weeklyReportRepository,
                              DailyNoteService dailyNoteService) {
        this.weeklyReportRepository = weeklyReportRepository;
        this.dailyNoteService = dailyNoteService;
    }

    /**
     * @param view  "reports"(기본) 또는 "records"
     * @param month view=records일 때 보고 있는 달("yyyy-MM"). 없거나 형식이 틀리면 이번 달
     * @param q     view=records일 때의 기록 검색어(부분일치, 대소문자 무시). 비어 있으면 그 달 전체.
     *              <b>검색 범위는 보고 있는 달 안으로 한정</b>하고, 월을 넘겨도 검색어는 유지한다
     *              (프론트가 월 이동 링크에 {@code recordQuery}를 실어 보낸다)
     */
    @GetMapping("/history")
    public String list(@RequestParam(required = false) String view,
                       @RequestParam(required = false) String month,
                       @RequestParam(required = false) String q,
                       Model model) {
        List<WeeklyReport> reports =
                weeklyReportRepository.findByStatusOrderByWeekStartDesc(ReportStatus.SUBMITTED);
        model.addAttribute("reports", reports);
        model.addAttribute("reportCount", reports.size());
        model.addAttribute("lowThreshold", LOW_MAN_WEEK_THRESHOLD);
        model.addAttribute("activeTab", "history");

        boolean records = "records".equals(view);
        model.addAttribute("historyView", records ? "records" : "reports");
        // 서브 세그먼트 버튼의 건수는 어느 뷰에 있든 둘 다 보여야 한다.
        model.addAttribute("dailyNoteCount", dailyNoteService.count());

        if (records) {
            DailyNoteController.populateRecordsView(model, dailyNoteService,
                    DailyNoteController.parseMonth(month), q);
        }
        return "history";
    }

    /** 예전 상세 화면은 없앴다 — 해당 주의 작성 화면이 곧 상세이자 편집 화면이다. */
    @GetMapping("/history/{id}")
    public String detail(@PathVariable Long id) {
        WeeklyReport report = weeklyReportRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return "redirect:/entry?week=" + report.getWeekStart();
    }
}
