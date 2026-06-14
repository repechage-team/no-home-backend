package com.ssafy.home.common.health.service;

import com.ssafy.home.common.health.dto.DatabaseHealth;
import com.ssafy.home.common.health.dto.HealthResponse;
import com.ssafy.home.common.health.mapper.HealthCheckMapper;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    private final HealthCheckMapper healthCheckMapper;

    public HealthService(HealthCheckMapper healthCheckMapper) {
        this.healthCheckMapper = healthCheckMapper;
    }

    public HealthResponse check() {
        try {
            Integer probe = healthCheckMapper.selectDatabaseProbe();
            return new HealthResponse("UP", DatabaseHealth.connected(probe));
        } catch (RuntimeException exception) {
            return new HealthResponse("DEGRADED", DatabaseHealth.disconnected(exception.getClass().getSimpleName()));
        }
    }
}
