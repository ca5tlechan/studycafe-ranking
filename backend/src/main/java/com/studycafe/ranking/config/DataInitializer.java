package com.studycafe.ranking.config;

import com.studycafe.ranking.domain.School;
import com.studycafe.ranking.repository.SchoolRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 파일럿용 학교 목록 시드. 테이블이 비어 있을 때만 삽입한다.
 * (운영 시 운영자가 실제 주변/자주 오는 학교로 교체 — §3.7. 무소속은 school_id=null 이라 시드 대상 아님.)
 */
@Component
class DataInitializer implements CommandLineRunner {

    private final SchoolRepository schoolRepository;

    DataInitializer(SchoolRepository schoolRepository) {
        this.schoolRepository = schoolRepository;
    }

    @Override
    public void run(String... args) {
        if (schoolRepository.count() > 0) {
            return;
        }
        schoolRepository.saveAll(List.of(
                new School("서울대학교", "서울대"),
                new School("연세대학교", "연세대"),
                new School("고려대학교", "고려대"),
                new School("성균관대학교", "성대"),
                new School("한양대학교", "한대"),
                new School("서강대학교", "서강대"),
                new School("중앙대학교", "중대"),
                new School("경희대학교", "경희대")
        ));
    }
}
