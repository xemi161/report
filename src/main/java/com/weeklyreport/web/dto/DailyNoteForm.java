package com.weeklyreport.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

/**
 * 일일 기록 추가/수정 폼 바인딩 전용.
 *
 * <p>{@code @ModelAttribute}로 받는 이유는 빈 문자열 → null 변환 때문이다 —
 * 시간 칸을 비운 채 저장하면 0이 아니라 <b>null(안 적음)</b>이 되어야 한다
 * ({@code ItemForm.hours}와 같은 바인딩 규칙).
 */
public class DailyNoteForm {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate workDate;

    private String text;

    private BigDecimal hours;

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public BigDecimal getHours() {
        return hours;
    }

    public void setHours(BigDecimal hours) {
        this.hours = hours;
    }
}
