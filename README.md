# Finshot - Remittance Transfer API

Tugas take-home backend, versi 3.11, **Bagian 1 dan Bagian 2**.  
Nasabah di Korea mengirimkan mata uang KRW kepada penerima di Filipina (PHP).

## Cara Menjalankan

Persyaratan: **Java 17 atau yang lebih baru**. Tidak ada dependensi lain yang perlu diinstal: database menggunakan H2 *in-memory* dan Maven sudah disertakan melalui wrapper bawaan.

```bash
./mvnw spring-boot:run
```

Di Windows: jalankan perintah yang sama menggunakan Git Bash, atau jalankan `mvn spring-boot:run` jika Maven sudah terinstal di sistem Anda (`mvnw.cmd` sengaja dihilangkan karena Gmail memblokir lampiran file berekstensi .cmd di dalam file zip). Jika `./mvnw` memunculkan pesan "permission denied" (file zip dapat menghilangkan izin eksekusi file): jalankan `sh mvnw spring-boot:run`.

Jalankan perintah dari direktori utama (*root*) repositori. API berjalan di port `http://localhost:8080` dan selalu dimulai dengan database yang baru dan bersih setiap kali aplikasi dijalankan.

| Keterangan | Perintah |
|---|---|
| Pengujian / Tests (output konsol dari pengetesan saya: `test-output.txt`) | `./mvnw test` |
| Skenario S1-S9 (kondisi aplikasi aktif dan baru dijalankan) | `bash scenarios.sh > RESULT.md` |

## Teknologi (Stack)

- Java 17, Spring Boot 4.1 (Spring MVC, JdbcTemplate)
- Database H2 in-memory; seluruh tabel, constraint, dan seed data terdapat di dalam `schema.sql`
- JUnit 5 dan AssertJ untuk pengujian (*unit/integration test*)

## Endpoint

| Method | Path | Berhasil | Error |
|---|---|---|---|
| POST | `/api/transfers` | 201 | 400, 409, 422 |
| GET | `/api/transfers/{transferId}` | 200 | 404 |
| POST | `/api/transfers/{transferId}/cancel` | 200 | 404, 409 |
| POST | `/api/transfers/{transferId}/send` | 200 | 400, 404, 409 |
| POST | `/api/callbacks/partner` | 200 | 400, 404, 409 |

Semua nominal pada respons dikirimkan sebagai *string* JSON (`"20600.00"`). Format pesan error menggunakan `{"error": "...", "message": "..."}`.

## Cara Klien Melakukan Retry Permintaan Pembuatan Secara Aman

Setiap pemanggilan `POST /api/transfers` wajib menyertakan *header* **`Idempotency-Key`** (maksimal 64 karakter).  
Klien menghasilkan sebuah UUID acak baru (v4) saat pengguna memulai proses transfer baru, menyimpannya, lalu mengirimkan request yang persis sama dengan key yang sama pada setiap percobaan ulang (*retry* akibat timeout, koneksi terputus, atau klik ganda).

| Request | Hasil |
|---|---|
| Key baru | Transfer berhasil dibuat: 201, header `Idempotent-Replayed: false` |
| Key sama, body sama | Tidak membuat transfer baru. Data transfer asli dikembalikan: 201, `transferId` yang sama, header `Idempotent-Replayed: true` |
| Key sama, body berbeda | 409 `CONFLICT` |
| Tanpa key | 400 `VALIDATION_ERROR` |

```bash
curl -i -X POST http://localhost:8080/api/transfers \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 6f1c2a9e-1b7d-4c1e-9a52-3c1f0b8d7e21" \
  -d '{"customerId":"C001","sendCurrency":"KRW","sendAmount":"500000","receiveCurrency":"PHP","recipientName":"Juan Dela Cruz"}'
```

## Ringkasan Bagian 2

