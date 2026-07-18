package com.studycafe.ranking.batch;

import com.studycafe.ranking.domain.CheckInSession;
import com.studycafe.ranking.domain.SessionStatus;
import com.studycafe.ranking.repository.CheckInSessionRepository;
import com.studycafe.ranking.repository.UserRepository;
import com.studycafe.ranking.studyrecord.StudyRecordService;
import com.studycafe.ranking.studytime.StudyClock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 04:00 일일 자동 마감(§3.6a) + 경고 적립(§3.6c).
 *
 * <p>04:00 시점에 아직 열려 있는 세션을 전부 종료한다. 각 세션은 <b>자기 스터디 날짜의 04:00</b>
 * (배치 실행 시각이 아니라)에 닫아, 미실행일이 있어도 세션당 하루를 넘겨 인정하지 않는다.
 * 종료 후 §3.1 분할·집계를 다시 태우고, 스스로 체크아웃하지 않은 소유자에게 경고 1회를 적립한다.
 *
 * <p>{@link #closeOverdue(Instant)} 는 {@code now} 를 인자로 받아 테스트에서 임의 시각으로 검증할 수 있다.
 */
@Service
public class DailyCloseService {

    private static final Logger log = LoggerFactory.getLogger(DailyCloseService.class);

    private final CheckInSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final StudyRecordService studyRecordService;

    public DailyCloseService(CheckInSessionRepository sessionRepository,
                             UserRepository userRepository,
                             StudyRecordService studyRecordService) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.studyRecordService = studyRecordService;
    }

    /**
     * {@code now} 시점 기준으로 마감 대상(그 이전 04:00을 넘긴 ACTIVE 세션)을 자동 종료한다.
     * 멱등: 이미 닫힌 세션은 findAllByStatus(ACTIVE) 에 잡히지 않으므로 두 번 돌려도 결과가 같다.
     *
     * @return 닫은 세션 수
     */
    @Transactional
    public int closeOverdue(Instant now) {
        // now 직전(포함)의 04:00 = 이번에 지나간 스터디 경계. 이 시각을 넘겨 열려 있던 세션이 대상.
        Instant boundary = StudyClock.startOf(StudyClock.studyDateOf(now));

        int closed = 0;
        for (CheckInSession session : sessionRepository.findAllByStatus(SessionStatus.ACTIVE)) {
            Instant checkInAt = session.getCheckInAt();
            if (!checkInAt.isBefore(boundary)) {
                continue; // 이번 경계 이후 체크인 → 아직 오늘 세션, 마감 대상 아님
            }
            // autoCloseIfActive 가 영속성 컨텍스트를 비우므로(clearAutomatically, 아래 참고),
            // 그 이후엔 session 엔티티에 다시 의존하지 않도록 필요한 값을 먼저 뽑아둔다.
            Long userId = session.getUser().getId();
            // 각 세션은 자기 날짜의 04:00에 닫는다(= checkIn 직후 첫 04:00). 정상 세션이면 boundary 와 같다.
            Instant closeAt = StudyClock.nextDayBoundary(checkInAt);

            // 원자적 조건부 종료. 이 순간 사용자가 스스로 체크아웃했다면(경쟁) 0건이 되어 이 세션은
            // 건드리지 않는다 — session.autoClose() 로 메모리 엔티티만 검사하면, 별도 트랜잭션이 이미
            // 바꾼 DB 상태를 못 보고 통과해 버려 사용자의 정상 체크아웃을 조용히 덮어쓰게 된다
            // (실제로 재현해 확인함: 예외 없이 COMPLETED → AUTO_CLOSED 로 덮어써짐).
            int updated = sessionRepository.autoCloseIfActive(session.getId(), closeAt);
            if (updated == 0) {
                continue; // 이미 사용자가 직접 종료 — 그 흐름에서 이미 재계산됐다
            }

            // 원본 세션에서 스터디 날짜 합계를 재계산(§3.1 멱등).
            studyRecordService.recompute(userId, checkInAt, closeAt);

            // 스스로 체크아웃하지 않은 결과이므로 소유자에게 경고 1회(§3.6c). 벌크 업데이트로 적립한다
            // (clearAutomatically 로 User 엔티티도 detach 되어 setter 로는 변경이 유실된다).
            // 세션의 스터디 날짜(체크인 기준)로 귀속한다 — 배치 실행 시각(now) 기준이면 월 경계
            // (예: 7/31 체크인 → 8/1 04:00 마감)에서 잘못된 달에 적립된다.
            userRepository.addWarning(userId, StudyClock.studyMonthYm(checkInAt));
            closed++;
        }
        if (closed > 0) {
            log.info("04:00 자동 마감: {}건 종료 (boundary={})", closed, boundary);
        }
        return closed;
    }
}
