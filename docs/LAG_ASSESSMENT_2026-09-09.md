# AynaTV kasma durum tespiti — 9 Eylül 2026

Bu çalışma yalnız tanılama içerir. Kaynak kodu, APK, kalite, emülatör veya alıcı ayarları değiştirilmedi; uygulama/emülatör yeniden başlatılmadı. emulator-5582'ye işlem yapılmadı.

## Ölçülen durum

emulator-5584 üzerinde AynaTV pid 21620 çalışıyor, AirScreen süreci yok. Android cihaz saati incelemede 8 Eylül 22:08 GMT; aşağıdaki oturum zamanları Android logcat saatidir, eski bir kabul raporundan alınmadı.

- 22:05:46–22:06:01 dikey 498×1080 akışında kare giriş sayacı 12–13 civarı.
- 22:06:03 sonrasında 1920×1080, H.264 c2.goldfish.h264.decoder aktif. 22:06:06–22:07:02 aralığında giriş sayacı 7–11 civarı; videoFeedMs örnekleri 133, 136, 151, 176, 149 ve 135 ms.
- Bu `fps`, VideoRenderer._updateStats içindeki giriş sayacıdır; panelde sunulan FPS değildir. videoFeedMs da yalnız donanım decoder süresi değildir: kilit, besleme, çıkış boşaltma ve ilgili beklemeleri kapsar. Yine de 30 FPS'nin 33 ms kare bütçesine göre ciddi geri basınç gösterir.
- Aynı oturumda audioUnderruns 12, audioDecodeErrors 20 ve audioXruns 10'a yükselmiş. Dikey ekrana dönüldükten sonra xrun 16'ya kadar ilerlemiş. Ses sorunu yalnız görüntüyle senkron algısı değildir; gerçek açlık/çözme/çıkış sorunları kaydedilmiş.
- İncelenen sağlık kayıtlarında videoClockAgeMs=-1 ve lateOutputs=0. -1 değeri kodda geçerli sayılan -10 saniye…60 saniye aralığının dışını maskeler; kesin gecikme miktarı değildir. Geç kalmış kareleri eleme yolu bu oturumda etkili görünmüyor.
- 3 saniyelik Windows örneğinde iki qemu süreci tek çekirdek ölçeğinde yaklaşık %188.5 ve %14.1 CPU kullanmış; çalışma kümeleri yaklaşık 2496 MB ve 958 MB. Bunlar toplam makine CPU yüzdesi değildir. Süreçlerin hangi AVD'ye ait olduğu CIM sorgusu yetki hatası nedeniyle eşleştirilemedi. İki emülatörün varlığı tek başına regresyonun nedeni sayılmaz.
- Android dumpsys cpuinfo çıktısı geçmiş bir 300 saniyelik pencereydi; anlık yük diye yorumlanmadı. Windows toplam/boş RAM sorgusu yetki hatası verdi; RAM yetersizliği kanıtlanmadı.

## Koddan doğrulanan mekanizmalar ve şüpheliler

1. Native RTP okuma yolu video_process callback'ini eşzamanlı çağırıyor; Kotlin aynı callback içinde feedFrame yapıyor. Bu besleme geciktiğinde ağ okuma yolu da bekleyebilir. Dolayısıyla kullanıcıdaki “kapatınca eski görüntüleri oynamaya devam etme” şikâyetiyle uyumlu bir geri basınç yolu var. TCP kuyruğunun gerçek boyutu henüz ölçülmedi.
2. Geç kalmış kare eleme, geçerli yerel duvar saati PTS'sine bağlı. raop_ntp_convert_remote_time, zaman eşleme yanıtı alınmadığında 0 döndürüyor. Mevcut -1 ölçümü tek başına PTS'nin sıfır olduğunu kanıtlamaz; eksik NTP eşleme, yanlış ofset veya büyük saat farkı ayrıştırılmalı. Bu koşul çözülmeden mevcut geç kare mantığına güvenilmemeli.
3. Donmayı önlemek için Goldfish boyut değişiminde decoder yeniden kuruluyor; her yeni decoder'da SurfaceTexture/EGL giriş hattı da yenileniyor. 22:06:01.895 boyut bildirimi → 22:06:03.053 codec start logu yaklaşık 1.16 saniye. 22:07:06.655 → 22:07:07.510 yaklaşık 0.86 saniye. Bunlar geçiş maliyetidir; sabit çözünürlükteki sürekli yavaşlığın tamamını açıklamaz.
4. Son değişiklikler arasında emülatörde 30 FPS tavanı, adaptif ayırma sınırları, daha sık çıkış boşaltma ve saat tabanlı geç kare eleme var. Bunların bir arada kalması tek bir değişikliği suçlamak için yeterli değil. `renderer-changes.diff`, donma düzeltmesi öncesindeki mevcut yedekle farkı kaydeder; kullanıcının “en başta hızlı” dediği sürümün kesin kaynak/APK kimliği henüz eşleştirilmedi.
5. AirScreen Auto bu cihazda 720p ilan ederken AynaTV 1080p kullanıyor. Bu karşılaştırma farkıdır, AynaTV'nin kendi önceki sürümüne göre gerilemesini açıklayan bir kanıt değildir.

## Sonuç ve önerilen düzeltme sırası

Kasma ölçümle doğrulandı. “Sadece internet” veya “sadece emülatör” demek için kanıt yok. Öncelikli inceleme alanı **senkron decoder beslemesindeki geri basınç + kullanılamayan kare zamanlaması**; ses decode hataları ayrı izlenmeli.

Sonraki çalışma: (1) yerel/uzak PTS ve NTP eşleme geçerliliğini ayırmak, (2) feedFrame içindeki bekleme/kilit/decoder/çıkış sürelerini ayrı ölçmek, (3) eski hızlı APK kimliğini sabitleyip aynı içerik ve çözünürlükte karşılaştırmak, (4) yalnız kanıtlanan gerilemeyi geri almak. Başarılı video geçişlerini topluca geri almak veya kaliteyi sessizce düşürmek önerilmiyor.

Bu tespitte sorunun tamamı çözülmedi ve tek bir suçlu commit doğrulanmadı. Kullanıcının talebi gereği düzeltme uygulanmadı.

Kanıtlar: `artifacts/lag-assessment-20260909/filtered.log`, `timing-audio.log`, `host-cpu-sample.json`, `renderer-changes.diff`.
