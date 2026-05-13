package com.hmdp.ratelimit.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RateLimitDecision {
    private boolean allowed;
    private boolean blacklistHit;
    private String message;
}
