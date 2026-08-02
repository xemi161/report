package com.weeklyreport.service;

import java.time.DayOfWeek;
import java.time.LocalDate;

import org.springframework.stereotype.Service;

/**
 * 주차 라벨 계산. ISO 주차가 아니라 "N월 M주차" 형식을 쓴다.
 * 규칙: 금요일 시작~목요일 종료, 그 주 목요일이 속한 달을 기준으로
 * 그 달의 몇 번째 목요일이 포함된 주인지로 M주차를 결정한다.
 * 예: 07.31(금)~08.06(목)은 목요일이 8월이므로 "8월 1주".
 */
@Service
public class WeekLabelService {

    public WeekPeriod current() {
        return forDate(LocalDate.now());
    }

    /** 주어진 날짜가 속한 주(금~목)의 구간/라벨을 계산한다. */
    public static WeekPeriod forDate(LocalDate date) {
        int daysSinceFriday = Math.floorMod(
                date.getDayOfWeek().getValue() - DayOfWeek.FRIDAY.getValue(), 7);
        LocalDate weekStart = date.minusDays(daysSinceFriday);
        return forWeekStart(weekStart);
    }

    /** weekStart는 반드시 금요일이어야 한다. */
    public static WeekPeriod forWeekStart(LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(6);
        int weekOfMonth = ((weekEnd.getDayOfMonth() - 1) / 7) + 1;
        // 스키마 문서의 실제 표기 예시("8월 1주")를 따름 - "차"는 붙지 않는다.
        String label = weekEnd.getMonthValue() + "월 " + weekOfMonth + "주";
        return new WeekPeriod(weekStart, weekEnd, label);
    }
}
