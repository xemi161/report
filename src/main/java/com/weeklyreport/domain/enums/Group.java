package com.weeklyreport.domain.enums;

/** 그룹 표기 순서는 항상 PROJECT -> DEV -> ETC -> VACATION 고정. */
public enum Group {
    PROJECT("프로젝트"),
    DEV("개발"),
    ETC("기타"),
    VACATION("휴가");

    private final String label;

    Group(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
