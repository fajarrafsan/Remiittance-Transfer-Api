package com.finshot.remittance.dto;

public record PartnerCallbackRequest(String partnerRef, String eventId, String status) {
}
