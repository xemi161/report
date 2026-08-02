package com.weeklyreport.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.weeklyreport.domain.Project;
import com.weeklyreport.domain.ReportItem;
import com.weeklyreport.domain.WeeklyReport;
import com.weeklyreport.domain.enums.Group;
import com.weeklyreport.domain.enums.ReportStatus;
import com.weeklyreport.repository.ProjectRepository;
import com.weeklyreport.service.EntryService;
import com.weeklyreport.web.dto.ItemForm;

@Controller
public class EntryController {

    private final EntryService entryService;
    private final ProjectRepository projectRepository;

    public EntryController(EntryService entryService, ProjectRepository projectRepository) {
        this.entryService = entryService;
        this.projectRepository = projectRepository;
    }

    @GetMapping("/entry")
    public String entry(Model model) {
        WeeklyReport report = entryService.getOrCreateCurrentDraft();
        if (report.getStatus() == ReportStatus.SUBMITTED) {
            return "redirect:/history/" + report.getId();
        }

        List<Project> activeProjects = projectRepository.findByActiveTrueOrderByNameAsc();

        Map<Project, List<ReportItem>> projectItemsMap = new LinkedHashMap<>();
        for (Project project : activeProjects) {
            projectItemsMap.put(project, report.getItems().stream()
                    .filter(i -> i.getGroup() == Group.PROJECT && project.equals(i.getProject()))
                    .toList());
        }

        model.addAttribute("report", report);
        model.addAttribute("projects", activeProjects);
        model.addAttribute("projectItemsMap", projectItemsMap);
        model.addAttribute("devItems", filterGroup(report, Group.DEV));
        model.addAttribute("etcItems", filterGroup(report, Group.ETC));
        model.addAttribute("vacationItems", filterGroup(report, Group.VACATION));
        model.addAttribute("activeMenu", "entry");
        return "entry";
    }

    @PostMapping("/entry/projects")
    public String addProject(@RequestParam String name) {
        if (name != null && !name.isBlank()) {
            entryService.addProject(name);
        }
        return "redirect:/entry";
    }

    @PostMapping("/entry/projects/{id}/rename")
    public String renameProject(@PathVariable Long id, @RequestParam String name) {
        if (name != null && !name.isBlank()) {
            entryService.renameProject(id, name);
        }
        return "redirect:/entry";
    }

    @PostMapping("/entry/items")
    public String addItem(@ModelAttribute ItemForm form, RedirectAttributes redirectAttributes) {
        WeeklyReport report = entryService.getOrCreateCurrentDraft();
        try {
            entryService.addItem(report, form);
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/entry";
    }

    @PostMapping("/entry/items/{id}")
    public String updateItem(@PathVariable Long id, @ModelAttribute ItemForm form, RedirectAttributes redirectAttributes) {
        WeeklyReport report = entryService.getOrCreateCurrentDraft();
        try {
            entryService.updateItem(report, id, form);
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/entry";
    }

    @PostMapping("/entry/items/{id}/delete")
    public String deleteItem(@PathVariable Long id) {
        WeeklyReport report = entryService.getOrCreateCurrentDraft();
        entryService.deleteItem(report, id);
        return "redirect:/entry";
    }

    @PostMapping("/entry/items/{id}/move-to-project")
    public String moveToProject(@PathVariable Long id,
                                 @RequestParam(required = false) Long projectId,
                                 @RequestParam(required = false) String newProjectName,
                                 RedirectAttributes redirectAttributes) {
        WeeklyReport report = entryService.getOrCreateCurrentDraft();
        try {
            entryService.moveDevItemToProject(report, id, projectId, newProjectName);
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/entry";
    }

    @PostMapping("/entry/submit")
    public String submit(RedirectAttributes redirectAttributes) {
        WeeklyReport report = entryService.getOrCreateCurrentDraft();
        try {
            entryService.submit(report);
            return "redirect:/export/" + report.getId();
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/entry/preview";
        }
    }

    private List<ReportItem> filterGroup(WeeklyReport report, Group group) {
        return report.getItems().stream().filter(i -> i.getGroup() == group).toList();
    }
}
