package com.weeklyreport.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.weeklyreport.domain.Project;
import com.weeklyreport.domain.ReportItem;

public interface ReportItemRepository extends JpaRepository<ReportItem, Long> {

    List<ReportItem> findByProjectOrderByWeeklyReport_WeekStartDesc(Project project);
}
