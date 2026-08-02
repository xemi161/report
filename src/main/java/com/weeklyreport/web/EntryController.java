package com.weeklyreport.web;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.weeklyreport.domain.AppSettings;
import com.weeklyreport.domain.Project;
import com.weeklyreport.domain.ReportItem;
import com.weeklyreport.domain.WeeklyReport;
import com.weeklyreport.domain.enums.Group;
import com.weeklyreport.repository.AppSettingsRepository;
import com.weeklyreport.repository.ProjectRepository;
import com.weeklyreport.service.EntryService;
import com.weeklyreport.service.MdExportService;
import com.weeklyreport.service.WeekLabelService;
import com.weeklyreport.service.WeekPeriod;
import com.weeklyreport.web.dto.ItemForm;

/**
 * "작성" 탭. 대시보드를 없애고 통계·입력·제출을 이 화면 하나로 합쳤다.
 *
 * <p>변경(추가/삭제/토글)은 htmx로 {@code entry :: writeView} 프래그먼트만 다시 렌더링해 돌려주고,
 * 인라인 필드 수정은 화면을 갈아끼우면 입력 포커스가 날아가므로 저장만 하고 응답을 버린다
 * (템플릿에서 {@code hx-swap="none"}).
 */
@Controller
public class EntryController {

    private final EntryService entryService;
    private final ProjectRepository projectRepository;
    private final AppSettingsRepository appSettingsRepository;
    private final MdExportService mdExportService;

    public EntryController(EntryService entryService,
                            ProjectRepository projectRepository,
                            AppSettingsRepository appSettingsRepository,
                            MdExportService mdExportService) {
        this.entryService = entryService;
        this.projectRepository = projectRepository;
        this.appSettingsRepository = appSettingsRepository;
        this.mdExportService = mdExportService;
    }

    // ---------- 화면 ----------

    @GetMapping({"/", "/entry"})
    public String entry(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                        Model model) {
        populateWriteView(model, week);
        return "entry";
    }

    @PostMapping("/entry/start")
    public String startDraft(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                             Model model) {
        WeekPeriod period = WeekLabelService.forWeekStart(week);
        WeeklyReport report = entryService.createDraft(period);
        model.addAttribute("toast", entryService.hasCarriedOverItems(report)
                ? "보고서 초안을 만들었어요 (이월 항목 포함)"
                : "보고서 초안을 만들었어요");
        populateWriteView(model, week);
        return "entry :: writeView";
    }

    // ---------- 프로젝트 ----------

    @PostMapping("/entry/projects")
    public String addProject(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                             @RequestParam(required = false) String name,
                             Model model) {
        entryService.addProject(name);
        populateWriteView(model, week);
        return "entry :: writeView";
    }

    @PostMapping("/entry/projects/{id}/rename")
    public String renameProject(@PathVariable Long id, @RequestParam String name) {
        entryService.renameProject(id, name);
        return "fragments-entry :: noop";
    }

    @PostMapping("/entry/projects/{id}/ticket")
    public String updateProjectTicket(@PathVariable Long id,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                                       @RequestParam(required = false) String ticket) {
        requireReport(week).ifPresent(report -> entryService.updateProjectTicket(report, id, ticket));
        return "fragments-entry :: noop";
    }

    @PostMapping("/entry/projects/{id}/delete")
    public String deleteProject(@PathVariable Long id,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                                 Model model) {
        requireReport(week).ifPresent(report -> entryService.deactivateProject(report, id));
        populateWriteView(model, week);
        return "entry :: writeView";
    }

    // ---------- 항목 ----------

    @PostMapping("/entry/items")
    public String addItem(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                          @ModelAttribute ItemForm form,
                          Model model) {
        requireReport(week).ifPresent(report -> {
            try {
                entryService.addItem(report, form);
            } catch (RuntimeException e) {
                model.addAttribute("error", e.getMessage());
            }
        });
        populateWriteView(model, week);
        return "entry :: writeView";
    }

    /** 인라인 필드 수정: 저장만 하고 화면은 그대로 둔다(포커스 유지). */
    @PostMapping("/entry/items/{id}")
    public String updateItem(@PathVariable Long id,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                             @ModelAttribute ItemForm form) {
        requireReport(week).ifPresent(report -> entryService.updateItem(report, id, form));
        return "fragments-entry :: noop";
    }

    @PostMapping("/entry/items/{id}/delete")
    public String deleteItem(@PathVariable Long id,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                             Model model) {
        requireReport(week).ifPresent(report -> entryService.deleteItem(report, id));
        populateWriteView(model, week);
        return "entry :: writeView";
    }

    @PostMapping("/entry/items/{id}/vacation-period")
    public String toggleVacationPeriod(@PathVariable Long id,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                                        Model model) {
        requireReport(week).ifPresent(report -> entryService.toggleVacationPeriod(report, id));
        populateWriteView(model, week);
        return "entry :: writeView";
    }

