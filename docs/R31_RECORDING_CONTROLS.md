# R31 kayıt kontrolleri

## Değişiklikler
- Doğrudan videoda ana kontrol sırası ortalandı.
- Alt sıraya Kaydı başlat/Kaydı durdur ve Kayıtlar eklendi.
- Kayıtlar penceresi 720p/1080p tercihini saklar ve MP4 dışa aktarmayı sunar.
- Kayıt kullanıcının Android ekran yakalama onayıyla başlar. Etkin kayıt göstergesi görünür.
- Kayıt altyapısı şu anda görüntüyü kaydeder; ses kaydı yoktur. Korumalı içerik kaydedilemez.
- Yayın bitince veya uygulama arka plana gidince (PiP hariç) kayıt durur.

## Otomatik video kalitesi
Kalite menüsündeki Otomatik, Media3/ExoPlayer adaptif parça seçimine döner. Kaynak birden fazla desteklenen kalite sunarsa ağ hızına ve tampon durumuna göre seçim yapılır; manuel seçim bu davranışı sabit parçaya kilitler. YouTube ile sınırlı değildir. Tek kalite sunan kaynak için yeni kalite üretmez. Cihazın decoder yükünü izleyerek ek kalite sınırı koyan ayrı bir algoritma uygulanmadı.

## Güncel kapsam
- Çoklu AirPlay cihazı iptal edildi.
- Telefon ekranı kapalı oynatma işi ertelendi.
- Genel dayanıklılık testi ayrı geliştirme listesi maddesinden çıkarıldı; değişikliklere ilişkin doğrulama sürer.
- Giriş kontrolü mevcut.

## Doğrulama
- ARM32/ARM64 APK ve Android test paketi derlendi; APK imzası doğrulandı.
- Xiaomi TV: doğrudan mod ana sırası erişilebilirlik düğme sınırlarıyla ortalanmış doğrulandı; ilk odak Döndür üzerinde.
- Xiaomi TV: sistem izni ile gerçek 720p ve 1080p ekran kayıtları üretildi, kontrollerden durduruldu, MP4 süre/boyut bilgisi doğrulandı.
- MP4 dışa aktarma dosya özeti asılla eşleşti; yayın/alıcı durumu korundu.
- Yerel HLS ve gerçek Media3 decoder: 720p/1080p, otomatik kaliteye dönüş, Türkçe/İngilizce ses ve altyazı testleri geçti.
- Bunlar gerçek TV üstünde sentetik yayın durumu ve yerel test içeriğiyle çalıştırıldı; iPhone yayınının kayıt performansı ve YouTube kabulü kullanıcı testini bekler.
- R31 / versionCode 66 TV'ye temiz kuruldu.

APK: artifacts/releases/AirRelay-R31-recording-controls.apk
SHA-256: 8067E957B25FE1B06D1C664B4FEB2F910CF690D187B551CCE0C692C916C2349D

Kanıtlar: artifacts/r31-direct-controls.txt, artifacts/r31-record720.txt, artifacts/r31-record1080.txt, artifacts/r31-track-options.txt, artifacts/r31-direct-centered-controls.png, artifacts/r31-recording-options.png.
