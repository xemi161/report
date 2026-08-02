package com.weeklyreport.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** 온보딩에서 1회 입력되는 싱글 로우 설정. id는 항상 1로 고정한다. */
@Entity
public class AppSettings {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    private String name;

    private String role;

    private String ticketPrefix;

    protected AppSettings() {
    }

    public AppSettings(String name, String role, String ticketPrefix) {
        this.id = SINGLETON_ID;
        this.name = name;
        this.role = role;
        this.ticketPrefix = ticketPrefix;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getTicketPrefix() {
        return ticketPrefix;
    }

    public void setTicketPrefix(String ticketPrefix) {
        this.ticketPrefix = ticketPrefix;
    }
}
