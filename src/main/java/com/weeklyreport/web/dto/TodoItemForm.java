package com.weeklyreport.web.dto;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

import com.weeklyreport.domain.enums.TodoPriority;

/**
 * 할 일 추가/수정 폼 바인딩 전용({@code DailyNoteForm}과 같은 자리).
 *
 * <p>{@code priority}는 enum 이름 그대로 받는다({@code HIGH}/{@code MID}/{@code LOW}).
 * 빈 문자열은 Spring이 null로 바인딩하고, null은 서비스가 기본값(MID)으로 채운다.
 */
public class TodoItemForm {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dueDate;

    private String text;

    private TodoPriority priority;

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public TodoPriority getPriority() {
        return priority;
    }

    public void setPriority(TodoPriority priority) {
        this.priority = priority;
    }
}
