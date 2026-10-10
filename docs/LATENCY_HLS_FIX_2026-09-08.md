# Gecikme ve YouTube hazırlama zaman aşımı

Kullanıcı, önceki düzeltmeden sonra videolar ve oyunlar arasındaki geçişlerin çalıştığını; buna karşılık yansıtmanın 3–4 saniye geciktiğini ve YouTube'un 408 verdiğini bildirdi. Kararan oyun bu çalışmanın dışında tutuldu. Bu rapor önceki başarısız denemeleri değiştirmez.

## Değişiklikler

- Telefonda görülen `FCUP response failed: status=0` reddi düzeltildi. FCUP'nin gönderici tarafından çözülen kaynak yanıtında HTTP durumu sıfır veya eksik olabilir. Eşleşen istek kimliği ve geçerli HLS başlangıcı bulunan gövde bu durumda kabul edilir. Gerçek HTTP hata kodları, boş/geçersiz gövde, gömülü NUL ve aşırı büyük gövde reddedilir. Eski/iptal edilmiş istek yanıtlarının engellenmesi korundu.
- Android yerel HLS oynatıcısının hazır olmasını sunucu iş parçacığında beklemek, aynı sunucudan playlist yüklemesini engelliyordu. Yalnız yerel `/master.m3u8` oynatmasında bu 10 saniyelik bekleme kaldırıldı; gerçek hazır olma/duration bilgisi sonraki oynatma sorgularıyla iletilir. Dış doğrudan URL yolu değiştirilmedi.
- Her çizimdeki iki EGL boyut sorgusu kaldırıldı. Boyut, mevcut SurfaceView değişim bildirimi üzerinden güncellenir. Bu değişiklik tek başına performans testini düzeltmedi; böyle bir başarı iddiası yoktur.
- Goldfish decoder'ın önceki adaptif tampon yapılandırması geri getirildi, ancak çözünürlük değişiminde codec yine yeniden başlatılır. Önceki donma/bozuk kırpma düzeltmesi böylece korunur.
- Decoder girişini beklemeden önce çıkışlar boşaltılır; çıkışları kontrol etmeden 20 ms beklemek yerine 1 ms aralık kullanılır. Toplam yeniden deneme bütçeleri aynı kaldı.
- Yük altında yalnız geç kalmış **çözülmüş** karelerin gösterimi azaltılır; sıkıştırılmış referans kareler atılmaz. Yerel saate eşlenmiş PTS 180 ms–60 saniye gerideyse, toparlanırken en az 100 ms aralıkla görüntüye izin verilir. İlk kare ve uzun aradan gelen sabit görüntü engellenmez. Farklı saat tabanlı test verisi ve açık zamanlanmış gösterim modu bu kurala alınmaz. Ses motoru/zamanlaması değiştirilmedi.
- **Yalnız ranchu/goldfish emülatöründe 30 FPS tavanı** uygulanır. Çözünürlük korunur; kaydedilmiş 60 FPS tercihi ve gerçek TV davranışı değiştirilmez. Bu bir geliştirme profili ve açık bir kare hızı sınırlamasıdır; 60 FPS sorunu gizlenmiş sayılmaz.

## Test kanıtları

Tüm yeni kanıtlar `artifacts/latency-408-20260908/` altında tutulur.

- `native-filtered-before.txt`: gerçek telefon oturumunda üç ayrı `status=0` reddi.
- `fcup-test.log`: 11 durum/gövde kabul ve ret kontrolü geçti.
- `media-request-test.log`: eski, yinelenen, eksik, iptal edilen ve sıra dışı FCUP istek kimliği kontrolleri geçti.
- `native-smoke.log`: gerçek Android native yolu ilk testte oynatıcı hazır olma bekleyişine takıldı. Test başarısızdır.
- `native-smoke-fixed.log`: alıcı açılışı tamamlanmadan yapılan deneme bağlantı kapanmasıyla başarısızdır.
- `native-smoke-ready.log`: düzeltmeden sonra hem 200 hem 0 yanıtıyla iki playlist native katmanda işlendi; Media3 ilk görüntüsü gerçek yerel TS içeriğiyle doğrulandı. Test yalnız localhost sentetik göndericidir, YouTube/iPhone kabulü değildir. Test portları sonunda kaldırılır.
- `clocked-before.log`: 60 FPS hedefinde 90 karenin girişi 2757/2994/2813 ms sürdü; 1500 ms kaynak süresine yetişmedi.
- `throughput-after.log`, `clocked-after.log`, `clocked-allocation.log`, `clocked-poll.log`: ara denemeler; 60 FPS hedefini karşılamadılar. Bunlar çözülmüş performans kanıtı sayılmaz.
- `throughput-final-30fps.log`: 30 FPS hedefinde 90 karenin girişi 2969/2971/2996 ms sürdü; 3000 ms kaynak süresine yetişti ve giriş karesi düşmedi. Yerel ImageReader testi görüntüyü CPU'ya da okur; bu ölçüm gerçek iPhone uçtan uca gecikmesi veya panel FPS'si değildir.
- `transitions-final.log`: son APK'nın ilk kare toparlanması, 15 boyut/yüzey geçişi ve üç yeniden bağlantı kontrolünün kesin sonucu bu dosyadadır.

## Kabul sınırı

Son yüklü APK'da `native-smoke-final.log` iki FCUP durumunda da ilk görüntüyü doğruladı; `transitions-final.log` ilk kare toparlanması, 15 geçiş ve üç yeniden bağlantıda geçti. Yerel HLS testinden sonra görünüm yayın yüzeyinde kaldığı için test oturumlarını temizlemek üzere yalnız uygulama yeniden açıldı; emülatör yeniden başlatılmadı. Bu temizlik bir ürün düzeltmesi/test başarısı olarak sayılmaz. Son ekran “Bağlanmaya hazır”; APK ve eşleştirme anahtarı özeti `delivery.json` ile doğrulandı.

Güncel APK ile gerçek iPhone'da gecikme ve YouTube tekrar denenmelidir. Yerel native testte görüntü görmek, gerçek YouTube videosunun erişim ve ses parçalarını doğrulamaz. Kullanıcıdaki 3–4 saniyelik gecikmenin tamamen bittiği henüz ölçülmüş değildir. Emülatörün 60 FPS performansı açık sınırlamadır; Xiaomi TV testi yapılmadı. PIN, eşleştirme anahtarı ve keşif kimliği korunur; emülatör yeniden başlatılmaz.
