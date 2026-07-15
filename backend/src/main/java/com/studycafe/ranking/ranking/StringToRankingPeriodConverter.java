package com.studycafe.ranking.ranking;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * "this_week" 같은 쿼리 파라미터 → RankingPeriod. 잘못된 값이면 valueOf 가 IllegalArgumentException →
 * Spring 이 MethodArgumentTypeMismatchException 으로 감싸 400(ApiError) 응답.
 */
@Component
class StringToRankingPeriodConverter implements Converter<String, RankingPeriod> {

    @Override
    public RankingPeriod convert(String source) {
        return RankingPeriod.valueOf(source.trim().toUpperCase(Locale.ROOT));
    }
}
