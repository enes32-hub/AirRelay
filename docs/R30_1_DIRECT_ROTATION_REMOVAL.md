# R30.1 — doğrudan videoda döndürme kapatıldı

Kullanıcı doğrudan video döndürülürken ciddi hata bildirdi. Bu sürüm doğrudan video döndürmesini desteklemiyor: kontrol panelindeki döndürme düğmesi yalnız ekran yansıtmasında gösteriliyor. Doğrudan video yüzeyine daima 0 derece aktarılıyor; önceki yansıtma açısı doğrudan videoyu döndüremez veya TextureView seçimine neden olamaz. Yansıtmaya dönüldüğünde kullanıcının yansıtma açısı korunur.

Bu, bildirilen işlemi kaldıran dar kapsamlı düzeltmedir; önceki çöküşün kesin native/codec nedeni teşhis edildiği iddia edilmez. Kalite, altyazı ve ses seçimleri değiştirilmedi.

- Derleme: başarılı; ARM32, ARM64 ve x86_64 aynı APK içinde.
- Paket: versionCode 62 / 0.1.0-rc30.1-test.
- APK imza doğrulama: geçti.
- Python regresyonları: 57 geçti. Bunlar Android görüntü/çökme testi değildir.
- Cihaz üstünde yeni sürüm testi: çalıştırılmadı. ADB cihaz listesi boş, emülatör çalışmıyor.
- TV kurulumu: yapılmadı. 192.168.31.131:5555 bağlantısı ilk denemede ve yetkili yeniden denemede Windows soket erişim hatası 10013 verdi. Mevcut TV sürümü değiştirilmedi.

APK: `artifacts/releases/AirRelay-R30.1-no-direct-rotation.apk`
SHA-256: `109DB09D89ED2C18D5A7BC026F5F65229E0591CABB3FB2F0E71F266AB4AE4931`
Önceki paketler korunuyor. Kullanıcı kabulü bekliyor.

## Sonraki bağlantı ve kurulum

TV ping yanıtı verdi. Normal ve yetkili adb connect denemeleri 10013 ile başarısız oldu; çalışan ADB sunucusu kısıtlı süreçten başlatılmıştı. ADB sunucusu yetkili ortamda kill-server / start-server ile yeniden başlatılınca aynı IP ve porttan bağlantı başarılı oldu. TV yeniden başlatılmadı ve ağ/ADB ayarları değiştirilmedi.

R30.1 daha sonra TV'ye temiz kurulumla başarıyla yüklendi ve açıldı. Paket sorgusu versionCode 62 / 0.1.0-rc30.1-test doğruladı. Kullanıcının önceki temiz kurulum talimatı uyarınca tercihler, izin seçimleri ve eşleştirmeler sıfırlandı. Yukarıdaki kurulamadı kaydı ilk denemeye aittir; gerçek iPhone/video kabulü hâlâ bekliyor.
