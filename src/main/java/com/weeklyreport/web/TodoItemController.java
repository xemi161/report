package com.weeklyreport.web;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.weeklyreport.domain.TodoItem;
import com.weeklyreport.domain.enums.TodoPriority;
import com.weeklyreport.service.TodoItemService;
import com.weeklyreport.web.dto.TodoItemForm;

/**
 * 할 일(TODO) 리스트의 추가/수정/삭제. 지금은 <b>대시보드 카드 한 곳</b>에서만 쓰이므로
 * {@code DailyNoteController}처럼 화면을 고르는 {@code view} 파라미터가 없다 —
 * 모든 구조 변경이 같은 프래그먼트({@code fragments-todo :: todoCard}) 하나를 돌려준다.
 *
 * <p>htmx 배선 규약 그대로다:
 * <ul>
 *   <li><b>구조가 바뀌는 동작</b>(추가·삭제·완료 토글·우선순위 변경·기한 변경)은 목록의 순서와 그룹이
 *       전부 달라지므로 카드를 통째로 다시 렌더링해 {@code hx-swap="outerHTML"}로 갈아끼운다.</li>
 *   <li><b>인라인 텍스트 수정</b>만 {@code hx-swap="none"} — 다시 그리면 입력 포커스가 날아간다.</li>
 * </ul>
 *
 * <p>주(week)와 무관한 데이터라 {@code week} 쿼리스트링을 받지 않는다.
 */
@Controller
public class TodoItemController {

    /**
     * 접힘 상태에서 보여줄 미완료 건수. 이 값을 넘는 항목도 <b>서버는 전부 내려보내고</b>
     * 화면(app.js)이 감췄다 펼친다 — "+N건 더 보기"에 서버 왕복을 만들지 않기 위해서다.
     * (완료 목록 접기/펴기도 같은 방식이다.)
     */
    static final int TODO_VISIBLE = 8;

    private final TodoItemService todoItemService;

    public TodoItemController(TodoItemService todoItemService) {
        this.todoItemService = todoItemService;
    }

    /** 할 일 추가. 텍스트가 비어 있으면 아무것도 만들지 않고 카드만 그대로 다시 그린다. */
    @PostMapping("/todos")
    public String add(@ModelAttribute TodoItemForm form, Model model) {
        todoItemService.add(form.getDueDate(), form.getText(), form.getPriority());
        return renderCard(model);
    }

    /** 인라인 텍스트 수정. 저장만 하고 화면은 건드리지 않는다(포커스 유지). */
    @PostMapping("/todos/{id}")
    public String updateText(@PathVariable Long id, @ModelAttribute TodoItemForm form) {
        todoItemService.updateText(id, form.getText());
        return "fragments-entry :: noop";
    }

    /**
     * 완료 토글. <b>파라미터를 받지 않고 서버가 현재 값을 반전한다</b> —
     * 체크가 풀린 체크박스는 전송 자체가 되지 않아 값을 믿을 수 없기 때문이다.
     */
    @PostMapping("/todos/{id}/done")
    public String toggleDone(@PathVariable Long id, Model model) {
        todoItemService.toggleDone(id);
        return renderCard(model);
    }

    /** 우선순위 순환(높음 → 보통 → 낮음 → 높음). 역시 파라미터 없이 서버가 다음 단계로 넘긴다. */
    @PostMapping("/todos/{id}/priority")
    public String cyclePriority(@PathVariable Long id, Model model) {
        todoItemService.cyclePriority(id);
        return renderCard(model);
    }

    /** 기한 변경. 빈 값이면 무시하고 카드만 다시 그린다. */
    @PostMapping("/todos/{id}/due")
    public String updateDueDate(@PathVariable Long id,
                                @RequestParam(required = false)
                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate,
                                Model model) {
        todoItemService.updateDueDate(id, dueDate);
        return renderCard(model);
    }

    @PostMapping("/todos/{id}/delete")
    public String delete(@PathVariable Long id, Model model) {
        todoItemService.delete(id);
        return renderCard(model);
    }

    private String renderCard(Model model) {
        populateTodoCard(model, todoItemService);
        return "fragments-todo :: todoCard";
    }

    /**
     * TODO 카드의 모델. <b>대시보드도 이 메서드를 부른다</b> — 카드를 조작한 뒤 이 컨트롤러가
     * 같은 프래그먼트를 같은 속성으로 다시 그려야 하므로 모델의 소유권을 여기에 둔다
     * ({@code DailyNoteController.populateDashboardCard()}와 같은 구조).
     *
     * <p>목록은 <b>미완료 전체</b>를 내려준다. 앞 {@link #TODO_VISIBLE}건만 보이고 나머지는
     * 화면에서 감추는데, 잘리는 지점이 그룹 중간일 수 있으므로 각 날짜 그룹이
     * {@code startIndex}(미완료 전체에서 그 그룹 첫 항목의 순번)를 함께 들고 간다 —
     * 그룹째 감출지 판단하는 값이다. "기한 지남" 그룹은 항상 맨 앞이라 startIndex가 0이다.
     */
    static void populateTodoCard(Model model, TodoItemService todoItemService) {
        LocalDate today = LocalDate.now();
        List<TodoItem> open = todoItemService.findOpen();
        List<TodoItem> overdue = todoItemService.overdue(open, today);
        List<TodoItem> upcoming = todoItemService.upcoming(open, today);
        List<TodoItem> done = todoItemService.findDone();

        model.addAttribute("today", today);
        model.addAttribute("todoOverdue", overdue);
        model.addAttribute("todoOverdueCount", overdue.size());
        model.addAttribute("todoDayGroups", todoItemService.groupByDueDate(upcoming, overdue.size()));
        model.addAttribute("todoOpenCount", open.size());
        model.addAttribute("todoDueTodayCount", todoItemService.dueTodayCount(open, today));
        model.addAttribute("todoDone", done);
        model.addAttribute("todoDoneCount", done.size());
        model.addAttribute("todoVisibleLimit", TODO_VISIBLE);
        model.addAttribute("todoHiddenCount", Math.max(0, open.size() - TODO_VISIBLE));
        // 추가 입력줄의 초기값 — 기한은 오늘, 우선순위는 보통.
        model.addAttribute("todoDefaultDue", today);
        model.addAttribute("todoDefaultPriority", TodoPriority.MID);
        // 우선순위 pill의 한국어 라벨을 템플릿에 하드코딩하지 않도록 목록째 내려준다(순환 순서 그대로).
        model.addAttribute("todoPriorities", TodoPriority.values());
    }
}
