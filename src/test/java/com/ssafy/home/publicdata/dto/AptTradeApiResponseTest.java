package com.ssafy.home.publicdata.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AptTradeApiResponseTest {

    // 정상: 공통표준 "00"(NORMAL_CODE) / RTMS 자체 "000"(NORMAL SERVICE) + null/blank.
    @Test
    void isSuccessForNormalResultCodes() {
        assertThat(response("00").isSuccess()).isTrue();
        assertThat(response("000").isSuccess()).isTrue();
        assertThat(response(" 000 ").isSuccess()).isTrue(); // trim
        assertThat(response(null).isSuccess()).isTrue();
        assertThat(response("").isSuccess()).isTrue();
    }

    // 오류 코드는 모두 비-0(30/22/...). "0" 단독·"099"는 명세상 정상이 아니다(추정 일반화 배제).
    @Test
    void isFailureForErrorResultCodes() {
        assertThat(response("30").isSuccess()).isFalse();
        assertThat(response("22").isSuccess()).isFalse();
        assertThat(response("0").isSuccess()).isFalse();
        assertThat(response("099").isSuccess()).isFalse();
    }

    private static AptTradeApiResponse response(String resultCode) {
        return new AptTradeApiResponse(resultCode, "msg", 0, List.of());
    }
}
