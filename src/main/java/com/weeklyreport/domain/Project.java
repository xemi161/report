package com.weeklyreport.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * 프로젝트명은 수정 가능하며 수정 시 전체 이력에 반영되어야 하므로,
 * ReportItem은 이름 문자열을 복제하지 않고 이 엔티티를 FK로 참조한다.
 */
@Entity
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /**
     * 프로젝트 = 일감(티켓) 하나 모델이라 티켓번호는 프로젝트가 소유한다.
     * ReportItem.ticket에는 저장 시점에 이 값이 복사되며(md 스키마는 항목 1줄 = 티켓 1개 유지),
     * 과거 보고서의 티켓번호는 그때 복사된 값 그대로 남는다.
     */
    private String ticket;

    /** "종료" 버튼 자리 확보용 플래그. 종료 시 실제 동작(집계/조회 반영 방식)은 아직 미정. */
    private boolean active = true;

    protected Project() {
    }

    public Project(String name) {
        this.name = name;
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

    public String getTicket() {
        return ticket;
    }

    public void setTicket(String ticket) {
        this.ticket = ticket;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * 같은 프로젝트 행이 서로 다른 영속성 컨텍스트(다른 쿼리)에서 로딩되면
     * 별개의 자바 인스턴스가 되므로, id 기준 동등성이 없으면 Map/Set 키 비교나
     * item.getProject().equals(project) 매칭이 깨진다.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Project other)) {
            return false;
        }
        // other가 Hibernate 지연 로딩 프록시일 수 있으므로 반드시 getId()로 접근한다.
        // (필드 직접 접근(other.id)은 프록시의 실제 초기화를 우회해 항상 null을 반환한다.)
        return getId() != null && getId().equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
