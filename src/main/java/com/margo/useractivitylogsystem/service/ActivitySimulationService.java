package com.margo.useractivitylogsystem.service;

import com.margo.useractivitylogsystem.model.ActivityRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivitySimulationService {

    private final UserActivityService userActivityService;
    private final Random random = new Random();

    private static final List<UUID> MOCK_USER_IDS = List.of(
            UUID.fromString("3f2b8c1e-6a4d-4e7b-9c15-2d8a7f0b1e34"),
            UUID.fromString("a1d5e7f2-83c9-4b06-8e4a-5c9b2d1f7a60"),
            UUID.fromString("c47e90b3-15fa-4d28-b3e6-98a0d2c4f511"),
            UUID.fromString("c47e80b3-15fa-4d28-b3e6-98a0d2c4f511"),
            UUID.fromString("c47e93b3-15fa-4d28-b3e6-98a0d2c4f511")
    );

    private static final List<String> MOCK_ACTIVITY_TYPES = List.of(
            "PAGE_VIEW",
            "USER_LOGIN",
            "UPDATE_PROFILE",
            "PURCHASE_COMPLETE",
            "EXPORT_REPORT"
    );

    private static final List<String> MOCK_DETAILS = List.of(
            "Navigated to the /dashboard page",
            "Successful login",
            "Updated user email preferences",
            "Completed purchase",
            "Downloaded PDF report"
    );

    @Scheduled(fixedRate = 5000, initialDelay = 2000)
    public void simulateActivityLog() {
        UUID userId = getRandomElement(MOCK_USER_IDS);
        String activityType = getRandomElement(MOCK_ACTIVITY_TYPES);
        String details = getRandomElement(MOCK_DETAILS);

        Integer customTtl = random.nextBoolean() ? 3600 : null;

        ActivityRequest request = new ActivityRequest(activityType, details, customTtl);

        try {
            userActivityService.saveActivity(userId, request);
            log.debug("Simulated activity logged successfully for user with id: {}. Type: {}", userId, activityType);
        } catch (Exception ex) {
            log.error("Simulation failed to persist activity for user with id:{}. Error: {}", userId, ex.getMessage());
        }
    }

    private <T> T getRandomElement(List<T> list) {
        return list.get(random.nextInt(list.size()));
    }
}
