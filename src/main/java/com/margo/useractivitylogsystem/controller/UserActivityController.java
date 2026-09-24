package com.margo.useractivitylogsystem.controller;

import com.margo.useractivitylogsystem.model.ActivityRequest;
import com.margo.useractivitylogsystem.model.ActivityResponse;
import com.margo.useractivitylogsystem.service.UserActivityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Validated
@RestController
@RequestMapping("/user_activities")
@RequiredArgsConstructor
public class UserActivityController {

    private final UserActivityService userActivityService;

    @PostMapping("/{userId}")
    public ResponseEntity<ActivityResponse> saveActivity(
            @PathVariable UUID userId,
            @Valid @RequestBody ActivityRequest request) {
        log.debug("Request to send the activity {} for user {}", request, userId);
        ActivityResponse response = userActivityService.saveActivity(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{userId}")
    public List<ActivityResponse> getActivities(
            @PathVariable UUID userId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) @Min(1) @Max(100) Integer limit) {
        log.debug("Request to get activities (details from={}, to={}, limit={}) for user {}",
                from, to, limit, userId);
        return userActivityService.getUserActivities(userId, limit, from, to);
    }
}
