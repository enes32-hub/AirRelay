# AynaTV Revizyon 2 — yerel entegrasyon ve doğrulama kaydı

7 Eylül 2026. **Emülatör entegrasyonu geçti; gerçek iPhone akışı ve sunum performansı kabulü bekliyor.** Debug uygulama ve Android test APK'ları derlendi; AynaTV'ye ayrılmış Android TV API 36 `aynatv_tv_api36` / `emulator-5584` üzerinde çalıştırıldı. Son host çalıştırmasında 231 kontrol, Android instrumentation çalıştırmasında 13 senaryo geçti. Yerel AVC üretimiyle gerçek decoder/EGL zinciri ayrıca sınandı. Bunlar iPhone ekranı, ses/senkron veya nihai ürün kabulü değildir.

## Kaynak devri, bütünlük ve geri dönüş

Paketin `01_Son_Surum` bölümündeki Revizyon 2 kaynakları esas alındı. Eski patch dosyaları tekrar uygulanmadı. İçe aktarma kaydı, 20 güncellenen ve 68 eklenen dosyayı içerir: `artifacts/rev2-integration/import.json`.

| Kayıt | Yer / sonuç |
|---|---|
| Gelen kaynak arşivinin değiştirilmeyen kopyası | `artifacts/rev2-integration/incoming-revision2.zip` |
| Rev2 öncesi kaynak yedeği | `artifacts/AynaTV-Rev2-Oncesi-Kaynak-2026-09-07.zip`; 10.745 giriş, ZIP CRC kontrolü geçti |
| Önceki APK'nın geri dönüş kopyası | `artifacts/rev2-integration/rollback-before-rev2.apk` |
| Bağımsız kaynak doğrulama alanı | `artifacts/rev2-integration/pristine/AynaTV` |
| Gelen ZIP kontrolü | 10.812 giriş; CRC hatası yok, çıkarma hedefi dışına çıkan yol yok |
| Gelen teslim manifesti | 10.811 dosyanın boyutu ve SHA-256 özeti doğrulandı |

SHA-256 kayıtları:

```text
incoming-revision2.zip:
0aaddd32e728840bcb391c8ae630d995f8dbbcc86b896345b65db7b434532546
AynaTV-Rev2-Oncesi-Kaynak-2026-09-07.zip:
6782468dce95ba091a648cfdffb9a350f2c61a77c2efe69e09be18183a595710
rollback-before-rev2.apk:
867bde2214d9ad60cecc8565db265325bdb00542a56495a56f85048deda0a32d
```

Yerel SDK yapılandırması sıfırlanmadı. Emülatör kurulumu mevcut uygulama verilerini koruyan güncelleme olarak yapıldı; kurulu APK'nın özeti teslim APK'sıyla eşleşiyor ve `airplay.pem` eşleştirme anahtarının özeti değişmedi. Kanıt: `artifacts/rev2-integration/delivery.json`. Geri dönüşte yedek kaynak/APK kullanılabilir; veri silme veya uygulamayı kaldırma varsayılan geri dönüş yöntemi değildir.

`SOURCE_MANIFEST.json`, `UPDATED_SOURCE_MANIFEST.json` ve `docs/productization/`, `docs/refinement2/` içindeki tarihsel manifest/kanıtlar değiştirilmedi. Yerel düzeltmelerden sonra tarihsel tam kaynak eşitliği kontrollerinin aynı sonucu vermesi beklenmez; bu belgeler güncel kaynakmış gibi yeniden yazılmadı. Güncel karşılaştırma ayrı rapordadır.

## Yerel düzeltmeler

