package com.ssafy.home.publicdata.service;

import com.ssafy.home.publicdata.dto.AptRentApiItem;
import com.ssafy.home.publicdata.mapper.HouseDealInsertCommand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AptRentImportCommandFactoryTest {

    @Test
    void monthlyRentZeroBecomesJeonseAndPositiveValueBecomesMonthly() {
        AptRentImportCommandFactory factory = new AptRentImportCommandFactory();

        HouseDealInsertCommand jeonse = factory.toDealCommand("11110", "202407", 1L, item("0"));
        HouseDealInsertCommand monthly = factory.toDealCommand("11110", "202407", 1L, item("120"));

        assertThat(jeonse.dealType()).isEqualTo("jeonse");
        assertThat(jeonse.rentType()).isEqualTo("jeonse");
        assertThat(jeonse.depositManwon()).isEqualTo(50000);
        assertThat(jeonse.monthlyRentManwon()).isZero();
        assertThat(monthly.dealType()).isEqualTo("monthly");
        assertThat(monthly.rentType()).isEqualTo("monthly");
        assertThat(monthly.monthlyRentManwon()).isEqualTo(120);
    }

    private static AptRentApiItem item(String monthlyRent) {
        return new AptRentApiItem(
                "11110", "창신동", "232", "두산", "11110-34", "1999",
                "2024", "7", "20", "50,000", monthlyRent, "59.95", "3",
                "202407~202607", "신규", "사용", "45,000", "0",
                "지봉로5길 7", "11110", "4100390", "1", "0", "00007", "00000"
        );
    }
}
