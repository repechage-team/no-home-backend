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

    @Test
    void parseReadsStandardApiResultCodeAndMessage() {
        String xml = """
                <response>
                  <header>
                    <resultCode>30</resultCode>
                    <resultMsg>SERVICE KEY IS NOT REGISTERED ERROR.</resultMsg>
                  </header>
                  <body>
                    <totalCount>0</totalCount>
                    <items></items>
                  </body>
                </response>
                """;

        AptTradeApiResponse response = new PublicDataAptTradeXmlParser().parse(xml);

        assertThat(response.resultCode()).isEqualTo("30");
        assertThat(response.resultMsg()).contains("SERVICE KEY");
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.totalCount()).isZero();
        assertThat(response.items()).isEmpty();
    }

    @Test
    void parseReadsGatewayErrorEnvelope() {
        String xml = """
                <OpenAPI_ServiceResponse>
                  <cmmMsgHeader>
                    <returnReasonCode>22</returnReasonCode>
                    <returnAuthMsg>LIMITED NUMBER OF SERVICE REQUESTS EXCEEDS ERROR.</returnAuthMsg>
                  </cmmMsgHeader>
                </OpenAPI_ServiceResponse>
                """;

        AptTradeApiResponse response = new PublicDataAptTradeXmlParser().parse(xml);

        assertThat(response.resultCode()).isEqualTo("22");
        assertThat(response.resultMsg()).contains("LIMITED NUMBER");
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.totalCount()).isZero();
        assertThat(response.items()).isEmpty();
    }
}
