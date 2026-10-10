# Yayın profilleri ve decoder toparlama — 9 Eylül 2026

## Yapılan değişiklikler

- Ayarlar / Bağlantı altında Yayın önceliği seçimi: Özel, Dengeli, Görüntü kalitesi, Düşük gecikme, Kararlılık. Türkçe ve İngilizce etiketler var.
- Mevcut kullanıcılarda Özel varsayılandır; kaydedilmiş gelişmiş ayarlar korunur. Profil seçimi taslak mekanizmasını kullanır, mevcut yayını yeniden başlatmaz. Uygulama alıcı yeniden başlatıldığında gerçekleşir.
- Dengeli: 1080p/30, 180 ms eski-kare eşiği. Kalite: 1080p/30, 250 ms. Düşük gecikme: 720p/en fazla 60, 100 ms. Kararlılık: 720p/30, 200 ms. Emülatörde önceki 30 FPS sınırı korunur. Dikey yön için boyutlar çevrilir, decoder kapasitesiyle sınırlanır.
- Bunlar ilan edilen sınırlar ve çözülmüş kare sunumu tercihidir. Göndericiyi kesin FPS/bitrate üretmeye zorlamaz. Bağlantı sırasında otomatik çözünürlük müzakeresi veya YouTube ABR ayarı eklenmedi; arayüz bunu açıklar.
- Aynı decoder çıkış boşaltmasında birden fazla kare hazırsa eski çözülmüş resimler yüzeye gönderilmez, son resim sunulur. Sıkıştırılmış referans kareler atılmaz. Zamanlanmış çıktı modu bu birleştirmeyi kullanmaz.
- Geçerli duvar saati PTS'si yoksa decoder içinde geçirilen süre monoton saatle ölçülür. Gözlemler 256 kayıtla sınırlıdır; tekrarlanan/sıfır PTS ve decoder değişimi ele alınır. Ses zamanlaması ve paket PTS değiştirilmedi.
- Günlüklere decoderResidenceMs, coalescedOutputs, videoPtsZero ve clockMappingValid eklendi. Residence, ağdaki bekleme veya telefon-ekran toplam gecikmesi değildir. NTP eşlemesinin temel nedeni henüz düzeltilmiş sayılmaz.

## Kontrollü yerel karşılaştırma

Önceki kurulu APK SHA-256: `3758cc5c09255bb577be42ed386ed9a77692edfa52d9d4f40d419fe6ea956fdc`. Yeni APK: `ab12bf259e8fdfebc2949683bc0ba2f23cc3ce0dd81f2f072510f6b70dd64f8c`.

Aynı emülatör, aynı H.264 Goldfish decoder, aynı sentetik 90 kare/30 FPS yükü. Hedef 3000 ms.

| Ölçüm | Önceki APK | Yeni APK |
|---|---|---|
| 3 turun besleme süreleri | 8130 / 8230 / 8230 ms | 5902 / 6249 / 6300 ms |
| Besleme medyanı | 68 / 63 / 68 ms | 43 / 49 / 52 ms |
| Besleme p95 | 275 / 328 / 266 ms | 229 / 190 / 230 ms |
| Testin gözlediği RGB kareleri | 40 / 39 / 39 | 33 / 35 / 37 |
| Sıkıştırılmış giriş karesi düşmesi | 0 / 0 / 0 | 0 / 0 / 0 |

Yerel besleme süresi iyileşti; bazı çözülmüş ara resimler gösterilmedi. **İki sürüm de 3000 ms hedefini karşılamadı.** Tek önce/sonra serisi host yükünden etkilenebilir; gerçek iPhone gecikmesi veya eski en hızlı sürüme dönüş kanıtı değildir. Kullanıcının en baştaki hızlı APK kimliği henüz kesin eşleştirilmedi.

## Doğrulama ve sınırlar

- Geçti: x86_64 debug + test APK derlemesi (`build.log`).
- Geçti: profil sınırları, özel ayar koruma hesapları, dikey boyut, emülatör tavanı, sıfır/aynı PTS, nesil temizliği ve kayıt sınırı host testleri (`host-checks.log`).
- Geçti: gerçek MediaCodec/EGL ile gözlem toparlama, 15 çözünürlük geçişi ve üç yeniden bağlantı (`freeze-regressions.log`). Bu bir 30 FPS performans kabulü değildir.
- Geçti: gerçek Türkçe profil menüsü; Dengeli taslağını kaydetme/geri alma, Düşük gecikme profilini uygulama ve Özel'e geri dönme. Native başlangıç günlüğünde responsive=1280×720/30, ardından custom=1920×1080/30 doğrulandı (`profile-activation.log`). Son seçim Özel; ileri ayarlar değişmedi.
- Profil UI görüntüsü `profiles.png` ve metinleri `profiles-ui.json`. Eşleştirme anahtar SHA-256 öncekiyle aynı.
- Tam Android UI suite bu tur yeniden çalıştırılmadı; profil menüsünün klavyeyle uçtan uca ve büyük yazıyla tüm odak testi henüz yapılmadı.
- Gerçek iPhone, YouTube canlı/dublaj, ses kabulü ve uzun süreli performans testi yapılmadı. Ses decode hataları için yeni düzeltme bu pakette yok; önceki tek ses sahibi düzeni korunuyor.

APK ve günlükler `artifacts/adaptive-profiles-20260909/` altında. Emülatör kapatılmadı; kurulum `install -r` ile veriler korunarak yapıldı. PIN, eşleştirme, sabit portlar ve keşif kimliği değiştirilmedi.
