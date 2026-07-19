package com.studycafe.ranking.repository;

import com.studycafe.ranking.domain.School;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SchoolRepository extends JpaRepository<School, Long> {

    List<School> findAllByOrderByNameAsc();

    boolean existsByName(String name);
}
