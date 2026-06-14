package com.ssafy.home.common.health.mapper;

import org.apache.ibatis.annotations.Select;

public interface HealthCheckMapper {

    @Select("SELECT 1")
    Integer selectDatabaseProbe();
}
