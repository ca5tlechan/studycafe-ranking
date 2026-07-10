package com.studycafe.ranking.studytime;

import java.time.LocalDate;

/** 세션을 스터디 날짜(04:00 기준)로 쪼갠 한 조각. */
public record StudySegment(LocalDate studyDate, long seconds) {}
