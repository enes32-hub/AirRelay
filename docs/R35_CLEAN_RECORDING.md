# R35 — Yalnız yayın görüntüsü ve sesinin kaydı

## Teslim

- Sürüm: 0.1.0-rc35-test, versionCode 70.
- TV APK: `artifacts/releases/AirRelay-R35-clean-recording.apk` (arm64-v8a + armeabi-v7a).
- SHA-256: `D9F697CDFB40B9870D53FCDC051D8AD405B5CF9C8F77B9F2DC16ED0BFA995A75`.
- TV'de kurulu APK'nın SHA-256 özeti de bu değerle doğrulandı.
- Emülatör: `artifacts/releases/AirRelay-R35-emulator.apk` (x86_64).
- R34 geri dönüş APK'sı korunuyor. Kurulumlar `install -r` ile yapıldı; uygulama verileri temizlenmedi. Kullanıcının kayıtları kopyalanmadı veya silinmedi. Testlerde üretilen kayıtlar temizlendi.

## Uygulama

Tam ekran VirtualDisplay video yakalaması kaldırıldı. Ekran yansıtmanın decoder görüntüsü ve Media3 doğrudan video görüntüsü, arayüz birleştirilmeden önce EGL üzerinden kayıt kodlayıcısına aktarılıyor. Kayıtta AirRelay menüsü, kontrol düğmeleri, bildirimler ve Android ekranı bulunmuyor. Mikrofon kullanılmıyor; Android 10 ve üzerinde mevcut UID filtreli uygulama ses yakalama yolu korunuyor. Sistem yakalama onayı ses yakalamak için hâlâ gerekli.

Kaynaklar değişirken önce eski EGL üreticisi ayrılıyor, sonra yeni kaynak aynı MP4'e bağlanıyor. Kaynak duraklatıldığında son görüntü yeniden sunularak kayıt süresi korunuyor. Kayıt en fazla 30 FPS; TV önizlemesinin FPS tercihi değiştirilmedi. Kayıt hatası oynatma görüntüsünden ayrı ele alınıyor. MediaRecorder'a monoton mutlak zaman damgası veriliyor; ses, ilk kaydedilen kareye hizalanıyor ve bu kareden önceki ses örnekleri kırpılıyor.

Kayıt kaynak görüntüsünü kendi yönünde ve oranı korunarak saklar. TV tarafındaki döndürme/esnetme ve ayrı çizilen altyazı arayüzü kayda işlenmez. Kaynak videoya zaten gömülü altyazı görüntünün parçasıdır.

## Geçti

| Kontrol | Ortam | Kanıt |
|---|---|---|
| 720p ve 1080p gerçek AVC MP4, örtücü menünün kayda girmemesi, mirror → Media3 geçişi | Emülatör ve TV | r35-clean-emulator-verified.txt, r35-clean-tv-verified.txt |
| Kaydedilen video ile orijinal kaynak piksel karşılaştırması | Emülatör / TV | 337 ve 336 / 336 ve 314 örnek, toplam 361; kodlayıcı dönüşümü toleransıyla |
| Kayıt sırasında 10 üretici değişimi, duraklatılmış video ve kısa bellek gözlemi | Emülatör ve TV | Yaklaşık 40 sn kayıt; gözlenen PSS artışı 722 KB / 1288 KB |
| Gerçek sistem onayı, 1080p kayıt, kontrollerden durdurma, MP4 metadata ve dışa aktarma hash eşitliği | Emülatör ve TV | r35-audio-emulator-clean.txt, r35-final-tv-record-controls-audio.txt |
| AAC çözümünde sıfır olmayan uygulama ses örnekleri | Emülatör ve TV | Test tonu peak 8795 / 8054 |
| Media3 doğrudan HLS videosunun kendi sesinin kaydı | TV | r35-direct-audio-tv.txt; AAC peak 5115, harici test tonu yok |
| 12 sabit kare döndürmesi ve 12 canlı HLS döndürmesi | TV | r35-rotation-tv-stable.txt; Surface değişimi yok, 44 canlı decoder karesi |
| Kayıtlar sekmesi, metadata, yerel oynatma/duraklatma/sarma, oluşturulan dosyanın silinmesi | TV | r35-final-tv-recording-library.txt |
| 3 gerçek TCP kapatma/yeniden bağlantı, alıcının açık kalması, eski callback engeli | TV | r35-final-tv-transport-end.txt |
| EGL hata toparlanması, eski kare koruması, 15 çözünürlük geçişi ve 3 gerçek decoder oturumu | Emülatör | r35-decoder-emulator.txt |

Kanıt günlükleri `artifacts/` altındadır. `artifacts/r35-clean-test-tv.png`, kayıt sırasında TV'nin üstünü tamamen kapatan test arayüzünün gerçek ekran görüntüsüdür. Buna rağmen dosyadaki görüntü orijinal kaynakla karşılaştırılarak doğrulanmıştır.

## Bulunup giderilenler ve test düzeltmeleri

- İlk gerçek kayıt denemesinde göreli zaman damgaları MediaRecorder tarafından reddedildi; 0 örnekli dosya ve stop hatası oluştu. Mutlak monoton zamanla giderildi; aynı test tekrarları geçti.
- Test videosunun kendi magenta renkleri ilk basit menü-renk taramasında yanlış pozitif üretti. Kontrol orijinal video kareleriyle doğrudan karşılaştırmaya dönüştürüldü.
- Eski döndürme testi codec/grid sınırlarından tek piksel örneklediğinde 42/49 sonucu verdi. Yeni test en az 50 sabit renk bölgesi seçer ve %90 eşleşme ister; eşik düşürülmedi. Görüntü yönü ve canlı decoder sürekliliği ayrı doğrulandı.
- Android 16 emülatörünün tek uygulama seçen izin akışı ve önceki testten kalan izin penceresi ilk ses testlerini engelledi. Varsayılan ekran onayı kullanıldı; test penceresi kapatılarak temiz başlangıçla tekrar edildi ve geçti. Bir deneme test süreci zorla durdurularak sonlandırıldı; başarı olarak sayılmadı.
- Eski günlüklerdeki genel SIMULATED_CALLBACKS başlığı gerçek yerel decoder testleri için fazla geneldi. Son test paketinde kapsam başlığı düzeltildi; eski günlükler değiştirilmedi.

## Çalıştırılmadı / kabul sınırı

Gerçek iPhone üzerinden yeni kaydın görüntü-ses senkronu ve içerik geçişi son kullanıcı kabulü bekliyor. Yerel AVC/HLS ve test sesleri tam AirPlay kabul testi değildir. Çok saatlik bellek testi, 1 saat/2 GB sınırına kadar doldurma, düşük depolama alanı ve farklı TV modelleri bu turda sınanmadı. Kısa PSS gözlemi uzun süreli sızıntı olmadığına kanıt sayılmaz. DRM korumalı içerik desteği iddia edilmez. Android 10 öncesinde mevcut sessiz kayıt sınırı devam eder.

Test APK'ları iki cihazdan kaldırıldı. Emülatörde test için verilen ses izni önceki kapalı durumuna döndürüldü; TV'nin önceden verilmiş ses izni korundu. TV ve emülatör uygulamaları yeniden açıldı.
