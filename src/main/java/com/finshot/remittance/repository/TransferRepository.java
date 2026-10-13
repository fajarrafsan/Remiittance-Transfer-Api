package com.finshot.remittance.repository;

import com.finshot.remittance.model.Transfer;
import com.finshot.remittance.model.TransferStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class TransferRepository {
    private static final String SELECT_TRANSFER = """
            SELECT transfer_id, customer_id, idempotency_key, request_hash,
                send_currency, send_amount, fee, total_debit, fx_rate,
                receive_currency, receive_amount, recipient_name, status,
                partner_ref, failure_reason, created_at, updated_at
            FROM transfers
            """;
    private final JdbcTemplate jdbc;

    public TransferRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean customerExists(String customerId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM customers WHERE customer_id = ?", Integer.class, customerId
        );
        return count != null && count > 0;
    }

    public void insert(Transfer t) {
        jdbc.update("""
                        INSERT INTO transfers (transfer_id, customer_id, idempotency_key, request_hash,
                        send_currency, send_amount, fee, total_debit, fx_rate,
                        receive_currency, receive_amount,recipient_name, status,
                        created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """, t.transferId(), t.customerId(), t.idempotencyKey(), t.requestHash(),
                t.sendCurrency(), t.sendAmount(), t.fee(), t.totalDebit(), t.fxRate(), t.receiveCurrency(), t.receiveAmount(), t.recipientName(), t.status().name(), t.createdAt(), t.updatedAt());
    }

    public Optional<Transfer> findById(String transferId) {
        return jdbc.query(SELECT_TRANSFER + "WHERE transfer_id = ?", this::mapRow, transferId).stream().findFirst();
    }

    public Optional<Transfer> findByIdempotencyKey(String idempotencyKey) {
        return jdbc.query(SELECT_TRANSFER + "WHERE idempotency_key = ?", this::mapRow, idempotencyKey).stream().findFirst();
    }

    public void lockCustomer(String customerId) {
        jdbc.queryForObject("SELECT customer_id FROM customers WHERE customer_id = ? FOR UPDATE", String.class, customerId);
    }

    public BigDecimal sumActiveSendAmount(String customerId, OffsetDateTime from, OffsetDateTime to) {
        Long sum = jdbc.queryForObject("""
                SELECT COALESCE(SUM(send_amount), 0)
                FROM transfers
                WHERE customer_id = ?
                    AND STATUS NOT IN (?, ?)
                    AND created_at >= ? AND created_at < ? 
                """, Long.class, customerId, TransferStatus.CANCELLED.name(), TransferStatus.FAILED.name(), from, to);
        return BigDecimal.valueOf(sum == null ? 0 : sum);
    }


    public boolean updateStatus(String transferId, TransferStatus from, TransferStatus to, OffsetDateTime now) {
        int row = jdbc.update("UPDATE transfers SET status = ?, updated_at = ? WHERE transfer_id = ? AND status = ?", to.name(), now, transferId, from.name());
        return row == 1;
    }

    public boolean startSending(String transferId, String partnerRef, OffsetDateTime now) {
        int rows = jdbc.update(
                "UPDATE transfers SET status = ?, partner_ref = ?, updated_at = ? WHERE transfer_id = ? AND status = ?",
                TransferStatus.SENDING.name(), partnerRef, now, transferId, TransferStatus.REQUESTED.name());
        return rows == 1;
    }

    public boolean markFailed(String transferId, String reason, OffsetDateTime now) {
        int rows = jdbc.update(
                "UPDATE transfers SET status = 'FAILED', failure_reason = ?, updated_at = ? "
                        + "WHERE transfer_id = ? AND status = 'SENDING'",
                reason, now, transferId);
        return rows == 1;
    }

    public long nextPartnerRefNumber() {
        Long next = jdbc.queryForObject("SELECT NEXT VALUE FOR partner_ref_seq", Long.class);
        return next == null ? 0 : next;
    }

    public Optional<Transfer> findByPartnerRef(String partnerRef) {
        return jdbc.query(SELECT_TRANSFER + "WHERE partner_ref = ?", this::mapRow, partnerRef).stream().findFirst();
    }

    private Transfer mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Transfer(
                rs.getString("transfer_id"),
                rs.getString("customer_id"),
                rs.getString("idempotency_key"),
                rs.getString("request_hash"),
                rs.getString("send_currency"),
                BigDecimal.valueOf(rs.getLong("send_amount")),
                BigDecimal.valueOf(rs.getLong("fee")),
                BigDecimal.valueOf(rs.getLong("total_debit")),
                rs.getBigDecimal("fx_rate"),
                rs.getString("receive_currency"),
                rs.getBigDecimal("receive_amount"),
                rs.getString("recipient_name"),
                TransferStatus.valueOf(rs.getString("status")),
                rs.getString("partner_ref"),
                rs.getString("failure_reason"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }


}
