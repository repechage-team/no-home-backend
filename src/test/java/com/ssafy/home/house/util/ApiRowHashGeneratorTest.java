package com.ssafy.home.house.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiRowHashGeneratorTest {

    @Test
    void generateReturnsDeterministicHashFromCanonicalApiRowFields() {
        ApiRowHashInput input = new ApiRowHashInput(
                " RTMSDataSvcAptTrade ",
                "11590",
                "202405",
                "흑석동",
                "10",
                "흑석한강센트레빌",
                "2024",
                "5",
                "12",
                "150,000",
                "84.970",
                "12"
        );

        String first = ApiRowHashGenerator.generate(input);
        String second = ApiRowHashGenerator.generate(input);

        assertThat(first).isEqualTo(second);
        assertThat(first).isEqualTo("7c55c6660859c6907fe3abdd88442ac6e7cd9884ec8c6af5e7f9319ed7816c33");
    }
}
