package com.weeklyreport.service;

import org.springframework.stereotype.Service;

import com.weeklyreport.domain.ReportItem;
import com.weeklyreport.domain.WeeklyReport;

/**
 * 완료율 100% 미만인 project/dev 그룹 항목만 다음 주로 자동 이월한다.
 * (기타/휴가 그룹은 이월 대상이 아니다.)
 */
@Service
public class CarryOverService {

    /** previous의 이월 대상 항목을 target에 carriedOver=true로 복사한다. */
    public void applyCarryOver(WeeklyReport previous, WeeklyReport target) {
        for (ReportItem source : previous.getItems()) {
            if (!source.isCarryOverEligible()) {
                continue;
            }
            ReportItem copy = ReportItem.forGroup(source.getGroup());
            copy.setProject(source.getProject());
            copy.setTicket(source.getTicket());
            copy.setTitle(source.getTitle());
            copy.setPhase(source.getPhase());
            copy.setCompletion(source.getCompletion());
            copy.setCarriedOver(true);
            copy.setSortOrder(target.getItems().size());
            // 시간/일수/일정/비고는 새 주에 다시 입력받도록 비워둔다.
            target.addItem(copy);
        }
    }
}
