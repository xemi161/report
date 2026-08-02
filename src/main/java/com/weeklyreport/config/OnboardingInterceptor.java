package com.weeklyreport.config;

import org.springframework.web.servlet.HandlerInterceptor;

import com.weeklyreport.repository.AppSettingsRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 온보딩(이름/직책 입력)이 끝나기 전에는 다른 화면에 접근하지 못하도록 막는다. */
public class OnboardingInterceptor implements HandlerInterceptor {

    private final AppSettingsRepository appSettingsRepository;

    public OnboardingInterceptor(AppSettingsRepository appSettingsRepository) {
        this.appSettingsRepository = appSettingsRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        boolean onboarded = appSettingsRepository.existsById(com.weeklyreport.domain.AppSettings.SINGLETON_ID);
        if (!onboarded) {
            response.sendRedirect(request.getContextPath() + "/onboarding");
            return false;
        }
        return true;
    }
}
