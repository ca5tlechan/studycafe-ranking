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

    /** `마스킹이름(학교축약명)` + 같은 학교 동명이인 접미 숫자(name_seq>1). 무소속은 `(무소속)`. */
    public static String rankingLabel(User user) {
        String masked = maskName(user.getDisplayName());
        School school = user.getSchool();
        String schoolPart = (school != null)
                ? (school.getShortName() != null ? school.getShortName() : school.getName())
                : "무소속";
        String suffix = user.getNameSeq() > 1 ? String.valueOf(user.getNameSeq()) : "";
        return masked + "(" + schoolPart + ")" + suffix;
    }
}
