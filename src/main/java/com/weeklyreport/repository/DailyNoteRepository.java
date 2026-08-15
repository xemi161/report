package com.weeklyreport.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.weeklyreport.domain.DailyNote;

/**
 * 일일 기록 조회. 연관관계가 없는 엔티티라 fetch 전략을 챙길 것이 없다.
 *
 * <p>주 조회는 오름차순(금→목), 월 조회는 <b>내림차순(최신 위)</b>이다 —
 * 정렬 방향이 서로 반대인 것은 의도된 설계다(작성 패널은 한 주를 처음부터 읽는 자리,
 * "한 일 기록" 화면은 최근에서 거슬러 올라가는 자리라 기준 축이 다르다). 버그가 아니다.
 *
 * <p>한 달 최대 ~60건 규모라 페이지네이션은 두지 않는다.
 */
public interface DailyNoteRepository extends JpaRepository<DailyNote, Long> {

    /** 주 범위(대시보드 카드 · 작성 화면 좌측 패널 공용). 같은 날 안에서는 입력 순서(id)를 지킨다. */
    List<DailyNote> findByWorkDateBetweenOrderByWorkDateAscIdAsc(LocalDate from, LocalDate to);

    /** 월 범위("한 일 기록" 화면). 날짜는 최신순, 같은 날 안에서는 입력 순서 그대로. */
    List<DailyNote> findByWorkDateBetweenOrderByWorkDateDescIdAsc(LocalDate from, LocalDate to);

    /**
     * 월 범위 + 본문 부분일치("한 일 기록" 화면의 검색). 정렬은 위 월 조회와 완전히 동일하다 —
     * 검색은 <b>그 달 안에서만</b> 거르는 필터이지 전체 기간 검색이 아니다(월 페이저가 축을 유지해야 한다).
     *
     * <p>대소문자 무시는 파생 쿼리의 {@code IgnoreCase}에 맡긴다(H2에서 {@code LOWER(...) LIKE}로 나간다).
     * 한 달 최대 ~60건 규모라 인덱스 없는 LIKE로 충분하다.
     */
    List<DailyNote> findByWorkDateBetweenAndTextContainingIgnoreCaseOrderByWorkDateDescIdAsc(
            LocalDate from, LocalDate to, String text);

    /** 월 페이저의 이동 가능 범위 계산용. */
    Optional<DailyNote> findTopByOrderByWorkDateAsc();

    Optional<DailyNote> findTopByOrderByWorkDateDesc();
}
