# AynaTV — başka ajana devir

ÖNEMLİ yeni teşhis: `docs/HOST_CONTENTION_2026-09-09.md`. Kullanıcı diğer projede de kasma bildirdi. Host CPU %98–99; WSL Ubuntu içindeki topas PID2163 yaklaşık 13 çekirdeklik CPU kullanıyor. GPU %5. Hiçbir süreç kapatılmadı. Önce bu ortak yük kontrol edilmeli; önce/sonra APK performans ölçümleri host yükünden etkilenmiş olabilir. Başka projenin topas/WSL işini izinsiz durdurmayın.

9 Eylül güncel paket: `docs/ADAPTIVE_PROFILES_2026-09-09.md`; APK SHA-256 `ab12bf259e8fdfebc2949683bc0ba2f23cc3ce0dd81f2f072510f6b70dd64f8c`. Profiller, decoder çıkış birleştirme ve monoton residence ölçümü eklendi. Yerel 90 kare testi yaklaşık 8.2 saniyeden 6.2 saniyeye iyileşti ama 3 saniyelik hedefi karşılamadı. Kasma çözülmüş değildir; NTP temel nedeni ve ses decode hataları açıktır. Varsayılan mevcut kullanıcı için Özel, kalite değiştirilmedi. AynaTV yeniden hazır; emülatör kapatılmadı.

En güncel çalışma: `docs/CODEC_RECOVERY_2026-09-08.md`. Kurulu APK SHA-256 `3758cc5c09255bb577be42ed386ed9a77692edfa52d9d4f40d419fe6ea956fdc`. Yeni mirror kanalında uzak videoyu bırakma, tek ses çıkışı ve canlı başlangıç sentinel düzeltmesi içerir. AirScreen karşılaştırması `artifacts/airscreen-reference-20260908/README.md`; resmî mağazadan kurulu, test sonunda durduruldu. Gerçek iPhone kabulü açık; Android senaryoları geçti ama tam suite temizlik hatasıyla sonuçlandı. Önceki APK'yı güncel sanmayın.

En son gecikme/408 çalışması: `docs/LATENCY_HLS_FIX_2026-09-08.md` ve `artifacts/latency-408-20260908/`. Kullanıcı önceki APK'da geçişlerin düzeldiğini doğruladı, fakat gecikme ve 408 bildirdi. Yeni emülatör profili 1080p/30 FPS; kaydedilmiş TV 60 FPS tercihi korunur. Yeni gerçek iPhone testi beklenir.

8 Eylül 2026 donma/yeniden bağlantı düzeltmesi: en son kaynak değişiklikleri `docs/FREEZE_RECONNECT_FIX_2026-09-08.md` altında açıklanır; yeni APK ve testler `artifacts/freeze-fix-20260908/` altındadır. Önceki `transition-fix` APK'sı bu düzeltmeleri içermez. Yeni sürümün gerçek iPhone tekrar testi beklenir.

8 Eylül 2026 video geçişi çalışması: güncel kod, kabul sınırı ve test kayıtları için `docs/PLAYBACK_TRANSITIONS_2026-09-08.md` ve `artifacts/transition-fix/delivery.json` dosyalarını okuyun. Bu sürüm PIN düzeltmesini de içerir. Gerçek iPhone kabulü tamamlanmış değildir; eski veya yerel test raporlarından böyle bir sonuç çıkarmayın.

8 Eylül 2026 son düzeltme: PIN penceresi kontrol soketi kapanınca artık silinmiyor. Güncel kurulu APK ve testler için `docs/PIN_LIFETIME_FIX_2026-09-08.md` dosyasını okuyun; önceki Revizyon 2 APK'sı bu düzeltmeyi içermez.

Bu kaynak paketi 7 Eylül 2026 tarihinde çalışma klasörünün mevcut halinden hazırlanmıştır. Önce bu notu, `HEDEF.md`, ana şartnameyi ve `docs/IPHONE14_PROTOCOL_FINDINGS.md` dosyasını okuyun. Bazı eski belgeler entegrasyondan önceki aşamayı anlatır.

## Kullanıcının hedefi ve tercihleri

- Uygulama yalnız Android TV alıcısıdır. iPhone 14 Pro'nun sistem Ekran Yansıtma menüsünden görüntü ve ses almalıdır; iPhone'a yardımcı uygulama kurulmaz.
- Nihai cihaz Xiaomi TV Box 4K'dır. Geliştirme ve ara testler emülatörde yapılır; fiziksel TV testi nihai kabule bırakılmıştır.
- Ekran yansıtma ve oyunlar canlıdır; bunlara süre/sarma çubuğu eklenmez. 8 Eylül güncel istek: ayrı ve sarılabilir video oynatımında süre çubuğu ve ileri/geri sarma bulunmalı; bağlantı sürerken videolar arasında ana sayfa kendiliğinden açılmamalı.
- Bu bilgisayardaki projeye ayrılan emülatör `aynatv_tv_api36`, son bilinen ADB seri numarası `emulator-5584` idi. Güncel cihaz durumunu kontrol edin; diğer projelerin emülatörlerini kullanmayın veya kapatmayın.
- Kullanıcı ücretli SDK istemiyor. Mevcut üçüncü taraf kaynakların lisans ve bildirimlerini koruyun.
- 8 Eylül 2026 güncel tercih: AynaTV emülatöründe fiziksel klavye desteği her açılışta açık olmalı (`hw.keyboard=yes`). Önceki `no` tercihi geçersizdir. Normal APK güncellemelerinde emülatörü kapatmayın; yalnız donanım ayarı gibi gerekli durumlarda yeniden başlatın.

