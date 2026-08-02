package com.weeklyreport.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.weeklyreport.domain.ReportItem;
import com.weeklyreport.domain.enums.Group;

class ManWeekServiceTest {

    private final ManWeekService service = new ManWeekService();

    @Test
    void 하루_8시간_풀타임_1일_투입은_0점2_맨위크() {
        ReportItem item = ReportItem.forGroup(Group.DEV);
        item.setHours(BigDecimal.valueOf(8));
        item.setDays(1);

        assertThat(service.itemManWeek(item)).isEqualByComparingTo("0.20");
    }

    @Test
    void days가_생략되면_1로_간주한다() {
        ReportItem item = ReportItem.forGroup(Group.DEV);
        item.setHours(BigDecimal.valueOf(8));
        // days 미설정

        assertThat(service.itemManWeek(item)).isEqualByComparingTo("0.20");
    }

    @Test
    void hours가_없으면_0으로_처리한다() {
        ReportItem item = ReportItem.forGroup(Group.ETC);

        assertThat(service.itemManWeek(item)).isEqualByComparingTo("0.00");
    }

    @Test
    void 풀타임으로_한_주를_꽉_채우면_총합이_1점0() {
        ReportItem project = ReportItem.forGroup(Group.PROJECT);
        project.setHours(BigDecimal.valueOf(8));
        project.setDays(4);

        ReportItem vacation = ReportItem.forGroup(Group.VACATION);
        vacation.setHours(BigDecimal.valueOf(8));

        assertThat(service.totalManWeek(List.of(project, vacation))).isEqualByComparingTo("1.00");
    }
}
