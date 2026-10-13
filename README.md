# DESAIN SISTEM

## Q1. Tabel, index, dan constraint

Tabel `customers` sudah tersedia (*given*). Tabel `transfers` menyimpan satu baris untuk setiap transaksi transfer: nominal, status, *idempotency key*, *hash request*, referensi partner (`partner_ref`), alasan kegagalan, dan catatan waktu (*timestamps*). Tabel `partner_events` mencatat setiap *callback* yang telah diproses.

Nominal KRW bertipe `BIGINT` (tidak memiliki satuan desimal/sen). `receive_amount` bertipe `DECIMAL(19,2)` dan `fx_rate` bertipe `DECIMAL(10,4)`, sehingga database tidak akan pernah melakukan pembulatan otomatis.

Setiap *constraint* mencegah satu masalah spesifik:

- `PRIMARY KEY (transfer_id)`: Mencegah duplikasi data transfer.
- `FOREIGN KEY (customer_id)`: Mencegah transfer untuk nasabah yang tidak terdaftar.
- `UNIQUE (idempotency_key)`: Mencegah transfer ganda akibat *retry*, bahkan ketika dua request *retry* tiba pada saat yang bersamaan (*concurrent*).
- `UNIQUE (partner_ref)`: Mencegah satu *callback* mencocokkan dua transfer yang berbeda.
- `PRIMARY KEY (event_id)` pada `partner_events`: Mencegah *callback* yang sama diproses dua kali.
- `CHECK (status IN (...))`: Mencegah status yang tidak dikenal/tidak valid.
- `CHECK` bahwa transfer yang terkirim harus memiliki `partner_ref`: Mencegah adanya transfer yang tidak bisa ditemukan oleh *callback*.
- `CHECK` pada nominal dan aturan `total_debit = send_amount + fee`: Mencegah uang bernilai negatif dan kesalahan perhitungan total.

Index `(customer_id, created_at)`: Pengambilan agregasi total (*sum*) limit harian hanya membaca data transaksi milik satu nasabah pada hari tersebut secara efisien.

## Q2. Idempotensi (Idempotency)

Klien mengirimkan *header* `Idempotency-Key`: berupa UUID baru untuk setiap transaksi baru, dan digunakan kembali pada setiap *retry* untuk transfer tersebut. Server menyimpan *key* tersebut beserta *hash* SHA-256 dari kolom-kolom *request* pada baris data transfer.

Saat pembuatan (*create*):

1. **Key ditemukan, hash sama:** Kembalikan transfer yang sudah tersimpan (HTTP 201, dengan header `Idempotent-Replayed: true`).
2. **Key ditemukan, hash berbeda:** Kembalikan error 409 (*Conflict*).
3. **Key tidak ditemukan:** Masukkan (*insert*) transfer baru.

Perlindungan sesungguhnya berada di level database, yaitu melalui *constraint* `UNIQUE` pada kolom `idempotency_key`. Pengecekan di level kode hanyalah jalur cepat (*fast path*).

Jika dua request yang identik tiba pada detik yang persis sama, keduanya bisa saja mendeteksi "key tidak ditemukan", namun hanya satu perintah `INSERT` yang akan berhasil. Request lainnya akan terkena error duplikasi *key* (*duplicate-key error*). Kode akan menangkap error tersebut, membaca baris data yang berhasil disimpan berdasarkan *key*, membandingkan *hash*-nya, lalu mengembalikannya. Dengan demikian, selalu dipastikan hanya ada tepat satu transfer per *idempotency key*.

## Q3. Nilai Uang (Money)

- **Kode:** Menggunakan `BigDecimal` yang dibuat dari *string* (`new BigDecimal("0.0412")`), dan tidak pernah menggunakan `double`, karena tipe `double` tidak dapat menyimpan angka 0.0412 secara presisi.
- **API:** Semua nominal dikirim sebagai *string* JSON (`"20600.00"`), sehingga klien tidak membacanya sebagai tipe *floating point* dan ketelitian dua angka desimal tetap terjaga.
- **Database:** KRW disimpan sebagai `BIGINT` (bilangan won bulat), PHP sebagai `DECIMAL(19,2)`, dan kurs sebagai `DECIMAL(10,4)`.