    @PostMapping("/entry/items/{id}/move-to-project")
    public String moveToProject(@PathVariable Long id,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                                 @RequestParam(required = false) Long projectId,
                                 @RequestParam(required = false) String newProjectName,
                                 Model model) {
        requireReport(week).ifPresent(report -> {
            try {
                entryService.moveDevItemToProject(report, id, projectId, newProjectName);
            } catch (RuntimeException e) {
                model.addAttribute("error", e.getMessage());
            }
        });
        populateWriteView(model, week);
        return "entry :: writeView";
    }

    // ---------- 미리보기 / 제출 ----------

    /** 제출 전 미리보기 모달(htmx로 열림). */
    @GetMapping("/entry/preview")
    public String preview(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                          Model model) {
        WeeklyReport report = requireReport(week).orElse(null);
        AppSettings settings = appSettingsRepository.findById(AppSettings.SINGLETON_ID).orElseThrow();
        model.addAttribute("report", report);
        model.addAttribute("week", week);
        model.addAttribute("mdText", report == null ? "" : mdExportService.export(settings, report));
        model.addAttribute("errors", report == null ? List.of() : entryService.validateForSubmit(report));
        return "fragments-entry :: previewModal";
    }

    /**
     * 제출 후 곧바로 md 다운로드로 넘긴다. htmx가 아닌 일반 폼 전송이라
     * 브라우저가 첨부파일 응답을 받아 현재 화면은 그대로 두고 파일만 내려받는다.
     */
    @PostMapping("/entry/submit")
    public String submit(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate week,
                         RedirectAttributes redirectAttributes) {
        WeeklyReport report = requireReport(week).orElse(null);
        if (report == null) {
            return "redirect:/entry?week=" + week;
        }
        try {
            entryService.submit(report);
            return "redirect:/export/" + report.getId();
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/entry?week=" + week;
        }
    }

    // ---------- 뷰 모델 ----------

    private Optional<WeeklyReport> requireReport(LocalDate week) {
        return entryService.findByWeekStart(week);
    }

    /**
     * 작성 탭 렌더링에 필요한 값 일체. 주차가 지정되지 않으면 이번 주를 본다.
     * 해당 주 보고서가 없으면 report=null로 두고 템플릿이 빈 상태 화면을 그린다.
     */
    private void populateWriteView(Model model, LocalDate week) {
        WeekPeriod period = week == null
                ? WeekLabelService.forDate(LocalDate.now())
                : WeekLabelService.forWeekStart(week);
        WeeklyReport report = entryService.findByWeekStart(period.weekStart()).orElse(null);

        model.addAttribute("period", period);
        model.addAttribute("week", period.weekStart());
        model.addAttribute("prevWeek", period.previous().weekStart());
        model.addAttribute("nextWeek", period.next().weekStart());
        model.addAttribute("report", report);
        model.addAttribute("activeTab", "write");

        if (report == null) {
            model.addAttribute("projectCards", List.of());
            model.addAttribute("devItems", List.of());
            model.addAttribute("etcItems", List.of());
            model.addAttribute("vacationItems", List.of());
            return;
        }

        model.addAttribute("projectCards", buildProjectCards(report));
        model.addAttribute("devItems", filterGroup(report, Group.DEV));
        model.addAttribute("etcItems", filterGroup(report, Group.ETC));
        model.addAttribute("vacationItems", filterGroup(report, Group.VACATION));
        model.addAttribute("avgManWeek", entryService.recentAverageManWeek());
        model.addAttribute("activeProjectCount", projectRepository.findByActiveTrueOrderByNameAsc().size());
    }

    /**
     * 활성 프로젝트마다 카드 하나. "프로젝트 = 일감 하나"라 카드 헤더가 곧 일감번호이고,
     * 그 아래 세부 항목들이 이 주에 그 프로젝트로 기록된 ReportItem들이다.
     *
     * <p>이 주에 항목이 있는 프로젝트는 <b>종료(비활성)됐더라도 카드를 그린다</b> —
     * 활성 목록만으로 그리면 그 항목들이 화면에서만 사라진 채 합계·md 내보내기에는 그대로 남아
     * 손댈 수 없는 유령 데이터가 된다.
     */
    private List<ProjectCard> buildProjectCards(WeeklyReport report) {
        Map<Project, List<ReportItem>> itemsByProject = new LinkedHashMap<>();
        for (Project project : projectRepository.findByActiveTrueOrderByNameAsc()) {
            itemsByProject.put(project, new ArrayList<>());
        }
        for (ReportItem item : report.getItems()) {
            if (item.getGroup() == Group.PROJECT && item.getProject() != null) {
                itemsByProject.computeIfAbsent(item.getProject(), p -> new ArrayList<>()).add(item);
            }
        }
        return itemsByProject.entrySet().stream()
                .map(e -> new ProjectCard(e.getKey(), e.getValue()))
                .toList();
    }

    private List<ReportItem> filterGroup(WeeklyReport report, Group group) {
        return report.getItems().stream().filter(i -> i.getGroup() == group).toList();
    }

    /** 화면에서만 쓰는 묶음 — DB 구조(항목 1개 = md 1줄)는 그대로다. */
    public record ProjectCard(Project project, List<ReportItem> items) {
    }
}