- **Olay zamanları:** `DiagnosticStore` kuyruğu yalnız metin yerine olayın alındığı duvar saati ve monoton zamanı da tutar. Disk yazımı/toplu boşaltma gecikse bile satır olay zamanını korur. Kuyruk taşma bildirimi ilk atlanan olayın zamanını taşır; bellek sınırı, temizleme ve metin filtreleme korunur.
- **İlk kare gözlemi:** Decoder çıkışının oturum nesli, medya PTS değeri ve yüzey zaman damgası kaydedilir. `SurfaceTexture` tarafından gerçekten alınan kareyle eşleştirilen, geçerli oturuma ait başarılı EGL gönderimi ilk kare durumunu açabilir. Eski bekleyen kare, gecikmiş callback veya önbellekteki dokunun yeniden çizimi yeni oturumu canlı göstermez. Belirsiz zaman damgası eşleşmesi ve gözlem kuyruğu taşması canlılık kanıtı sayılmaz. Bu kayıt katmanı medya karelerini yeniden zamanlamaz; başarılı EGL gönderimi de fiziksel panelde gösterim ya da uçtan uca gecikme ölçümü değildir.
- **Ekran oturumunun korunması:** Ekran sesi veya mirror başlangıcı, ekran oturumu bilgisini son kontrol bağlantısı bitene kadar tutar. Video kesilmesi müzik oynatma durumuna düşmez; görüntü bekleniyor durumu korunur. Yeni sunum oturumu eski ilk-kare callback'ini kabul etmez. Bağlantı sonunda bu bilgiler temizlenir; gerçek yalnız ses oturumu ayrı kalır.
- **Android test koşucusu:** `app/build.gradle.kts` içine yerel entegrasyon instrumentation koşucusu bildirildi; son çalıştırmadaki 13 senaryonun sonuçları aşağıda ayrı kanıtlarla yer alır.
- **Gerçek Compose derleme düzeltmesi:** İlk Android derlemesi `ReceiverHome.kt:34` içindeki, iç içe `RowScope` üzerinden örtük `maxHeight` erişiminde durdu. `compactGuide = maxHeight < 380.dp` değeri `BoxWithConstraints` kapsamında hesaplanarak alt çağrıya geçirildi. Bu, aynı boyut kararını koruyan kapsam düzeltmesidir.
- **Karanlık temada metin görünürlüğü:** Emülatörde saptanan koyu metin sorunu giderildi. Kalıcı video yüzeyi üzerindeki `Box`, `Material Surface` varsayılan içerik rengini sağlamadığı için tema artık `LocalContentColor = palette.onBackground` değerini açıkça sağlar.
- **İsteğe bağlı Android test derlemesi:** `scripts/build_receiver.ps1` için `-WithIntegrationTests` seçeneği eklendi. Bu seçenek `:app:assembleDebugAndroidTest` görevini normal debug derlemesine ekler; seçenek verilmediğinde mevcut debug derleme görevi korunur.
- **Durdurmanın ana iş parçacığından ayrılması:** İlk gerçek emülatör ölçümündeki 713,626 ms durdurma çağrısının ardından native temizleme, süreç ömürlü ve sıralı bir IO kuyruğuna taşındı. Kullanımdan çıkarılan pointer sahipliği `retiredHandle` üzerinden aktarılır; yeni başlangıç önceki temizliği bekler. Callback nesli ve `stopping` denetimi eski alıcının yeni durumu etkilemesini engeller; oynatıcı işleri Android ana iş parçacığında kalır.
- **Tamamlanmış temizleme referanslarının bırakılması:** Başarılı cleanup, kuyruk hâlâ aynı görevi gösteriyorsa referansını kilit altında temizler. Böylece tamamlanmış lazy coroutine görevlerinin servis/yüzey/önceki görev zincirini süreç boyunca tutması önlenir. Android hızlı durdur/başlat senaryosu sonunda kuyruk referansının temizlendiğini de doğrular.
- **Yerelleştirilmiş diyaloglar:** `ProductEnvironment`, `ReceiverDialogs` ve `ReceiverSettings` akışındaki Türkçe diyalog görünümü düzeltildi. Ad düzenleme diyaloğu gerçek Türkçe arayüzde tekrar kontrol edildi.

