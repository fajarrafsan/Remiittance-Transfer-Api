# HASIL PENGUJIAN (RESULT)

Skenario S1-S9, dijalankan berurutan pada aplikasi yang baru dinyalakan (2026-10-04 20:58 +0700).
Pada pengujian ini, file jar dijalankan di port 18080 (bukan port default 8080), dan skrip diarahkan ke port tersebut:

```bash
java -jar target/remittance-transfer-api-0.0.1-SNAPSHOT.jar --server.port=18080 --server.address=127.0.0.1
BASE_URL=http://127.0.0.1:18080 bash scenarios.sh > RESULT.md
```

Dengan perintah bawaan `./mvnw spring-boot:run` (port 8080), perintah biasa `bash scenarios.sh > RESULT.md` akan mengirimkan request yang persis sama.
Setiap request ditampilkan sesuai aslinya: method, path, header, dan body.

## Bagian 1 - S1 sampai S6

### S1 - Buat transfer untuk C001, nominal 500000

Request:

```http
POST /api/transfers
Content-Type: application/json
Idempotency-Key: key-s1

{"customerId":"C001","sendCurrency":"KRW","sendAmount":"500000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```

Status: 201

Header response: `Idempotent-Replayed: false`

Response:

```json
{
    "transferId": "2641d5bc-9ae3-4270-835e-4c15a2e96795",
    "status": "REQUESTED",
    "customerId": "C001",
    "recipientName": "Juan Dela Cruz",
    "sendCurrency": "KRW",
    "sendAmount": "500000",
    "fee": "5000",
    "totalDebit": "505000",
    "fxRate": "0.0412",
    "receiveCurrency": "PHP",
    "receiveAmount": "20600.00",
    "partnerRef": null,
    "failureReason": null,
    "createdAt": "2026-10-04T13:58:53.950Z",
    "updatedAt": "2026-10-04T13:58:53.950Z"
}
```

### S2 - Retry S1: Idempotency-Key sama, body sama

Request:

```http
POST /api/transfers
Content-Type: application/json
Idempotency-Key: key-s1

{"customerId":"C001","sendCurrency":"KRW","sendAmount":"500000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```

Status: 201

Header response: `Idempotent-Replayed: true`

Response:

```json
{
    "transferId": "2641d5bc-9ae3-4270-835e-4c15a2e96795",
    "status": "REQUESTED",
    "customerId": "C001",
    "recipientName": "Juan Dela Cruz",
    "sendCurrency": "KRW",
    "sendAmount": "500000",
    "fee": "5000",
    "totalDebit": "505000",
    "fxRate": "0.0412",
    "receiveCurrency": "PHP",
    "receiveAmount": "20600.00",
    "partnerRef": null,
    "failureReason": null,
    "createdAt": "2026-10-04T13:58:53.950Z",
    "updatedAt": "2026-10-04T13:58:53.950Z"
}
```

### S3 - Idempotency-Key sama seperti S1, namun nominal 600000

Request:

```http
POST /api/transfers
Content-Type: application/json
Idempotency-Key: key-s1

{"customerId":"C001","sendCurrency":"KRW","sendAmount":"600000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```

Status: 409

Response:

```json
{
    "error": "CONFLICT",
    "message": "Idempotency-Key was already used with different request data"
}
```

### S4 - Buat transfer lainnya

Request:

```http
POST /api/transfers
Content-Type: application/json
Idempotency-Key: key-s4

{"customerId":"C003","sendCurrency":"KRW","sendAmount":"1000000","receiveCurrency":"PHP","recipientName":"Ana Reyes"}
```

Status: 201

Header response: `Idempotent-Replayed: false`

Response:

```json
{
    "transferId": "2e3d4938-e43f-4958-8789-c24bdce876e0",
    "status": "REQUESTED",
    "customerId": "C003",
    "recipientName": "Ana Reyes",
    "sendCurrency": "KRW",
    "sendAmount": "1000000",
    "fee": "10000",
    "totalDebit": "1010000",
    "fxRate": "0.0412",
    "receiveCurrency": "PHP",
    "receiveAmount": "41200.00",
    "partnerRef": null,
    "failureReason": null,
    "createdAt": "2026-10-04T13:58:56.625Z",
    "updatedAt": "2026-10-04T13:58:56.625Z"
}
```

### S4 - Batalkan transfer tersebut

Request:

```http
POST /api/transfers/2e3d4938-e43f-4958-8789-c24bdce876e0/cancel
```

Status: 200

Response:

