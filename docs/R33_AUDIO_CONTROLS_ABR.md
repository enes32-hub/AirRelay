# R33 sesli kayıt, ilerleme ve kumanda

## Değişiklikler
- Android 10+ üzerinde MediaProjection ile sadece uygulamanın playback sesi yakalanır; mikrofon kullanılmaz. RECORD_AUDIO izni ve sistem ekran yakalama onayı gerekir.
- Ses 48 kHz stereo AAC olarak kodlanır, video ile zaman farkı korunarak MP4'e akış halinde birleştirilir. Bütün kayıt RAM'e alınmaz. Birleştirme için disk alanı ayrılır.
- Ses eklenemediğinde video korunur ve sessiz kaydedildiğine ilişkin hata mesajı gösterilir. Android 10 öncesi kayıt sessizdir.
- Doğrudan yayındaki çubuk saniyelik konuma yuvarlanır; oynatma motorunun 250 ms durum bildirimi ve zamanlaması değişmez.
- Yerel kayıt oynatıcısının süre çubuğu en az 1000 ms aralıkla güncellenir. Gizli panel OK veya yön tuşunun ilk basışında açılır; aynı basış videoyu yanlışlıkla durdurmaz/başlatmaz.

## Doğrulama
- TV üzerinde gerçek ekran yakalama + AAC kayıt üretildi. AAC çözüldü, üretilen 440 Hz test sesinin sinyali doğrulandı (peak 8843). MP4 metadata ve dışa aktarma özeti geçti. Gerçek iPhone A/V senkronu kullanıcı kabulünü bekliyor.
- Yerel HTTP HLS, 720p/1080p varyantlar, gerçek decoder ve aktarım hızı sınırlamasıyla otomatik 1080 -> 720 -> 1080 testi geçti. Manuel kalite seçimi kullanılmadı; bu özellik YouTube'a özgü değil. Tek varyant sunan kaynakta yeni çözünürlük üretmez.
- Kayıtlar sekmesine giriş testinin ilk koşusu ekran hazır olmadan etiket seçimi nedeniyle zaman aşımına uğradı. Ekran beklemesi eklenerek tekrar test geçti; gizli kontroller OK ile açıldı, duraklatılmış video yanlışlıkla başlamadı.
- Telefon ekranı kapalı doğrudan oynatma: kullanıcı çalıştığını doğruladı.

Kara liste geliştirme planı: DEVICE_BLOCKLIST_PLAN.md. Bu sürümde ek kara liste arayüzü uygulanmadı; mevcut eşleşmiş cihaz engelleme korunur.

## Son teslim durumu
APK: artifacts/releases/AirRelay-R33-audio-controls.apk
SHA-256: 21CD1DDFFD6B2C9B2EFE6BD024EFB6AD67630C1170E8A5F0058E78DB59198146
R33 / versionCode 68. ARM32/ARM64 build ve APK imzası geçti.

Sesli kayıt/yerel kontroller/otomatik kalite testlerinin geçtiği ilk R33 TV'ye kuruldu. Ardından ses encoder EOS kapanışına ek zaman aşımı ve monoton timestamp düzeltmesi derlendi. Bu son APK yüklenirken TV ADB offline oldu; tekrar deneme de başarısız. Son paket cihazda test edilmedi/kurulmadı. Önceki başarılı testler son yamaya aitmiş gibi raporlanmaz.

Bağlantı geri geldiğinde:
1. Son APK'yı install -r ile kayıtları koruyarak kur.
2. record-controls-audio testini son APK üstünde çalıştır.
3. Test için verilen RECORD_AUDIO iznini geri al (pm revoke); kullanıcı sonraki sesli kayıtta izni kendi seçsin.
4. com.aynatv.receiver.test test paketini kaldır ve uygulamayı aç.

Kanıtlar: artifacts/r33-audio-test.txt, artifacts/r33-controls-retest.txt, artifacts/r33-adaptive-test.txt, artifacts/r33-final-build.log. Hatalı ilk kontrol testi de artifacts/r33-controls-test.txt içinde korunur.
