# AirScreen teknik saha incelemesi

## Kapsam ve kanıt sınırı

İncelenen uygulama Google Play'den kurulan **AirScreen 2.15.1 (568)**, paket `com.ionitech.airscreen`, x86_64 sürümüdür. Cihaz mevcut `aynatv_tv_api36 / emulator-5584`, Android API 36'dır. AynaTV silinmedi ve emülatör yeniden başlatılmadı. Bu çalışma sırasında gerçek iPhone bağlı değildi; ekran yansıtma gecikmesi, ses kalitesi, YouTube canlı yayın veya oyun kabul testi yapılmadı.

Yöntem: gerçek uygulama menülerinin incelenmesi, kurulu APK/split envanteri, kaynak metinleri ve kitaplık işaretlerinin okunması, emülatöre geçici ADB yönlendirmesiyle AirPlay `/info` yanıtının alınması. Özel uygulama verilerine veya dışa kapalı bileşenlere erişim zorlanmadı; DRM/lisans kontrolleri aşılmadı. APK ikilileri AynaTV'ye aktarılmadı.

Kanıtlar `artifacts/airscreen-reference-20260908/` altında. Aynı dizindeki Python betikleri paket envanteri ve herkese açık protokol sorgusunu tekrar üretir. İşaret veya seçenek bulunması, o yolun gerçek yayında kullanıldığı anlamına gelmez.

## En önemli bulgu: Auto, bu emülatörde 720p ilan ediyor

| Gözlem | Sonuç | Ne anlama gelmez? |
|---|---|---|
| Çözünürlük menüsü | Auto, 2560×1440*, 1920×1080*, 1280×720, 800×480, Customize | Bütün cihazlarda aynı seçenekler olduğu anlamına gelmez |
| Özel 1920×1080 etkin olduğunda `/info` | width=1920, height=1080, refreshRate=60 | Gerçek 60 FPS veya düşük gecikme ölçümü değildir |
| Auto geri yüklendikten sonra `/info` | **width=1280, height=720, refreshRate=60** | Gelen her akışın kesin 720p olacağını kanıtlamaz |
| Yıldızlı çözünürlük uyarısı | “It may result in a black screen or lag” | Sorunun yalnız çözünürlükten kaynaklandığını kanıtlamaz |
| Performans değerlendirmesi | İlk incelemede Average/3.5; sonraki açılışta Poor | Kararlı bir bağımsız benchmark değildir |

Özel çözünürlük ekranı açıldığında 1920×1080 seçimi etkinleşti; inceleme sonunda **Auto geri yüklendi ve protokol yanıtı tekrar okundu**. Son ham yanıt `public-capabilities-5000.json`, menü kanıtları `resolution-ui.json` ve `resolution-options.json`.

1080p kare, 720p karenin 2.25 katı piksel içerir. Bu nedenle AynaTV'nin 1080p profiliyle AirScreen'in Auto/720p profilini karşılaştırıp farkın yalnız codec'ten geldiğini söylemek doğru olmaz. Öte yandan bu oran tek başına CPU yükü, sıkıştırılmış bant genişliği veya gecikme oranı değildir. Sonraki karşılaştırma aynı çözünürlük ve benzer gerçek FPS ile yapılmalıdır.

Mağaza ve üretici sitesi UHD/4K desteği duyuruyor. Bu emülatörde incelenen menüde 3840×2160 hazır seçeneği görülmedi; özel çözünürlük alanı var. **4K başarılı oynatma doğrulanmadı.**

## Codec ve oynatıcı yapısı

