package com.finshot.remittance.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class PartnerEventRepository {
    private final JdbcTemplate jdbc;


    public PartnerEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> findPartnerRef(String eventId) {
        return jdbc.queryForList("SELECT partner_ref FROM partner_events WHERE event_id = ?", String.class, eventId).stream().findFirst();
    }

    public void insert(String eventId, String partnerRef, String status, OffsetDateTime receivedAt) {
        jdbc.update("INSERT INTO partner_events (event_id, partner_ref, status, received_at) VALUES (?, ?, ?, ?)", eventId, partnerRef, status, receivedAt);
    }
}
