package com.studycafe.ranking.user;

import com.studycafe.ranking.common.exception.SchoolNotFoundException;
import com.studycafe.ranking.common.exception.UserNotFoundException;
import com.studycafe.ranking.domain.School;
import com.studycafe.ranking.domain.User;
import com.studycafe.ranking.repository.SchoolRepository;
import com.studycafe.ranking.repository.UserRepository;
import com.studycafe.ranking.user.dto.UserResponse;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;

    public UserService(UserRepository userRepository, SchoolRepository schoolRepository) {
        this.userRepository = userRepository;
        this.schoolRepository = schoolRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long userId) {
        return userRepository.findByIdWithSchool(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    /**
     * 소속(학교) 변경 — 유저 본인(프로필)과 관리자(전학 처리)가 공유하는 단일 로직. schoolId=null 이면 무소속.
     * 랭킹은 현재 학교 기준이라 과거 기록도 새 학교 랭킹으로 함께 집계된다(별도 이관 불필요). 동명이인 seq(§3.3)는
     * 새 학교에서 안 쓰인 가장 작은 값으로(삭제로 생긴 빈자리 재사용). 같은 소속 재선택은 no-op — 본인이 대상
     * 카운트에 잡혀 단독 사용자 seq 가 부풀는 것을 막는다.
     */
    @Transactional
    public void changeSchool(Long userId, Long schoolId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        Long currentSchoolId = user.getSchool() != null ? user.getSchool().getId() : null;
        if (Objects.equals(currentSchoolId, schoolId)) {
            return; // 같은 소속 → 변경 없음(seq 부풀림 방지)
        }
        School newSchool = (schoolId == null) ? null
                : schoolRepository.findById(schoolId).orElseThrow(() -> new SchoolNotFoundException(schoolId));
        int nameSeq = (newSchool == null)
                ? NameSeqAllocator.smallestUnused(userRepository.findNameSeqsByDisplayNameAndSchoolIsNull(user.getDisplayName()))
                : NameSeqAllocator.smallestUnused(userRepository.findNameSeqsByDisplayNameAndSchool(user.getDisplayName(), newSchool));
        user.moveToSchool(newSchool, nameSeq);
    }
}
