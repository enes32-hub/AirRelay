# PIN penceresinin erken kapanması — 8 Eylül 2026

iPhone denemesinde PIN isteğinin ardından TCP bağlantısı yaklaşık 20 ms içinde kapandı. `onConnectionDestroy()` son bağlantıda PIN'i ve eşleştirme durumunu temizliyordu. Native çekirdek PIN'i alıcı seviyesinde tutarken arayüz kontrol soketinin ömrüne bağlıydı.

`AirPlayService` artık bağlantı kapanışında veya transport resetinde bekleyen PIN'i korur. Mevcut 120 saniyelik zaman aşımı uzatılmadı. Yeni PIN eski değerin yerini alır; medya kurulumu, kullanıcı iptali, alıcı durdurma ve zaman aşımı PIN'i temizler. Native kaynaklar, PIN üretimi/doğrulaması, portlar ve eşleştirme anahtarları değiştirilmedi.

Doğrulama:

- Android derlemesi geçti (`build-pin-lifetime.log`).
- 15 Android entegrasyon senaryosu ve 3 yerel AVC oturumu geçti (`android-pin-fix-tests.log`). Yeni senaryolar bağlantı kapanışı/reset sonrası pencereyi, yeni PIN'i, medya kurulumu, iptal ve mevcut expiry callback'ini kapsar. Zaman aşımı testinde callback doğrudan çalıştırılmıştır; 120 saniye bekleme ölçümü değildir.
- Windows köprüsünden gerçek native `POST /pair-pin-start` isteği gönderildi. Soket kapatıldıktan 6,6 saniye sonra PIN penceresi hâlâ görünüyordu. PIN kaydedilmedi. Test iptal edildi ve alıcı hazır bırakıldı (`pin-bridge-smoke.json`). Bu yerel test gerçek iPhone eşleştirme kabulü değildir.
- Emülatör kapatılmadan, veri koruyan APK güncellemesi yapıldı.

Güncel APK: `artifacts/rev2-integration/aynatv-revision2-pin-fix-debug.apk`.
SHA-256: `ea0a3073a9e249b333e50e3bc72e874856ef84dc5399b6c12ca6bec819d2ec6d`.
Önceki Revizyon 2 raporları ve APK'ları tarihsel kayıt olarak korundu. iPhone'daki eski PIN istemi kapatılıp alıcı yeniden seçilerek gerçek eşleştirme sınanmalıdır.
