package com.weeklyreport.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.weeklyreport.domain.enums.TodoPriority;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 할 일(TODO) 한 줄 — 앞으로 할 일을 적어두는 개인 목록이다.
 *
 * <p><b>{@link DailyNote}와 완전히 같은 성격의 데이터다.</b> ReportItem/WeeklyReport/Project 어느 것과도
 * FK로 이어지지 않고, {@code .md} 내보내기에 절대 나가지 않으며(카드 하단에 그 문구가 상시 노출된다),
 * 특정 주(weekStart)에 속하지도 않는다 — 주를 넘겨봐도 같은 목록이다.
 * 연관관계가 없으므로 {@code Project}처럼 id 기준 equals/hashCode를 오버라이드할 이유도 없다.
 *
 * <p><b>일일 기록과도 별개다.</b> 완료 처리해도 그 날의 {@link DailyNote}로 넘어가지 않고,
 * 기록에 적은 시간·맨위크 계산과도 아무 관계가 없다(예상소요시간 필드 자체가 없다).
 *
 * <p><b>자동 이월이 없다.</b> 기한이 지나도 날짜를 옮기지 않고 원래 기한에 그대로 두며,
 * 목록에서 "기한 지남" 그룹으로 묶어 표시만 강조한다(기한을 옮기는 것은 사람의 선택이다).
 */
@Entity
@Table(indexes = @Index(columnList = "dueDate"))
public class TodoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 기한. <b>필수</b>다 — 비워 두고 추가하면 서비스가 오늘 날짜로 채운다(목록의 정렬축이라 null을 허용하지 않는다). */
    private LocalDate dueDate;

    /**
     * 할 일 한 줄. {@code DailyNote.text}와 같은 이유로 컬럼명을 명시한다
     * (H2에서 TEXT는 데이터 타입 이름이라 컬럼명으로 쓰면 혼동을 부른다).
     */
    @Column(name = "todo_text", length = 1000)
    private String text;

    private boolean done = false;

    @Enumerated(EnumType.STRING)
    private TodoPriority priority = TodoPriority.MID;

    private LocalDateTime createdAt = LocalDateTime.now();

    protected TodoItem() {
    }

    public TodoItem(LocalDate dueDate, String text, TodoPriority priority) {
        this.dueDate = dueDate;
        this.text = text;
        this.priority = TodoPriority.orDefault(priority);
    }

    public Long getId() {
        return id;
    }

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

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public TodoPriority getPriority() {
        return priority;
    }

    public void setPriority(TodoPriority priority) {
        this.priority = TodoPriority.orDefault(priority);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** 기한 지남 = <b>미완료</b>이면서 기한이 오늘보다 이르다. 완료한 일은 아무리 늦어도 지남으로 보지 않는다. */
    public boolean isOverdue(LocalDate today) {
        return !done && dueDate != null && dueDate.isBefore(today);
    }

    /** "기한 지남" 행 앞에 붙는 날짜 칩 표기("08.10"). 그룹 안에서 항목마다 기한이 달라 행마다 보여준다. */
    public String dueShortLabel() {
        return dueDate == null ? "" : String.format("%02d.%02d", dueDate.getMonthValue(), dueDate.getDayOfMonth());
    }
}
