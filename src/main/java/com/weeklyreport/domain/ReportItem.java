package com.weeklyreport.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.weeklyreport.domain.enums.Group;
import com.weeklyreport.domain.enums.Phase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Index;

/**
 * 그룹별 필수값(스키마 문서 기준):
 * - project/dev: 티켓번호, 업무명, 완료율 필수 / 시간·일수·일정·비고 선택
 * - etc: 업무명 필수, 시간 선택
 * - vacation: 날짜 필수, 시간 선택
 * 필드 유효성은 그룹마다 달라 Bean Validation으로 표현하기 어려우므로 서비스 계층에서 검증한다.
 */
@Entity
@Table(indexes = @Index(columnList = "weeklyReport_id"))
public class ReportItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "weeklyReport_id")
    private WeeklyReport weeklyReport;

    @Enumerated(EnumType.STRING)
    @Column(name = "group_type")
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    private String ticket;

    private String title;

    @Enumerated(EnumType.STRING)
    private Phase phase;

    private BigDecimal hours;

    private Integer days;

    private Integer completion;

    private LocalDate devDoneDate;

    private LocalDate testDate;

    private LocalDate deployDate;

    private String note;

    private boolean carriedOver = false;

    /** vacation 그룹 전용 날짜. */
    private LocalDate date;

    private int sortOrder;

    protected ReportItem() {
    }

    public static ReportItem forGroup(Group group) {
        ReportItem item = new ReportItem();
        item.group = group;
        return item;
    }

    public Long getId() {
        return id;
    }

    public WeeklyReport getWeeklyReport() {
        return weeklyReport;
    }

    public void setWeeklyReport(WeeklyReport weeklyReport) {
        this.weeklyReport = weeklyReport;
    }

    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public String getTicket() {
        return ticket;
    }

    public void setTicket(String ticket) {
        this.ticket = ticket;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Phase getPhase() {
        return phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
    }

    public BigDecimal getHours() {
        return hours;
    }

    public void setHours(BigDecimal hours) {
        this.hours = hours;
    }

    public Integer getDays() {
        return days;
    }

    public void setDays(Integer days) {
        this.days = days;
    }

    /** days 생략 시 1로 간주 (스키마 문서 규칙). */
    public int daysOrDefault() {
        return days == null ? 1 : days;
    }

    public Integer getCompletion() {
        return completion;
    }

    public void setCompletion(Integer completion) {
        this.completion = completion;
    }

    public LocalDate getDevDoneDate() {
        return devDoneDate;
    }

    public void setDevDoneDate(LocalDate devDoneDate) {
        this.devDoneDate = devDoneDate;
    }

    public LocalDate getTestDate() {
        return testDate;
    }

    public void setTestDate(LocalDate testDate) {
        this.testDate = testDate;
    }

    public LocalDate getDeployDate() {
        return deployDate;
    }

    public void setDeployDate(LocalDate deployDate) {
        this.deployDate = deployDate;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public boolean isCarriedOver() {
        return carriedOver;
    }

    public void setCarriedOver(boolean carriedOver) {
        this.carriedOver = carriedOver;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    /** 이월 대상 여부: project/dev 그룹이면서 완료율이 100 미만. */
    public boolean isCarryOverEligible() {
        return (group == Group.PROJECT || group == Group.DEV)
                && (completion == null || completion < 100);
    }
}
