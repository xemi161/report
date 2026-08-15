package com.weeklyreport.web;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.weeklyreport.domain.AppSettings;
import com.weeklyreport.repository.AppSettingsRepository;

/**
 * 모든 화면 헤더(이름·직책)에 필요한 값을 채워준다.
 * DailyNoteController는 페이지가 아니라 프래그먼트만 돌려주지만, 그 프래그먼트가
 * 레이아웃 값을 참조할 수 있으므로 함께 포함해 둔다.
 */
@ControllerAdvice(assignableTypes = {
        DashboardController.class,
        EntryController.class,
        HistoryController.class,
        DailyNoteController.class})
public class LayoutAdvice {

    private final AppSettingsRepository appSettingsRepository;

    public LayoutAdvice(AppSettingsRepository appSettingsRepository) {
        this.appSettingsRepository = appSettingsRepository;
    }

    /** 온보딩 직후를 제외하면 항상 존재한다. 없으면 인터셉터가 온보딩으로 돌려보낸다. */
    @ModelAttribute("settings")
    public AppSettings settings() {
        return appSettingsRepository.findById(AppSettings.SINGLETON_ID).orElse(null);
    }
}
