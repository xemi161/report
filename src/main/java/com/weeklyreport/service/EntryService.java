package com.weeklyreport.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weeklyreport.domain.AppSettings;
import com.weeklyreport.domain.Project;
import com.weeklyreport.domain.ReportItem;
import com.weeklyreport.domain.WeeklyReport;
import com.weeklyreport.domain.enums.Group;
import com.weeklyreport.domain.enums.Phase;
import com.weeklyreport.domain.enums.ReportStatus;
import com.weeklyreport.repository.AppSettingsRepository;
import com.weeklyreport.repository.ProjectRepository;
import com.weeklyreport.repository.WeeklyReportRepository;
import com.weeklyreport.web.dto.ItemForm;

/** 등록(입력) 화면의 CRUD/제출 로직. */
@Service
@Transactional
public class EntryService {

    private final WeeklyReportRepository weeklyReportRepository;
    private final ProjectRepository projectRepository;
    private final AppSettingsRepository appSettingsRepository;
    private final CarryOverService carryOverService;
    private final ManWeekService manWeekService;
    private final TicketNumberService ticketNumberService;

    public EntryService(WeeklyReportRepository weeklyReportRepository,
                         ProjectRepository projectRepository,
                         AppSettingsRepository appSettingsRepository,
                         CarryOverService carryOverService,
                         ManWeekService manWeekService,
                         TicketNumberService ticketNumberService) {
        this.weeklyReportRepository = weeklyReportRepository;
        this.projectRepository = projectRepository;
        this.appSettingsRepository = appSettingsRepository;
        this.carryOverService = carryOverService;
        this.manWeekService = manWeekService;
        this.ticketNumberService = ticketNumberService;
    }

    public WeeklyReport getOrCreateCurrentDraft() {
        WeekPeriod current = WeekLabelService.forDate(java.time.LocalDate.now());
        return weeklyReportRepository.findByWeekStart(current.weekStart())
                .orElseGet(() -> createDraft(current));
    }

    private WeeklyReport createDraft(WeekPeriod period) {
        WeeklyReport report = new WeeklyReport(period.label(), period.weekStart(), period.weekEnd());
        weeklyReportRepository.findByWeekStart(period.previous().weekStart())
                .ifPresent(previous -> carryOverService.applyCarryOver(previous, report));
        return weeklyReportRepository.save(report);
    }

    public Project addProject(String name) {
        Project project = new Project(name.trim());
        return projectRepository.save(project);
    }

    public void renameProject(Long projectId, String newName) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 프로젝트입니다: " + projectId));
        project.setName(newName.trim());
    }

    public ReportItem addItem(WeeklyReport report, ItemForm form) {
        requireEditable(report);
        Group group = Group.valueOf(form.getGroup());
        ReportItem item = ReportItem.forGroup(group);
        item.setSortOrder(report.getItems().size());
        applyForm(item, form);
        report.addItem(item);
        weeklyReportRepository.save(report);
        return item;
    }

    public void updateItem(WeeklyReport report, Long itemId, ItemForm form) {
        requireEditable(report);
        ReportItem item = findItem(report, itemId);
        applyForm(item, form);
        weeklyReportRepository.save(report);
    }

    public void deleteItem(WeeklyReport report, Long itemId) {
        requireEditable(report);
        ReportItem item = findItem(report, itemId);
        report.removeItem(item);
        weeklyReportRepository.save(report);
    }

    /** 개발 그룹 항목을 기존/신규 프로젝트로 편입시켜 프로젝트 그룹으로 이동. */
    public void moveDevItemToProject(WeeklyReport report, Long itemId, Long projectId, String newProjectName) {
        requireEditable(report);
        ReportItem item = findItem(report, itemId);
        if (item.getGroup() != Group.DEV) {
            throw new IllegalStateException("개발 그룹 항목만 프로젝트로 이동할 수 있습니다.");
        }
        Project project = resolveProject(projectId, newProjectName);
        item.setGroup(Group.PROJECT);
        item.setProject(project);
        weeklyReportRepository.save(report);
    }

    private void applyForm(ReportItem item, ItemForm form) {
        Group group = item.getGroup();
        if (group == Group.PROJECT) {
            item.setProject(resolveProject(form.getProjectId(), form.getNewProjectName()));
        }
        if (group == Group.PROJECT || group == Group.DEV) {
            AppSettings settings = currentSettings();
            item.setTicket(ticketNumberService.complete(form.getTicket(), settings.getTicketPrefix()));
            item.setPhase(Phase.fromLabel(form.getPhase()));
            item.setDays(form.getDays());
            item.setCompletion(form.getCompletion());
            item.setDevDoneDate(form.getDevDoneDate());
            item.setTestDate(form.getTestDate());
            item.setDeployDate(form.getDeployDate());
            item.setNote(form.getNote());
        }
        if (group == Group.VACATION) {
            item.setDate(form.getDate());
        }
        item.setTitle(form.getTitle());
        item.setHours(form.getHours());
    }

    private Project resolveProject(Long projectId, String newProjectName) {
        if (projectId != null) {
            return projectRepository.findById(projectId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 프로젝트입니다: " + projectId));
        }
        if (newProjectName != null && !newProjectName.isBlank()) {
            return addProject(newProjectName);
        }
        throw new IllegalArgumentException("프로젝트 그룹 항목은 기존 프로젝트를 선택하거나 새 프로젝트명을 입력해야 합니다.");
    }

    private ReportItem findItem(WeeklyReport report, Long itemId) {
        return report.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 항목입니다: " + itemId));
    }

    private void requireEditable(WeeklyReport report) {
        if (!report.isEditable()) {
            throw new IllegalStateException("제출된 보고서는 수정할 수 없습니다.");
        }
    }

    private AppSettings currentSettings() {
        return appSettingsRepository.findById(AppSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("온보딩이 완료되지 않았습니다."));
    }

    /** 그룹별 필수값 검증. 문제가 없으면 빈 리스트를 반환한다. */
    public List<String> validateForSubmit(WeeklyReport report) {
        List<String> errors = new ArrayList<>();
        int index = 0;
        for (ReportItem item : report.getItems()) {
            index++;
            String prefix = index + "번째 항목(" + item.getGroup().label() + ") - ";
            switch (item.getGroup()) {
                case PROJECT, DEV -> {
                    if (isBlank(item.getTicket())) {
                        errors.add(prefix + "티켓번호는 필수입니다.");
                    }
                    if (isBlank(item.getTitle())) {
                        errors.add(prefix + "업무명은 필수입니다.");
                    }
                    if (item.getCompletion() == null) {
                        errors.add(prefix + "완료율은 필수입니다.");
                    }
                }
                case ETC -> {
                    if (isBlank(item.getTitle())) {
                        errors.add(prefix + "업무명은 필수입니다.");
                    }
                }
                case VACATION -> {
                    if (item.getDate() == null) {
                        errors.add(prefix + "날짜는 필수입니다.");
                    }
                }
            }
        }
        if (report.getItems().isEmpty()) {
            errors.add("최소 한 개 이상의 항목을 입력해야 합니다.");
        }
        return errors;
    }

    public void submit(WeeklyReport report) {
        List<String> errors = validateForSubmit(report);
        if (!errors.isEmpty()) {
            throw new IllegalStateException(String.join(" / ", errors));
        }
        report.setStatus(ReportStatus.SUBMITTED);
        report.setSubmittedAt(LocalDateTime.now());
        report.setTotalHours(manWeekService.totalHours(report.getItems()));
        report.setTotalManWeek(manWeekService.totalManWeek(report.getItems()));
        weeklyReportRepository.save(report);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
