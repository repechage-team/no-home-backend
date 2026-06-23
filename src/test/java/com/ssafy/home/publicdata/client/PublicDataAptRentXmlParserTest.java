package com.ssafy.home.publicdata.client;

import com.ssafy.home.publicdata.dto.AptRentApiResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicDataAptRentXmlParserTest {

    @Test
    void parseReadsApartmentRentItems() {
        String xml = """
                <response>
                  <header><resultCode>000</resultCode><resultMsg>OK</resultMsg></header>
                  <body>
                    <items>
                      <item>
                        <aptNm>두산</aptNm>
                        <aptSeq>11110-34</aptSeq>
                        <buildYear>1999</buildYear>
                        <contractTerm>202407~202607</contractTerm>
                        <contractType>신규</contractType>
                        <dealDay>20</dealDay>
                        <dealMonth>7</dealMonth>
                        <dealYear>2024</dealYear>
                        <deposit>50,000</deposit>
                        <excluUseAr>59.95</excluUseAr>
                        <floor>3</floor>
                        <jibun>232</jibun>
                        <monthlyRent>0</monthlyRent>
                        <preDeposit>45,000</preDeposit>
                        <preMonthlyRent>0</preMonthlyRent>
                        <roadnm>지봉로5길 7</roadnm>
                        <sggCd>11110</sggCd>
                        <umdNm>창신동</umdNm>
                        <useRRRight>사용</useRRRight>
                      </item>
                    </items>
                    <totalCount>1</totalCount>
                  </body>
                </response>
                """;

        AptRentApiResponse response = new PublicDataAptRentXmlParser().parse(xml);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).deposit()).isEqualTo("50,000");
        assertThat(response.items().get(0).monthlyRent()).isEqualTo("0");
        assertThat(response.items().get(0).contractTerm()).isEqualTo("202407~202607");
        assertThat(response.items().get(0).roadnm()).isEqualTo("지봉로5길 7");
        assertThat(response.items().get(0).aptSeq()).isEqualTo("11110-34");
    }
}
