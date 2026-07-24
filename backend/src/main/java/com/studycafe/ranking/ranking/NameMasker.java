package com.studycafe.ranking.ranking;

import com.studycafe.ranking.domain.School;
import com.studycafe.ranking.domain.User;

/** 랭킹 표시용 이름 마스킹(§3.3). */
public final class NameMasker {

    private NameMasker() {
    }

    /** 첫/끝 글자만 노출, 사이는 O. 2글자=첫+O, 1글자 이하=원문 그대로. */
    public static String maskName(String name) {
        if (name == null) {
            return "";
        }
        int n = name.length();
        if (n <= 1) {
            return name;
        }
        if (n == 2) {
            return name.charAt(0) + "O";
        }
        return name.charAt(0) + "O".repeat(n - 2) + name.charAt(n - 1);
    }

    /** `마스킹이름(학교축약명)` + 같은 학교 동명이인 접미 알파벳(name_seq>1). 무소속은 `(무소속)`. */
    public static String rankingLabel(User user) {
        String masked = maskName(user.getDisplayName());
        School school = user.getSchool();
        String schoolPart;
        if (school == null) {
            schoolPart = "무소속";
        } else {
            String shortName = school.getShortName();
            // short_name 이 null 또는 공백이면 name 으로 폴백(빈 괄호 `()` 방지).
            schoolPart = (shortName != null && !shortName.isBlank()) ? shortName : school.getName();
        }
        // 첫 사람(seq 1)은 미표기, 둘째부터 접미 알파벳(2→B, 3→C…). 기존 로직 그대로, 숫자만 알파벳으로.
        String suffix = user.getNameSeq() > 1 ? alphaSuffix(user.getNameSeq()) : "";
        return masked + "(" + schoolPart + ")" + suffix;
    }

    /** name_seq 를 알파벳 접미로. 엑셀 열처럼 A,B,…,Z,AA,… 로 확장(seq 1→A, 2→B, 27→AA). */
    private static String alphaSuffix(int seq) {
        StringBuilder sb = new StringBuilder();
        for (int n = seq; n > 0; n = (n - 1) / 26) {
            sb.insert(0, (char) ('A' + (n - 1) % 26));
        }
        return sb.toString();
    }
}