- **Send** (`POST /api/transfers/{id}/send`, body opsional `{"partnerRef": "..."}`): Status transfer berpindah dari `REQUESTED` -> `SENDING`, lalu `partnerRef` disimpan dan di-commit ke database. Setelah itu mitra tiruan (*fake partner*) dipanggil, hingga maksimal 3 kali percobaan dengan jeda waktu (*backoff*) 100 ms dan 200 ms. Jika diterima: status transfer tetap `SENDING`. Jika ketiga percobaan gagal: status menjadi `FAILED` disertai alasan kegagalan. Setiap percobaan dicatat ke log (nomor percobaan, `partnerRef`, hasil).
- **Limit Harian**: Maksimal 3.000.000 KRW untuk `sendAmount` per nasabah per hari kalender Asia/Seoul, transaksi berstatus `CANCELLED` dan `FAILED` tidak dihitung. Jika melebihi limit: 422 `LIMIT_EXCEEDED`. Baris data nasabah dikunci (*row lock*) selama proses validasi, sehingga dua request yang datang di detik yang bersamaan tidak dapat sama-sama lolos (lihat DESIGN.md Q4).
- **Callback** (`POST /api/callbacks/partner`): Satu-satunya cara untuk mengubah status menjadi `COMPLETED`, dan hanya berlaku dari status `SENDING`. Bersifat idempoten berdasarkan `eventId`: event yang sama dikirim dua kali hanya akan mengubah transfer satu kali, dan pengiriman kedua menerima HTTP 200 dengan header `Idempotent-Replayed: true`.

## Struktur Proyek

```
src/main/java/com/finshot/remittance
├── controller   TransferController, PartnerCallbackController
├── service      TransferService            pembuatan (idempoten, limit harian), get, cancel
│                PartnerService             kirim ke partner dengan retry, callback
│                TransferRequestValidator   aturan validasi (400)
│                TransferCalculator         biaya admin, total debet, dan nominal PHP (seluruh pembulatan ada di sini)
├── repository   TransferRepository, PartnerEventRepository (SQL menggunakan JdbcTemplate)
├── client       PartnerClient, FakePartnerClient
├── model        Transfer, TransferStatus (state machine), TransferAmounts
├── dto          body request dan response, ApiError
└── exception    ApiException, ErrorCode, GlobalExceptionHandler
```

Pengujian: `TransferCalculatorTest` (aturan 1.1), `TransferIdempotencyTest` (retry, termasuk 8 request serentak di waktu yang sama), `DailyLimitTest` (dua request di waktu yang sama), `PartnerFlowTest` (diterima, gagal, callback berulang).

## Asumsi

Dokumen instruksi menyisakan beberapa poin terbuka. Berikut keputusan yang saya ambil:

