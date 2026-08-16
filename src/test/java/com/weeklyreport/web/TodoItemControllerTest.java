package com.weeklyreport.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import com.weeklyreport.domain.TodoItem;
import com.weeklyreport.domain.enums.TodoPriority;
import com.weeklyreport.repository.TodoItemRepository;
import com.weeklyreport.service.TodoItemService;
import com.weeklyreport.web.dto.TodoItemForm;

/**
 * 라우트 → 반환 프래그먼트 / 모델 속성명 계약.
 *
 * <p>이 계약이 깨져도 자바는 컴파일된다 — 프래그먼트 이름도 모델 속성명도 문자열이라
 * 오타가 나면 화면이 조용히 비거나 런타임에 EL1007E로만 드러난다({@code DailyNoteControllerViewTest}와 같은 이유).
 * 그래서 {@code fragments-todo.html}이 실제로 읽는 이름을 여기서 문자열째 고정한다.
 */
class TodoItemControllerTest {

    private final TodoItemService mockService = Mockito.mock(TodoItemService.class);
    private final TodoItemController controller = new TodoItemController(mockService);

    /** 모델 검증용 — 그룹핑/정렬까지 실제로 돌려야 startIndex 같은 계산값을 볼 수 있다. */
    private final TodoItemRepository todoItemRepository = Mockito.mock(TodoItemRepository.class);
    private final TodoItemService realService = new TodoItemService(todoItemRepository);

    private TodoItem todo(long id, LocalDate due, TodoPriority priority, boolean done) {
        TodoItem todo = new TodoItem(due, "할 일 " + id, priority);
        ReflectionTestUtils.setField(todo, "id", id);
        todo.setDone(done);
        return todo;
    }

    private TodoItemForm form(String text) {
        TodoItemForm form = new TodoItemForm();
        form.setText(text);
        return form;
    }

    // ---------- 라우트 → 프래그먼트 ----------

    @Test
    void 구조가_바뀌는_다섯_라우트는_전부_TODO_카드를_통째로_돌려준다() {
        // 추가·완료토글·우선순위·기한·삭제는 목록의 정렬/그룹/건수가 전부 달라진다 → outerHTML 교체.
        Model model = new ExtendedModelMap();

        assertThat(controller.add(form("새 할 일"), model)).isEqualTo("fragments-todo :: todoCard");
        assertThat(controller.toggleDone(1L, model)).isEqualTo("fragments-todo :: todoCard");
        assertThat(controller.cyclePriority(1L, model)).isEqualTo("fragments-todo :: todoCard");
        assertThat(controller.updateDueDate(1L, LocalDate.of(2026, 8, 20), model))
                .isEqualTo("fragments-todo :: todoCard");
        assertThat(controller.delete(1L, model)).isEqualTo("fragments-todo :: todoCard");
    }

    @Test
    void 인라인_텍스트_수정만_빈_응답을_돌려준다() {
        // 다시 그리면 입력 포커스가 날아간다 → hx-swap="none"과 짝을 이루는 noop 프래그먼트.
        assertThat(controller.updateText(1L, form("고친 내용"))).isEqualTo("fragments-entry :: noop");

        Mockito.verify(mockService).updateText(1L, "고친 내용");
    }

    @Test
    void 완료와_우선순위는_값을_받지_않고_서비스에_반전_순환만_지시한다() {
        // 체크가 풀린 체크박스는 전송 자체가 안 되므로 클라이언트 값을 받으면 안 된다.
        Model model = new ExtendedModelMap();
        controller.toggleDone(7L, model);
        controller.cyclePriority(7L, model);

        Mockito.verify(mockService).toggleDone(7L);
        Mockito.verify(mockService).cyclePriority(7L);
    }

    @Test
    void 추가는_폼의_기한_텍스트_우선순위를_그대로_서비스에_넘긴다() {
        TodoItemForm form = new TodoItemForm();
        form.setDueDate(LocalDate.of(2026, 8, 20));
        form.setText("스펙 회신");
        form.setPriority(TodoPriority.HIGH);

        controller.add(form, new ExtendedModelMap());

        Mockito.verify(mockService).add(LocalDate.of(2026, 8, 20), "스펙 회신", TodoPriority.HIGH);
    }

    // ---------- 모델 속성명 ----------

    @Test
    void TODO_카드_모델은_템플릿이_참조하는_이름을_전부_채운다() {
        Model model = new ExtendedModelMap();
        TodoItemController.populateTodoCard(model, realService);

        assertThat(model.asMap()).containsKeys("today", "todoOverdue", "todoOverdueCount", "todoDayGroups",
                "todoOpenCount", "todoDueTodayCount", "todoDone", "todoDoneCount", "todoVisibleLimit",
                "todoHiddenCount", "todoDefaultDue", "todoDefaultPriority", "todoPriorities");
    }