| Bileşen/işaret | Doğrudan kanıt | Çıkarım sınırı |
|---|---|---|
| IJK oynatıcı | libijkplayer.so, libijksdl.so; tv.danmaku.ijk sınıf adları | Bütün aktarım türlerinin IJK kullandığı kanıtlanmadı |
| FFmpeg | libijkffmpeg.so; avcodec/avformat/swresample/swscale/avfilter build seçenekleri | Tüm FFmpeg biçimlerinin uygulamada erişilebilir olduğu söylenemez |
| H.264 ve HEVC | IJK MediaCodec AVC/HEVC seçenekleri ve FFmpeg H.264/HEVC kaynak işaretleri | **AirPlay mirroring HEVC kullanıyor** denemez |
| Ses | AAC kitaplık işaretleri; FFmpeg Opus/Vorbis işaretleri; `/info` audioFormats | Gerçek AAC/ALAC formatı, kanal sayısı veya örnekleme hızı akış olmadan belirlenmedi |
| Android MediaCodec | Decoder seçimi, input/output, flush, resolution-change işaretleri | Donanım decoder'ının gerçekten seçildiği ancak akış sırasında doğrulanabilir |
| Chromium/Crosswalk | libxwalk.so ve Chromium MediaCodecBridge sınıfları | Ana AirPlay görüntüsünün tarayıcıdan çizildiği anlamına gelmez |
| ExoPlayer/Media3 | Paket içinde ExoPlayer/Media3 MediaCodec işaretleri | Reklam/bağımlılık da olabilir; ana alıcının Exo kullandığı kanıtlanmadı |
| Keşif | libmdns.so; AirPlay ayarı | Keşfin bütün ayrıntıları henüz karşılaştırılmadı |

FFmpeg build metninde `ijkplayer-0.8.8` derleme yolu ve `Lavf/57.83.100` işareti var. Bunlar bileşen geçmişine işaret eder; uygulamanın tamamına kesin sürüm etiketi değildir. `--disable-hwaccels` FFmpeg derleme seçeneği görülüyor; buna rağmen IJK Android MediaCodec yolu ayrıca bulunuyor. Dolayısıyla bu bayraktan “AirScreen donanım hızlandırma kullanmıyor” sonucu çıkarılamaz.

FFmpeg içinde LGPL 2.1 veya sonrası lisans işareti var. Bu AirScreen'in tamamının açık kaynak olduğu, ücretsiz kopyalanabileceği veya Apple'dan lisans alıp almadığı hakkında kanıt değildir.

## Uygulama ayarları ve gecikme yönetimine ilişkin bulgular

**Ayrı hızlandırma ayarları:** Gerçek Hardware acceleration ekranında Screen mirroring ON, Video playback OFF görüldü. Telefon ekranı ile uzak video aynı ayara mahkûm edilmiyor. Kaynak metinlerinde video takıldığında donanım hızlandırmayı açmayı öneren uyarı da bulunuyor. AynaTV'de bir yolu düzeltmek için çalışan diğer yolun decoder'ını değiştirmemek gerekir.

**Keep playing:** Menü, gönderici uygulamadan çıkıldıktan sonra AirPlay medyasına devam etmeyi ayrı bir seçenek olarak sunuyor. Bu, bağlantının ömrü ile tek videonun ömrünün farklı olduğunu gösteren kullanıcı arayüzü kanıtıdır; gerçek geçiş algoritması görülmedi. Kullanıcımızın istediği davranış ise ekran yansıtmasına dönüş geldiğinde eski videonun bırakılmasıdır.

**Birikmiş kare tanılaması:** APK metinlerinde “Unprocessed Buffer … frame(s)” ve ağ hızı için Kbps göstergeleri var. IJK içinde `framedrop`, `packet-buffering`, `mediacodec-handle-resolution-change`, `mediacodec-sync`, `start-on-prepared` işaretleri var. Bunların sayısal değerleri, aktif buffer üst sınırı veya toparlama eşikleri doğrulanmadı. AynaTV'ye rastgele aynı isimli parametreler eklemek çözüm sayılmaz; kullanılan oynatıcı sözleşmeleri farklıdır.

**Akıllı video kalitesi:** Kaynak metinleri, cihaz performansına göre uygun video kalitesi seçimini tanımlıyor. Pro satın alma veya ödüllü reklamla açılmasına ilişkin metinler de var; menüde ADVANCED / Locked görüldü. Satın alma yapılmadı, ücretli özellik açılmadı; algoritma ve gerçek kalite değişimi test edilmedi. Ekran kaydı için çözünürlük/kalite seçenekleri ayrıca bulunuyor; bunlar mirroring giriş kalitesiyle karıştırılmamalı.

**Diğer seçenekler:** AirPlay, Cast, Miracast, DLNA, NFS, tam ekran, zorunlu döndürme, arka plan servisi, dil ve proxy menüleri görüldü. Audio passthrough metni de pakette bulunuyor. TV odaklı AynaTV için hepsini eklemek gerekli değildir. Miracast menüsünün bulunması bu Android sürümünde çalıştığını göstermez.

## Protokol yanıtı

