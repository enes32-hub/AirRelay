# Codec ve oturum toparlama — 8 Eylül 2026

Yeni APK: `artifacts/codec-recovery-20260908/aynatv-codec-recovery-x86_64-debug.apk`.
SHA-256: `3758cc5c09255bb577be42ed386ed9a77692edfa52d9d4f40d419fe6ea956fdc`.
Derlenip emulator-5584 üzerine veriler korunarak kuruldu. Gerçek iPhone kabulü henüz yapılmadı.

## Değişiklikler

- iOS canlı yayın isteğinde gözlenen 9.223372e12 saniye başlangıç değeri geçerli sarma konumu sayılmıyor; oynatıcının varsayılan başlangıcı kullanılıyor.
- Yeni mirroring taşıma kanalı başladığında önceki uzak video bırakılıyor. Eski kanaldaki yinelenen bildirim bunun için yeterli değil.
- Uzak video sırasında ekran sesi çıkışı duraklatılıyor; dönüşte tekrar başlatılıyor. Native ses decoder'ı ve zamanlama algoritması değişmedi.
- HTTP hata kodu ve periyodik video/ses ilerleme ölçümleri eklendi. URL, PIN ve anahtar kaydedilmiyor.

## Doğrulama

- Geçti: x86_64 debug ve test APK derlemesi (`build-final.log`).
- Geçti: başlangıç konumu için 7 host kontrolü (`start-position-tests.log`).
- Geçti: gerçek MediaCodec/EGL ile gözlem toparlama, 15 çözünürlük geçişi, 3 yeniden bağlantı (`android-freeze.log`). Bunlar yerel kodlanmış sentetik görüntülerdir, iPhone veya ses kabulü değildir.
- Tam arayüz regresyonu çalıştırılamadı: kullanıcıya ait uygulanmamış ayar taslağı nedeniyle koruma kontrolü testi durdurdu (`android-full.log`). Taslak silinmedi.
- Genişletilmiş native canlı başlangıç testi henüz çalıştırılmadı.
- Karşılaştırma: yazılımsal H.264 decoder, 90 kare/60 FPS hedefinde yaklaşık 3.1–3.2 saniye; Goldfish yaklaşık 2.2–2.5 saniye sürdü. Yazılımsal decoder daha yavaştı; üretim seçimi değiştirilmedi. Bu ölçüm uçtan uca gecikme değildir.

YouTube canlı yayın, Shorts dönüşü, kesintisiz ses ve yüksek kaliteli içerikte biriken gecikme gerçek telefonda yeniden sınanmalı. Bu sorunların tamamı çözülmüş sayılmıyor.

## AirScreen incelemesi sonrasında ek doğrulama

- Normal alıcı açılışı bekleyen kullanıcı ayar taslağını uyguladı. Test için taslak silinmedi.
- Native FCUP → Media3 testi geçti: HTTP durum 200 ve 0; ayrıca gözlenen 9223372000000 saniye başlangıç değeriyle ilk gerçek decoder karesi alındı (`native-fcup-live-start.log`). Bu, YouTube erişimi/DRM veya gerçek canlı yayın kabulü değildir.
- Yeniden çalıştırılan Android testinde 17 senaryo geçti; yeni mirroring kanalıyla uzak videodan dönüş, 10 ardışık yerel video, 2 ses parçası seçimi, 5 canlı playlist edinimi ve çözünürlük geçişleri geçti. Duyulabilir ses ve gerçek gönderici protokolü doğrulanmadı.
- Tam test toplam sonucu başarılı sayılmıyor: sonunda `CLEANUP FAILED: IllegalStateException` oluştu (`android-full-after-start.log`). Aynı zaman aralığında AirScreen yeniden ön plana açıldı; temizlik hatasının kesin nedeni bu kayıtla kanıtlanamaz. Sonrasında AirScreen durdurulup AynaTV normal açılışla tekrar başlatıldı. Bu elle toparlama bir ürün düzeltmesi/test başarısı olarak sayılmıyor.
- Ana iş parçacığında ölçülen 18 alıcı çağrısı için maksimum 30.709 ms; tüm başlangıç süresi veya kare takılması ölçümü değildir.
- Eşleştirme anahtar özeti önceki sürümle aynı, `hw.keyboard=yes`.

AirScreen 2.15.1 resmî Play Store'dan kurulup APK bileşenleri ve ayarları incelendi. Ayrıntılar `artifacts/airscreen-reference-20260908/README.md`. Üçüncü taraf kodu/ikilisi AynaTV'ye aktarılmadı; mevcut özellik kaldırılmadı.
