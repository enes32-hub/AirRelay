# Sabit native kaynak incelemesi

31 Ağustos 2026. **Üretim çekirdeği onaylanmadı.** İnceleme kaynak envanteri ve köken sinyalleridir; hukuk görüşü, güvenlik taraması veya uyumluluk testi değildir.

## Kapsam ve kanıt

[Araştırma kilidi](native-audit.lock.json), [JSON raporu](native-source-audit.json) ve [dosya envanteri](native-source-inventory.csv) birlikte okunmalıdır. `scripts/audit_native_sources.py` her dosyayı commit ağacındaki Git blob SHA-1 ile karşılaştırır ve ayrıca SHA-256 üretir. Kaynaklar yalnız bellekte okunur; üçüncü taraf kodu, tablolar ve anahtarlar çalışma ağacına kopyalanmaz veya çalıştırılmaz.

| Kaynak | İncelenen dosya |
|---|---:|
| Android uyarlamasının native dosyaları ve yapılandırması | 21 |
| Uyarlamanın kullandığı UxPlay altmodülü | 72 |
| RPiPlay köken referansı | 11 |
| İlk playfair referansı | 10 |
| libplist kapsamındaki kaynak/başlık/lisanslar | 57 |
| openssl-cmake yapılandırması | 7 |
| FFmpeg ALAC için seçilen kaynak/lisanslar | 7 |
| **Toplam** | **185** |

Prefix kapsamı bazı kullanılmayan kaynakları da içerir; FFmpeg'in tüm transitif link grafiği incelenmiş değildir. Lisans başlığı sinyali lisans onayı değildir, bulunmaması da tek başına lisanssızlık hükmü değildir. Dosyaların hepsi `MANUAL_REVIEW_REQUIRED` durumundadır.

## Ana engel: upstream'in kendi belirsizlik beyanı

Android uyarlaması `UxPlay@462153392f2e30937424922039ff9f0cda5e7b1a` kullanır. Bu commit'in [README satır 2486–2488 bölümü](https://github.com/FDH2/UxPlay/blob/462153392f2e30937424922039ff9f0cda5e7b1a/README.md#L2486), FairPlay bileşeninin hukuk durumunun belirsiz olduğunu söyler. [RPiPlay README](https://github.com/FD-/RPiPlay/blob/64d0341ed3bef098c940c9ed0675948870a271f9/README.md#L124) aynı belirsizliği içerir.

UxPlay'in atıf zinciri RPiPlay → AirplayServer/ShairPlay/playfair yönündedir. [İlk playfair deposunun README'si](https://github.com/EstebanKubata/playfair/blob/96c232ed7e65cccfbd1eae8e0d70e7179799290d/README.md) yalnız proje başlığı içerir; kriptografik sabitlerin bağımsız üretimini veya hak kaynağını açıklamaz. Git geçmişine ve GPL metnine erişmek bu boşluğu tek başına kapatmaz. Oturum şifrelemesi içerik DRM'iyle aynı şey değildir; buna rağmen şartname belirsiz materyal kullanımını ayrıca yasaklıyor.

**Karar:** yalnız GPL lisansını kabul ederek bu çekirdeği AynaTV'ye eklemek için yeterli kanıt yok. [İnceleme soruları](../../legal/REVIEW_REQUEST.md) çözülmeden veya şartnameye uygun farklı çekirdek sağlanmadan bu kapı geçmez. Bu sonuç bileşenin hukuka aykırı olduğuna ilişkin hüküm değildir.

## Uyarlamayı olduğu gibi alamama nedenleri

- [Version catalog](https://github.com/jqssun/android-airplay-server/blob/c8defdd70d7e6a04f4f1b71d353653682d594106/gradle/libs.versions.toml) `media3 = 1.11.0-beta01` içerir. Şartnamedeki kararlı bağımlılık kuralına uymuyor; bizim `1.11.0` araştırma tabanımızla karıştırılmamalı.
- [CMake](https://github.com/jqssun/android-airplay-server/blob/c8defdd70d7e6a04f4f1b71d353653682d594106/app/src/main/cpp/CMakeLists.txt) OpenSSL `3.4.4` seçiyor. [openssl-cmake BuildOpenSSL](https://github.com/viaduck/openssl-cmake/blob/4edd36a8dab5f85a8f92650b5bdf6e0cab13aab8/cmake/BuildOpenSSL.cmake) hash doğrulamasını `OPENSSL_BUILD_HASH` verilmesine bağlıyor ve bir mirror URL kullanıyor. İncelenen üst CMake'de bu hash ataması yok. Tam yapılandırma ve indirilen kaynağın doğrulanmış hash'i olmadan bu bağımlılık alınamaz.
- [FFmpeg yapılandırması](https://github.com/jqssun/android-airplay-server/blob/c8defdd70d7e6a04f4f1b71d353653682d594106/app/src/main/cpp/cmake/BuildFFmpeg.cmake) ağ desteğini kapatarak statik ALAC decoder hedefliyor. Bayrakların ve lisansların tam karşılık gelen çıktısı, gerçek build grafiği ve kaynak sunma yükümlülüğü ayrıca incelenmeli.

Bu bulgular AynaTV'ye aktarılmış hatalar değildir: aday bağımlılıkları henüz eklenmedi. `contains_private_key_pem_marker=false` yalnız dar PEM işaret denetimidir; kriptografik tablo veya gömülü sır yokluğunu kanıtlamaz.

## Yeniden üretim

```powershell
python scripts/audit_native_sources.py
```

Sabit commit'ler değişmez; ağ hatası veya Git blob uyuşmazlığı sonuç üretimini durdurur. JSON ve CSV aynı koşudan oluşturulur. Araştırma indirmeleri gelecekteki yerel ağla sınırlı Android uygulamasının trafiği değildir.
