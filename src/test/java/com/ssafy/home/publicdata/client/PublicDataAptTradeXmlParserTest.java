package com.ssafy.home.publicdata.client;

import com.ssafy.home.publicdata.dto.AptTradeApiResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicDataAptTradeXmlParserTest {

    @Test
    void parseReadsTotalCountAndApartmentTradeItems() {
        String xml = """
                <response>
                  <body>
                    <totalCount>1</totalCount>
                    <items>
                      <item>
                        <sggCd>11590</sggCd>
                        <umdNm>상도동</umdNm>
                        <jibun>10</jibun>
                        <aptNm>상도아파트</aptNm>
                        <buildYear>2011</buildYear>
                        <dealYear>2024</dealYear>
                        <dealMonth>5</dealMonth>
                        <dealDay>12</dealDay>
                        <dealAmount>150,000</dealAmount>
                        <excluUseAr>84.970</excluUseAr>
                        <floor>12</floor>
                      </item>
                    </items>
                  </body>
                </response>
                """;

        AptTradeApiResponse response = new PublicDataAptTradeXmlParser().parse(xml);

        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).aptNm()).isEqualTo("상도아파트");
        assertThat(response.items().get(0).dealAmount()).isEqualTo("150,000");
    }
}
