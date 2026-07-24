package com.studycafe.ranking.user;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 동명이인 접미 시퀀스 배정(§3.3). 같은 (이름, 학교) 그룹에서 <b>아직 안 쓰인 가장 작은 양의 seq</b> 를 준다.
 * <p>{@code 개수+1} 이 아니라 "가장 작은 빈자리"를 쓰는 이유: 삭제로 중간이 비면(예: 1·3 만 남음) 개수+1 은
 * 이미 존재하는 3 과 충돌해 두 사람이 같은 접미(C)를 갖는다. 빈자리 재사용은 그 자리(2)를 새 가입자가 채워
 * 충돌을 막고 번호를 촘촘히 유지한다. 기존 사용자 seq 는 건드리지 않는다(재정렬 없음).
 * <p><b>동시성(의도적 결정)</b>: "조회→계산→저장"이 원자적이지 않아, 같은 이름·학교로 <i>같은 순간</i>
 * 두 명이 가입/전학하면 같은 seq 를 받을 수 있다. 다만 name_seq 는 <b>랭킹 표시용 접미(disambiguation)일
 * 뿐 식별키가 아니라</b>(유니크 제약 없음, 조회 키로 안 씀) — 겹쳐도 두 사람이 같은 접미로 보이는 표시상
 * 문제에 그치고 데이터 손상·기능 오류는 없다. 단일 인스턴스 파일럿에서 이 확률·영향은 무시할 수준이라
 * 원자적 예약(그룹 잠금/유니크 제약+재시도)을 <b>의도적으로 도입하지 않는다</b>. 무소속(school_id NULL)이
 * 섞여 단순 유니크 제약으로는 못 막는 점도 이유. 규모가 커지면 그때 도입한다.
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
