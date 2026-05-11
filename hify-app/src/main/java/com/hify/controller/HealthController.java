package com.hify.controller;

import com.hify.common.web.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health check controller
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("Hify is running");
    }
}
