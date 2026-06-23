package com.ssafy.home.publicdata.dto;

import java.util.List;
import java.util.Set;

public record AptTradeApiResponse(String resultCode, String resultMsg, int totalCount, List<AptTradeApiItem> items) {

    /**
     * 정상 결과코드. 공통표준 "00"(NORMAL_CODE)과 RTMS 자체 "000"(NORMAL SERVICE) 두 가지가 정상이다.
     * 근거: data.go.kr 공통 오류코드표(정상=00, 오류는 01/04/12/20/22/30/31/32/99 등 비-0) +
     * getRTMSDataSvcAptTrade 응답 실측(resultCode=000, resultMsg=OK).
     */
    private static final Set<String> NORMAL_RESULT_CODES = Set.of("00", "000", "03");

    public boolean isSuccess() {
        if (resultCode == null) {
            return true;
        }
        String code = resultCode.trim();
        return code.isEmpty() || NORMAL_RESULT_CODES.contains(code);
    }
}
