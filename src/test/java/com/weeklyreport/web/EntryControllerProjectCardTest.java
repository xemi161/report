package com.weeklyreport.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.weeklyreport.domain.Project;
import com.weeklyreport.domain.ReportItem;
import com.weeklyreport.domain.WeeklyReport;
import com.weeklyreport.domain.enums.Group;
import com.weeklyreport.repository.AppSettingsRepository;
import com.weeklyreport.repository.ProjectRepository;
import com.weeklyreport.service.DailyNoteService;
import com.weeklyreport.service.EntryService;
import com.weeklyreport.service.MdExportService;

import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

/**
 * 작성 화면의 프로젝트 카드 구성 규칙.
 * 특히 "종료된 프로젝트의 지난 항목이 화면에서 사라지면 안 된다"를 지킨다 —
 * 활성 프로젝트만으로 카드를 만들면 그 항목들이 합계·md에는 남은 채 편집할 수 없게 된다.
 */
class EntryControllerProjectCardTest {

    private final ProjectRepository projectRepository = Mockito.mock(ProjectRepository.class);
    private final EntryService entryService = Mockito.mock(EntryService.class);
    private final AppSettingsRepository appSettingsRepository = Mockito.mock(AppSettingsRepository.class);
    private final MdExportService mdExportService = Mockito.mock(MdExportService.class);
    /** 좌측 일일 기록 패널은 이 테스트의 관심사가 아니다(기록은 보고서 데이터와 무관). */
    private final DailyNoteService dailyNoteService = Mockito.mock(DailyNoteService.class);

    private final EntryController controller = new EntryController(
            entryService, projectRepository, appSettingsRepository, mdExportService, dailyNoteService);

    private Project project(long id, String name, boolean active) {
        Project p = new Project(name);
        ReflectionTestUtils.setField(p, "id", id);
        p.setActive(active);
        return p;
    }

    private ReportItem projectItem(Project project, String title) {
        ReportItem item = ReportItem.forGroup(Group.PROJECT);
        item.setProject(project);
        item.setTitle(title);
        return item;
    }

    @SuppressWarnings("unchecked")
    private List<EntryController.ProjectCard> cardsFor(WeeklyReport report) {
        Mockito.when(entryService.findByWeekStart(Mockito.any())).thenReturn(java.util.Optional.of(report));
        Model model = new ExtendedModelMap();
        ReflectionTestUtils.invokeMethod(controller, "populateWriteView", model, report.getWeekStart());
        return (List<EntryController.ProjectCard>) model.asMap().get("projectCards");
    }

    private WeeklyReport reportWith(ReportItem... items) {
        WeeklyReport report = new WeeklyReport("8월 1주", LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 6));
        for (ReportItem item : items) {
            report.addItem(item);
        }
        return report;
    }

    @Test
    void 항목이_없는_활성_프로젝트도_빈_카드로_보인다() {
        Project active = project(1L, "GTPP", true);
        Mockito.when(projectRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(active));

        List<EntryController.ProjectCard> cards = cardsFor(reportWith());

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).project().getName()).isEqualTo("GTPP");
        assertThat(cards.get(0).items()).isEmpty();
    }

    @Test
    void 종료된_프로젝트라도_이_주에_항목이_있으면_카드가_남는다() {
        Project ended = project(2L, "종료된 프로젝트", false);
        Mockito.when(projectRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of());

        List<EntryController.ProjectCard> cards = cardsFor(reportWith(projectItem(ended, "남아있는 업무")));

        assertThat(cards).hasSize(1);
        assertThat(cards.get(0).project().getName()).isEqualTo("종료된 프로젝트");
        assertThat(cards.get(0).items()).hasSize(1);
    }

    @Test
    void 활성_프로젝트의_항목은_해당_카드로만_묶인다() {
        Project a = project(1L, "A", true);
        Project b = project(2L, "B", true);
        Mockito.when(projectRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(a, b));

        List<EntryController.ProjectCard> cards =
                cardsFor(reportWith(projectItem(a, "a-1"), projectItem(b, "b-1"), projectItem(a, "a-2")));

        assertThat(cards).hasSize(2);
        assertThat(cards.get(0).items()).hasSize(2);
        assertThat(cards.get(1).items()).hasSize(1);
    }
}
