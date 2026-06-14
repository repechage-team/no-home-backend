package com.ssafy.home.common.health.controller;

import com.ssafy.home.common.health.dto.HealthResponse;
import com.ssafy.home.common.health.service.HealthService;
import com.ssafy.home.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping
    public ApiResponse<HealthResponse> health() {
        HealthResponse health = healthService.check();
        if (health.database().connected()) {
            return ApiResponse.ok(health);
        }
        return ApiResponse.fail("application is running, but database check failed", health);
    }
}
