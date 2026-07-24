package com.studycafe.ranking.user;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 동명이인 접미 시퀀스 배정(§3.3). 같은 (이름, 학교) 그룹에서 <b>아직 안 쓰인 가장 작은 양의 seq</b> 를 준다.
 * <p>{@code 개수+1} 이 아니라 "가장 작은 빈자리"를 쓰는 이유: 삭제로 중간이 비면(예: 1·3 만 남음) 개수+1 은
 * 이미 존재하는 3 과 충돌해 두 사람이 같은 접미(C)를 갖는다. 빈자리 재사용은 그 자리(2)를 새 가입자가 채워
 * 충돌을 막고 번호를 촘촘히 유지한다. 기존 사용자 seq 는 건드리지 않는다(재정렬 없음).
 * <p>파일럿 규모 기준 베스트 에포트 — 완전 동시 가입 레이스(같은 이름·학교, 같은 순간)까지는 막지 않는다.
 */
public final class NameSeqAllocator {

    private NameSeqAllocator() {
    }

    public static int smallestUnused(List<Integer> usedSeqs) {
        Set<Integer> used = new HashSet<>(usedSeqs);
        int seq = 1;
        while (used.contains(seq)) {
            seq++;
        }
        return seq;
    }
}
