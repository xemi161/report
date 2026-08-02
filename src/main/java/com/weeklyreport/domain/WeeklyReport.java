package com.weeklyreport.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.weeklyreport.domain.enums.ReportStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;

/** 1주일치 보고서. weekStart(금)~weekEnd(목) 단위로 유일해야 한다. */
@Entity
public class WeeklyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** "N월 M주차" 형식. ISO 주차 아님. */
    private String weekLabel;

    private LocalDate weekStart;

    private LocalDate weekEnd;

    @Enumerated(EnumType.STRING)
    private ReportStatus status = ReportStatus.DRAFT;

    private BigDecimal totalHours = BigDecimal.ZERO;

    private BigDecimal totalManWeek = BigDecimal.ZERO;

    private LocalDateTime submittedAt;

    @OneToMany(mappedBy = "weeklyReport", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<ReportItem> items = new ArrayList<>();

    protected WeeklyReport() {
    }

    public WeeklyReport(String weekLabel, LocalDate weekStart, LocalDate weekEnd) {
        this.weekLabel = weekLabel;
        this.weekStart = weekStart;
        this.weekEnd = weekEnd;
    }

    public Long getId() {
        return id;
    }

    public String getWeekLabel() {
        return weekLabel;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public LocalDate getWeekEnd() {
        return weekEnd;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public void setStatus(ReportStatus status) {
        this.status = status;
    }

    public BigDecimal getTotalHours() {
        return totalHours;
    }

    public void setTotalHours(BigDecimal totalHours) {
        this.totalHours = totalHours;
    }

    public BigDecimal getTotalManWeek() {
        return totalManWeek;
    }

    public void setTotalManWeek(BigDecimal totalManWeek) {
        this.totalManWeek = totalManWeek;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public List<ReportItem> getItems() {
        return items;
    }

    public boolean isEditable() {
        return status == ReportStatus.DRAFT;
    }

    public void addItem(ReportItem item) {
        item.setWeeklyReport(this);
        items.add(item);
    }

    public void removeItem(ReportItem item) {
        items.remove(item);
        item.setWeeklyReport(null);
    }
}
