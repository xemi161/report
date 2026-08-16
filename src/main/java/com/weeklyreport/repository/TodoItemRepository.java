package com.weeklyreport.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.weeklyreport.domain.TodoItem;

/**
 * 할 일 조회. 연관관계가 없는 엔티티라 fetch 전략을 챙길 것이 없다.
 *
 * <p><b>정렬을 SQL에 맡기지 않는다.</b> 미완료 목록의 2차 정렬축인 우선순위는
 * {@code @Enumerated(STRING)}이라 {@code ORDER BY priority}가 문자열 순(HIGH → LOW → MID)으로 나온다.
 * 그래서 여기서는 걸러오기만 하고 순서는 {@code TodoItemService}가 Java 비교자로 정한다
 * (개인 목록 규모라 정렬을 메모리에서 해도 비용이 없다).
 */
public interface TodoItemRepository extends JpaRepository<TodoItem, Long> {

    List<TodoItem> findByDoneFalse();

    List<TodoItem> findByDoneTrue();
}
