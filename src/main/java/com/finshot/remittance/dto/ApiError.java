package com.finshot.remittance.dto;

public record ApiError(
        String error,
        String message
) {
}
