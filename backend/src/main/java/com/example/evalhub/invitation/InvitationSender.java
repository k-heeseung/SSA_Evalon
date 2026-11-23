package com.example.evalhub.invitation;

import com.example.evalhub.entity.Role;

public interface InvitationSender {
    /** role에 따라 심사위원/매니저 안내 문구를 고른다. */
    String send(String recipient, String temporaryPassword, Role role);

    default String send(String recipient, String temporaryPassword) {
        return send(recipient, temporaryPassword, Role.EVALUATOR);
    }

    class DeliveryException extends RuntimeException {
        private final String code;
        private final boolean retryable;
        private final long retryAfterSeconds;
        public DeliveryException(String code, boolean retryable) { this(code, retryable, 0); }
        public DeliveryException(String code, boolean retryable, long retryAfterSeconds) {
            super(code);
            this.code = code;
            this.retryable = retryable;
            this.retryAfterSeconds = retryAfterSeconds;
        }
        public String code() { return code; }
        public boolean retryable() { return retryable; }
        public long retryAfterSeconds() { return retryAfterSeconds; }
    }
}
