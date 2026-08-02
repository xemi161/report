package com.weeklyreport.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.weeklyreport.domain.AppSettings;
import com.weeklyreport.domain.Project;
import com.weeklyreport.domain.ReportItem;
import com.weeklyreport.domain.WeeklyReport;
import com.weeklyreport.domain.enums.Group;

/**
 * weekly-report-md-schema.md 에 정의된 형식 그대로
 * 사람이 읽는 마크다운 본문 + {@code <!--DATA ... DATA-->} JSON 블록을 생성한다.
 */
@Service
public class MdExportService {

    private static final DateTimeFormatter MD_MONTH_DAY = DateTimeFormatter.ofPattern("MM.dd");
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .configure(SerializationFeature.INDENT_OUTPUT, true);

    private final ManWeekService manWeekService;

    public MdExportService(ManWeekService manWeekService) {
        this.manWeekService = manWeekService;
    }

    public String fileName(AppSettings settings, WeeklyReport report) {
        return "주간보고_" + settings.getName() + "_" + report.getWeekLabel().replace(" ", "") + ".md";
    }

    public String export(AppSettings settings, WeeklyReport report) {
        StringBuilder sb = new StringBuilder();
        appendHumanBody(sb, settings, report);
        sb.append("<!--DATA\n");
        sb.append(buildJson(settings, report));
        sb.append("\nDATA-->\n");
        return sb.toString();
    }

    // ---------- 사람이 읽는 영역 ----------

    private void appendHumanBody(StringBuilder sb, AppSettings settings, WeeklyReport report) {
        sb.append("# ").append(settings.getName()).append(" · ").append(report.getWeekLabel()).append("\n\n");
        sb.append(settings.getRole()).append(" · ")
                .append(formatKoreanDate(report.getWeekStart())).append(" ~ ")
                .append(formatKoreanDate(report.getWeekEnd())).append("\n\n");

        appendProjectGroup(sb, report);
        appendSimpleProjectlessGroup(sb, report, Group.DEV);
        appendEtcOrVacation(sb, report, Group.ETC);
        appendEtcOrVacation(sb, report, Group.VACATION);

        sb.append("---\n\n");
        BigDecimal totalHours = manWeekService.totalHours(report.getItems());
        BigDecimal totalManWeek = manWeekService.totalManWeek(report.getItems());
        sb.append("**합계: ").append(formatPlainNumber(totalHours)).append("h / ")
                .append(totalManWeek.toPlainString()).append(" 맨위크**\n\n");
    }

    private void appendProjectGroup(StringBuilder sb, WeeklyReport report) {
        List<ReportItem> items = report.getItems().stream()
                .filter(i -> i.getGroup() == Group.PROJECT)
                .toList();
        if (items.isEmpty()) {
            return;
        }
        sb.append("## 프로젝트\n\n");
        Set<Project> projects = new LinkedHashSet<>();
        for (ReportItem item : items) {
            projects.add(item.getProject());
        }
        for (Project project : projects) {
            sb.append("### ").append(project.getName()).append("\n\n");
            for (ReportItem item : items) {
                if (item.getProject() != null && item.getProject().equals(project)) {
                    appendProjectOrDevLine(sb, item);
                }
            }
            sb.append("\n");
        }
    }

    private void appendSimpleProjectlessGroup(StringBuilder sb, WeeklyReport report, Group group) {
        List<ReportItem> items = report.getItems().stream()
                .filter(i -> i.getGroup() == group)
                .toList();
        if (items.isEmpty()) {
            return;
        }
        sb.append("## ").append(group.label()).append("\n\n");
        for (ReportItem item : items) {
            appendProjectOrDevLine(sb, item);
        }
        sb.append("\n");
    }

    private void appendProjectOrDevLine(StringBuilder sb, ReportItem item) {
        sb.append("- ");
        if (item.getTicket() != null && !item.getTicket().isBlank()) {
            sb.append(item.getTicket()).append(" : ");
        }
        sb.append(item.getTitle());
        if (item.getPhase() != null) {
            sb.append(" [").append(item.getPhase().shortLabel()).append("]");
        }
        sb.append(" — ");
        if (item.getHours() != null) {
            sb.append(formatPlainNumber(item.getHours())).append("h · ");
        }
        sb.append(item.getCompletion() == null ? 0 : item.getCompletion()).append("%");
        if (item.isCarriedOver()) {
            sb.append(" (이월)");
        }
        sb.append("\n");

        if (item.getDevDoneDate() != null) {
            sb.append("  - 개발완료일: ").append(item.getDevDoneDate()).append("\n");
        }
        if (item.getTestDate() != null) {
            sb.append("  - 테스트예정일: ").append(item.getTestDate()).append("\n");
        }
        if (item.getDeployDate() != null) {
            sb.append("  - 배포예정일: ").append(item.getDeployDate()).append("\n");
        }
        if (item.getNote() != null && !item.getNote().isBlank()) {
            sb.append("  - 비고: ").append(item.getNote()).append("\n");
        }
    }

