package com.weeklyreport.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weeklyreport.domain.AppSettings;
import com.weeklyreport.domain.Project;
import com.weeklyreport.domain.ReportItem;
import com.weeklyreport.domain.WeeklyReport;
import com.weeklyreport.domain.enums.Group;
import com.weeklyreport.domain.enums.Phase;

class MdExportServiceTest {

    private final ManWeekService manWeekService = new ManWeekService();
    private final MdExportService service = new MdExportService(manWeekService);

    private String exportSample() {
        AppSettings settings = new AppSettings("정준호", "파트원", "NHNKCP-개발1팀");
        WeeklyReport report = new WeeklyReport("8월 1주", LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 6));

        ReportItem project = ReportItem.forGroup(Group.PROJECT);
        project.setProject(new Project("GTPP"));
        project.setTicket("NHNKCP-개발1팀/23");
        project.setTitle("외국환 보고서 현행화");
        project.setPhase(Phase.DEVELOPMENT);
        project.setHours(BigDecimal.valueOf(8));
        project.setCompletion(60);
        project.setTestDate(LocalDate.of(2026, 8, 10));
        project.setNote("외부 연계사 일정으로 테스트 1주 연기");
        report.addItem(project);

        ReportItem dev = ReportItem.forGroup(Group.DEV);
        dev.setTicket("NHNKCP-개발1팀/26");
        dev.setTitle("2차인증 프로세스 개선");
        dev.setPhase(Phase.ANALYSIS_DESIGN);
        dev.setHours(BigDecimal.valueOf(1));
        dev.setCompletion(5);
        dev.setCarriedOver(true);
        report.addItem(dev);

        ReportItem etc = ReportItem.forGroup(Group.ETC);
        etc.setTitle("파트 주간회의");
        etc.setHours(BigDecimal.valueOf(1));
        report.addItem(etc);

        ReportItem vacation = ReportItem.forGroup(Group.VACATION);
        vacation.setDate(LocalDate.of(2026, 8, 1));
        vacation.setHours(BigDecimal.valueOf(8));
        report.addItem(vacation);

        return service.export(settings, report);
    }

    @Test
    void 사람이_읽는_영역이_스키마_형식을_따른다() {
        String md = exportSample();

        assertThat(md).contains("# 정준호 · 8월 1주");
        assertThat(md).contains("파트원 · 07.31 (금) ~ 08.06 (목)");
        assertThat(md).contains("### GTPP");
        assertThat(md).contains("- NHNKCP-개발1팀/23 : 외국환 보고서 현행화 [개발] — 8h · 60%");
        assertThat(md).contains("  - 테스트예정일: 2026-08-10");
        assertThat(md).contains("  - 비고: 외부 연계사 일정으로 테스트 1주 연기");
        assertThat(md).contains("- NHNKCP-개발1팀/26 : 2차인증 프로세스 개선 [설계] — 1h · 5% (이월)");
        assertThat(md).contains("- 파트 주간회의 — 1h");
        assertThat(md).contains("- 2026-08-01 — 8h");
        assertThat(md).contains("**합계: 18h / 0.45 맨위크**");
    }

    @Test
    void JSON_데이터_블록을_파싱할_수_있다() throws Exception {
        String md = exportSample();

        String json = md.substring(md.indexOf("<!--DATA\n") + "<!--DATA\n".length(), md.indexOf("\nDATA-->"));
        JsonNode root = new ObjectMapper().readTree(json);

        assertThat(root.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(root.get("name").asText()).isEqualTo("정준호");
        assertThat(root.get("weekLabel").asText()).isEqualTo("8월 1주");
        assertThat(root.get("weekStart").asText()).isEqualTo("2026-07-31");
        assertThat(root.get("totalHours").asLong()).isEqualTo(18);
        assertThat(root.get("totalManWeek").asDouble()).isEqualTo(0.45);
        assertThat(root.get("items")).hasSize(4);

        JsonNode projectItem = root.get("items").get(0);
        assertThat(projectItem.get("group").asText()).isEqualTo("project");
        assertThat(projectItem.get("project").asText()).isEqualTo("GTPP");
        assertThat(projectItem.get("phase").asText()).isEqualTo("개발");
        assertThat(projectItem.get("days").asInt()).isEqualTo(1);
        assertThat(projectItem.get("devDoneDate").isNull()).isTrue();

        JsonNode etcItem = root.get("items").get(2);
        assertThat(etcItem.get("group").asText()).isEqualTo("etc");
        assertThat(etcItem.has("completion")).isFalse();

        JsonNode vacationItem = root.get("items").get(3);
        assertThat(vacationItem.get("date").asText()).isEqualTo("2026-08-01");
        assertThat(vacationItem.get("endDate").isNull()).isTrue();
    }

    @Test
    void 기간_휴가는_시작일과_종료일을_함께_표기한다() throws Exception {
        AppSettings settings = new AppSettings("정준호", "파트원", "NHNKCP-개발1팀");
        WeeklyReport report = new WeeklyReport("8월 1주", LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 6));

        ReportItem vacation = ReportItem.forGroup(Group.VACATION);
        vacation.setDate(LocalDate.of(2026, 8, 5));
        vacation.setEndDate(LocalDate.of(2026, 8, 6));
        vacation.setHours(BigDecimal.valueOf(16));
        report.addItem(vacation);

        String md = service.export(settings, report);

        assertThat(md).contains("- 2026-08-05 ~ 2026-08-06 — 16h");

        String json = md.substring(md.indexOf("<!--DATA\n") + "<!--DATA\n".length(), md.indexOf("\nDATA-->"));
        JsonNode item = new ObjectMapper().readTree(json).get("items").get(0);
        assertThat(item.get("date").asText()).isEqualTo("2026-08-05");
        assertThat(item.get("endDate").asText()).isEqualTo("2026-08-06");
    }

    @Test
    void 프로젝트_세부항목의_제목이_비면_프로젝트명으로_대체된다() {
        AppSettings settings = new AppSettings("정준호", "파트원", "NHNKCP-개발1팀");
        WeeklyReport report = new WeeklyReport("8월 1주", LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 6));

        ReportItem item = ReportItem.forGroup(Group.PROJECT);
        item.setProject(new Project("GTPP"));
        item.setTicket("NHNKCP-개발1팀/23");
        item.setPhase(Phase.DEVELOPMENT);
        item.setHours(BigDecimal.valueOf(8));
        item.setCompletion(60);
        report.addItem(item);

        assertThat(service.export(settings, report))
                .contains("- NHNKCP-개발1팀/23 : GTPP [개발] — 8h · 60%");
    }

    @Test
    void 파일명은_공백없는_주차라벨을_사용한다() {
        AppSettings settings = new AppSettings("정준호", "파트원", "NHNKCP-개발1팀");
        WeeklyReport report = new WeeklyReport("8월 1주", LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 6));

        assertThat(service.fileName(settings, report)).isEqualTo("주간보고_정준호_8월1주.md");
    }
}
