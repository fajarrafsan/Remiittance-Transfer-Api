package com.finshot.remittance.client;

public interface PartnerClient {
    // true = diterima, Hasil akhirnya akan di perolah kemudian melalui callBack
    boolean send(String partnerRef);
}
