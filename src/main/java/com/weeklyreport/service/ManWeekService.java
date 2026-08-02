package com.weeklyreport.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;

import com.weeklyreport.domain.ReportItem;

/**
 * 맨위크(투입공수) 계산.
 * 맨위크 = (1 × hours × days) / 5 / 8   (days 생략 시 1)
 * totalManWeek = 전체 항목 시간 합(= Σ hours×days) / 40
 * (두 식은 동치: Σ 개별 맨위크 = Σ(hours×days)/40 = totalManWeek)
 */
@Service
public class ManWeekService {

    private static final BigDecimal WEEKLY_HOURS = BigDecimal.valueOf(40);
    private static final int MAN_WEEK_SCALE = 2;

    /** 항목이 이번 주에 투입한 총 시간(hours × days). hours가 없으면 0. */
    public BigDecimal itemTotalHours(ReportItem item) {
        if (item.getHours() == null) {
            return BigDecimal.ZERO;
        }
        return item.getHours().multiply(BigDecimal.valueOf(item.daysOrDefault()));
    }

    public BigDecimal itemManWeek(ReportItem item) {
        return itemTotalHours(item)
                .divide(WEEKLY_HOURS, MAN_WEEK_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal totalHours(List<ReportItem> items) {
        return items.stream()
                .map(this::itemTotalHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalManWeek(List<ReportItem> items) {
        return totalHours(items)
                .divide(WEEKLY_HOURS, MAN_WEEK_SCALE, RoundingMode.HALF_UP);
    }
}
