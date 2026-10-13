# DESAIN SISTEM

## Q1. Tabel, Index, dan Constraint

Tabel `customers` sudah tersedia (*given*). Tabel `transfers` menyimpan satu baris untuk setiap transaksi transfer: nominal, status, *idempotency key*, *hash request*, referensi partner (`partner_ref`), alasan kegagalan, serta catatan waktu (*timestamps*). Tabel `partner_events` mencatat setiap *callback* yang telah diproses.

Nominal mata uang KRW bertipe `BIGINT` (tidak memiliki satuan pecahan/desimal). `receive_amount` bertipe `DECIMAL(19,2)` dan `fx_rate` bertipe `DECIMAL(10,4)`, sehingga database tidak akan pernah melakukan pembulatan otomatis.

Setiap *constraint* mencegah satu masalah spesifik:

- `PRIMARY KEY (transfer_id)`: Mencegah duplikasi data transfer.
- `FOREIGN KEY (customer_id)`: Mencegah transfer untuk nasabah yang tidak terdaftar/tidak valid.
- `UNIQUE (idempotency_key)`: Mencegah transfer ganda akibat *retry*, bahkan ketika dua request *retry* masuk secara bersamaan (*concurrent*).
- `UNIQUE (partner_ref)`: Mencegah satu *callback* mencocokkan dua transfer yang berbeda.
- `PRIMARY KEY (event_id)` pada `partner_events`: Mencegah *callback* yang sama diproses dua kali.
- `CHECK (status IN (...))`: Mencegah status yang tidak dikenal/tidak valid.
- `CHECK` memastikan transfer yang terkirim memiliki `partner_ref`: Mencegah adanya transfer yang tidak bisa ditemukan oleh *callback*.
- `CHECK` pada nominal dan aturan `total_debit = send_amount + fee`: Mencegah nominal uang bernilai negatif dan kesalahan perhitungan total.

Index `(customer_id, created_at)`: Pengambilan data agregasi (*sum*) untuk limit harian hanya membaca data transaksi milik satu nasabah pada hari tersebut secara efisien.

## Q2. Idempotensi (Idempotency)

Klien mengirimkan *header* `Idempotency-Key`: berupa UUID baru untuk setiap transfer baru, dan digunakan kembali pada setiap *retry* untuk transfer tersebut. Server menyimpan *key* tersebut beserta *hash* SHA-256 dari *field request* pada baris data tabel transfer.

Saat pembuatan transfer (*create*):

1. **Key ditemukan, hash sama:** Kembalikan data transfer yang tersimpan (HTTP 201, dengan header `Idempotent-Replayed: true`).
2. **Key ditemukan, hash berbeda:** Kembalikan HTTP 409 (*Conflict*).
3. **Key tidak ditemukan:** Lakukan *insert* transfer baru.

Perlindungan utamanya berada di level database, yaitu melalui *constraint* `UNIQUE` pada kolom `idempotency_key`. Pengecekan pada level kode hanyalah jalur cepat (*fast path*).

Jika dua request yang identik tiba pada milidetik yang persis sama (*race condition*), keduanya bisa saja mendeteksi "key tidak ditemukan", tetapi hanya satu proses `INSERT` yang akan berhasil. Request lainnya akan terkena error duplikasi *key* (*duplicate-key error*). Kode akan menangkap (*catch*) error tersebut, membaca baris data yang berhasil disimpan berdasarkan *key*, membandingkan *hash*-nya, lalu mengembalikannya. Dengan demikian, selalu terjamin hanya ada tepat satu transfer per *idempotency key*.

## Q3. Penanganan Nominal Uang (Money)

- **Kode:** Menggunakan `BigDecimal` yang dibuat dari *string* (`new BigDecimal("0.0412")`), dan tidak pernah menggunakan `double`, karena tipe `double` tidak dapat menyimpan angka desimal 0.0412 secara presisi.
- **API:** Semua nominal dikirimkan sebagai *string* JSON (`"20600.00"`), sehingga tidak ada klien yang membacanya sebagai tipe *floating-point*, dan presisi dua angka desimal di belakang koma tetap terjaga.
- **Database:** KRW disimpan sebagai `BIGINT` (satuan won bulat), PHP sebagai `DECIMAL(19,2)`, dan kurs (*exchange rate*) sebagai `DECIMAL(10,4)`.

