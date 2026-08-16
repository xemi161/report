package com.weeklyreport.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import com.weeklyreport.domain.TodoItem;
import com.weeklyreport.domain.enums.TodoPriority;
import com.weeklyreport.repository.TodoItemRepository;

/**
 * 할 일 목록의 정렬·그룹핑·CRUD 규칙.
 *
 * <p>정렬을 특히 촘촘히 고정하는 이유: 우선순위가 {@code @Enumerated(STRING)}이라
 * SQL {@code ORDER BY}로는 HIGH → LOW → MID(문자열 순)가 나오고, 그래서 순서를 Java 비교자가 쥐고 있다.
 * 누군가 "레포지토리에서 정렬해오면 되지 않나"라며 비교자를 지우면 컴파일도 되고 화면도 뜨지만
 * 목록 순서만 조용히 틀어진다 — 그 회귀를 여기서 잡는다.
 */
class TodoItemServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 17);

    private final TodoItemRepository todoItemRepository = Mockito.mock(TodoItemRepository.class);
    private final TodoItemService todoItemService = new TodoItemService(todoItemRepository);

    private TodoItem todo(long id, String due, TodoPriority priority, boolean done) {
        TodoItem todo = new TodoItem(LocalDate.parse(due), "할 일 " + id, priority);
        ReflectionTestUtils.setField(todo, "id", id);
        todo.setDone(done);
        return todo;
    }

    private List<Long> ids(List<TodoItem> todos) {
        return todos.stream().map(TodoItem::getId).toList();
    }

    // ---------- 정렬 ----------

    @Test
    void 미완료는_기한_이른순_다음_우선순위_다음_등록순으로_정렬된다() {
        // 일부러 뒤죽박죽으로 넣는다 — 레포지토리는 순서를 보장하지 않는다.
        Mockito.when(todoItemRepository.findByDoneFalse()).thenReturn(List.of(
                todo(4L, "2026-08-18", TodoPriority.HIGH, false),
                todo(1L, "2026-08-17", TodoPriority.LOW, false),
                todo(2L, "2026-08-17", TodoPriority.HIGH, false),
                todo(3L, "2026-08-17", TodoPriority.HIGH, false)));

        assertThat(ids(todoItemService.findOpen())).containsExactly(2L, 3L, 1L, 4L);
    }

    @Test
    void 우선순위_정렬은_문자열_순서가_아니라_선언_순서를_따른다() {
        // 문자열 정렬이면 HIGH → LOW → MID가 되어 "보통"이 "낮음"보다 아래로 내려간다.
        Mockito.when(todoItemRepository.findByDoneFalse()).thenReturn(List.of(
                todo(1L, "2026-08-17", TodoPriority.LOW, false),
                todo(2L, "2026-08-17", TodoPriority.MID, false),
                todo(3L, "2026-08-17", TodoPriority.HIGH, false)));

        assertThat(todoItemService.findOpen())
                .extracting(TodoItem::getPriority)
                .containsExactly(TodoPriority.HIGH, TodoPriority.MID, TodoPriority.LOW);
    }

    @Test
    void 완료는_기한_늦은순_등록_역순으로_정렬된다() {
        // 미완료와 방향이 반대인 것은 의도된 설계다(최근에 끝낸 것이 위).
        Mockito.when(todoItemRepository.findByDoneTrue()).thenReturn(List.of(
                todo(1L, "2026-08-11", TodoPriority.MID, true),
                todo(2L, "2026-08-12", TodoPriority.MID, true),
                todo(3L, "2026-08-12", TodoPriority.MID, true)));

        assertThat(ids(todoItemService.findDone())).containsExactly(3L, 2L, 1L);
    }

    // ---------- 기한 지남 / 오늘 이후 ----------

    @Test
    void 기한이_오늘인_항목은_기한지남이_아니라_오늘_이후로_들어간다() {
        List<TodoItem> open = List.of(
                todo(1L, "2026-08-15", TodoPriority.MID, false),
                todo(2L, "2026-08-17", TodoPriority.MID, false),
                todo(3L, "2026-08-18", TodoPriority.MID, false));

        assertThat(ids(todoItemService.overdue(open, TODAY))).containsExactly(1L);
        assertThat(ids(todoItemService.upcoming(open, TODAY))).containsExactly(2L, 3L);
    }

    @Test
    void 오늘_마감_건수는_기한이_정확히_오늘인_것만_센다() {
        List<TodoItem> open = List.of(
                todo(1L, "2026-08-16", TodoPriority.MID, false),
                todo(2L, "2026-08-17", TodoPriority.MID, false),
                todo(3L, "2026-08-17", TodoPriority.HIGH, false),
                todo(4L, "2026-08-18", TodoPriority.MID, false));

        assertThat(todoItemService.dueTodayCount(open, TODAY)).isEqualTo(2);
    }

    @Test
    void 엔티티의_기한지남_판정은_미완료일_때만_참이다() {
        // ⚠️ TodoItem.isOverdue()는 현재 호출부가 없다(서비스가 같은 조건을 직접 필터링한다).
        //    그래도 공개 메서드이므로 서비스 필터와 같은 답을 내는지 고정해 둔다 —
        //    한쪽만 고치면 지표 타일 숫자와 목록이 어긋난다.
        assertThat(todo(1L, "2026-08-10", TodoPriority.HIGH, false).isOverdue(TODAY)).isTrue();
        assertThat(todo(2L, "2026-08-10", TodoPriority.HIGH, true).isOverdue(TODAY)).isFalse();
        assertThat(todo(3L, "2026-08-17", TodoPriority.HIGH, false).isOverdue(TODAY)).isFalse();
    }

    @Test
    void 기한지남_행의_날짜_칩은_두자리_월일로_찍는다() {
        assertThat(todo(1L, "2026-08-09", TodoPriority.MID, false).dueShortLabel()).isEqualTo("08.09");
    }

    // ---------- 날짜 그룹 ----------

    @Test
    void 날짜별_그룹의_startIndex는_앞_그룹_건수만큼_누적된다() {
        // 이 값이 틀리면 접힘 상태에서 감출 행을 잘못 고른다(화면이 8건이 아닌 엉뚱한 수를 보여준다).
        List<TodoItem> upcoming = List.of(
                todo(1L, "2026-08-17", TodoPriority.MID, false),
                todo(2L, "2026-08-17", TodoPriority.MID, false),
                todo(3L, "2026-08-18", TodoPriority.MID, false),
                todo(4L, "2026-08-20", TodoPriority.MID, false));

        // "기한 지남" 3건이 앞에 있다고 가정 → 첫 그룹은 3번부터 시작한다.
        List<TodoItemService.TodoGroup> groups = todoItemService.groupByDueDate(upcoming, 3);

        assertThat(groups).extracting(TodoItemService.TodoGroup::startIndex).containsExactly(3, 5, 6);
        assertThat(groups).extracting(TodoItemService.TodoGroup::count).containsExactly(2, 1, 1);
        assertThat(groups.get(0).date()).isEqualTo(LocalDate.of(2026, 8, 17));
    }

    @Test
    void 그룹_라벨은_기록_화면과_같은_월일_요일_표기다() {
        List<TodoItemService.TodoGroup> groups = todoItemService.groupByDueDate(
                List.of(todo(1L, "2026-08-17", TodoPriority.MID, false)), 0);

        assertThat(groups.get(0).label()).isEqualTo("08.17 (월)");
    }

    @Test
    void 그룹_순서는_들어온_정렬을_그대로_보존한다() {
        List<TodoItemService.TodoGroup> groups = todoItemService.groupByDueDate(List.of(
                todo(1L, "2026-08-17", TodoPriority.MID, false),
                todo(2L, "2026-08-25", TodoPriority.MID, false),
                todo(3L, "2026-08-18", TodoPriority.MID, false)), 0);

        assertThat(groups).extracting(TodoItemService.TodoGroup::date)
                .containsExactly(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 25), LocalDate.of(2026, 8, 18));
    }

    // ---------- CRUD ----------

    @Test
    void 빈_텍스트는_할_일을_만들지_않는다() {
        // Enter 연타로 빈 줄이 쌓이지 않게 — DailyNoteService.add()와 같은 규칙.
        assertThat(todoItemService.add(TODAY, "   ", TodoPriority.MID)).isEmpty();
        assertThat(todoItemService.add(TODAY, null, TodoPriority.MID)).isEmpty();
        Mockito.verify(todoItemRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void 기한을_안_적으면_오늘로_우선순위를_안_고르면_보통으로_채운다() {
        Mockito.when(todoItemRepository.save(Mockito.any())).thenAnswer(i -> i.getArgument(0));

        todoItemService.add(null, "  스펙 회신  ", null);

        ArgumentCaptor<TodoItem> saved = ArgumentCaptor.forClass(TodoItem.class);
        Mockito.verify(todoItemRepository).save(saved.capture());
        assertThat(saved.getValue().getDueDate()).isEqualTo(LocalDate.now());
        assertThat(saved.getValue().getPriority()).isEqualTo(TodoPriority.MID);
        assertThat(saved.getValue().getText()).isEqualTo("스펙 회신");
    }

    @Test
    void 완료_토글은_보내온_값이_아니라_서버의_현재_값을_반전한다() {
        // 체크가 풀린 체크박스는 전송 자체가 안 되므로 클라이언트 값을 믿을 수 없다.
        TodoItem todo = todo(1L, "2026-08-17", TodoPriority.MID, false);
        Mockito.when(todoItemRepository.findById(1L)).thenReturn(Optional.of(todo));

        todoItemService.toggleDone(1L);
        assertThat(todo.isDone()).isTrue();

        todoItemService.toggleDone(1L);
        assertThat(todo.isDone()).isFalse();
    }

    @Test
    void 우선순위는_높음_보통_낮음_높음으로_한_바퀴_돈다() {
        TodoItem todo = todo(1L, "2026-08-17", TodoPriority.HIGH, false);
        Mockito.when(todoItemRepository.findById(1L)).thenReturn(Optional.of(todo));

        todoItemService.cyclePriority(1L);
        assertThat(todo.getPriority()).isEqualTo(TodoPriority.MID);
        todoItemService.cyclePriority(1L);
        assertThat(todo.getPriority()).isEqualTo(TodoPriority.LOW);
        todoItemService.cyclePriority(1L);
        assertThat(todo.getPriority()).isEqualTo(TodoPriority.HIGH);
    }

    @Test
    void 기한을_빈_값으로_보내면_무시한다() {
        // 기한 없는 할 일은 목록에서 자리를 잡을 수 없다(정렬축이라 null을 남기지 않는다).
        TodoItem todo = todo(1L, "2026-08-17", TodoPriority.MID, false);
        Mockito.when(todoItemRepository.findById(1L)).thenReturn(Optional.of(todo));

        todoItemService.updateDueDate(1L, null);
        assertThat(todo.getDueDate()).isEqualTo(LocalDate.of(2026, 8, 17));

        todoItemService.updateDueDate(1L, LocalDate.of(2026, 8, 20));
        assertThat(todo.getDueDate()).isEqualTo(LocalDate.of(2026, 8, 20));
    }

    @Test
    void 텍스트가_null이면_건드리지_않고_값이_오면_공백을_떼고_저장한다() {
        TodoItem todo = todo(1L, "2026-08-17", TodoPriority.MID, false);
        Mockito.when(todoItemRepository.findById(1L)).thenReturn(Optional.of(todo));

        todoItemService.updateText(1L, null);
        assertThat(todo.getText()).isEqualTo("할 일 1");

        todoItemService.updateText(1L, "  고친 내용  ");
        assertThat(todo.getText()).isEqualTo("고친 내용");
    }
}
