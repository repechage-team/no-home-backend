package com.ssafy.home.publicdata.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PublicDataAptTradeClient {

    private static final String ENDPOINT = "https://apis.data.go.kr/1613000/RTMSDataSvcAptTrade/getRTMSDataSvcAptTrade";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 15000;

    private final PublicDataApiKeyProvider apiKeyProvider;
    private final RestClient restClient;

    @Autowired
    public PublicDataAptTradeClient(PublicDataApiKeyProvider apiKeyProvider) {
        this(apiKeyProvider, createRestClient());
    }

    PublicDataAptTradeClient(PublicDataApiKeyProvider apiKeyProvider, RestClient restClient) {
        this.apiKeyProvider = apiKeyProvider;
        this.restClient = restClient;
    }

    public String fetchXml(String lawdCd, String dealYmd) {
        return fetchXml(lawdCd, dealYmd, 1, 100);
    }

    public String fetchXml(String lawdCd, String dealYmd, int pageNo, int numOfRows) {
        String serviceKey = apiKeyProvider.getRequiredServiceKey();
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("apis.data.go.kr")
                        .path("/1613000/RTMSDataSvcAptTrade/getRTMSDataSvcAptTrade")
                        .queryParam("serviceKey", serviceKey)
                        .queryParam("LAWD_CD", lawdCd)
                        .queryParam("DEAL_YMD", dealYmd)
                        .queryParam("pageNo", pageNo)
                        .queryParam("numOfRows", numOfRows)
                        .build(true))
                .retrieve()
                .body(String.class);
    }

    private static RestClient createRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MS);
        return RestClient.builder()
                .requestFactory(requestFactory)
                // 명시 User-Agent. data.go.kr 앞단 WAF가 일부 봇 UA(curl/wget)를 차단함을 확인했고,
                // 기본 UA에 의존하지 않도록 서비스 식별 UA를 둔다.
                .defaultHeader("User-Agent", "no-home/1.0")
                .build();
    }
}
