package com.weeklyreport.web.dto;

public class OnboardingForm {

    private String name;
    private String role = "파트원";
    private String ticketPrefix;

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
