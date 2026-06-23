package com.ssafy.home.publicdata.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PublicDataAptRentClient {

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 15000;

    private final PublicDataApiKeyProvider apiKeyProvider;
    private final RestClient restClient;

    @Autowired
    public PublicDataAptRentClient(PublicDataApiKeyProvider apiKeyProvider) {
        this(apiKeyProvider, createRestClient());
    }

    PublicDataAptRentClient(PublicDataApiKeyProvider apiKeyProvider, RestClient restClient) {
        this.apiKeyProvider = apiKeyProvider;
        this.restClient = restClient;
    }

    public String fetchXml(String lawdCd, String dealYmd) {
        return fetchXml(lawdCd, dealYmd, 1, 100);
    }

    public String fetchXml(String lawdCd, String dealYmd, int pageNo, int numOfRows) {
        String serviceKey = apiKeyProvider.getRequiredAptRentServiceKey();
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("apis.data.go.kr")
                        .path("/1613000/RTMSDataSvcAptRent/getRTMSDataSvcAptRent")
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
                .build();
    }
}
