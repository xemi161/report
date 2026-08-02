package com.weeklyreport.service;

import org.springframework.stereotype.Service;

/**
 * 두레이 방식 티켓번호(예: NHNKCP-개발1팀/117) 자동완성.
 * 숫자만 입력하면 설정된 접두사를 붙여 완성된 문자열로 저장한다.
 * 접두사가 이후 바뀌어도 이미 저장된 티켓 문자열은 그대로 보존된다.
 */
@Service
public class TicketNumberService {

    public String complete(String rawInput, String currentPrefix) {
        if (rawInput == null) {
            return null;
        }
        String trimmed = rawInput.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.matches("\\d+")) {
            return currentPrefix + "/" + trimmed;
        }
        return trimmed;
    }
}