    @Test
    void 추가줄_기본값은_오늘_기한과_보통_우선순위이고_순환_순서를_통째로_내려준다() {
        // 한국어 라벨(높음/보통/낮음)을 템플릿·app.js에 하드코딩하지 않으려고 목록째 내려보낸다.
        Model model = new ExtendedModelMap();
        TodoItemController.populateTodoCard(model, realService);

        assertThat(model.asMap().get("todoDefaultDue")).isEqualTo(LocalDate.now());
        assertThat(model.asMap().get("todoDefaultPriority")).isEqualTo(TodoPriority.MID);
        assertThat((TodoPriority[]) model.asMap().get("todoPriorities"))
                .containsExactly(TodoPriority.HIGH, TodoPriority.MID, TodoPriority.LOW);
    }

    @Test
    void 기한지남은_별도_목록으로_빠지고_나머지만_날짜_그룹이_된다() {
        LocalDate today = LocalDate.now();
        Mockito.when(todoItemRepository.findByDoneFalse()).thenReturn(List.of(
                todo(1L, today.minusDays(5), TodoPriority.MID, false),
                todo(2L, today.minusDays(2), TodoPriority.MID, false),
                todo(3L, today, TodoPriority.MID, false),
                todo(4L, today.plusDays(1), TodoPriority.MID, false)));

        Model model = new ExtendedModelMap();
        TodoItemController.populateTodoCard(model, realService);

        assertThat((List<?>) model.asMap().get("todoOverdue")).hasSize(2);
        assertThat(model.asMap().get("todoOverdueCount")).isEqualTo(2);
        assertThat(model.asMap().get("todoOpenCount")).isEqualTo(4);
        assertThat(model.asMap().get("todoDueTodayCount")).isEqualTo(1);

        @SuppressWarnings("unchecked")
        List<TodoItemService.TodoGroup> groups =
                (List<TodoItemService.TodoGroup>) model.asMap().get("todoDayGroups");
        // 기한 지남 2건이 목록 맨 앞을 차지하므로 첫 날짜 그룹은 2번부터 시작한다.
        assertThat(groups).extracting(TodoItemService.TodoGroup::startIndex).containsExactly(2, 3);
    }

    @Test
    void 접힘_기준을_넘은_건수만_더_보기_숫자가_되고_넘지_않으면_0이다() {
        LocalDate today = LocalDate.now();
        List<TodoItem> nine = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            nine.add(todo(i + 1L, today.plusDays(i), TodoPriority.MID, false));
        }
        Mockito.when(todoItemRepository.findByDoneFalse()).thenReturn(nine);

        Model model = new ExtendedModelMap();
        TodoItemController.populateTodoCard(model, realService);

        assertThat(model.asMap().get("todoVisibleLimit")).isEqualTo(8);
        assertThat(model.asMap().get("todoHiddenCount")).isEqualTo(1);

        Mockito.when(todoItemRepository.findByDoneFalse()).thenReturn(nine.subList(0, 3));
        Model few = new ExtendedModelMap();
        TodoItemController.populateTodoCard(few, realService);
        assertThat(few.asMap().get("todoHiddenCount")).isEqualTo(0);
    }

    @Test
    void 완료한_할_일은_지워지지_않고_별도_목록으로_따로_내려간다() {
        LocalDate today = LocalDate.now();
        Mockito.when(todoItemRepository.findByDoneFalse())
                .thenReturn(List.of(todo(1L, today, TodoPriority.MID, false)));
        Mockito.when(todoItemRepository.findByDoneTrue()).thenReturn(List.of(
                todo(2L, today.minusDays(1), TodoPriority.MID, true),
                todo(3L, today.minusDays(3), TodoPriority.MID, true)));

        Model model = new ExtendedModelMap();
        TodoItemController.populateTodoCard(model, realService);

        assertThat(model.asMap().get("todoDoneCount")).isEqualTo(2);
        assertThat((List<?>) model.asMap().get("todoDone")).hasSize(2);
        // 완료본은 미완료 건수·"+N건 더 보기" 계산에 섞이지 않는다.
        assertThat(model.asMap().get("todoOpenCount")).isEqualTo(1);
        assertThat(model.asMap().get("todoHiddenCount")).isEqualTo(0);
        // 기한이 지난 완료본이 지표 타일 숫자를 올리면 안 된다.
        assertThat(model.asMap().get("todoOverdueCount")).isEqualTo(0);
    }
}
