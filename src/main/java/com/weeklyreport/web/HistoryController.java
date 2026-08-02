package com.weeklyreport.web;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import com.weeklyreport.domain.WeeklyReport;
import com.weeklyreport.domain.enums.ReportStatus;
import com.weeklyreport.repository.WeeklyReportRepository;

/** "히스토리" 탭. 목록일 뿐이고, 클릭하면 그 주의 작성 화면으로 들어가 그대로 수정할 수 있다. */
@Controller
public class HistoryController {

    /** 이 맨위크 미만인 주는 이상 신호로 강조 표시한다 (풀타임 1.0 기준 80% 미만). */
    private static final BigDecimal LOW_MAN_WEEK_THRESHOLD = new BigDecimal("0.80");

    private final WeeklyReportRepository weeklyReportRepository;

    public HistoryController(WeeklyReportRepository weeklyReportRepository) {
        this.weeklyReportRepository = weeklyReportRepository;
    }

    @GetMapping("/history")
    public String list(Model model) {
        List<WeeklyReport> reports =
                weeklyReportRepository.findByStatusOrderByWeekStartDesc(ReportStatus.SUBMITTED);
        model.addAttribute("reports", reports);
        model.addAttribute("lowThreshold", LOW_MAN_WEEK_THRESHOLD);
        model.addAttribute("activeTab", "history");
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
