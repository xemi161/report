package com.weeklyreport.service;

import java.time.LocalDate;

/** 금요일 시작 ~ 목요일 종료 1주일 구간과 그 "N월 M주차" 라벨. */
public record WeekPeriod(LocalDate weekStart, LocalDate weekEnd, String label) {

    /** 파일명에 쓰이는 공백 없는 라벨 (예: "8월1주"). 본문/JSON에는 공백이 있는 {@link #label()}을 사용한다. */
    public String labelForFilename() {
        return label.replace(" ", "");
    }

    public WeekPeriod next() {
        LocalDate nextStart = weekStart.plusDays(7);
        return WeekLabelService.forWeekStart(nextStart);
    }

    public WeekPeriod previous() {
        LocalDate prevStart = weekStart.minusDays(7);
        return WeekLabelService.forWeekStart(prevStart);
    }
}
