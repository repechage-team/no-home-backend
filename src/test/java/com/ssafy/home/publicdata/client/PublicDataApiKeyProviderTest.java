package com.ssafy.home.publicdata.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicDataApiKeyProviderTest {

    @Test
    void getRequiredServiceKeyFailsWhenKeyIsBlank() {
        PublicDataApiKeyProvider provider = new PublicDataApiKeyProvider(" ");

        assertThatThrownBy(provider::getRequiredServiceKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("public-data.service-key");
    }

    @Test
    void getRequiredServiceKeyTrimsConfiguredKey() {
        PublicDataApiKeyProvider provider = new PublicDataApiKeyProvider(" key ");

        assertThat(provider.getRequiredServiceKey()).isEqualTo("key");
    }
}
