package com.ssafy.home.publicdata.service;

import com.ssafy.home.publicdata.dto.AptTradeApiItem;
import com.ssafy.home.publicdata.mapper.HouseDealInsertCommand;
import com.ssafy.home.publicdata.mapper.HouseUpsertCommand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AptTradeImportCommandFactoryTest {

    @Test
    void convertsApiRowToHouseAndDealCommands() {
        AptTradeApiItem item = new AptTradeApiItem(
                "11590", "상도동", "10", "상도아파트", "2011",
                "2024", "5", "12", "150,000", "84.970", "12",
                null, null, null, null, null, null, null, null, null
        );
        AptTradeImportCommandFactory factory = new AptTradeImportCommandFactory();

        HouseUpsertCommand houseCommand = factory.toHouseCommand("11590", 9L, item);
        HouseDealInsertCommand dealCommand = factory.toDealCommand("11590", "202405", 3L, item);

        assertThat(houseCommand.regionId()).isEqualTo(9L);
        assertThat(houseCommand.aptNm()).isEqualTo("상도아파트");
        assertThat(houseCommand.buildYear()).isEqualTo(2011);
        assertThat(dealCommand.houseId()).isEqualTo(3L);
        assertThat(dealCommand.dealAmountManwon()).isEqualTo(150000);
        assertThat(dealCommand.apiRowHash()).hasSize(64);
        assertThat(dealCommand.rawResponse()).contains("\"aptNm\":\"상도아파트\"");
    }
}
