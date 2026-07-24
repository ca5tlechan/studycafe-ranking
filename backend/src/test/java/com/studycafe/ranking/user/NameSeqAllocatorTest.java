package com.studycafe.ranking.user;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class NameSeqAllocatorTest {

    @Test
    void smallestUnused_fillsGapsFromDeletions() {
        assertEquals(1, NameSeqAllocator.smallestUnused(List.of()));        // 첫 사람
        assertEquals(2, NameSeqAllocator.smallestUnused(List.of(1)));       // 둘째
        assertEquals(4, NameSeqAllocator.smallestUnused(List.of(1, 2, 3))); // 연속 → 다음
        assertEquals(2, NameSeqAllocator.smallestUnused(List.of(1, 3)));    // 가운데 빈자리(2) 재사용
        assertEquals(1, NameSeqAllocator.smallestUnused(List.of(2, 3)));    // 맨 앞(1) 비면 1
        assertEquals(2, NameSeqAllocator.smallestUnused(List.of(3, 1)));    // 순서 무관
    }
}