## Son doğrulanan geliştirme durumu (1 Eylül 2026)

- Kotlin/Compose Android TV uygulaması ve JNI üzerinden UxPlay çekirdeği ana `app` modülündedir. Paket kimliği `com.aynatv.receiver`; Activity `io.github.jqssun.airplay.MainActivity`.
- Windows üzerinde iPhone ile protokol denemeleri yapıldı. Android emülatörde kontrol/PIN bağlantısı kuruldu, fakat görüntü paketleri gelmedi.
- Kaynak incelemesi, UxPlay'in medya portlarını dinamik seçtiğini ve sabit port yönlendiren Windows köprüsüyle uyuşmadığını gösterdi. `native_bridge.cpp` başlangıcına TCP mirror=7100, UDP timing=7011, audio-control=6001 ve audio-data=6000 atamaları eklendi. RTSP varsayılanı 7000'dir.
- Bu değişiklik sonrasında APK derlendi, emülatöre kuruldu ve başlangıç logunda port ayarları doğrulandı. **Düzeltmeden sonra gerçek iPhone video/ses akışının çalıştığı henüz doğrulanmadı.** İlk iş bunu uçtan uca ölçmek olmalıdır. Özellikle emülatör NAT'ı üzerinden çift yönlü UDP zamanlama davranışını da inceleyin.
- Ekran yansıtma genel görünümünden `NowPlayingContent` ve `AudioMiniController` kaldırıldı; bağlantı bekleme metinleri ve canlı rozeti eklendi. Ayrı AirPlay `/play` yolu hâlâ vardır ve oradaki eski video taşıma kontrolleri ayrıca gözden geçirilmelidir.
- Önceki oturum notları ses tamponlama ve kare sunumu değişikliklerini içerir. Bunlar fiziksel TV kalite kabulü değildir.
- 44 Python unittest geçti. Bunlar kaynak envanteri, native biçim ve hazırlık kayıt denetimleridir; canlı görüntü/ses veya UI kabul testleri değildir.
- Son yerel APK adı `aynatv-receiver-x86_64-live-ui-portfix-debug.apk`, SHA256 `867BDE2214D9AD60CECC8565DB265325BDB00542A56495A56F85048DEDA0A32D` idi. APK bu kaynak ZIP'ine dahil edilmez.
- Nihai ürün tamamlanmış değildir; gerçek akış, toparlanma, senkron ve fiziksel TV kabulü açık işlerdir. 1 Eylül'de açık bırakılan süreçlerin bugün hâlâ çalıştığını varsaymayın.

## Derleme ve araçlar

- `scripts/build_receiver.ps1` Windows'ta kaynakları ASCII yola kopyalayarak native derlemeyi yürütür. Son kullanılan derleme yolu `C:\AynaTVBuild6` idi; bu harici klasör ZIP içinde değildir.
- `local.properties` makineye özgü olduğu için çıkarılmıştır. Yeni makinede SDK konumunu tanımlayan bu dosyayı oluşturun; betik proje kökünde bu dosyayı bekler. SDK/NDK/CMake/Python/MSYS2 gereksinimleri için `docs/BUILD_ENVIRONMENT.md`, Gradle dosyaları ve derleme betiğini okuyun.
- `python -m unittest discover -s tests -v` hazırlık testlerini çalıştırır; pytest gerekli değildir.
- `tools/emulator-airplay-bridge/bridge.py` yalnız emülatör test köprüsüdür. Gerçek TV uygulamasının parçası değildir. LAN adresi, iPhone adresi, emülatör konsol portu ve alıcının güncel public key/device ID değerleri ortamdan tekrar belirlenmelidir. Son laboratuvar ilan adı `AynaTV Dev Direct` idi.

## Paket kapsamı

Ana uygulama ve gömülü üçüncü taraf kaynaklar, Gradle wrapper ve yapılandırmaları, prototip, testler, betikler, Windows araçlarının kaynakları, şartname ve lisans/SBOM belgeleri dahildir. `artifacts` altındaki kaynak betikleri ve JSON raporları da korunur.

Git metaverisi, derleme/IDE önbellekleri, araştırma için indirilen kopyalar, derlenmiş APK/DLL/EXE'ler, Windows taşınabilir çalışma zamanı, ham oturum logları, yerel ayarlar ve eşleştirme/imzalama anahtarları dahil değildir. Dosya listesi ve içerik SHA256 değerleri ZIP kökündeki `SOURCE_MANIFEST.json` içindedir.
