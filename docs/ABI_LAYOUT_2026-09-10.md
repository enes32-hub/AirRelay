# Native paket hizalaması denetimi — 10 Eylül 2026

`scripts/verify_native.py` yalnız ELF ve APK ZIP düzenini doğrular. İmza, kurulum,
codec performansı, dinamik bağımlılıkların tamamı veya gerçek 16 KB Android
cihazında çalışma için kabul kanıtı değildir. JSON çıktısı bu nedenle
`scope: artifact-layout-only` ve `runtime_verified: false` alanlarını korur.

## ABI bazında uygulanan kurallar

| ABI | ELF sınıfı / makine | En az PT_LOAD hizalaması |
|---|---|---|
| armeabi-v7a | ELF32 / ARM (40) | 4096 bayt |
| arm64-v8a | ELF64 / AArch64 (183) | 16384 bayt |
| x86_64 | ELF64 / x86-64 (62) | 16384 bayt |

Android'in 16 KB sayfa boyutu gereksinimi 64 bit yerel kitaplıklar içindir.
32 bit ARM kitaplığındaki 4 KB PT_LOAD hizalaması kendi hedefi için geçerlidir;
bu, onun 16 KB hizalı olduğu anlamına gelmez. Her kitaplık için
`required_elf_alignment` ABI gereksinimini, `elf_16kb_alignment` ise **gerçekte
gözlenen** tüm yükleme segmentlerinin en az 16 KB hizalı olup olmadığını bildirir.
4 KB ARM32 kitaplığı başarılı incelemede `elf_16kb_alignment: false` döndürür.

Tüm yükleme segmentlerinde hizalama ikinin kuvveti olmalı, dosya/sanal adres
kalanları aynı olmalı, dosya ve bellek sınırları geçerli olmalıdır. ELF sınıfı
ve makine ABI ile eşleşmelidir. 64 bit kitaplıkta 4 KB hizalama hâlâ reddedilir.

APK içinde doğrudan yüklenen, sıkıştırılmamış ve şifrelenmemiş **bütün** `.so`
girdilerinin ZIP veri başlangıcı ayrıca 16 KB hizalı kalmalıdır. Bu ZIP
politikası ARM32 için de korunur. ZIP hizalamasının geçmesi, içindeki ELF
hizalamasının geçtiği anlamına gelmez. TV paketinde iki ARM ABI ve bunların
aynı kitaplık adları bulunmalıdır.

Kaynak: [Android — Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes).

## Düzeltme ve doğrulama kaydı

Önceki denetçi bütün ABIlere 16 KB ELF şartını uyguladığı için ARM32 4 KB
segmentlerini de reddediyordu. Eski başarısız sonuçlar değiştirilmedi:

- `artifacts/product-polish-20260910/inspector-arm-layout-before-final.json`
- `artifacts/product-polish-20260910/inspector-arm-layout-before-final.log`

Aynı başlangıç RC TV APK'sı yeni ABI kurallarıyla yeniden incelendi:

- Dosya: `Velora-Cast-0.1.0-rc1-tv-test.apk`
- SHA-256: `f30416ccc2bc8b73cd89bd113015904dd0c1d25e5004d412d278fbc7c5c64f27`
- Sonuç: ELF/ZIP düzeni **GEÇTİ**. 6 ARM64 kitaplığın tümü 16 KB; 6 ARM32
  kitaplığın 3'ü 4 KB, 3'ü 16 KB. Her iki ABI eksiksiz ve ZIP hizalaması 16 KB.
- Kanıt: `artifacts/product-polish-20260910/inspector-arm-layout-abi-corrected.json`
  ve aynı adlı `.log`.

Bu önceki RC dosyasıdır. Daha sonra yeniden derlenen APK'nın özeti ve hizalama
sonucu ayrıca alınmalıdır; bu kayıt sonraki dosyalara otomatik olarak uygulanmaz.

`tests/test_native_layout.py` içindeki 24 test yeniden çalıştırıldı. Korpus,
ARM32 4 KB kabulünü ve dürüst raporunu, 4 KB altı/ikinin kuvveti olmayan
hizalama reddini, yanlış sınıf/makineyi, ARM64 ve x86_64 için 4 KB reddini,
16 KB ZIP içindeki bozuk ELF reddini ve ARM32 için de ZIP hizalama zorunluluğunu
kapsar. Sentetik ELF/ZIP testleri fiziksel cihaz kanıtı değildir.

Komutlar:

```powershell
python -m unittest discover -s tests -p test_native_layout.py -v
python scripts/verify_native.py --apk <APK> --output <yeni-rapor.json>
```

Test kanıtı: `artifacts/product-polish-20260910/inspector-native-layout-tests.log`.
