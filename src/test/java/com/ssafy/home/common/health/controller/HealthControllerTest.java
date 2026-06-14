package com.ssafy.home.common.health.controller;

import com.ssafy.home.common.health.dto.DatabaseHealth;
import com.ssafy.home.common.health.dto.HealthResponse;
import com.ssafy.home.common.health.service.HealthService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class HealthControllerTest {

    @Test
    void healthReturnsCommonJsonResponse() throws Exception {
        HealthService healthService = mock(HealthService.class);
        when(healthService.check()).thenReturn(new HealthResponse("UP", DatabaseHealth.connected(1)));
        MockMvc mockMvc = standaloneSetup(new HealthController(healthService)).build();

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.database.connected").value(true));
    }
}
