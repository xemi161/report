package com.weeklyreport.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import com.weeklyreport.domain.AppSettings;
import com.weeklyreport.domain.WeeklyReport;
import com.weeklyreport.repository.AppSettingsRepository;
import com.weeklyreport.repository.WeeklyReportRepository;
import com.weeklyreport.service.MdExportService;

/** md 파일 다운로드. 제출된 주는 몇 번이든 다시 내려받을 수 있다. */
@Controller
public class ExportController {

    private final MdExportService mdExportService;
    private final AppSettingsRepository appSettingsRepository;
    private final WeeklyReportRepository weeklyReportRepository;

    public ExportController(MdExportService mdExportService,
                             AppSettingsRepository appSettingsRepository,
                             WeeklyReportRepository weeklyReportRepository) {
        this.mdExportService = mdExportService;
        this.appSettingsRepository = appSettingsRepository;
        this.weeklyReportRepository = weeklyReportRepository;
    }

    @GetMapping("/export/{id}")
    @ResponseBody
    public ResponseEntity<byte[]> export(@PathVariable Long id) {
        WeeklyReport report = weeklyReportRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        AppSettings settings = appSettingsRepository.findById(AppSettings.SINGLETON_ID).orElseThrow();

        String content = mdExportService.export(settings, report);
        String fileName = mdExportService.fileName(settings, report);
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                .body(content.getBytes(StandardCharsets.UTF_8));
    }
}
