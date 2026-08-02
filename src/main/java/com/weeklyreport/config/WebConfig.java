package com.weeklyreport.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.weeklyreport.repository.AppSettingsRepository;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppSettingsRepository appSettingsRepository;

    public WebConfig(AppSettingsRepository appSettingsRepository) {
        this.appSettingsRepository = appSettingsRepository;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new OnboardingInterceptor(appSettingsRepository))
                .excludePathPatterns("/onboarding", "/onboarding/**", "/css/**", "/js/**", "/favicon.ico");
    }
}
