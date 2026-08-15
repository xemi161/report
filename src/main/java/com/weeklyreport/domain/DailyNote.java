package com.weeklyreport.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 일일 기록("오늘 한 일") 한 줄. 주간보고를 쓸 때 옆에 두고 보는 개인 메모다.
 *
 * <p><b>다른 엔티티와 연관관계가 전혀 없다</b> — ReportItem/WeeklyReport/Project 어느 것과도
 * FK로 이어지지 않고, 승격(기록 → 보고서 항목 자동 등록) 장치도 없다(설계 v3에서 제거 확정).
 * 연관이 없으므로 Project처럼 id 기준 equals/hashCode를 오버라이드할 이유도 없다
 * (다른 영속성 컨텍스트에서 로딩된 같은 행을 Map/Set 키로 맞대볼 일이 없다).
 *
 * <p><b>소속 주(weekStart)를 저장하지 않는다.</b> 주 경계(금~목)는 {@code WeekLabelService}가
 * 유일한 정의이고, 여기에 weekStart를 복제하면 주 정의가 바뀔 때 동기화 버그가 생긴다.
 * 주/월 조회는 전부 workDate 범위 검색으로 푼다.
 *
 * <p>이 기록은 {@code .md} 내보내기에 절대 포함되지 않으며, 맨위크·총 투입시간 계산에도 섞이지 않는다.
 */
@Entity
@Table(indexes = @Index(columnList = "workDate"))
public class DailyNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 기록의 기준 날짜. 소속 주는 이 값에서 파생한다. */
    private LocalDate workDate;

    /**
     * 한 일 한 줄. H2에서 TEXT는 데이터 타입 이름이라 컬럼명으로 쓰면 혼동을 부르므로
     * ({@code group} → {@code group_type}과 같은 이유) 컬럼명을 명시적으로 피해둔다.
     */
    @Column(name = "note_text", length = 1000)
    private String text;

    /**
     * 투입시간. <b>선택 입력이라 null이 정상</b>이며 null은 "0시간"이 아니라 "안 적음"을 뜻한다.
     * 합계에서만 0으로 취급한다.
     */
    private BigDecimal hours;

    private LocalDateTime createdAt = LocalDateTime.now();

    protected DailyNote() {
    }

    public DailyNote(LocalDate workDate, String text, BigDecimal hours) {
        this.workDate = workDate;
        this.text = text;
        this.hours = hours;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public void setWorkDate(LocalDate workDate) {
        this.workDate = workDate;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public BigDecimal getHours() {
        return hours;
    }

    public void setHours(BigDecimal hours) {
        this.hours = hours;
    }

    /**
     * 입력칸에 넣을 시간 표기. DB에서 돌아온 BigDecimal은 scale이 붙어 "2.00"처럼 보이는데
     * 56px짜리 시간 칸에서 잘리므로 뒷자리 0을 뗀다({@code ReportItem.hoursDisplay()}와 동일 규칙).
     * 미입력은 빈 문자열 — 화면에서 placeholder "-"가 보여야 한다.
     */
    public String hoursDisplay() {
        if (hours == null) {
            return "";
        }
        BigDecimal stripped = hours.stripTrailingZeros();
        if (stripped.scale() < 0) {
            stripped = stripped.setScale(0);
        }
        return stripped.toPlainString();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
