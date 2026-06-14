package com.ssafy.home.publicdata.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PublicDataApiKeyProvider {

    private final String serviceKey;

    public PublicDataApiKeyProvider(@Value("${public-data.service-key:}") String serviceKey) {
        this.serviceKey = serviceKey;
    }

    public String getRequiredServiceKey() {
        if (!StringUtils.hasText(serviceKey)) {
            throw new IllegalStateException("public-data.service-key is not configured");
        }
        return serviceKey.trim();
    }
}
