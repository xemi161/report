package com.weeklyreport;

import java.awt.Desktop;
import java.net.URI;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class WeeklyReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(WeeklyReportApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void openBrowserOnStartup(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        String port = env.getProperty("local.server.port", env.getProperty("server.port", "8099"));
        if (Boolean.getBoolean("weeklyreport.noBrowser")) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create("http://localhost:" + port));
            }
        } catch (Exception ignored) {
            // 브라우저 자동 오픈 실패는 치명적이지 않음 (수동으로 접속 가능)
        }
    }
}
