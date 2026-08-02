package com.weeklyreport.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.weeklyreport.domain.AppSettings;
import com.weeklyreport.repository.AppSettingsRepository;
import com.weeklyreport.web.dto.OnboardingForm;

@Controller
public class OnboardingController {

    private final AppSettingsRepository appSettingsRepository;

    public OnboardingController(AppSettingsRepository appSettingsRepository) {
        this.appSettingsRepository = appSettingsRepository;
    }

    @GetMapping("/onboarding")
    public String form(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new OnboardingForm());
        }
        return "onboarding";
    }

    @PostMapping("/onboarding")
    public String submit(@ModelAttribute("form") OnboardingForm form, Model model) {
        if (isBlank(form.getName()) || isBlank(form.getRole()) || isBlank(form.getTicketPrefix())) {
            model.addAttribute("error", "이름, 직책, 티켓 접두사를 모두 입력해 주세요.");
            return "onboarding";
        }
        AppSettings settings = new AppSettings(form.getName().trim(), form.getRole().trim(), form.getTicketPrefix().trim());
        appSettingsRepository.save(settings);
        return "redirect:/entry";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