    private void appendEtcOrVacation(StringBuilder sb, WeeklyReport report, Group group) {
        List<ReportItem> items = report.getItems().stream()
                .filter(i -> i.getGroup() == group)
                .toList();
        if (items.isEmpty()) {
            return;
        }
        sb.append("## ").append(group.label()).append("\n\n");
        for (ReportItem item : items) {
            String label = group == Group.VACATION ? item.getDate().toString() : item.getTitle();
            sb.append("- ").append(label);
            if (item.getHours() != null) {
                sb.append(" — ").append(formatPlainNumber(item.getHours())).append("h");
            }
            sb.append("\n");
        }
        sb.append("\n");
    }

    private String formatKoreanDate(LocalDate date) {
        return MD_MONTH_DAY.format(date) + " (" + koreanDayOfWeek(date.getDayOfWeek()) + ")";
    }

    private String koreanDayOfWeek(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> "월";
            case TUESDAY -> "화";
            case WEDNESDAY -> "수";
            case THURSDAY -> "목";
            case FRIDAY -> "금";
            case SATURDAY -> "토";
            case SUNDAY -> "일";
        };
    }

    private String formatPlainNumber(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() < 0) {
            stripped = stripped.setScale(0);
        }
        return stripped.toPlainString();
    }

    // ---------- 기계가 읽는 영역 (JSON) ----------

    private String buildJson(AppSettings settings, WeeklyReport report) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("schemaVersion", 1);
        root.put("name", settings.getName());
        root.put("role", settings.getRole());
        root.put("weekLabel", report.getWeekLabel());
        root.put("weekStart", report.getWeekStart().toString());
        root.put("weekEnd", report.getWeekEnd().toString());
        root.put("items", report.getItems().stream().map(this::itemJson).toList());
        root.put("totalHours", numberForJson(manWeekService.totalHours(report.getItems())));
        root.put("totalManWeek", manWeekService.totalManWeek(report.getItems()).doubleValue());

        try {
            return JSON_MAPPER.writeValueAsString(root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, Object> itemJson(ReportItem item) {
        return switch (item.getGroup()) {
            case PROJECT, DEV -> projectOrDevItemJson(item);
            case ETC -> etcItemJson(item);
            case VACATION -> vacationItemJson(item);
        };
    }

    private Map<String, Object> projectOrDevItemJson(ReportItem item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("group", item.getGroup().name().toLowerCase());
        map.put("project", item.getGroup() == Group.PROJECT && item.getProject() != null
                ? item.getProject().getName() : null);
        map.put("ticket", item.getTicket());
        map.put("title", item.getTitle());
        map.put("phase", item.getPhase() != null ? item.getPhase().label() : null);
        map.put("hours", item.getHours() != null ? numberForJson(item.getHours()) : null);
        map.put("days", item.daysOrDefault());
        map.put("completion", item.getCompletion());
        map.put("devDoneDate", item.getDevDoneDate() != null ? item.getDevDoneDate().toString() : null);
        map.put("testDate", item.getTestDate() != null ? item.getTestDate().toString() : null);
        map.put("deployDate", item.getDeployDate() != null ? item.getDeployDate().toString() : null);
        map.put("note", item.getNote());
        map.put("carriedOver", item.isCarriedOver());
        return map;
    }

    private Map<String, Object> etcItemJson(ReportItem item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("group", "etc");
        map.put("title", item.getTitle());
        if (item.getHours() != null) {
            map.put("hours", numberForJson(item.getHours()));
        }
        return map;
    }

    private Map<String, Object> vacationItemJson(ReportItem item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("group", "vacation");
        map.put("date", item.getDate().toString());
        if (item.getHours() != null) {
            map.put("hours", numberForJson(item.getHours()));
        }
        return map;
    }

    private Object numberForJson(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() <= 0) {
            return stripped.longValueExact();
        }
        return stripped.doubleValue();
    }
}
