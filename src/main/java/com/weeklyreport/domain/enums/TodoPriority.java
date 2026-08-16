package com.weeklyreport.domain.enums;

/**
 * 할 일(TODO)의 우선순위 3단계. 화면에서는 pill 버튼 하나를 눌러 HIGH → MID → LOW → HIGH로 순환한다
 * (승인된 목업의 {@code nextPrio()}와 같은 순서 — 셀렉트 박스가 아니다).
 *
 * <p><b>선언 순서가 곧 정렬 순서다</b>({@link #order()}). 미완료 목록은 기한이 같을 때 이 순서로 줄을 세운다.
 * 다만 DB에는 {@code @Enumerated(STRING)}으로 이름이 저장되므로 <b>SQL {@code ORDER BY}로는 이 순서가 나오지 않는다</b>
 * (문자열 정렬이면 HIGH → LOW → MID가 된다). 그래서 정렬은 {@code TodoItemService}가 Java에서 한다.
 */
public enum TodoPriority {
    HIGH("높음"),
    MID("보통"),
    LOW("낮음");

    private final String label;

    TodoPriority(String label) {
        this.label = label;
    }

    /** 화면에 그대로 찍히는 한국어 표기("높음"/"보통"/"낮음"). */
    public String label() {
        return label;
    }

    /** 정렬 우선순위(작을수록 위). 선언 순서를 그대로 쓴다. */
    public int order() {
        return ordinal();
    }

    /** pill 버튼을 눌렀을 때 갈 다음 단계. 마지막에서 처음으로 돌아온다. */
    public TodoPriority next() {
        TodoPriority[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    /** 폼에서 값이 안 왔거나 알 수 없는 값이면 기본값(보통). */
    public static TodoPriority orDefault(TodoPriority priority) {
        return priority == null ? MID : priority;
    }
}
