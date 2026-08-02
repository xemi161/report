package com.weeklyreport.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.weeklyreport.domain.AppSettings;

public interface AppSettingsRepository extends JpaRepository<AppSettings, Long> {
}
