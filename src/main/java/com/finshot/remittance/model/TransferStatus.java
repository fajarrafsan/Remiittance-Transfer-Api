package com.finshot.remittance.model;

public enum TransferStatus {

    REQUESTED,
    SENDING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean canMoveTo(TransferStatus next) {
        return switch (this) {
            case REQUESTED -> next == SENDING || next == CANCELLED;
            case SENDING -> next == COMPLETED || next == FAILED;
            case COMPLETED, FAILED, CANCELLED -> false;
        };
    }
}
