package com.studycafe.ranking.admin;

import com.studycafe.ranking.admin.dto.AdminDtos.AdminSchool;
import com.studycafe.ranking.admin.dto.AdminDtos.AdminUser;
import com.studycafe.ranking.admin.dto.AdminDtos.BatchResult;
import com.studycafe.ranking.admin.dto.AdminDtos.CafeQr;
import com.studycafe.ranking.admin.dto.AdminDtos.SchoolRequest;
import com.studycafe.ranking.batch.DailyCloseService;
import com.studycafe.ranking.common.exception.AdminRuleViolationException;
import com.studycafe.ranking.common.exception.CafeNotFoundException;
import com.studycafe.ranking.common.exception.DuplicateSchoolNameException;
import com.studycafe.ranking.common.exception.SchoolNotFoundException;
import com.studycafe.ranking.common.exception.UserNotFoundException;
import com.studycafe.ranking.domain.Cafe;
import com.studycafe.ranking.domain.CheckInSession;
import com.studycafe.ranking.domain.Role;
import com.studycafe.ranking.domain.School;
import com.studycafe.ranking.domain.SessionStatus;
import com.studycafe.ranking.domain.User;
import com.studycafe.ranking.repository.CafeRepository;
import com.studycafe.ranking.repository.CheckInSessionRepository;
import com.studycafe.ranking.repository.DailyStudyRecordRepository;
import com.studycafe.ranking.repository.SchoolRepository;
import com.studycafe.ranking.repository.UserRepository;
import com.studycafe.ranking.studyrecord.StudyRecordService;
import com.studycafe.ranking.studytime.StudyClock;
import com.studycafe.ranking.studytime.StudyTimePolicy;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 조작 오케스트레이션. 권한 검사는 SecurityConfig(/api/admin/** = ADMIN)가 담당한다. */
@Service
@Transactional
public class AdminService {

    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;
    private final CafeRepository cafeRepository;
    private final CheckInSessionRepository sessionRepository;
    private final DailyStudyRecordRepository recordRepository;
    private final StudyRecordService studyRecordService;
    private final DailyCloseService dailyCloseService;

    public AdminService(UserRepository userRepository,
                        SchoolRepository schoolRepository,
                        CafeRepository cafeRepository,
                        CheckInSessionRepository sessionRepository,
                        DailyStudyRecordRepository recordRepository,
                        StudyRecordService studyRecordService,
                        DailyCloseService dailyCloseService) {
        this.userRepository = userRepository;
        this.schoolRepository = schoolRepository;
        this.cafeRepository = cafeRepository;
        this.sessionRepository = sessionRepository;
        this.recordRepository = recordRepository;
        this.studyRecordService = studyRecordService;
        this.dailyCloseService = dailyCloseService;
    }

    // ---------- 사용자 ----------

    @Transactional(readOnly = true)
    public List<AdminUser> listUsers() {
        int ym = StudyClock.studyMonthYm(Instant.now());
        Set<Long> checkedIn = Set.copyOf(sessionRepository.findUserIdsByStatus(SessionStatus.ACTIVE));
        return userRepository.findAllWithSchool().stream()
                .map(u -> {
                    int warnings = u.effectiveWarnings(ym);
                    return new AdminUser(
                            u.getId(), u.getLoginId(), u.getDisplayName(),
                            u.getSchool() != null ? u.getSchool().getId() : null,
                            u.getSchool() != null ? u.getSchool().getName() : null,
                            u.getRole(),
                            warnings,
                            warnings >= StudyTimePolicy.PENALTY_THRESHOLD,
                            checkedIn.contains(u.getId()));
                })
                .toList();
    }

    public void changeRole(Long adminUserId, Long targetUserId, Role role) {
        // 본인 강등 금지 — 실수로 마지막 관리자가 사라지는 것을 막는다.
        if (adminUserId.equals(targetUserId) && role != Role.ADMIN) {
            throw new AdminRuleViolationException("자신의 관리자 권한은 스스로 회수할 수 없습니다.");
        }
        User target = getUser(targetUserId);
        target.changeRole(role);
    }

    public void deleteUser(Long adminUserId, Long targetUserId) {
        if (adminUserId.equals(targetUserId)) {
            throw new AdminRuleViolationException("자신의 계정은 삭제할 수 없습니다.");
        }
        User target = getUser(targetUserId);
        // 연쇄 정리: 집계 기록 → 세션 → 사용자 (FK 순서).
        recordRepository.deleteByUserId(target.getId());
        sessionRepository.deleteByUserId(target.getId());
        userRepository.delete(target);
    }

    public void resetWarnings(Long userId) {
        getUser(userId).resetWarnings();
    }

    /** 강제 체크아웃 — 열린 세션이 없으면 조용히 무시(멱등). 닫으면 집계 재계산. */
    public void forceCheckout(Long userId) {
        getUser(userId); // 존재 확인(없으면 404)
        CheckInSession active = sessionRepository.findActiveByUserId(userId).orElse(null);
        if (active == null) {
            return;
        }
        Instant now = Instant.now();
        int updated = sessionRepository.forceCloseIfActive(active.getId(), now);
        if (updated > 0) {
            studyRecordService.recompute(userId, active.getCheckInAt(), now);
        }
    }

    // ---------- 학교 ----------

    @Transactional(readOnly = true)
    public List<AdminSchool> listSchools() {
        Map<Long, Integer> memberCounts = new HashMap<>();
        for (Object[] row : userRepository.countMembersBySchool()) {
            memberCounts.put((Long) row[0], ((Long) row[1]).intValue());
        }
        return schoolRepository.findAllByOrderByNameAsc().stream()
                .map(s -> new AdminSchool(s.getId(), s.getName(), s.getShortName(),
                        memberCounts.getOrDefault(s.getId(), 0)))
                .toList();
    }

    public AdminSchool createSchool(SchoolRequest req) {
        if (schoolRepository.existsByName(req.name())) {
            throw new DuplicateSchoolNameException(req.name());
        }
        try {
            // saveAndFlush: existsByName 이후 동시 생성 레이스에서 제약 위반을 커밋이 아닌 지금 유발해 잡는다.
            School saved = schoolRepository.saveAndFlush(new School(req.name(), blankToNull(req.shortName())));
            return new AdminSchool(saved.getId(), saved.getName(), saved.getShortName(), 0);
        } catch (DataIntegrityViolationException e) {
            // schools 의 유일한 유니크 제약은 name 이라, 여기 위반은 이름 중복이다 → 409.
            throw new DuplicateSchoolNameException(req.name());
        }
    }

    public AdminSchool updateSchool(Long schoolId, SchoolRequest req) {
        School school = getSchool(schoolId);
        // 이름을 바꾸는 경우에만 중복 검사(자기 자신 제외).
        if (!school.getName().equals(req.name()) && schoolRepository.existsByName(req.name())) {
            throw new DuplicateSchoolNameException(req.name());
        }
        school.update(req.name(), blankToNull(req.shortName()));
        try {
            schoolRepository.flush(); // 동시 수정 레이스의 name 유니크 위반을 지금 잡는다.
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateSchoolNameException(req.name());
        }
        return new AdminSchool(school.getId(), school.getName(), school.getShortName(),
                userRepository.findBySchool(school).size());
    }

    public void deleteSchool(Long schoolId) {
        School school = getSchool(schoolId);
        // 소속 학생은 삭제하지 않고 무소속으로 돌린다(비파괴적, 되돌릴 수 있음).
        for (User member : userRepository.findBySchool(school)) {
            member.setSchool(null);
        }
        schoolRepository.delete(school);
    }

    // ---------- 카페 QR ----------

    /** QR 토큰 재발급. 기존 QR 은 무효가 되므로 새 토큰으로 QR 을 재출력·부착해야 한다. */
    public CafeQr rotateCafeQr(Long cafeId) {
        Cafe cafe = cafeRepository.findById(cafeId)
                .orElseThrow(() -> new CafeNotFoundException(cafeId));
        cafe.rotateQrToken("STUDYCAFE-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
        return new CafeQr(cafe.getId(), cafe.getName(), cafe.getQrToken());
    }

    @Transactional(readOnly = true)
    public List<CafeQr> listCafes() {
        return cafeRepository.findAll().stream()
                .map(c -> new CafeQr(c.getId(), c.getName(), c.getQrToken()))
                .collect(Collectors.toList());
    }

    // ---------- 배치 ----------

    /** 04:00 자동 마감을 지금 수동 실행(슬립으로 걸러진 배치 복구/테스트). */
    public BatchResult runDailyClose() {
        return new BatchResult(dailyCloseService.closeOverdue(Instant.now()));
    }

    // ---------- helpers ----------

    private User getUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }

    private School getSchool(Long id) {
        return schoolRepository.findById(id).orElseThrow(() -> new SchoolNotFoundException(id));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