Pembulatan hanya dilakukan di satu tempat, yaitu `TransferCalculator`, dan hanya sekali saat transfer dibuat:

- Biaya admin (*fee*) = 1% dari `sendAmount`, dibulatkan dengan *HALF_UP* ke bilangan bulat KRW terdekat, lalu diterapkan batas minimum sebesar 3.000 KRW;
- `receiveAmount` = `sendAmount` × 0.0412, dibulatkan dengan *HALF_UP* ke 2 angka desimal.

Hasil perhitungan langsung disimpan dan tidak pernah dihitung ulang. Dengan begitu, data respons, database, dan setiap pembacaan di kemudian hari akan selalu menampilkan angka yang sama, serta perubahan kurs atau aturan di masa depan tidak akan memengaruhi data transfer lama.

## Q4. Limit Harian di Bawah Kondisi Konkurensi

Menerapkan penguncian baris data nasabah (*row-level lock*). Pembuatan transfer berjalan dalam satu transaksi database:

1. `SELECT ... FROM customers WHERE customer_id = ? FOR UPDATE` (mengunci data nasabah);
2. Periksa kembali *idempotency key*;
3. Hitung total (*sum*) `send_amount` hari ini (berdasarkan hari di zona waktu `Asia/Seoul`, tanpa status CANCELLED dan FAILED);
4. Tolak dengan 422 jika melebihi limit, atau lakukan `INSERT`;
5. `COMMIT`.

Request kedua untuk nasabah yang sama akan menunggu di langkah 1 sampai request pertama selesai melakukan *commit*, sehingga hasil penjumlahannya sudah mencakup transfer pertama. Dua request tidak akan pernah bisa lolos bersamaan jika jumlah keduanya melebihi 3.000.000 KRW. Langkah 2 memastikan bahwa request *retry* yang sempat tertahan antrean *lock* akan menerima data transfer aslinya, bukan error 422.

*Trade-off* yang diambil: Permintaan dari nasabah yang sama diproses satu per satu secara berurutan, sehingga lonjakan request (*burst*) dari nasabah yang sama akan sedikit menunggu. Namun, nasabah yang berbeda tidak saling mengunci (*non-blocking*). Aturan ini juga terjamin karena seluruh proses *insert* wajib melalui kode ini.

## Q5. Callback Tiba Sebelum Hasil Partner Tersimpan

Sistem tidak akan rusak/gagal karena urutan penulisan datanya (*order of writes*):

1. Sebelum memanggil partner, kode terlebih dahulu menyimpan status `SENDING` dan `partnerRef` ke database, lalu melakukan `COMMIT`.
2. Setelah itu, kode memanggil API partner di luar transaksi database.

Dengan alur ini, *callback* yang tiba lebih awal akan selalu menemukan transfer dalam status `SENDING` dan berhasil mengubahnya menjadi `COMPLETED`.

Setelah pemanggilan partner, kode hanya akan memperbarui database jika ketiga percobaan (*retries*) gagal, dan query `UPDATE` tersebut menyertakan klausa `WHERE status = 'SENDING'`. Jika *callback* sudah lebih dahulu menyelesaikan transfer tersebut, update kegagalan akan mengubah 0 baris sehingga status `COMPLETED` tetap bertahan. Perulangan *retry* juga akan berhenti jika status transfer sudah bukan lagi `SENDING`, dan respons pengiriman akan membaca ulang data transfer terkini.

Masalah baru akan terjadi jika `partnerRef` baru disimpan setelah partner merespons: *callback* yang tiba lebih awal akan mendapatkan error 404. Menyimpan datanya terlebih dahulu adalah solusi yang tepat.