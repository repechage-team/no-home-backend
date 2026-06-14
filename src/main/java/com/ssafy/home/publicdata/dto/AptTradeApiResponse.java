package com.ssafy.home.publicdata.dto;

import java.util.List;

public record AptTradeApiResponse(int totalCount, List<AptTradeApiItem> items) {
}