Cleanup başarısız olursa kuyruk bilinçli olarak başarısız durumda tutulur ve güvenli olduğu bilinmeyen native pointer/portların yeniden kullanımı engellenir. Otomatik sessiz tekrar denenmez; toparlanma uygulama sürecinin yeniden başlatılmasını gerektirir. Cleanup hata enjeksiyonu ve eski servis yok edilirken yeni servis örneğinin oluşturulması bu turda çalıştırılmadı; hızlı tekrar testi aynı bağlı servis örneğini kullanır.

## Kaynak koruma ve host testleri

Pristine kaynak üzerinde testler tekrar çalıştırıldı; arşiv içindeki eski sonuçlar yeniden kullanım kanıtı sayılmadı. Baseline kaydı 7 Eylül 2026 23:12:13 İstanbul saati; son host kaydı 23:49:22, son regresyon kaydı 23:49:19 İstanbul saatidir. Sonuçlar `final-host/` altında saklandı; önceki çalıştırmalar korunmuştur. Kesin UTC zamanları ilgili JSON kayıtlarındadır.

| Kontrol | Pristine Rev2 | Yerel düzeltmeler sonrası |
|---|---:|---:|
| Kotlin ürün/durum/günlük mantığı | 76 geçti | 76 geçti |
| Kotlin etkileşim/ayar/NSD sarmalayıcı mantığı | 58 geçti | 58 geçti |
| C++ günlük çıktı filtresi | 15 geçti | 15 geçti |
| Python hazırlık araçları | 44 geçti | 44 geçti |
| Ekran oturumu regresyonları | Yeni test yok | 15 geçti |
| İlk kare/decoder kökeni regresyonları | Yeni test yok | 17 geçti |
| Günlük alım zamanı/kuyruk regresyonları | Yeni test yok | 6 geçti |
| **Toplam yürütülen host/Python kontrolü** | **193 geçti** | **231 geçti** |

Baseline kaynak kontrolleri ayrıca 10.581 dosyalık Rev2 koruma listesini, 10.579 orijinal dosya ile 5 izinli değişiklik sınırını, 16 statik etkileşim korumasını ve 336 Türkçe/İngilizce metin çiftini doğruladı. Host NSD ve Android sınıfları açıkça belirtilen test ikameleridir; Compose, Binder, codec veya gerçek ağ yürütmesi olarak yorumlanmamalıdır.

İlk iki kaynak görüntüsü, `core-comparison.json` ve `final-core-comparison.json` içinde değiştirilmeden korundu. Son yerel düzeltmelerden sonra 23:51:24 İstanbul saatinde oluşturulan `post-runtime-core-comparison.json` esas alınmalıdır: **10.811 gelen dosyanın 10.799'u aynı, 12 mevcut dosya planlı olarak değişmiş; eksik veya açıklanamayan değişiklik yok.** Yeni helper ve test kaynakları ayrıca kaydedildi; mevcut dosya sayısına dahil değildir.

| Korunan grup | Son kaynak karşılaştırması |
|---|---|
| Native C/C++ kaynakları ve bağımlılıklar | 10.551 dosya aynı |
| UxPlay üçüncü taraf kaynakları | 96 dosya aynı |
| Ses motoru, ses yardımcıları ve timeline | 6 dosya aynı |
| Emülatör AirPlay köprüsü | 9 dosya aynı |
| JNI Kotlin bildirimleri | Aynı |
| Codec/alıcı varsayılanları | Aynı |
| Tarihsel teslim manifestleri ve kanıtları | 23 dosya aynı |
| Servisin `NativeBridge` çağrı ifadeleri | **Birebir eşitlik false:** aşağıdaki açıklanmış cleanup değişikliği |

Native ABI, Kotlin native bildirimleri ve C/C++ uygulaması korunmuştur; **Kotlin çağrı ifadeleri ve yaşam döngüsü zamanlaması birebir aynı değildir**. Önceki iki `nativeDestroy(nativeHandle)` yeri, ortak `nativeDestroy(retiredHandle)` yerine taşındı; `nativeStop(nativeHandle)` çağrısı `nativeStop(retiredHandle)` oldu. Normal durdurmada stop, destroy işleminden önce çalışır; yeni başlangıç temizliği bekler. Karşılaştırma bu farkları açıkça eklenen/çıkarılan ifadeler olarak raporlar. Tarihsel strict JNI çağrı kontrolü değiştirilerek eşitlik sağlanmış gibi gösterilmedi.

