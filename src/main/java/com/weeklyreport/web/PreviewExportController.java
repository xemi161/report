package com.weeklyreport.web;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.weeklyreport.domain.AppSettings;
import com.weeklyreport.domain.WeeklyReport;
import com.weeklyreport.domain.enums.ReportStatus;
import com.weeklyreport.repository.AppSettingsRepository;
import com.weeklyreport.repository.WeeklyReportRepository;
import com.weeklyreport.service.EntryService;
import com.weeklyreport.service.MdExportService;

@Controller
public class PreviewExportController {

    private final EntryService entryService;
    private final MdExportService mdExportService;
    private final AppSettingsRepository appSettingsRepository;
    private final WeeklyReportRepository weeklyReportRepository;

    public PreviewExportController(EntryService entryService,
                                    MdExportService mdExportService,
                                    AppSettingsRepository appSettingsRepository,
                                    WeeklyReportRepository weeklyReportRepository) {
        this.entryService = entryService;
        this.mdExportService = mdExportService;
        this.appSettingsRepository = appSettingsRepository;
        this.weeklyReportRepository = weeklyReportRepository;
    }

    @GetMapping("/entry/preview")
    public String preview(Model model) {
        WeeklyReport report = entryService.getOrCreateCurrentDraft();
        AppSettings settings = currentSettings();
        model.addAttribute("report", report);
        model.addAttribute("mdText", mdExportService.export(settings, report));
        model.addAttribute("editable", report.isEditable());
        model.addAttribute("activeMenu", "entry");
        return "preview";
    }

    @GetMapping("/export/{id}")
    @ResponseBody
    public ResponseEntity<byte[]> export(@PathVariable Long id) throws UnsupportedEncodingException {
        WeeklyReport report = weeklyReportRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (report.getStatus() != ReportStatus.SUBMITTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "제출되지 않은 보고서는 내보낼 수 없습니다.");
        }
        AppSettings settings = currentSettings();
        String content = mdExportService.export(settings, report);
        String fileName = mdExportService.fileName(settings, report);
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                .body(content.getBytes(StandardCharsets.UTF_8));
    }

    private AppSettings currentSettings() {
        return appSettingsRepository.findById(AppSettings.SINGLETON_ID).orElseThrow();
    }
}