```json
{
    "transferId": "2e3d4938-e43f-4958-8789-c24bdce876e0",
    "status": "CANCELLED",
    "customerId": "C003",
    "recipientName": "Ana Reyes",
    "sendCurrency": "KRW",
    "sendAmount": "1000000",
    "fee": "10000",
    "totalDebit": "1010000",
    "fxRate": "0.0412",
    "receiveCurrency": "PHP",
    "receiveAmount": "41200.00",
    "partnerRef": null,
    "failureReason": null,
    "createdAt": "2026-10-04T13:58:56.625Z",
    "updatedAt": "2026-10-04T13:58:57.536Z"
}
```

### S5 - Batalkan transfer yang sama sekali lagi

Request:

```http
POST /api/transfers/2e3d4938-e43f-4958-8789-c24bdce876e0/cancel
```

Status: 409

Response:

```json
{
    "error": "CONFLICT",
    "message": "cannot change status from CANCELLED to CANCELLED"
}
```

### S6 - Buat transfer dengan sendAmount = 5000 (di bawah batas minimum)

Request:

```http
POST /api/transfers
Content-Type: application/json
Idempotency-Key: key-s6

{"customerId":"C001","sendCurrency":"KRW","sendAmount":"5000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```

Status: 400

Response:

```json
{
    "error": "VALIDATION_ERROR",
    "message": "sendAmount must be at least 10000"
}
```

## Bagian 2 - S7 sampai S9

S7: Nasabah C002 mengirim 1.000.000 KRW sebanyak tiga kali (3.000.000 = batas limit harian), kemudian mencoba mengirim 10.000 KRW lagi.

### S7 - C002, transfer 1 dari 3

Request:

```http
POST /api/transfers
Content-Type: application/json
Idempotency-Key: key-s7-1

{"customerId":"C002","sendCurrency":"KRW","sendAmount":"1000000","receiveCurrency":"PHP","recipientName":"Maria Santos"}
```

Status: 201

Header response: `Idempotent-Replayed: false`

Response:

```json
{
    "transferId": "2d53bd5a-b44b-4230-9bec-d25848bb98c3",
    "status": "REQUESTED",
    "customerId": "C002",
    "recipientName": "Maria Santos",
    "sendCurrency": "KRW",
    "sendAmount": "1000000",
    "fee": "10000",
    "totalDebit": "1010000",
    "fxRate": "0.0412",
    "receiveCurrency": "PHP",
    "receiveAmount": "41200.00",
    "partnerRef": null,
    "failureReason": null,
    "createdAt": "2026-10-04T13:59:00.121Z",
    "updatedAt": "2026-10-04T13:59:00.121Z"
}
```

### S7 - C002, transfer 2 dari 3

Request:

```http
POST /api/transfers
Content-Type: application/json
Idempotency-Key: key-s7-2

{"customerId":"C002","sendCurrency":"KRW","sendAmount":"1000000","receiveCurrency":"PHP","recipientName":"Maria Santos"}
```

Status: 201

Header response: `Idempotent-Replayed: false`

Response:

```json
{
    "transferId": "4ecbd1cc-384d-451b-a6b1-9369dc981521",
    "status": "REQUESTED",
    "customerId": "C002",
    "recipientName": "Maria Santos",
    "sendCurrency": "KRW",
    "sendAmount": "1000000",
    "fee": "10000",
    "totalDebit": "1010000",
    "fxRate": "0.0412",
    "receiveCurrency": "PHP",
    "receiveAmount": "41200.00",
    "partnerRef": null,
    "failureReason": null,
    "createdAt": "2026-10-04T13:59:01.023Z",
    "updatedAt": "2026-10-04T13:59:01.023Z"
}
```

### S7 - C002, transfer 3 dari 3 (berhasil terakhir, akumulasi hari ini 3.000.000)

Request:

```http
POST /api/transfers
Content-Type: application/json
Idempotency-Key: key-s7-3

{"customerId":"C002","sendCurrency":"KRW","sendAmount":"1000000","receiveCurrency":"PHP","recipientName":"Maria Santos"}
```

Status: 201

Header response: `Idempotent-Replayed: false`

Response:

```json
{
    "transferId": "a0ec65f8-d6dd-49cc-8213-d0b083117696",
    "status": "REQUESTED",
    "customerId": "C002",
    "recipientName": "Maria Santos",
    "sendCurrency": "KRW",
    "sendAmount": "1000000",
    "fee": "10000",
    "totalDebit": "1010000",
    "fxRate": "0.0412",
    "receiveCurrency": "PHP",
    "receiveAmount": "41200.00",
    "partnerRef": null,
    "failureReason": null,
    "createdAt": "2026-10-04T13:59:01.906Z",
    "updatedAt": "2026-10-04T13:59:01.906Z"
}
```

