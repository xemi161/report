package com.weeklyreport.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.weeklyreport.domain.Project;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByActiveTrueOrderByNameAsc();
}
