package com.finshot.remittance.client;

import org.springframework.stereotype.Component;

// Gagal jika partnerRef berakhiran 0, 1, atau 2. Setiap pemanggilan memakan waktu setidaknya 200 ms.
@Component
public class FakePartnerClient implements PartnerClient {

    static final long CALL_DURATION_MS = 200;

    @Override
    public boolean send(String partnerRef) {
        try {
            Thread.sleep(CALL_DURATION_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return !failsFor(partnerRef);
    }

    static boolean failsFor(String partnerRef) {
        char last = partnerRef.charAt(partnerRef.length() - 1);
        return last == '0' || last == '1' || last == '2';
    }
}
