package com.weeklyreport.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 등록 화면 인라인 편집 행에 바인딩되는 폼 객체. */
public class ItemForm {

    private Long id;
    private String group;
    private Long projectId;
    private String newProjectName;
    private String ticket;
    private String title;
    private String phase;
    private BigDecimal hours;
    private Integer days;
    private Integer completion;
    private LocalDate devDoneDate;
    private LocalDate testDate;
    private LocalDate deployDate;
    private String note;
    private LocalDate date;
    private LocalDate endDate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public String getNewProjectName() {
        return newProjectName;
    }

    public void setNewProjectName(String newProjectName) {
        this.newProjectName = newProjectName;
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

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
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

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }
}
