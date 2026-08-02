package com.weeklyreport.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.weeklyreport.domain.Project;
import com.weeklyreport.domain.ReportItem;
import com.weeklyreport.domain.WeeklyReport;
import com.weeklyreport.domain.enums.Group;

class CarryOverServiceTest {

    private final CarryOverService service = new CarryOverService();

    @Test
    void 완료율_100_미만인_프로젝트_개발_항목만_이월된다() {
        WeeklyReport previous = new WeeklyReport("8월 1주", LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 6));

        ReportItem incompleteProject = ReportItem.forGroup(Group.PROJECT);
        incompleteProject.setProject(new Project("GTPP"));
        incompleteProject.setTitle("미완료 프로젝트 업무");
        incompleteProject.setCompletion(60);
        previous.addItem(incompleteProject);

        ReportItem completeDev = ReportItem.forGroup(Group.DEV);
        completeDev.setTitle("완료된 개발 업무");
        completeDev.setCompletion(100);
        previous.addItem(completeDev);

        ReportItem etc = ReportItem.forGroup(Group.ETC);
        etc.setTitle("주간회의");
        previous.addItem(etc);

        ReportItem vacation = ReportItem.forGroup(Group.VACATION);
        vacation.setDate(LocalDate.of(2026, 8, 3));
        previous.addItem(vacation);

        WeeklyReport target = new WeeklyReport("8월 2주", LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 13));
        service.applyCarryOver(previous, target);

        assertThat(target.getItems()).hasSize(1);
        ReportItem carried = target.getItems().get(0);
        assertThat(carried.getTitle()).isEqualTo("미완료 프로젝트 업무");
        assertThat(carried.isCarriedOver()).isTrue();
        assertThat(carried.getCompletion()).isEqualTo(60);
    }

    @Test
    void 이월된_항목은_시간_일정_비고가_초기화된다() {
        WeeklyReport previous = new WeeklyReport("8월 1주", LocalDate.of(2026, 7, 31), LocalDate.of(2026, 8, 6));
        ReportItem item = ReportItem.forGroup(Group.DEV);
        item.setTitle("작업중");
        item.setCompletion(30);
        item.setHours(java.math.BigDecimal.valueOf(4));
        item.setNote("사유");
        previous.addItem(item);

        WeeklyReport target = new WeeklyReport("8월 2주", LocalDate.of(2026, 8, 7), LocalDate.of(2026, 8, 13));
        service.applyCarryOver(previous, target);

        ReportItem carried = target.getItems().get(0);
        assertThat(carried.getHours()).isNull();
        assertThat(carried.getNote()).isNull();
    }
}
