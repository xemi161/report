package com.weeklyreport.service;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.weeklyreport.domain.TodoItem;
import com.weeklyreport.domain.enums.TodoPriority;
import com.weeklyreport.repository.TodoItemRepository;

/**
 * 할 일(TODO) 목록의 조회/정렬/그룹핑/CRUD.
 *
 * <p><b>주간보고와 완전히 분리되어 있다</b> — {@code DailyNoteService}와 같은 위치의 개인 데이터라
 * 맨위크·총 투입시간 계산에 섞이지 않고 {@code .md} 내보내기에도 나가지 않는다.
 * 예상소요시간 같은 시간 필드가 아예 없으므로 합칠 숫자 자체가 없다.
 */
@Service
@Transactional
public class TodoItemService {

    /**
     * 미완료 정렬: 기한 이른 순 → 우선순위(높음·보통·낮음) → 등록순(id).
     * <b>기한 지난 것이 자연히 맨 위로 온다</b>(따로 끌어올리는 로직이 없다 — 기한이 가장 이르므로).
     */
    private static final Comparator<TodoItem> OPEN_ORDER =
            Comparator.comparing(TodoItem::getDueDate)
                    .thenComparingInt(t -> TodoPriority.orDefault(t.getPriority()).order())
                    .thenComparing(TodoItem::getId);

    /** 완료 정렬: 최근에 끝낸 것이 위(기한 늦은 순 → 등록 역순). 미완료와 방향이 반대인 것은 의도된 설계다. */
    private static final Comparator<TodoItem> DONE_ORDER =
            Comparator.comparing(TodoItem::getDueDate).reversed()
                    .thenComparing(Comparator.comparing(TodoItem::getId).reversed());

    private final TodoItemRepository todoItemRepository;

    public TodoItemService(TodoItemRepository todoItemRepository) {
        this.todoItemRepository = todoItemRepository;
    }

    // ---------- 조회 ----------

    /** 미완료 전체(정렬 완료). 대시보드 카드가 이 목록 앞쪽 N건만 펼쳐 보여준다. */
    @Transactional(readOnly = true)
    public List<TodoItem> findOpen() {
        return todoItemRepository.findByDoneFalse().stream().sorted(OPEN_ORDER).toList();
    }

    /** 완료 전체(정렬 완료). 지우지 않고 접어두는 이유는 잘못 체크한 것을 되돌릴 수 있어야 하기 때문이다. */
    @Transactional(readOnly = true)
    public List<TodoItem> findDone() {
        return todoItemRepository.findByDoneTrue().stream().sorted(DONE_ORDER).toList();
    }

    // ---------- 집계/그룹핑 ----------

    /** 기한이 지난(오늘보다 이른) 미완료 항목. 날짜가 제각각이라 날짜별로 쪼개지 않고 한 그룹으로 묶는다. */
    public List<TodoItem> overdue(List<TodoItem> open, LocalDate today) {
        return open.stream().filter(t -> t.getDueDate().isBefore(today)).toList();
    }

    /** 오늘 이후(오늘 포함) 기한의 미완료 항목. 이쪽만 날짜별 그룹으로 나눈다. */
    public List<TodoItem> upcoming(List<TodoItem> open, LocalDate today) {
        return open.stream().filter(t -> !t.getDueDate().isBefore(today)).toList();
    }

    public int dueTodayCount(List<TodoItem> open, LocalDate today) {
        return (int) open.stream().filter(t -> today.equals(t.getDueDate())).count();
    }

    /**
     * 이미 정렬된 목록을 기한 날짜별로 묶는다(정렬을 그대로 보존하므로 기한 이른 날부터 나온다).
     *
     * @param startIndex 이 그룹 목록의 첫 항목이 <b>미완료 전체 목록에서 몇 번째인지</b>.
     *                   "기한 지남" 그룹이 앞에 오므로 보통 그 건수를 넘긴다.
     *                   화면이 접힘 상태에서 앞 N건만 보여줄 때 그룹째 감출지 판단하는 데 쓴다.
     */
    public List<TodoGroup> groupByDueDate(List<TodoItem> todos, int startIndex) {
        Map<LocalDate, List<TodoItem>> byDate = new LinkedHashMap<>();
        for (TodoItem todo : todos) {
            byDate.computeIfAbsent(todo.getDueDate(), d -> new ArrayList<>()).add(todo);
        }
        List<TodoGroup> groups = new ArrayList<>();
        int index = startIndex;
        for (Map.Entry<LocalDate, List<TodoItem>> entry : byDate.entrySet()) {
            groups.add(new TodoGroup(entry.getKey(), entry.getValue(), index));
            index += entry.getValue().size();
        }
        return groups;
    }

    // ---------- CRUD ----------

    /**
     * 할 일 추가. 텍스트가 비면 아무것도 만들지 않고 빈 Optional을 돌려준다
     * (Enter 연타로 빈 줄이 쌓이지 않게 — {@code DailyNoteService.add()}와 같은 규칙).
     *
     * @param dueDate  비어 있으면 오늘로 채운다(기한은 정렬축이라 null을 남기지 않는다)
     * @param priority 비어 있으면 보통(MID)
     */
    public Optional<TodoItem> add(LocalDate dueDate, String text, TodoPriority priority) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        LocalDate due = dueDate == null ? LocalDate.now() : dueDate;
        return Optional.of(todoItemRepository.save(new TodoItem(due, text.trim(), priority)));
    }

    /** 인라인 텍스트 수정. null이면 건드리지 않는다({@code DailyNoteService.update()}와 같은 규칙). */
    public void updateText(Long id, String text) {
        if (text == null) {
            return;
        }
        requireTodo(id).setText(text.trim());
    }

    /**
     * 완료 토글. <b>클라이언트가 보낸 값을 믿지 않고 서버가 현재 값을 반전한다</b> —
     * 체크가 풀린 체크박스는 폼 전송에 파라미터 자체가 실리지 않아 "안 보냈다"와 "false"를 구분할 수 없다.
     */
    public void toggleDone(Long id) {
        TodoItem todo = requireTodo(id);
        todo.setDone(!todo.isDone());
    }

    /** 우선순위 순환(HIGH → MID → LOW → HIGH). 화면의 pill 버튼 한 번 클릭에 대응한다. */
    public void cyclePriority(Long id) {
        TodoItem todo = requireTodo(id);
        todo.setPriority(TodoPriority.orDefault(todo.getPriority()).next());
    }

    /** 기한 변경. 빈 값이면 무시한다 — 기한 없는 할 일은 목록에서 자리를 잡을 수 없다. */
    public void updateDueDate(Long id, LocalDate dueDate) {
        if (dueDate == null) {
            return;
        }
        requireTodo(id).setDueDate(dueDate);
    }

    public void delete(Long id) {
        todoItemRepository.deleteById(id);
    }

    private TodoItem requireTodo(Long id) {
        return todoItemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 할 일입니다: " + id));
    }

    /**
     * 같은 기한을 가진 할 일 묶음. 날짜 헤더(라벨/오늘 칩) + 그 아래 행들을 한 덩어리로 넘긴다
     * ({@code DailyNoteService.DayGroup}과 같은 역할).
     */
    public record TodoGroup(LocalDate date, List<TodoItem> todos, int startIndex) {

        public int count() {
            return todos.size();
        }

        /** "08.17 (월)" — 기록 화면의 날짜 헤더와 같은 표기. */
        public String label() {
            return String.format("%02d.%02d (%s)", date.getMonthValue(), date.getDayOfMonth(),
                    date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN));
        }

        public boolean isToday() {
            return date.equals(LocalDate.now());
        }
    }
}
