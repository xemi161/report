package com.weeklyreport.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.weeklyreport.domain.Project;
import com.weeklyreport.domain.ReportItem;
import com.weeklyreport.domain.WeeklyReport;
import com.weeklyreport.domain.enums.Group;
import com.weeklyreport.repository.AppSettingsRepository;
import com.weeklyreport.repository.ProjectRepository;
import com.weeklyreport.repository.ReportItemRepository;
import com.weeklyreport.repository.WeeklyReportRepository;

/**
 * {@link EntryService#activeProjectsWithProgress()} — 대시보드와 작성 탭이 공유하는
 * "진행중인 프로젝트" 판정 로직. 원래 {@code DashboardController}에 있던 것을 서비스로
 * 옮기면서(두 화면이 같은 이름의 지표를 다르게 계산하던 모순을 없애기 위해) 여기로 이전했다.
 *
 * <p>진행률은 완료율의 <b>평균</b>이다. 최댓값으로 바꾸면 설계만 끝난 프로젝트가 완료로 사라진다.
 */
class EntryServiceTest {

    private final WeeklyReportRepository weeklyReportRepository = Mockito.mock(WeeklyReportRepository.class);
    private final ProjectRepository projectRepository = Mockito.mock(ProjectRepository.class);
    private final AppSettingsRepository appSettingsRepository = Mockito.mock(AppSettingsRepository.class);
    private final CarryOverService carryOverService = Mockito.mock(CarryOverService.class);
    private final ManWeekService manWeekService = new ManWeekService();
    private final TicketNumberService ticketNumberService = Mockito.mock(TicketNumberService.class);
    private final ReportItemRepository reportItemRepository = Mockito.mock(ReportItemRepository.class);

    private final EntryService entryService = new EntryService(
            weeklyReportRepository, projectRepository, appSettingsRepository,
            carryOverService, manWeekService, ticketNumberService, reportItemRepository);

    private Project project(long id, String name) {
        Project p = new Project(name);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    private ReportItem projectItem(Project project, WeeklyReport report, Integer completion) {
        ReportItem item = ReportItem.forGroup(Group.PROJECT);
        item.setProject(project);
        item.setWeeklyReport(report);
        item.setCompletion(completion);
        return item;
    }

    private WeeklyReport report(String label, LocalDate weekStart) {
        return new WeeklyReport(label, weekStart, weekStart.plusDays(6));
    }

    @Test
    void 진행률은_그_주_완료율의_평균이다_최댓값이_아니다() {
        Project gtpp = project(1L, "GTPP");
        WeeklyReport last = report("8월 2주", LocalDate.of(2026, 8, 7));
        Mockito.when(reportItemRepository.findActiveProjectItemsRecentFirst())
                .thenReturn(List.of(projectItem(gtpp, last, 100), projectItem(gtpp, last, 20)));
        Mockito.when(projectRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(gtpp));

        List<EntryService.ProjectProgress> projects = entryService.activeProjectsWithProgress();

        assertThat(projects).hasSize(1);
        assertThat(projects.get(0).completion()).isEqualTo(60);
        assertThat(projects.get(0).lastWeekLabel()).isEqualTo("8월 2주");
    }

    @Test
    void 가장_최근_보고된_진행률이_100이면_진행중에서_빠진다() {
        Project done = project(1L, "끝난 프로젝트");
        WeeklyReport last = report("8월 2주", LocalDate.of(2026, 8, 7));
        Mockito.when(reportItemRepository.findActiveProjectItemsRecentFirst())
                .thenReturn(List.of(projectItem(done, last, 100)));
        Mockito.when(projectRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(done));

        assertThat(entryService.activeProjectsWithProgress()).isEmpty();
    }

    @Test
    void 더_오래된_주의_항목은_진행률_계산에_섞이지_않는다() {
        // 레포지토리가 최신 주부터 주므로, 프로젝트마다 "가장 최근 보고된 주"의 항목만 봐야 한다.
        Project gtpp = project(1L, "GTPP");
        WeeklyReport recent = report("8월 2주", LocalDate.of(2026, 8, 7));
        WeeklyReport older = report("8월 1주", LocalDate.of(2026, 7, 31));
        Mockito.when(reportItemRepository.findActiveProjectItemsRecentFirst())
                .thenReturn(List.of(projectItem(gtpp, recent, 80), projectItem(gtpp, older, 10)));
        Mockito.when(projectRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(gtpp));

        List<EntryService.ProjectProgress> projects = entryService.activeProjectsWithProgress();

        assertThat(projects.get(0).completion()).isEqualTo(80);
        assertThat(projects.get(0).lastWeekLabel()).isEqualTo("8월 2주");
    }

    @Test
    void 한_번도_보고된_적_없는_활성_프로젝트는_0퍼센트로_포함된다() {
        Project fresh = project(9L, "신규 프로젝트");
        Mockito.when(reportItemRepository.findActiveProjectItemsRecentFirst()).thenReturn(List.of());
        Mockito.when(projectRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(fresh));

        List<EntryService.ProjectProgress> projects = entryService.activeProjectsWithProgress();

        assertThat(projects).hasSize(1);
        assertThat(projects.get(0).completion()).isZero();
        assertThat(projects.get(0).lastWeekLabel()).isNull();
    }

    @Test
    void 완료율이_하나도_입력되지_않은_프로젝트는_0퍼센트다() {
        Project gtpp = project(1L, "GTPP");
        WeeklyReport last = report("8월 2주", LocalDate.of(2026, 8, 7));
        Mockito.when(reportItemRepository.findActiveProjectItemsRecentFirst())
                .thenReturn(List.of(projectItem(gtpp, last, null)));
        Mockito.when(projectRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(gtpp));

        List<EntryService.ProjectProgress> projects = entryService.activeProjectsWithProgress();

        assertThat(projects.get(0).completion()).isZero();
    }

    @Test
    void 대시보드와_작성탭의_진행중_프로젝트_수가_같은_기준으로_계산된다() {
        // EntryController.activeProjectCount도 이제 이 메서드를 재사용한다 — 화면마다
        // "진행중 프로젝트 수"가 다르게 나오는 모순을 막기 위한 회귀 테스트.
        Project gtpp = project(1L, "GTPP");
        Project done = project(2L, "끝난 프로젝트");
        WeeklyReport last = report("8월 2주", LocalDate.of(2026, 8, 7));
        Mockito.when(reportItemRepository.findActiveProjectItemsRecentFirst())
                .thenReturn(List.of(projectItem(gtpp, last, 50), projectItem(done, last, 100)));
        Mockito.when(projectRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(gtpp, done));

        assertThat(entryService.activeProjectsWithProgress()).hasSize(1);
    }
}
