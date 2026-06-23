package com.ssafy.home.interest.mapper;

import com.ssafy.home.interest.dto.InterestRegion;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:interest_region_mapper;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@MapperScan("com.ssafy.home.interest.mapper")
@Sql(scripts = "classpath:schema.sql")
class InterestRegionMapperTest {

    @Autowired
    private InterestRegionMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void insertSelectAndDeleteInterestRegion() {
        Long memberId = insertMember();
        mapper.insertRegion("11590", "1159010500", "서울특별시", "동작구", "흑석동");
        Long regionId = mapper.selectRegionId("11590", "흑석동").orElseThrow();

        int inserted = mapper.insertInterestRegion(memberId, regionId);
        int duplicated = mapper.insertInterestRegion(memberId, regionId);
        List<InterestRegion> regions = mapper.selectByMemberId(memberId);
        int deleted = mapper.deleteInterestRegion(memberId, regions.get(0).interestRegionId());

        assertThat(inserted).isEqualTo(1);
        assertThat(duplicated).isZero();
        assertThat(regions).extracting(InterestRegion::umdNm).containsExactly("흑석동");
        assertThat(deleted).isEqualTo(1);
        assertThat(mapper.selectByMemberId(memberId)).isEmpty();
    }

    private Long insertMember() {
        jdbcTemplate.update("""
                INSERT INTO members (email, password_hash, name, phone)
                VALUES ('interest@example.com', 'hash', 'Interest User', '010')
                """);
        return jdbcTemplate.queryForObject("SELECT member_id FROM members WHERE email = 'interest@example.com'", Long.class);
    }
}
