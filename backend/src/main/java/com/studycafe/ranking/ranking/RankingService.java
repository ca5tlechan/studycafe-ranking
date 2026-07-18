package com.studycafe.ranking.ranking;

import com.studycafe.ranking.common.exception.UserNotFoundException;
import com.studycafe.ranking.domain.User;
import com.studycafe.ranking.ranking.dto.IndividualRankingResponse;
import com.studycafe.ranking.ranking.dto.RankEntry;
import com.studycafe.ranking.ranking.dto.SchoolEntry;
import com.studycafe.ranking.ranking.dto.SchoolMineResponse;
import com.studycafe.ranking.ranking.dto.SchoolRankingResponse;
import com.studycafe.ranking.repository.CheckInSessionRepository;
import com.studycafe.ranking.repository.DailyStudyRecordRepository;
import com.studycafe.ranking.repository.UserRepository;
import com.studycafe.ranking.studytime.StudyClock;
import com.studycafe.ranking.studytime.StudyTimeAggregator;
import com.studycafe.ranking.studytime.StudyTimePolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 랭킹(§5.2/§5.3). 여기서 **랭킹 캡(하루 16h/주 84h, §3.6e)** 을 적용한다(마이페이지=실제시간과 대비).
 * 파일럿 규모(수십 명)라 per-user 집계를 가져와 Java에서 캡·정렬·마스킹·내순위를 계산한다.
 */
@Service
@Transactional(readOnly = true)
public class RankingService {

    private static final int MIN_SCHOOL_MEMBERS = 5; // §3.4
    private static final int LIST_MAX = 10;          // 4~10위
    private static final int PODIUM = 3;

    private final DailyStudyRecordRepository recordRepository;
    private final CheckInSessionRepository sessionRepository;
    private final UserRepository userRepository;

    public RankingService(DailyStudyRecordRepository recordRepository,
                          CheckInSessionRepository sessionRepository,
                          UserRepository userRepository) {
        this.recordRepository = recordRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
    }

    public IndividualRankingResponse individual(Long meUserId, RankingPeriod period) {
        LocalDate today = StudyClock.studyDateOf(Instant.now());
        return buildIndividual(period, rankedIndividuals(period, today, null), meUserId);
    }

    public SchoolMineResponse schoolMine(Long meUserId, RankingPeriod period) {
        User me = userRepository.findByIdWithSchool(meUserId)
                .orElseThrow(() -> new UserNotFoundException(meUserId));
        if (me.getSchool() == null) {
            return SchoolMineResponse.unavailable(); // 무소속 → 안내(§5.3)
        }
        LocalDate today = StudyClock.studyDateOf(Instant.now());
        List<RankRow> ranked = rankedIndividuals(period, today, me.getSchool().getId());
        return SchoolMineResponse.of(me.getSchool().getName(), buildIndividual(period, ranked, meUserId));
    }

    public SchoolRankingResponse school(RankingPeriod period) {
        LocalDate today = StudyClock.studyDateOf(Instant.now());
        Map<Long, Long> capped = cappedTotalsByUser(period, today);
        Map<Long, User> users = usersById(capped.keySet());

        Map<Long, List<Long>> bySchool = new HashMap<>();
        Map<Long, String> schoolNames = new HashMap<>();
        capped.forEach((userId, total) -> {
            User u = users.get(userId);
            if (u == null || u.getSchool() == null) {
                return; // 무소속 제외(§3.7)
            }
            Long schoolId = u.getSchool().getId();
            bySchool.computeIfAbsent(schoolId, k -> new ArrayList<>()).add(total);
            schoolNames.putIfAbsent(schoolId, u.getSchool().getName());
        });

        List<SchoolAgg> aggs = new ArrayList<>();
        bySchool.forEach((schoolId, totals) -> {
            int count = totals.size(); // 활동 인원 = 그 기간 기록 있는 학생 수
            if (count < MIN_SCHOOL_MEMBERS) {
                return; // 최소 인원 미달 제외(§3.4)
            }
            long sum = totals.stream().mapToLong(Long::longValue).sum();
            aggs.add(new SchoolAgg(schoolNames.get(schoolId), count, sum / count));
        });
        // 동점: 평균 desc → 활동 인원 desc → 학교명 asc (§3.5)
        aggs.sort(Comparator.comparingLong(SchoolAgg::avgSeconds).reversed()
                .thenComparing(Comparator.comparingInt(SchoolAgg::memberCount).reversed())
                .thenComparing(SchoolAgg::schoolName));

        List<SchoolEntry> all = new ArrayList<>();
        for (int i = 0; i < aggs.size(); i++) {
            SchoolAgg a = aggs.get(i);
            all.add(new SchoolEntry(i + 1, a.schoolName(), a.memberCount(), a.avgSeconds()));
        }
        return new SchoolRankingResponse(periodKey(period),
                all.stream().limit(PODIUM).toList(),
                all.stream().skip(PODIUM).limit(LIST_MAX - PODIUM).toList());
    }

