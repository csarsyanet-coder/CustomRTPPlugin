# ArsyaCustomRTP

Plugin **Custom RTP berbayar** untuk **Paper / Purpur** yang memakai **Vault Economy**.

Plugin ini memungkinkan player menggunakan RTP ke tujuan tertentu dengan biaya berdasarkan **persentase saldo**, dengan batas **minimum balance** dan **max cost** yang bisa diatur per tujuan/world.

## Fitur utama

- Command utama:
  - `/customrtp <tujuan>`
  - `/customrtp reload`
- Biaya RTP berdasarkan **persentase saldo player**
- Mendukung **minimum balance** per tujuan
- Mendukung **max cost** per tujuan
- Bisa memberi **permission khusus** per tujuan RTP
- Otomatis **refund** biaya jika command RTP gagal dijalankan
- Build otomatis dengan GitHub Actions

## Kebutuhan

- Java 17
- Paper / Purpur 1.20.4+
- Vault
- Plugin economy yang kompatibel dengan Vault
- Plugin RTP tujuan yang command-nya akan dipanggil oleh plugin ini

## Struktur singkat repo

```text
.github/workflows/build.yml
pom.xml
src/main/resources/plugin.yml
src/main/resources/config.yml
src/main/java/me/arsyadev/customrtp/CustomRTPPlugin.java
```

## Command

### `/customrtp <tujuan>`
Menjalankan RTP ke tujuan yang didefinisikan di `config.yml`.

Contoh:
```text
/customrtp overworld
/customrtp nether
/customrtp end
```

### `/customrtp reload`
Reload config plugin.

## Permission

```text
customrtp.admin
customrtp.bypasscost
```

### Penjelasan permission

- `customrtp.admin`  
  Izin untuk memakai `/customrtp reload`

- `customrtp.bypasscost`  
  Izin untuk bypass biaya RTP

## Cara kerja biaya RTP

Plugin menghitung biaya RTP dari saldo player:

```text
biaya = saldo × percent / 100
```

Lalu hasilnya dibatasi dengan:
- `minimum-balance`
- `max-cost`

Jika player tidak punya permission bypass, plugin akan:
1. cek saldo minimum
2. hitung biaya
3. potong saldo
4. jalankan command RTP
5. jika command gagal, saldo akan dikembalikan

## Contoh config

```yml
messages:
  reload: '&a&lSUKSES &8| &fConfig ArsyaCustomRTP berhasil direload.'
  disabled: '&c&lERROR &8| &fRTP ini sedang dinonaktifkan.'
  no-permission: '&c&lERROR &8| &fKamu tidak punya permission.'
  command-failed: '&c&lERROR &8| &fGagal menjalankan command RTP.'
  not-enough-balance: '&c&lERROR &8| &fSaldo minimal untuk RTP ini adalah &a$%minimum_balance%&f.'
  success: '&a&lSUKSES &8| &fSaldo dipotong &a$%cost% &funtuk RTP &e(%world%)&f.'

worlds:
  overworld:
    enabled: true
    permission: ''
    percent: 10
    minimum-balance: 1000
    max-cost: 50000
    rtp-command: 'rtp world world_resources'

  nether:
    enabled: true
    permission: ''
    percent: 10
    minimum-balance: 1000
    max-cost: 50000
    rtp-command: 'rtp world world_nether_resources'

  end:
    enabled: true
    permission: ''
    percent: 10
    minimum-balance: 1000
    max-cost: 50000
    rtp-command: 'rtp world world_the_end_resources'
```

## Penjelasan konfigurasi world

Setiap tujuan RTP ada di bawah `worlds:`.

### `enabled`
Mengaktifkan atau menonaktifkan tujuan RTP.

### `permission`
Permission khusus untuk tujuan itu.  
Kalau dikosongkan, semua player bisa memakainya.

### `percent`
Persentase saldo yang dipakai untuk menghitung biaya RTP.

### `minimum-balance`
Saldo minimum agar player boleh menggunakan RTP tersebut.

### `max-cost`
Batas maksimal biaya RTP.

### `rtp-command`
Command yang akan dijalankan oleh player untuk RTP ke tujuan itu.

## Placeholder pesan

Pesan di config mendukung placeholder berikut:

- `%world%`
- `%balance%`
- `%cost%`
- `%minimum_balance%`
- `%percent%`
- `%max_cost%`

## Contoh penggunaan

### RTP overworld
```text
/customrtp overworld
```

### RTP nether
```text
/customrtp nether
```

### Reload config
```text
/customrtp reload
```

## Build

Build plugin dengan Maven:

```bash
mvn clean package
```

Hasil file jar ada di folder:

```text
target/
```

## GitHub Actions

Repo ini bisa dibuild otomatis lewat workflow:

```text
.github/workflows/build.yml
```

Kalau workflow berhasil, file jar bisa diambil dari **Artifacts** di tab **Actions**.

## Catatan penting

- Plugin ini **bergantung pada Vault**
- Plugin economy harus aktif agar biaya RTP bisa dihitung
- Kalau Vault atau economy tidak ditemukan, plugin akan otomatis dinonaktifkan
- Command RTP tujuan harus valid, karena plugin ini hanya menjalankan command yang kamu isi di config

## Author

**ArsyaDev**
