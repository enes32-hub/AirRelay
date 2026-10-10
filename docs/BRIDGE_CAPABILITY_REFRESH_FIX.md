# Emülatör oynatma tercihi duyurusu — 21 Eylül 2026

Android ayarı `advertise_video=true`; aktif NSD kaydı video bitlerini
`0x5A7FFEE6` yerine `0x5A7FFEF7` olarak yayınlamıştı. Windows köprü günlüğü
özellik değişiminden sonra yalnız "withdrawing LAN services" satırında kaldı.
Kod, TXT değişikliğinde tüm dinleyicileri kapatıyor ve Python 3.13'te mevcut
istemciler kapanmadan tamamlanmayan `Server.wait_closed()` çağrısını bekliyordu.

Aynı ad/cihaz/anahtar için TXT değişimleri artık yalnız DNS-SD ve mDNS
kayıtlarını günceller; TCP/UDP ve emülatör port yönlendirmeleri korunur.
Windows kayıt beklemeleri olay döngüsü dışında çalışır. Gerçek alıcı
kapanışında accepted clients da kapatılarak supervisor beklemesi sonlandırılır.

Dokuz yerel test geçti (`artifacts/bridge-capability-refresh-tests.txt`).
Yeni test, gecikmeli kayıt güncellemesi sırasında gerçek yerel TCP bağlantısında
önce/sonra veri aktarımını ve doğru özellik ilanını doğrular. Windows DNS-SD
bu birim testte sahte sağlayıcıdır; gerçek iPhone otomatik mod geçişi testi değildir.

Köprü güncel kodla yeniden başlatıldı. Android APK, ses/codec/protokol
motorları değiştirilmedi. iPhone'un mevcut yansıtmayı kendiliğinden video
aktarımına çevirmesi garanti edilmez; video isteğini gönderici uygulama başlatır.
