package com.weeklyreport.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.weeklyreport.domain.AppSettings;
import com.weeklyreport.domain.Project;
import com.weeklyreport.domain.ReportItem;
import com.weeklyreport.domain.WeeklyReport;
import com.weeklyreport.domain.enums.ReportStatus;
import com.weeklyreport.repository.AppSettingsRepository;
import com.weeklyreport.repository.ProjectRepository;
import com.weeklyreport.repository.ReportItemRepository;
import com.weeklyreport.repository.WeeklyReportRepository;
import com.weeklyreport.service.WeekLabelService;
import com.weeklyreport.service.WeekPeriod;

@Controller
public class DashboardController {

    private final AppSettingsRepository appSettingsRepository;
    private final WeeklyReportRepository weeklyReportRepository;
    private final ProjectRepository projectRepository;
    private final ReportItemRepository reportItemRepository;
    private final WeekLabelService weekLabelService;

    public DashboardController(AppSettingsRepository appSettingsRepository,
                                WeeklyReportRepository weeklyReportRepository,
                                ProjectRepository projectRepository,
                                ReportItemRepository reportItemRepository,
                                WeekLabelService weekLabelService) {
        this.appSettingsRepository = appSettingsRepository;
        this.weeklyReportRepository = weeklyReportRepository;
        this.projectRepository = projectRepository;
        this.reportItemRepository = reportItemRepository;
        this.weekLabelService = weekLabelService;
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        AppSettings settings = appSettingsRepository.findById(AppSettings.SINGLETON_ID).orElseThrow();
        WeekPeriod current = weekLabelService.current();
        WeeklyReport currentReport = weeklyReportRepository.findByWeekStart(current.weekStart()).orElse(null);
        boolean currentSubmitted = currentReport != null && currentReport.getStatus() == ReportStatus.SUBMITTED;

        List<WeeklyReport> recent4 = weeklyReportRepository.findTop4ByStatusOrderByWeekStartDesc(ReportStatus.SUBMITTED);
        BigDecimal avgManWeek = recent4.isEmpty() ? BigDecimal.ZERO
                : recent4.stream().map(WeeklyReport::getTotalManWeek).reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(recent4.size()), 2, RoundingMode.HALF_UP);

        List<Project> activeProjects = projectRepository.findByActiveTrueOrderByNameAsc();
        List<ProjectProgress> progresses = activeProjects.stream().map(this::progressFor).toList();

        List<WeeklyReport> recentHistory = weeklyReportRepository.findTop3ByStatusOrderByWeekStartDesc(ReportStatus.SUBMITTED);

        model.addAttribute("settings", settings);
        model.addAttribute("weekLabel", current.label());
        model.addAttribute("weekStart", current.weekStart());
        model.addAttribute("weekEnd", current.weekEnd());
        model.addAttribute("needsReport", !currentSubmitted);
        model.addAttribute("avgManWeek", avgManWeek);
        model.addAttribute("activeProjectCount", activeProjects.size());
        model.addAttribute("projectProgresses", progresses);
        model.addAttribute("recentHistory", recentHistory);
        model.addAttribute("activeMenu", "dashboard");
        return "dashboard";
    }

    private ProjectProgress progressFor(Project project) {
        List<ReportItem> items = reportItemRepository.findByProjectOrderByWeeklyReport_WeekStartDesc(project);
        Integer completion = items.isEmpty() ? null : items.get(0).getCompletion();
        return new ProjectProgress(project.getName(), completion == null ? 0 : completion);
    }

    public record ProjectProgress(String name, int completion) {
    }
}