    // ---------- helpers ----------

    private List<RankRow> rankedIndividuals(RankingPeriod period, LocalDate today, Long schoolFilter) {
        Map<Long, Long> capped = cappedTotalsByUser(period, today);
        if (capped.isEmpty()) {
            return List.of();
        }
        Map<Long, User> users = usersById(capped.keySet());
        if (schoolFilter != null) {
            capped.keySet().removeIf(uid -> {
                User u = users.get(uid);
                return u == null || u.getSchool() == null || !u.getSchool().getId().equals(schoolFilter);
            });
            if (capped.isEmpty()) {
                return List.of();
            }
        }
        Map<Long, Instant> lastCheckout = lastCheckoutByUser(period, today);
        List<RankRow> rows = new ArrayList<>();
        capped.forEach((uid, total) ->
                rows.add(new RankRow(uid, total, lastCheckout.get(uid), NameMasker.rankingLabel(users.get(uid)))));
        // 동점: 합계 desc → 기간 내 마지막 체크아웃 이른 순(§3.5)
        rows.sort(Comparator.comparingLong(RankRow::total).reversed()
                .thenComparing(r -> r.lastCheckout() == null ? Instant.MAX : r.lastCheckout()));
        return rows;
    }

    private IndividualRankingResponse buildIndividual(RankingPeriod period, List<RankRow> rows, Long meUserId) {
        List<RankEntry> all = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            RankRow r = rows.get(i);
            all.add(new RankEntry(i + 1, r.label(), r.total(), r.userId().equals(meUserId)));
        }
        RankEntry myRank = all.stream().filter(RankEntry::isMe).findFirst().orElse(null);
        return new IndividualRankingResponse(periodKey(period),
                all.stream().limit(PODIUM).toList(),
                all.stream().skip(PODIUM).limit(LIST_MAX - PODIUM).toList(),
                myRank);
    }

    /** period 범위의 user별 캡 적용 합계(초). 하루 16h 캡 + (주간이면) 주 84h 캡 — StudyTimeAggregator 재사용. */
    private Map<Long, Long> cappedTotalsByUser(RankingPeriod period, LocalDate today) {
        Map<Long, List<Long>> dailyByUser = new HashMap<>();
        for (Object[] row : recordRepository.findUserSecondsInPeriod(period.start(today), period.end(today))) {
            dailyByUser.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add((Long) row[1]);
        }
        // 페널티 유저는 모든 랭킹(개인·학교 평균 분모)에서 제외(§3.6c). 여기 한 곳에서 걸러 양쪽에 반영한다.
        // 현재 스터디-월 기준이라, 월이 바뀌면(경고 리셋) 자동으로 다시 포함된다.
        dailyByUser.keySet().removeAll(
                userRepository.findPenalizedUserIds(StudyClock.monthYm(today), StudyTimePolicy.PENALTY_THRESHOLD));

        boolean weekly = period.isWeekly();
        Map<Long, Long> capped = new HashMap<>();
        dailyByUser.forEach((userId, daily) ->
                capped.put(userId, StudyTimeAggregator.rankingTotalSeconds(daily, weekly)));
        return capped;
    }

    private Map<Long, Instant> lastCheckoutByUser(RankingPeriod period, LocalDate today) {
        Instant start = StudyClock.startOf(period.start(today));
        Instant end = StudyClock.endOf(period.end(today));
        Map<Long, Instant> map = new HashMap<>();
        for (Object[] row : sessionRepository.lastCheckoutByUser(start, end)) {
            map.put((Long) row[0], toInstant(row[1]));
        }
        return map;
    }

    private Map<Long, User> usersById(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllByIdInWithSchool(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private static String periodKey(RankingPeriod period) {
        return period.name().toLowerCase(Locale.ROOT);
    }

    /** 집계 max(checkOutAt) 의 반환 타입(Instant/Timestamp/OffsetDateTime)을 Instant 로 정규화. */
    private static Instant toInstant(Object value) {
        if (value instanceof Instant i) {
            return i;
        }
        if (value instanceof Timestamp t) {
            return t.toInstant();
        }
        if (value instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        return null;
    }

    private record RankRow(Long userId, long total, Instant lastCheckout, String label) {
    }

    private record SchoolAgg(String schoolName, int memberCount, long avgSeconds) {
    }
}
