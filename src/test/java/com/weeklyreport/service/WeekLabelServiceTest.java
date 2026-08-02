package com.weeklyreport.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class WeekLabelServiceTest {

    @Test
    void 목요일이_다음달로_넘어가지_않는_평범한_주는_그대로_계산된다() {
        // 2026-07-31(금) ~ 2026-08-06(목): 목요일이 8월이므로 "8월 1주"
        WeekPeriod period = WeekLabelService.forWeekStart(LocalDate.of(2026, 7, 31));

        assertThat(period.weekStart()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(period.weekEnd()).isEqualTo(LocalDate.of(2026, 8, 6));
        assertThat(period.label()).isEqualTo("8월 1주");
    }

    @Test
    void 주중_아무_날짜로_조회해도_같은_주로_귀속된다() {
        // 2026-08-02(일)은 2026-07-31(금)~08-06(목) 주에 속함
        WeekPeriod period = WeekLabelService.forDate(LocalDate.of(2026, 8, 2));

        assertThat(period.weekStart()).isEqualTo(LocalDate.of(2026, 7, 31));
        assertThat(period.label()).isEqualTo("8월 1주");
    }

    @Test
    void 첫_목요일이_1일인_달의_주차_계산() {
        // 2026-10-01(목)이 10월의 첫 번째 목요일
        WeekPeriod week1 = WeekLabelService.forWeekStart(LocalDate.of(2026, 9, 25));
        WeekPeriod week2 = WeekLabelService.forWeekStart(LocalDate.of(2026, 10, 2));
        WeekPeriod week5 = WeekLabelService.forWeekStart(LocalDate.of(2026, 10, 23));

        assertThat(week1.label()).isEqualTo("10월 1주");
        assertThat(week2.label()).isEqualTo("10월 2주");
        assertThat(week5.label()).isEqualTo("10월 5주");
    }

    @Test
    void 파일명용_라벨은_공백이_제거된다() {
        WeekPeriod period = WeekLabelService.forWeekStart(LocalDate.of(2026, 7, 31));

        assertThat(period.labelForFilename()).isEqualTo("8월1주");
    }

    @Test
    void next와_previous는_7일_단위로_이동한다() {
        WeekPeriod period = WeekLabelService.forWeekStart(LocalDate.of(2026, 7, 31));

        assertThat(period.next().weekStart()).isEqualTo(LocalDate.of(2026, 8, 7));
        assertThat(period.previous().weekStart()).isEqualTo(LocalDate.of(2026, 7, 24));
    }
}
