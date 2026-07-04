package com.ietscroll.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class KeepAliveService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.self-url}")
    private String selfUrl;

    @Scheduled(fixedRate = 600000)
    public void pingSelf() {
        try {
            restTemplate.getForObject(selfUrl + "/actuator/health", String.class);
        } catch (Exception e) {
            System.out.println("Self-ping failed: " + e.getMessage());
        }
    }
}