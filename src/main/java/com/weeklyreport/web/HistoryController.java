package com.weeklyreport.web;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import com.weeklyreport.domain.AppSettings;
import com.weeklyreport.domain.WeeklyReport;
import com.weeklyreport.domain.enums.ReportStatus;
import com.weeklyreport.repository.AppSettingsRepository;
import com.weeklyreport.repository.WeeklyReportRepository;
import com.weeklyreport.service.MdExportService;

@Controller
public class HistoryController {

    /** 이 맨위크 미만인 주는 이상 신호로 강조 표시한다 (풀타임 1.0 기준 80% 미만). */
    private static final BigDecimal LOW_MAN_WEEK_THRESHOLD = new BigDecimal("0.80");

    private final WeeklyReportRepository weeklyReportRepository;
    private final AppSettingsRepository appSettingsRepository;
    private final MdExportService mdExportService;

    public HistoryController(WeeklyReportRepository weeklyReportRepository,
                              AppSettingsRepository appSettingsRepository,
                              MdExportService mdExportService) {
        this.weeklyReportRepository = weeklyReportRepository;
        this.appSettingsRepository = appSettingsRepository;
        this.mdExportService = mdExportService;
    }

    @GetMapping("/history")
    public String list(Model model) {
        List<WeeklyReport> reports = weeklyReportRepository.findByStatusOrderByWeekStartDesc(ReportStatus.SUBMITTED);
        model.addAttribute("reports", reports);
        model.addAttribute("lowThreshold", LOW_MAN_WEEK_THRESHOLD);
        model.addAttribute("activeMenu", "history");
        return "history";
    }

    @GetMapping("/history/{id}")
    public String detail(@PathVariable Long id, Model model) {
        WeeklyReport report = weeklyReportRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (report.getStatus() != ReportStatus.SUBMITTED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        AppSettings settings = appSettingsRepository.findById(AppSettings.SINGLETON_ID).orElseThrow();
        model.addAttribute("report", report);
        model.addAttribute("mdText", mdExportService.export(settings, report));
        model.addAttribute("activeMenu", "history");
        return "history-detail";
    }
}
