package com.weeklyreport.web;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.weeklyreport.domain.AppSettings;
import com.weeklyreport.domain.enums.ReportStatus;
import com.weeklyreport.repository.AppSettingsRepository;
import com.weeklyreport.repository.WeeklyReportRepository;

/** 모든 화면 헤더(이름·직책, 히스토리 개수)에 필요한 값을 채워준다. */
@ControllerAdvice(assignableTypes = {EntryController.class, HistoryController.class})
public class LayoutAdvice {

    private final AppSettingsRepository appSettingsRepository;
    private final WeeklyReportRepository weeklyReportRepository;

    public LayoutAdvice(AppSettingsRepository appSettingsRepository,
                         WeeklyReportRepository weeklyReportRepository) {
        this.appSettingsRepository = appSettingsRepository;
        this.weeklyReportRepository = weeklyReportRepository;
    }

    /** 온보딩 직후를 제외하면 항상 존재한다. 없으면 인터셉터가 온보딩으로 돌려보낸다. */
    @ModelAttribute("settings")
    public AppSettings settings() {
        return appSettingsRepository.findById(AppSettings.SINGLETON_ID).orElse(null);
    }

    @ModelAttribute("historyCount")
    public long historyCount() {
        return weeklyReportRepository.countByStatus(ReportStatus.SUBMITTED);
    }
}