Emülatörde TCP 5000 üzerinden `/info` isteği 200 ve plist döndürdü. Auto ayarında 1280×720/60 ilanının yanı sıra model `AppleTV3,2`, sourceVersion `220.68`, vv=2, keepAliveLowPower=1 ve keepAliveSendStatsAsBody=1 görüldü. Bunlar emüle edilen protokol kimlikleridir; fiziksel Apple TV veya Apple sertifikası kanıtı değildir.

audioFormats bit alanı 67108860, akış tipleri 100/101 olarak bildiriliyor. Güvenilir bir alan eşlemesi olmadan bunlardan belirli codec/kanal kombinasyonları çıkarılmadı. audioLatencies içindeki sıfır/false değer, uçtan uca ses gecikmesinin sıfır olduğu anlamına gelmez.

TCP 7000'de `/server-info` ve `/info` 404; TCP 5000'de `/server-info` 422 döndü. Bunlar belgelenmiş olumsuz gözlemlerdir; bağlantı başarısı diye raporlanmadı. Yalnız geçici localhost ADB yönlendirmeleri kullanılıp kaldırıldı. Mevcut AynaTV LAN köprüsü AirScreen kimliklerine çevrilmedi.

## AynaTV için uygulanabilir sonuçlar

1. **Önce eşit koşullu karşılaştırma:** Auto/720p AirScreen ile 1080p AynaTV karşılaştırması yapılmamalı. 720p ve 1080p ayrı test edilmeli; çözünürlük, gerçek çıktı FPS, kare yaşı ve ses buffer'ı kaydedilmeli. Kalite sessizce düşürülmemeli.
2. **Ekran ve uzak video yollarını ayrı tut:** Mevcut MediaCodec ekran yolu ve uzak oynatıcı korunmalı. IJK'ye toplu geçiş için kanıt yok; yeni ses/DRM/playlist regresyonları doğurabilir.
3. **Gecikmeyi aşama aşama ölç:** Ağ gelişi → decoder girişi → decoder çıkışı → yüzey sunumu ayrı izlenmeli. Birikme ağdaysa yalnız yüzeyde kare atlamak yeterli olmaz; H.264 referans kareleri rastgele düşürülmemeli.
4. **Tek ses sahibi:** Uzak video ve mirroring ses çıkışları aynı anda çalışmamalı. Mevcut düzeltmenin gerçek iPhone kabulü yapılmalı; format değişimi/yeniden başlama ayrıca ölçülmeli.
5. **Canlı yayın ve ses dili:** Bu inceleme YouTube canlı akışını veya dublaj seçim protokolünü çözmüş sayılmaz. İstek, playlist, segment ve HTTP durumları ayrı incelenmeli; AirScreen'in bu senaryoda başarısı henüz ölçülmedi.
6. **Görünür tanılama, sade normal ekran:** Ayrıntılı metrikler günlük/yardım ekranında bulunmalı; kullanıcıya normal yayında sürekli teknik kutu gösterilmemeli.

Bu saha çalışmasında AynaTV'nin codec veya kalite ayarı değiştirilmedi. Kullanıcının istediği mevcut özellikler kaldırılmadı.

## Kullanıcı döndüğünde karşılaştırma testi

Aynı iPhone, aynı ağ ve aynı emülatörde alıcılar sırayla çalıştırılmalı. Aynı anda iki alıcı başlatılmamalı. Her birinde: telefon ekranı; Fotoğraflar'da üç video; ekran dönüşü; YouTube iki video; Shorts → Safari; canlı yayın; ses parçası değişimi; yüksek hareketli oyun; durdur/yeniden bağlan. İlk tur Auto, ikinci tur eşit 720p, üçüncü tur eşit 1080p olmalı. İlan edilen 60 Hz ile gerçek sunulan FPS ayrı kaydedilmeli.

Gecikme için telefon ve emülatör ekranını aynı kamera görüntüsünde ölçmek gerekir; APK metrikleri tek başına uçtan uca gecikme değildir. Özellikle video kapanışından sonra kalan son karenin süresi ve sesin toparlanması ölçülmeli.

Resmî kaynaklar: [Google Play](https://play.google.com/store/apps/details?id=com.ionitech.airscreen&hl=tr), [üretici sitesi](https://www.airscreen.app/). Üretici iddiaları ile yerel gözlemler yukarıda ayrılmıştır.
