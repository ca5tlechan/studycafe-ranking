package com.studycafe.ranking.config;

import com.studycafe.ranking.domain.Cafe;
import com.studycafe.ranking.domain.School;
import com.studycafe.ranking.repository.CafeRepository;
import com.studycafe.ranking.repository.SchoolRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 파일럿용 시드 데이터(학교 목록 + 카페 1행). 각 테이블이 비어 있을 때만 삽입한다.
 * (운영 시 운영자가 실제 값으로 교체 — §3.7. 무소속은 school_id=null 이라 시드 대상 아님.)
 */
@Component
class DataInitializer implements CommandLineRunner {

    /** 파일럿 카페 QR 토큰. 실제 QR에 인코딩되는 값과 일치시킨다. */
    private static final String PILOT_CAFE_QR_TOKEN = "STUDYCAFE-PILOT-001";

    private final SchoolRepository schoolRepository;
    private final CafeRepository cafeRepository;

    DataInitializer(SchoolRepository schoolRepository, CafeRepository cafeRepository) {
        this.schoolRepository = schoolRepository;
        this.cafeRepository = cafeRepository;
    }

    @Override
    public void run(String... args) {
        seedSchools();
        seedCafe();
    }

    private void seedSchools() {
        if (schoolRepository.count() > 0) {
            return;
        }
        schoolRepository.saveAll(List.of(
                new School("서울대학교", "서울대"),
                new School("연세대학교", "연세대"),
                new School("고려대학교", "고려대"),
                new School("성균관대학교", "성대"),
                new School("한양대학교", "한양대"), // "한대"는 쓰지 않는 약칭 — 랭킹 이름에 그대로 노출된다
                new School("서강대학교", "서강대"),
                new School("중앙대학교", "중대"),
                new School("경희대학교", "경희대")
        ));
    }

    private void seedCafe() {
        if (cafeRepository.count() > 0) {
            return;
        }
        cafeRepository.save(new Cafe("스터디카페 파일럿점", PILOT_CAFE_QR_TOKEN));
    }
}