### S7 - C002, kirim tambahan 10000: ditolak

Request:

```http
POST /api/transfers
Content-Type: application/json
Idempotency-Key: key-s7-4

{"customerId":"C002","sendCurrency":"KRW","sendAmount":"10000","receiveCurrency":"PHP","recipientName":"Maria Santos"}
```

Status: 422

Response:

```json
{
    "error": "LIMIT_EXCEEDED",
    "message": "daily limit of 3000000 KRW exceeded: 3000000 KRW already used today (Asia/Seoul)"
}
```

### S8 - Buat transfer untuk C001, nominal 100000

Request:

```http
POST /api/transfers
Content-Type: application/json
Idempotency-Key: key-s8

{"customerId":"C001","sendCurrency":"KRW","sendAmount":"100000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```

Status: 201

Header response: `Idempotent-Replayed: false`

Response:

```json
{
    "transferId": "fd875bfc-31f6-4f6c-9c15-26555cbbbd0c",
    "status": "REQUESTED",
    "customerId": "C001",
    "recipientName": "Juan Dela Cruz",
    "sendCurrency": "KRW",
    "sendAmount": "100000",
    "fee": "3000",
    "totalDebit": "103000",
    "fxRate": "0.0412",
    "receiveCurrency": "PHP",
    "receiveAmount": "4120.00",
    "partnerRef": null,
    "failureReason": null,
    "createdAt": "2026-10-04T13:59:03.606Z",
    "updatedAt": "2026-10-04T13:59:03.606Z"
}
```

### S8 - Kirim transfer ke partner (partnerRef PR-S8-0004, diterima: status tetap SENDING)

Request:

```http
POST /api/transfers/fd875bfc-31f6-4f6c-9c15-26555cbbbd0c/send
Content-Type: application/json

{"partnerRef":"PR-S8-0004"}
```

Status: 200

Response:

```json
{
    "transferId": "fd875bfc-31f6-4f6c-9c15-26555cbbbd0c",
    "status": "SENDING",
    "customerId": "C001",
    "recipientName": "Juan Dela Cruz",
    "sendCurrency": "KRW",
    "sendAmount": "100000",
    "fee": "3000",
    "totalDebit": "103000",
    "fxRate": "0.0412",
    "receiveCurrency": "PHP",
    "receiveAmount": "4120.00",
    "partnerRef": "PR-S8-0004",
    "failureReason": null,
    "createdAt": "2026-10-04T13:59:03.606Z",
    "updatedAt": "2026-10-04T13:59:04.442Z"
}
```

### S8 - Callback dari partner, pengiriman pertama

Request:

```http
POST /api/callbacks/partner
Content-Type: application/json

{"partnerRef":"PR-S8-0004","eventId":"evt_0001","status":"COMPLETED"}
```

Status: 200

Header response: `Idempotent-Replayed: false`

Response:

```json
{
    "transferId": "fd875bfc-31f6-4f6c-9c15-26555cbbbd0c",
    "status": "COMPLETED",
    "customerId": "C001",
    "recipientName": "Juan Dela Cruz",
    "sendCurrency": "KRW",
    "sendAmount": "100000",
    "fee": "3000",
    "totalDebit": "103000",
    "fxRate": "0.0412",
    "receiveCurrency": "PHP",
    "receiveAmount": "4120.00",
    "partnerRef": "PR-S8-0004",
    "failureReason": null,
    "createdAt": "2026-10-04T13:59:03.606Z",
    "updatedAt": "2026-10-04T13:59:05.473Z"
}
```

### S8 - Callback yang sama dikirim ulang (eventId sama)

Request:

```http
POST /api/callbacks/partner
Content-Type: application/json

{"partnerRef":"PR-S8-0004","eventId":"evt_0001","status":"COMPLETED"}
```

Status: 200

Header response: `Idempotent-Replayed: true`

Response:

```json
{
    "transferId": "fd875bfc-31f6-4f6c-9c15-26555cbbbd0c",
    "status": "COMPLETED",
    "customerId": "C001",
    "recipientName": "Juan Dela Cruz",
    "sendCurrency": "KRW",
    "sendAmount": "100000",
    "fee": "3000",
    "totalDebit": "103000",
    "fxRate": "0.0412",
    "receiveCurrency": "PHP",
    "receiveAmount": "4120.00",
    "partnerRef": "PR-S8-0004",
    "failureReason": null,
    "createdAt": "2026-10-04T13:59:03.606Z",
    "updatedAt": "2026-10-04T13:59:05.473Z"
}
```

### S8 - Status akhir (Final state)

Request:

