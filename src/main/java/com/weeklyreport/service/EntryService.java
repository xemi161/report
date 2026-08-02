package com.weeklyreport.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

/**
 * 작성 화면의 CRUD/제출 로직.
 *
 * <p>제출 여부는 상태 표시일 뿐 잠금이 아니다 — 지난 주 보고서도 항상 수정·재제출할 수 있어야 한다는
 * 요구사항에 따라 편집 가능 여부 검사는 두지 않는다.
 */
@Service
@Transactional
public class EntryService {

    /** 최근 몇 주치 제출분으로 평균 맨위크를 낼지. */
    private static final int RECENT_WEEKS_FOR_AVERAGE = 4;

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

    // ---------- 주차 조회/생성 ----------

    /**
     * 해당 주의 보고서를 찾는다. 없으면 비어 있는 Optional — 주차를 넘겨보는 것만으로
     * 빈 보고서가 줄줄이 생기지 않도록 자동 생성하지 않는다(빈 상태 화면에서 명시적으로 만든다).
     */
    @Transactional(readOnly = true)
    public Optional<WeeklyReport> findByWeekStart(LocalDate weekStart) {
        return weeklyReportRepository.findByWeekStart(weekStart);
    }

    /** 빈 상태 화면의 "이번 주 보고서 작성 시작" — 지난주 미완료 항목을 이월해 초안을 만든다. */
    public WeeklyReport createDraft(WeekPeriod period) {
        return weeklyReportRepository.findByWeekStart(period.weekStart()).orElseGet(() -> {
            WeeklyReport report = new WeeklyReport(period.label(), period.weekStart(), period.weekEnd());
            weeklyReportRepository.findByWeekStart(period.previous().weekStart())
                    .ifPresent(previous -> carryOverService.applyCarryOver(previous, report));
            return weeklyReportRepository.save(report);
        });
    }

    /** 이월된 항목이 실제로 들어왔는지 (토스트 문구 분기용). */
    public boolean hasCarriedOverItems(WeeklyReport report) {
        return report.getItems().stream().anyMatch(ReportItem::isCarriedOver);
    }

    // ---------- 프로젝트 ----------

    public Project addProject(String name) {
        Project project = new Project(name == null || name.isBlank() ? "새 프로젝트" : name.trim());
        return projectRepository.save(project);
    }

    public void renameProject(Long projectId, String newName) {
        Project project = requireProject(projectId);
        if (newName != null && !newName.isBlank()) {
            project.setName(newName.trim());
        }
    }

    /**
     * 프로젝트(=일감)의 티켓번호를 바꾼다. 프로젝트 항목의 티켓은 이 값의 복사본이므로
     * 지금 편집 중인 주의 항목들에도 함께 반영한다(과거 주는 당시 값 그대로 둔다).
     */
    public void updateProjectTicket(WeeklyReport report, Long projectId, String rawTicket) {
        Project project = requireProject(projectId);
        String completed = ticketNumberService.complete(rawTicket, currentSettings().getTicketPrefix());
        project.setTicket(completed);
        for (ReportItem item : report.getItems()) {
            if (item.getGroup() == Group.PROJECT && project.equals(item.getProject())) {
                item.setTicket(completed);
            }
        }
        weeklyReportRepository.save(report);
    }

    /** 프로젝트 카드 삭제: 이 주의 해당 프로젝트 항목을 지우고 프로젝트를 비활성화한다. */
    public void deactivateProject(WeeklyReport report, Long projectId) {
        Project project = requireProject(projectId);
        report.getItems().removeIf(item -> item.getGroup() == Group.PROJECT && project.equals(item.getProject()));
        project.setActive(false);
        weeklyReportRepository.save(report);
    }

    // ---------- 항목 ----------

    public ReportItem addItem(WeeklyReport report, ItemForm form) {
        Group group = Group.valueOf(form.getGroup());
        ReportItem item = ReportItem.forGroup(group);
        item.setSortOrder(report.getItems().size());
        applyForm(item, form);
        report.addItem(item);
        weeklyReportRepository.save(report);
        return item;
    }

    public void updateItem(WeeklyReport report, Long itemId, ItemForm form) {
        ReportItem item = findItem(report, itemId);
        applyForm(item, form);
        weeklyReportRepository.save(report);
    }

    public void deleteItem(WeeklyReport report, Long itemId) {
        ReportItem item = findItem(report, itemId);
        report.removeItem(item);
        weeklyReportRepository.save(report);
    }

