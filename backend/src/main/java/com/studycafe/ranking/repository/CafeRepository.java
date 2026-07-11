package com.studycafe.ranking.repository;

import com.studycafe.ranking.domain.Cafe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CafeRepository extends JpaRepository<Cafe, Long> {

    Optional<Cafe> findByQrToken(String qrToken);
}
