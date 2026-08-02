package com.weeklyreport.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.weeklyreport.domain.WeeklyReport;
import com.weeklyreport.domain.enums.ReportStatus;

public interface WeeklyReportRepository extends JpaRepository<WeeklyReport, Long> {

    /**
     * items와 그 project 연관까지 즉시 로딩해 컨트롤러/템플릿/MdExportService에서
     * 세션 없이도 (지연 로딩 프록시 없이) 접근 가능하게 한다.
     */
    @Query("select distinct w from WeeklyReport w left join fetch w.items i left join fetch i.project where w.weekStart = :weekStart")
    Optional<WeeklyReport> findByWeekStart(@Param("weekStart") LocalDate weekStart);

    @Query("select distinct w from WeeklyReport w left join fetch w.items i left join fetch i.project where w.id = :id")
    Optional<WeeklyReport> findWithItemsById(@Param("id") Long id);

    List<WeeklyReport> findByStatusOrderByWeekStartDesc(ReportStatus status);

    List<WeeklyReport> findTop4ByStatusOrderByWeekStartDesc(ReportStatus status);

    List<WeeklyReport> findTop3ByStatusOrderByWeekStartDesc(ReportStatus status);
}