1. *Header* `Idempotency-Key` bersifat wajib. Tanpa header ini, mekanisme *retry* tidak dapat dikenali sehingga request ditolak (400).
2. Sebuah key berlaku untuk satu transfer di seluruh nasabah. Menggunakan kembali key yang sama dengan data field yang berbeda (termasuk beda `customerId`) akan menghasilkan HTTP 409.
3. Percobaan ulang (*retry*) mendapatkan status 201 yang sama dan data transfer yang sama seperti panggilan pertama, sesuai dengan kondisi status terbarunya (misalnya status `CANCELLED` jika transaksi tersebut telah dibatalkan setelahnya).
4. `sendAmount` berupa deretan angka bulat, seperti pada contoh instruksi. Format `"500000.00"`, `"5e5"`, dan `"+500000"` ditolak (400). Nominal lebih dari 15 digit ditolak karena terlalu besar, sehingga setiap nominal dipastikan muat dalam tipe data BIGINT.
5. `recipientName` yang hanya berisi spasi dihitung sebagai kosong. Kode mata uang bersifat *case-sensitive*: harus persis `"KRW"` dan `"PHP"`.
6. Validasi langsung mengembalikan pesan error dari aturan pertama yang dilanggar. Kolom `createdAt` dan `updatedAt` menggunakan format UTC, ISO-8601 dengan milidetik.
7. Respons error di luar daftar instruksi tetap mempertahankan struktur format error yang sama: `Content-Type` yang salah menghasilkan 400 `VALIDATION_ERROR`, method HTTP yang salah menghasilkan 405 `METHOD_NOT_ALLOWED`, dan error yang tidak terduga menghasilkan 500 `INTERNAL_ERROR`.
8. Dokumen instruksi tidak menjelaskan bagaimana suatu transfer masuk ke status `SENDING`, sehingga saya menambahkan endpoint `POST /api/transfers/{id}/send`. Pembuatan transfer baru tidak langsung mengirimkannya secara otomatis; jika langsung terkirim otomatis, skenario S4 dan S5 (pembatalan hanya diizinkan dari status `REQUESTED`) tidak akan dapat berjalan.
9. Pemanggil API dapat menentukan sendiri nilai `partnerRef` pada body request send (skenario S8 dan S9 melakukan hal ini, sesuai yang diminta instruksi untuk S9). Jika tidak diisi, server akan menghasilkan nilai otomatis `PR00000001`, `PR00000002`, … dari sequence database. Format `partnerRef` wajib diakhiri dengan angka digit.
10. Dokumen instruksi memberikan dua aturan berbeda untuk mitra tiruan (*fake partner*): `abs(partnerRef.hashCode()) % 10 < 3` dan "gagal jika digit terakhir dari partnerRef adalah 0, 1, atau 2". Kedua aturan ini tidak identik secara logika. Saya mengimplementasikan **aturan digit terakhir**: karena ditulis dengan huruf tebal, skenario S9 didasarkan pada aturan ini, dan tidak bergantung pada fungsi internal Java `hashCode()`. Referensi yang digunakan pada S8 (`PR-S8-0004`) dan S9 (`PR-S9-0001`) menghasilkan status yang sama di bawah kedua aturan tersebut.
11. Proses pengiriman (*send*) bersifat sinkron: respons dikembalikan ketika partner menerima (`SENDING`) atau setelah percobaan ketiga gagal (`FAILED`, membutuhkan waktu sekitar 1 detik).
12. Perhitungan limit harian menjumlahkan transfer berstatus `REQUESTED`, `SENDING`, dan `COMPLETED` yang dibuat pada hari kalender Asia/Seoul yang sama. Akumulasi total tepat 3.000.000 KRW diperbolehkan.
13. Callback dengan status selain `COMPLETED` akan ditolak (400). Nilai `eventId` yang sama namun dipasangkan dengan `partnerRef` yang berbeda menghasilkan HTTP 409.
14. Percobaan pengiriman ke partner dicatat ke log konsol dan ke file `target/app.log`; skrip `scenarios.sh` menyalin baris log pengujian S9 dari file tersebut.

## Hal yang Tidak Diimplementasikan

- Klien HTTP mitra nyata (*real partner*): instruksi meminta implementasi mitra tiruan (*fake partner*).
- Mekanisme pemulihan (*recovery*) untuk transfer yang tertahan di status `SENDING` akibat callback yang tidak kunjung tiba (misalnya *scheduled job/cron* yang secara berkala menanyakan status ke partner).
- Hal-hal yang secara eksplisit dinyatakan oleh dokumen instruksi tidak memiliki bobot penilaian: autentikasi, endpoint daftar/list data, Docker, OpenAPI/Swagger, API admin, dan metrik aplikasi.

## Alat Bantu AI yang Digunakan (AI Tools Used)

- Claude (Anthropic) - Menerjemahkan dokumen instruksi ke dalam Bahasa Indonesia dan membantu menyusun rencana kerja.
- Claude - Menghasilkan kode program, pengujian (*tests*), `schema.sql`, dan `scenarios.sh` sesuai struktur yang saya tentukan (controller, service, repository, model, dto), serta menyusun draf awal README.md dan DESIGN.md.
- Saya - Menginisialisasi proyek (Spring Initializr, IntelliJ IDEA), memperbaiki konfigurasi dan error skema database, meninjau (*code review*) kode program, serta menjalankan aplikasi, test, dan skenario pengujian di komputer lokal saya untuk memvalidasi hasilnya.