    /**
     * 휴가 항목의 하루 ↔ 기간 전환. 종료일 유무가 곧 기간 여부라 별도 플래그를 두지 않고,
     * 기간으로 켤 때는 시작일과 같은 날로 채워 사용자가 조정하게 한다.
     */
    public void toggleVacationPeriod(WeeklyReport report, Long itemId) {
        ReportItem item = findItem(report, itemId);
        if (item.getGroup() != Group.VACATION) {
            throw new IllegalStateException("휴가 항목만 기간으로 전환할 수 있습니다.");
        }
        if (item.isPeriodVacation()) {
            item.setEndDate(null);
        } else {
            item.setEndDate(item.getDate() != null ? item.getDate() : LocalDate.now());
        }
        weeklyReportRepository.save(report);
    }

    /** 개발 그룹 항목을 기존/신규 프로젝트로 편입시켜 프로젝트 그룹으로 이동. */
    public void moveDevItemToProject(WeeklyReport report, Long itemId, Long projectId, String newProjectName) {
        ReportItem item = findItem(report, itemId);
        if (item.getGroup() != Group.DEV) {
            throw new IllegalStateException("개발 그룹 항목만 프로젝트로 이동할 수 있습니다.");
        }
        Project project = resolveProject(projectId, newProjectName);
        item.setGroup(Group.PROJECT);
        item.setProject(project);
        item.setTicket(project.getTicket());
        weeklyReportRepository.save(report);
    }

    private void applyForm(ReportItem item, ItemForm form) {
        Group group = item.getGroup();
        if (group == Group.PROJECT) {
            Project project = resolveProject(form.getProjectId(), form.getNewProjectName());
            item.setProject(project);
            // 프로젝트 = 일감 하나: 티켓번호는 항목이 아니라 프로젝트 카드에서 관리한다.
            item.setTicket(project.getTicket());
        }
        if (group == Group.DEV) {
            item.setTicket(ticketNumberService.complete(form.getTicket(), currentSettings().getTicketPrefix()));
        }
        if (group == Group.PROJECT || group == Group.DEV) {
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
            item.setEndDate(form.getEndDate());
        }
        item.setTitle(form.getTitle());
        item.setHours(form.getHours());
    }

    private Project resolveProject(Long projectId, String newProjectName) {
        if (projectId != null) {
            return requireProject(projectId);
        }
        if (newProjectName != null && !newProjectName.isBlank()) {
            return addProject(newProjectName);
        }
        throw new IllegalArgumentException("프로젝트 그룹 항목은 기존 프로젝트를 선택하거나 새 프로젝트명을 입력해야 합니다.");
    }

    private Project requireProject(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 프로젝트입니다: " + projectId));
    }

    private ReportItem findItem(WeeklyReport report, Long itemId) {
        return report.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 항목입니다: " + itemId));
    }

    private AppSettings currentSettings() {
        return appSettingsRepository.findById(AppSettings.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("온보딩이 완료되지 않았습니다."));
    }

    // ---------- 통계 ----------

    /** 최근 제출된 주들의 평균 맨위크. 제출 이력이 없으면 0. */
    @Transactional(readOnly = true)
    public BigDecimal recentAverageManWeek() {
        List<WeeklyReport> recent = weeklyReportRepository
                .findTop4ByStatusOrderByWeekStartDesc(ReportStatus.SUBMITTED);
        if (recent.isEmpty()) {
            // 화면에서 "0.61"처럼 항상 소수 두 자리로 보이도록 자릿수를 맞춰둔다.
            return BigDecimal.ZERO.setScale(2);
        }
        return recent.stream()
                .map(WeeklyReport::getTotalManWeek)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(Math.min(recent.size(), RECENT_WEEKS_FOR_AVERAGE)), 2, RoundingMode.HALF_UP);
    }

    // ---------- 제출 ----------

    /** 그룹별 필수값 검증. 문제가 없으면 빈 리스트를 반환한다. */
    @Transactional(readOnly = true)
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
                    // 프로젝트 항목은 제목이 비면 프로젝트명으로 대체되므로 displayTitle 기준으로 본다.
                    if (isBlank(item.displayTitle())) {
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
                    if (item.getEndDate() != null && item.getDate() != null
                            && item.getEndDate().isBefore(item.getDate())) {
                        errors.add(prefix + "종료일은 시작일보다 빠를 수 없습니다.");
                    }
                }
            }
        }
        if (report.getItems().isEmpty()) {
            errors.add("최소 한 개 이상의 항목을 입력해야 합니다.");
        }
        return errors;
    }

    /** 제출(= md 내보내기). 이미 제출된 주도 다시 호출해 재제출할 수 있다. */
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
