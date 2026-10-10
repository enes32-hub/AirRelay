# R30.2 — doğrudan videoda sabit yüzeyle döndürme

R30'daki risk: 0 derece SurfaceView, diğer açılar TextureView kullanıyordu. 0/90 ve 270/0 geçişi, Media3 video çözerken çıkış Surface nesnesini kaldırıp yeniliyordu. Önceki kullanıcı hatasının yığını alınmadı; kesin hata kodu/kök neden kanıtlandığı iddia edilmez.

Doğrudan videoda şimdi tüm açılar aynı TextureView/Surface üzerinde çalışır (`stableTexture=true`). Dönüş, Compose görüntü dönüşümüyle yapılır; 90/270 için en/boy hesabı ters çevrilir. 180 gerçekten ters görüntü verir. Döndürme düğmesi geri gelir. Yansıtmanın mevcut davranışı, native protokol, decoder/ses zamanlaması, parça seçimleri değiştirilmez.

Bedel: doğrudan videoda 0 derecede de TextureView kullanılır; SurfaceView'e göre ek birleştirme maliyeti olabilir. Her TV'de 4K/HDR/korumalı içerik desteği garantisi verilmez. Uzun süreli yüksek çözünürlüklü performans ve gerçek iPhone senkron kabulü ayrıca gerekir.

Doğrulama hedefi: gerçek Xiaomi kutusu 192.168.31.131:5555. DirectRotationProbe gerçek yerel MP4'ü AirPlayVideoPlayer/Media3 üzerinden üretimdeki FittedReceiverSurface'e çözer. Durdurulmuş karenin 49 örnek pikselinin dönüş konumlarını karşılaştırır (0/90/180/270, üç tur). Çizilmiş Canvas test deseni değildir. Ek olarak canlı HLS oynarken 12 açı değişimi, Surface kimliği ve decoder kare ilerlemesi sınanır.

- İlk gerçek video dönüş testi geçti: 12 piksel kontrolü + 12 canlı dönüş, sıfır Surface değiştirme/yok etme ve oynatıcı hatası yok.
- Genel doğrudan video regresyonu geçti: 10 ardışık video, iki ses seçimi, 4 canlı liste erişimi. Canlı bölüm yalnız 12 saniyedir; uzun süreli bellek kanıtı değildir.
- Python testleri: 57 geçti.
- Uygulama ve Android test derlemeleri başarılı.
- Güçlendirilmiş son dönüş testi de geçti: 12 gerçek kare piksel kontrolü, 12 canlı dönüş, bu dönüşlerde 44 decoder karesi, sıfır Surface değiştirme/yok etme, oynatıcı hatası yok.
- Dört gerçek TV ekran görüntüsü `artifacts/r30-2-validation/direct-rotation/` altına alındı.
- APK imzası doğrulandı. Test APK'sı TV'den kaldırıldı. R30.2 temiz kurulup açıldı; paket sorgusu sürüm 63 / rc30.2 doğruladı. Önceki talimata göre ayarlar, eşleştirmeler ve izin seçimleri sıfırlandı.

APK: `artifacts/releases/AirRelay-R30.2-stable-direct-rotation.apk`
SHA-256: `08BB201B680A95FBD78142B1441A75668EAAD98F30DBD5505CE4E96B0271F5AB`
Android sürümü: 0.1.0-rc30.2-test / 63. ARM32/ARM64/x86_64 içerir. Önceki paketler korunur. Gerçek iPhone/platform ve işitilen ses senkron testi çalıştırılmadı; kullanıcı kabulü bekler.
