package com.studycafe.ranking.school;

import com.studycafe.ranking.repository.SchoolRepository;
import com.studycafe.ranking.school.dto.SchoolResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 회원가입 시 학교 선택용 목록. 무소속은 목록에 없고 클라이언트가 별도 선택(§3.7). */
@RestController
@RequestMapping("/api/schools")
public class SchoolController {

    private final SchoolRepository schoolRepository;

    public SchoolController(SchoolRepository schoolRepository) {
        this.schoolRepository = schoolRepository;
    }

    @GetMapping
    public List<SchoolResponse> list() {
        return schoolRepository.findAllByOrderByNameAsc()
                .stream()
                .map(SchoolResponse::from)
                .toList();
    }
}
