package com.ssafy.home.publicdata.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PublicDataApiKeyProvider {

    private final String serviceKey;
    private final String aptRentServiceKey;

    @Autowired
    public PublicDataApiKeyProvider(
            @Value("${public-data.service-key:}") String serviceKey,
            @Value("${public-data.apt-rent.service-key:}") String aptRentServiceKey
    ) {
        this.serviceKey = serviceKey;
        this.aptRentServiceKey = aptRentServiceKey;
    }

    public PublicDataApiKeyProvider(String serviceKey) {
        this(serviceKey, "");
    }

    public String getRequiredServiceKey() {
        if (!StringUtils.hasText(serviceKey)) {
            throw new IllegalStateException("public-data.service-key is not configured");
        }
        return serviceKey.trim();
    }

    public String getRequiredAptRentServiceKey() {
        if (!StringUtils.hasText(aptRentServiceKey)) {
            throw new IllegalStateException("public-data.apt-rent.service-key is not configured");
        }
        return aptRentServiceKey.trim();
    }
}