Kanıt dosyaları:

- `artifacts/rev2-integration/baseline/results.json` ve yanındaki kaynak/host test günlükleri.
- `artifacts/rev2-integration/integrated/results.json`, `regressions.json` ve her testin `.log` dosyası.
- `artifacts/rev2-integration/integrated/core-comparison.json`; ilk yerel kaynak görüntüsü, değiştirilmeden korundu.
- `artifacts/rev2-integration/integrated/final-core-comparison.json`; ara kaynak görüntüsü, değiştirilmeden korundu.
- `artifacts/rev2-integration/final-host/results.json`, `regressions.json` ve `.log` dosyaları; son 231 kontrol.
- `artifacts/rev2-integration/integrated/post-runtime-core-comparison.json`; son kaynak özetleri, 12 değişen dosya ve açıklanmış JNI yaşam döngüsü farkı.

Çevrimdışı host araçları: yerel Gradle önbelleğinden Kotlin/JVM 2.1.10 adaptörü, JDK 17, MSYS2 GCC 16.2 ve Python 3.13. Yeni araç indirmesi gerekmedi. Tekrarlama komutları proje kökünde:

```powershell
python artifacts/rev2-integration/tooling/run_validation.py --source . --label repeat-host --checks host interactions python
python artifacts/rev2-integration/tooling/run_regressions.py --label repeat-host
python artifacts/rev2-integration/tooling/compare_current_core.py --output repeat-core-comparison.json
```

Aynı etiketle host testini tekrar çalıştırmak yerel sonuç dosyalarını yeniler. Bir önceki yerel denemeyi de saklamak gerekiyorsa `run_validation.py` için ayrı etiket kullanılmalıdır. Tarihsel teslim klasörlerine sonuç yazılmamalıdır.

## Android derlemesi ve çalışma zamanı kabulü

| Adım | Gözlenen durum / kanıt |
|---|---|
| İlk x86_64 debug derlemesi, `C:\AynaTVBuild6` | **Başarısız:** 8 dakika 5 saniye sonunda Compose örtük alıcı hatası. `artifacts/rev2-integration/build-debug.log` |
| Kapsam düzeltmesinden sonraki derleme | **Geçti:** 1 dakika 29 saniye; `artifacts/rev2-integration/build-debug-retry1.log` |
| Tema düzeltmesi ve Android test APK'sı ile derleme | **Geçti:** `assembleDebug` ve `assembleDebugAndroidTest`, 54 saniye; `artifacts/rev2-integration/build-android-tests.log` |
| Son uygulama ve Android test derlemesi | **Geçti:** 1 dakika 14 saniye; `artifacts/rev2-integration/build-final.log` |
| Çalışma alanı / ASCII derleme girdisi eşleşmesi | **Geçti:** 78 Kotlin, XML/manifest, Android test ve app Gradle girdisi aynı; `final-source-build-match.json` |
| Oluşturulan debug APK | `artifacts/rev2-integration/aynatv-revision2-x86_64-debug.apk`; **39.030.337 bayt**, SHA-256 aşağıda |
| Oluşturulan Android test APK'sı | `artifacts/rev2-integration/aynatv-revision2-androidTest.apk`; son instrumentation çalıştırması geçti |
| `aynatv_tv_api36` / `emulator-5584` güncelleme kurulumu | **Geçti:** kurulu APK özeti eşleşiyor, eşleştirme anahtarı değişmedi; `delivery.json` |
| Android entegrasyonu | **13 senaryo geçti:** `android-results.json` ve `android-instrumentation-final.log`; kapsam ayrımı aşağıda |
| Ana ekran, rehber, ayarlar, günlükler; kumanda/klavye ve odak | **Geçti:** gerçek emülatör gezinmesi, geri dönüş odağı ve ekran görüntüleri `screenshots/02-13` |
| Uzun basış, kontrol gizleme, PIN önceliği, durdurma onayı | **Geçti:** gerçek Compose arayüzü/tuşları, senaryo girdileri kontrollü callback; instrumentation kanıtı |
| Türkçe/İngilizce, uzun alıcı adı, büyük yazı | **Geçti:** büyük yazı seçimi, %150 Android yazı ölçeği ve `AynaTV Living Room - Receiver Validation 2026` adı (45 ASCII karakter / 45 UTF-8 bayt); ad tekrar `AynaTV Receiver` yapıldı, test ayarları geri alındı |
| Başlat/durdur arayüz takılması | **Performans kabulü geçmedi:** doğrudan stop çağrısı iyileşti; son ayrı gfx kaydında 118 karenin 75'i janky (%63,56) |
| Güncel Windows köprüsü ve alıcı kimliği | **Yerel kontrol geçti:** RTSP 200, doğru adla plist, mDNS yanıtı; `bridge-check.json`. iPhone keşif/bağlantı kanıtı değildir |
| iPhone 14 Pro bağlantı, sürekli görüntü/ses, durdurma, yeniden bağlanma | **Çalıştırılmadı:** bu kaynak düzeltmeleri sonrasında gerçek iPhone akışı doğrulanmadı |