Pembulatan hanya dilakukan di satu tempat, yaitu pada kelas `TransferCalculator`, dan hanya sekali saat transfer dibuat:

- Biaya admin (*fee*) = 1% dari `sendAmount`, dibulatkan dengan *HALF_UP* ke bilangan bulat KRW terdekat, lalu diterapkan batas minimum sebesar 3.000 KRW;
- `receiveAmount` = `sendAmount` × 0.0412, dibulatkan dengan *HALF_UP* ke 2 angka desimal.

Hasil perhitungan langsung disimpan ke database dan tidak pernah dihitung ulang. Dengan begitu, data respons, database, dan pembacaan data di masa mendatang akan selalu menampilkan angka yang sama, serta perubahan kurs atau perubahan aturan di kemudian hari tidak akan mengubah data transaksi lama.

## Q4. Limit Harian di Bawah Kondisi Konkurensi (Concurrency)

Menerapkan penguncian baris data nasabah (*row-level locking*). Proses pembuatan transfer berjalan dalam satu transaksi database tunggal:

1. `SELECT ... FROM customers WHERE customer_id = ? FOR UPDATE` (mengunci data nasabah).
2. Periksa kembali *idempotency key*.
3. Hitung total (*sum*) `send_amount` hari ini (berdasarkan perhitungan hari pada zona waktu `Asia/Seoul`, mengabaikan status CANCELLED dan FAILED).
4. Tolak dengan HTTP 422 jika melebihi limit, atau jalankan perintah `INSERT`.
5. `COMMIT` transaksi.

Request kedua untuk nasabah yang sama akan tertahan/menunggu di langkah 1 hingga request pertama selesai melakukan *commit*. Dengan demikian, hasil penjumlahannya sudah mencakup transaksi pertama. Dua request tidak akan pernah bisa sama-sama lolos jika akumulasi keduanya melebihi batas 3.000.000 KRW. Langkah ke-2 memastikan bahwa request *retry* yang sempat tertahan antrean *lock* akan menerima data transfer aslinya, bukan error 422.

*Trade-off* yang diambil: Permintaan dari nasabah yang sama akan diproses satu per satu secara berurutan (*sequential*), sehingga jika terjadi lonjakan request (*burst*) dari nasabah yang sama, request tersebut akan menunggu sebentar. Namun, nasabah yang berbeda tidak akan saling mengunci satu sama lain. Aturan ini terjamin konsisten karena setiap proses *insert* wajib melewati jalur kode ini.

## Q5. Callback Tiba Sebelum Hasil Partner Tersimpan

Sistem tidak akan rusak/error karena urutan penulisan datanya (*order of writes*):

1. Sebelum memanggil API partner, kode akan terlebih dahulu menyimpan status `SENDING` dan `partnerRef` ke database, lalu melakukan `COMMIT`.
2. Setelah itu, kode memanggil API partner di luar transaksi database.

Dengan alur ini, *callback* yang datang lebih awal akan selalu menemukan transfer tersebut dalam status `SENDING`, lalu berhasil memperbaruinya menjadi `COMPLETED`.

Setelah pemanggilan ke partner selesai, kode hanya akan memperbarui data ke database jika seluruh 3 kali percobaan (*retries*) mengalami kegagalan, dan query `UPDATE` tersebut memiliki kondisi `WHERE status = 'SENDING'`. Jika *callback* sudah lebih dahulu menyelesaikan transfer tersebut menjadi `COMPLETED`, query update kegagalan tersebut akan mengubah 0 baris, sehingga status `COMPLETED` tetap aman. Perulangan *retry* juga akan otomatis berhenti jika status transfer sudah bukan lagi `SENDING`, dan respons pengiriman akan membaca ulang data transfer terkini dari database.

Masalah baru akan terjadi jika `partnerRef` baru disimpan setelah partner merespons: *callback* yang tiba lebih awal akan menghasilkan error 404. Menyimpan data dan mereferensikannya terlebih dahulu adalah solusi yang tepat.# DESAIN SISTEM