```http
GET /api/transfers/fd875bfc-31f6-4f6c-9c15-26555cbbbd0c
```

Status: 200

Response:

```json
{
    "transferId": "fd875bfc-31f6-4f6c-9c15-26555cbbbd0c",
    "status": "COMPLETED",
    "customerId": "C001",
    "recipientName": "Juan Dela Cruz",
    "sendCurrency": "KRW",
    "sendAmount": "100000",
    "fee": "3000",
    "totalDebit": "103000",
    "fxRate": "0.0412",
    "receiveCurrency": "PHP",
    "receiveAmount": "4120.00",
    "partnerRef": "PR-S8-0004",
    "failureReason": null,
    "createdAt": "2026-10-04T13:59:03.606Z",
    "updatedAt": "2026-10-04T13:59:05.473Z"
}
```

### S9 - Buat transfer untuk C001, nominal 100000

Request:

```http
POST /api/transfers
Content-Type: application/json
Idempotency-Key: key-s9

{"customerId":"C001","sendCurrency":"KRW","sendAmount":"100000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}
```

Status: 201

Header response: `Idempotent-Replayed: false`

Response:

```json
{
    "transferId": "006da487-c18a-4c4a-8006-8c0b0b27f641",
    "status": "REQUESTED",
    "customerId": "C001",
    "recipientName": "Juan Dela Cruz",
    "sendCurrency": "KRW",
    "sendAmount": "100000",
    "fee": "3000",
    "totalDebit": "103000",
    "fxRate": "0.0412",
    "receiveCurrency": "PHP",
    "receiveAmount": "4120.00",
    "partnerRef": null,
    "failureReason": null,
    "createdAt": "2026-10-04T13:59:08.126Z",
    "updatedAt": "2026-10-04T13:59:08.126Z"
}
```

### S9 - Kirim ke partner (partnerRef PR-S9-0001, ditolak 3 kali berturut-turut)

Request:

```http
POST /api/transfers/006da487-c18a-4c4a-8006-8c0b0b27f641/send
Content-Type: application/json

{"partnerRef":"PR-S9-0001"}
```

Status: 200

Response:

```json
{
    "transferId": "006da487-c18a-4c4a-8006-8c0b0b27f641",
    "status": "FAILED",
    "customerId": "C001",
    "recipientName": "Juan Dela Cruz",
    "sendCurrency": "KRW",
    "sendAmount": "100000",
    "fee": "3000",
    "totalDebit": "103000",
    "fxRate": "0.0412",
    "receiveCurrency": "PHP",
    "receiveAmount": "4120.00",
    "partnerRef": "PR-S9-0001",
    "failureReason": "partner did not accept the request after 3 attempts",
    "createdAt": "2026-10-04T13:59:08.126Z",
    "updatedAt": "2026-10-04T13:59:10.013Z"
}
```

Log proses retry S9 (baris dari file `target/app.log`):

```text
2026-10-04T20:59:09.308+07:00  INFO 40132 --- [remittance-transfer-api] [http-nio-127.0.0.1-18080-exec-10] c.f.remittance.service.PartnerService    : partner attempt 1/3 partnerRef=PR-S9-0001 outcome=FAILED
2026-10-04T20:59:09.610+07:00  INFO 40132 --- [remittance-transfer-api] [http-nio-127.0.0.1-18080-exec-10] c.f.remittance.service.PartnerService    : partner attempt 2/3 partnerRef=PR-S9-0001 outcome=FAILED
2026-10-04T20:59:10.013+07:00  INFO 40132 --- [remittance-transfer-api] [http-nio-127.0.0.1-18080-exec-10] c.f.remittance.service.PartnerService    : partner attempt 3/3 partnerRef=PR-S9-0001 outcome=FAILED
```

### S9 - Status akhir (Final state)

Request:

```http
GET /api/transfers/006da487-c18a-4c4a-8006-8c0b0b27f641
```

Status: 200

Response:

```json
{
    "transferId": "006da487-c18a-4c4a-8006-8c0b0b27f641",
    "status": "FAILED",
    "customerId": "C001",
    "recipientName": "Juan Dela Cruz",
    "sendCurrency": "KRW",
    "sendAmount": "100000",
    "fee": "3000",
    "totalDebit": "103000",
    "fxRate": "0.0412",
    "receiveCurrency": "PHP",
    "receiveAmount": "4120.00",
    "partnerRef": "PR-S9-0001",
    "failureReason": "partner did not accept the request after 3 attempts",
    "createdAt": "2026-10-04T13:59:08.126Z",
    "updatedAt": "2026-10-04T13:59:10.013Z"
}
```