Son derlemeden kopyalanan APK dosyalarının SHA-256 değerleri:

```text
aynatv-revision2-x86_64-debug.apk:
730c8abd9249f87422019d476fd4f465057465794c3985a4282c277cc7b6facb
aynatv-revision2-androidTest.apk:
555e1572489e8ac6705fa83498b95eac459e461260560bea37fd84f175ba5f78
```

Android senaryoları; gerçek activity/servis başlangıcını, iptal edilen ve hızlı tekrarlanan başlangıcı, eski callback'in reddini, keşif/ağ durumunu, ilk kare beklemesini, ekran sesi/video kesilmesini, yayın sırasında taslak ayarların ve iptalin etkisizliğini, kontrol gizleme/uzun tuş davranışını, bağlantı temizliğini ve PIN/durdurma diyaloğu önceliğini kapsar. Ağ kaybı ve keşif hatası **`SIMULATED_CALLBACKS`** ile gerçek gözlemcilere verilmiştir; fiziksel arayüz kesintisi değildir. Servis, Compose arayüzü ve input işlemleri gerçek Android ortamında çalışmıştır.

Manuel görüntüler `artifacts/rev2-integration/screenshots/` altındadır: `02-home-tr`, `03-help-tr`, `04-logs-tr`, `05-help-return-focus`, `06-settings-large-tr`, `07-home-large-tr`, `08-long-name-large-tr`, `09-long-name-font-scale-150`, `10-large-font-scrolled`, `11-home-en`, `12-name-dialog-tr-fixed`, `13-final-home-tr`. Kaydırma ve geri dönüş odağı görüldü; Türkçe ad diyaloğu düzeltmesi tekrar kontrol edildi. 48 UTF-8 bayt sınırını aşan ad reddedildi. Son Türkçe hazır ekran: `screenshots/14-final-ready.png`.

**Yerel video zinciri:** `LOCAL_ENCODED_AVC` modu, emülatörde `c2.android.avc.encoder` ile 320×192, kaynak 15 fps test görüntüsü üretip uygulamanın H.264 decoder/EGL zincirine verdi. Üç ayrı oturumda 36'şar kodlanmış kare beslendi; sırasıyla **23, 29, 29 RGB çıktı buffer'ı**, her oturumda **4 farklı piksel imzası**, **1 ilk-kare bildirimi** ve **0 giriş düşürmesi** gözlendi. Bu, gerçek `MediaCodec` ve EGL ile ekrandan bağımsız çıktı doğrulamasıdır. AirPlay şifreleme/ağ aktarımı, iPhone görüntüsü, ses ve fiziksel panelde gösterim bu deneyde sınanmadı.

## Ölçümler ve hazır bağlantı

