package com.studycafe.ranking.ranking;

import com.studycafe.ranking.domain.School;
import com.studycafe.ranking.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NameMaskerTest {

    @Test
    @DisplayName("maskName: 2/3/4글자 + 1글자 이하 방어")
    void maskName() {
        assertEquals("김O", NameMasker.maskName("김민"));
        assertEquals("김O현", NameMasker.maskName("김민현"));
        assertEquals("김OO현", NameMasker.maskName("김민서현"));
        assertEquals("김", NameMasker.maskName("김"));
        assertEquals("", NameMasker.maskName(""));
    }

    @Test
    @DisplayName("rankingLabel: 마스킹(학교축약명)")
    void rankingLabel_withSchool() {
        User u = new User("id", "hash", "김민현", 1, new School("ㅁㅁ중학교", "ㅁㅁ중"));
        assertEquals("김O현(ㅁㅁ중)", NameMasker.rankingLabel(u));
    }

    @Test
    @DisplayName("rankingLabel: 무소속")
    void rankingLabel_noSchool() {
        User u = new User("id", "hash", "김민현", 1, null);
        assertEquals("김O현(무소속)", NameMasker.rankingLabel(u));
    }

    @Test
    @DisplayName("rankingLabel: 동명이인 접미 알파벳(seq 2→B, 3→C, 첫 사람은 미표기)")
    void rankingLabel_nameSeqSuffix() {
        School s = new School("ㅁㅁ중학교", "ㅁㅁ중");
        assertEquals("김O현(ㅁㅁ중)", NameMasker.rankingLabel(new User("id", "hash", "김민현", 1, s)));
        assertEquals("김O현(ㅁㅁ중)B", NameMasker.rankingLabel(new User("id", "hash", "김민현", 2, s)));
        assertEquals("김O현(ㅁㅁ중)C", NameMasker.rankingLabel(new User("id", "hash", "김민현", 3, s)));
        // 26명 초과는 엑셀 열처럼 확장(27→AA).
        assertEquals("김O현(ㅁㅁ중)AA", NameMasker.rankingLabel(new User("id", "hash", "김민현", 27, s)));
    }

    @Test
    @DisplayName("rankingLabel: short_name 없으면 name 폴백")
    void rankingLabel_shortNameFallback() {
        User u = new User("id", "hash", "김민현", 1, new School("서울대학교", null));
        assertEquals("김O현(서울대학교)", NameMasker.rankingLabel(u));
    }
}
