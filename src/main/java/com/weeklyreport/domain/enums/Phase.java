package com.weeklyreport.domain.enums;

/** project, dev 그룹에만 해당하는 진행단계. */
public enum Phase {
    ANALYSIS_DESIGN("분석/설계", "설계"),
    DEVELOPMENT("개발", "개발"),
    TEST("테스트", "테스트");

    private final String label;
    private final String shortLabel;

    Phase(String label, String shortLabel) {
        this.label = label;
        this.shortLabel = shortLabel;
    }

    /** JSON 데이터 블록의 phase 값 (예: "분석/설계"). */
    public String label() {
        return label;
    }

    /** md 본문 대괄호 표기(`[설계]`, `[개발]`, `[테스트]`)에 쓰이는 짧은 표기. */
    public String shortLabel() {
        return shortLabel;
    }

    public static Phase fromLabel(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (Phase phase : values()) {
            if (phase.label.equals(value)) {
                return phase;
            }
        }
        throw new IllegalArgumentException("알 수 없는 구분: " + value);
    }
}
