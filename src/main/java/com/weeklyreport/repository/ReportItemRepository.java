package com.weeklyreport.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.weeklyreport.domain.Project;
import com.weeklyreport.domain.ReportItem;

public interface ReportItemRepository extends JpaRepository<ReportItem, Long> {

    List<ReportItem> findByProjectOrderByWeeklyReport_WeekStartDesc(Project project);

    /**
     * 대시보드 "진행중인 프로젝트"용 — 활성 프로젝트에 달린 항목을 최신 주부터 훑는다.
     *
     * <p>project/weeklyReport를 join fetch 하는 이유는 open-in-view=false라서다:
     * 진행률(항목 완료율 평균)과 "최근 보고 주차" 라벨을 트랜잭션 밖 화면에서 읽는다.
     * inner join이므로 프로젝트가 없는 항목(dev/etc/vacation)은 자연히 빠지지만,
     * 그룹 판정은 호출부에서 한 번 더 한다.
     */
    @Query("select i from ReportItem i join fetch i.project p join fetch i.weeklyReport w "
            + "where p.active = true order by w.weekStart desc, i.sortOrder asc")
    List<ReportItem> findActiveProjectItemsRecentFirst();
}
