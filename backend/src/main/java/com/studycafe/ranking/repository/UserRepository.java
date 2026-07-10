package com.studycafe.ranking.repository;

import com.studycafe.ranking.domain.School;
import com.studycafe.ranking.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    int countByDisplayNameAndSchool(String displayName, School school);

    int countByDisplayNameAndSchoolIsNull(String displayName);

    /** school 까지 fetch join (open-in-view=false 환경에서 지연로딩 예외 방지). */
    @Query("select u from User u left join fetch u.school where u.id = :id")
    Optional<User> findByIdWithSchool(@Param("id") Long id);
}