İlk emülatör denemesinde ana iş parçacığındaki doğrudan durdurma çağrısı en çok **713,626 ms** sürdü. Native teardown ayrıldıktan sonraki son çalıştırmada 15 doğrudan çağrı ölçüldü: 7 durdurma örneğinde en çok **56,842 ms**, 8 başlatma örneğinde en çok **15,3496 ms**. Önceki tekrarın toplam maksimumu **38,9293 ms** idi; koşular arasındaki değişkenlik korunarak raporlandı. Bunlar dispatch beklemesini, asenkron native başlangıç/temizliği, frame jank'i ve uçtan uca medya gecikmesini içermez; **16 ms veya takılmasızlık garantisi verilmez**. Kanıtlar: `android-instrumentation-attempt1.log`, `android-instrumentation-attempt2.log`, `android-instrumentation-final.log`, `android-results.json`.

Son kurulu APK ile üç ısınmış durdur/başlat döngüsünün ayrı gfx kaydı: **118 kare, 75 janky kare (%63,56); p50 36 ms, p90 57 ms, p95 65 ms, p99 77 ms; 38 yavaş UI thread olayı**. Kanıt: `artifacts/rev2-integration/gfx-warm-start-stop.txt`. **Performans kabulü geçmedi.** Daha kısa stop çağrısı, tüm çizim akışının akıcı olduğunu göstermiyor. Bunlar paylaşılan bilgisayardaki debug Android TV emülatörüne ait sınırlı ölçümlerdir; fiziksel Xiaomi TV veya GPU performansı iddiası yapılmaz. Önceki 115 kare / 63 janky kare ölçümü `*-before-final` kayıtlarında saklandı; son sonuçla karıştırılmamalıdır.

Windows köprüsünün yerel kontrolünde `192.168.31.32`, konsol `5584`, ağ arayüzü `9` ve alıcı adı **AynaTV Receiver** kullanıldı. Private ağ/firewall kuralları değiştirilmedi. Windows'tan RTSP bilgi isteği 200 döndü, plist alıcı adıyla eşleşti ve mDNS yanıtı gözlendi. Kontrol sırasında köprü süreci `7596` çalışıyordu; PID yalnız bu çalışma anının kaydıdır. Kaynak `LOCAL_HOST_BRIDGE_SMOKE` olduğundan bu sonuç iPhone'da görünürlük veya telefondan yayın bağlantısı garantisi değildir.

Emülatör ve köprü test için açık bırakıldı. Bu kurulumdaki köprüyü gerektiğinde nazikçe durdurmak için yeni `scripts/stop_revision2_bridge.ps1` kullanılmalıdır; yalnız stop dosyası yazar, çalışan köprü kendi `5584` yönlendirmelerini temizler ve emülatörü kapatmaz. Son kontrolde başka projelere ait `5580` ve `5582` cihazları da görünüyordu; bu entegrasyondaki cihaz işlemleri yalnız `5584` üzerinde yapıldı. Diğer cihazların tüm gfx ölçüm süresince çalıştığı varsayılmadı.

## Kabul sınırı ve devir

**Kabul durumu: emülatör entegrasyonu geçti; gerçek iPhone akışı ve sunum performansı kabulü bekliyor.** Kaynak bütünlüğü, 231 host kontrolü, gerçek Android'deki 13 kontrollü entegrasyon senaryosu, manuel TV arayüzü ve yerel decoder/EGL çıktısı doğrulandı. Alıcıyla gerçek iPhone ekran/ses oturumu kurulması, süreklilik, durdurma/yeniden bağlanma, ses-görüntü senkronu ve performans henüz kabul edilmedi. Uygulama nihai ürün olarak işaretlenmedi.

Xiaomi TV/ARM cihaz testi, ARM ve release dağıtımı, mağaza hazırlığı, korumalı medya ve nihai fiziksel kabul bu aşamada yapılmadı. Son aşamaya geçmeden gerçek iPhone ile görüntü/ses sürekliliği, durdurma ve yeniden bağlanma emülatörde doğrulanmalıdır. Xiaomi TV testi kullanıcının belirlediği nihai kabul aşamasında kalır.
