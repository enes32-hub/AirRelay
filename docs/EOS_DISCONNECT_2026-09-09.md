# Video bitişinde AirPlay kontrol bağlantısının kapanması

Kullanıcı sesin bu denemede düzgün olduğunu, ancak oturumun kendiliğinden
kapandığını bildirdi. Ham kanıt artifacts/disconnect-20260909/logcat.txt.

08:17:05.641 Media3 doğal video bitişi; 08:17:06.500 native video_reset 2
(HLS_SHUTDOWN) ve “Disconnecting on software request”; ardından bütün kontrol
bağlantıları kapandı. Köprü bu sırada ilan vermeye devam etti, uygulama PID 1339
çökmedi. Ses tanı kayıtları iki videoda Android playout başlangıcını sırasıyla
1113 ms ve 447 ms olarak gösterdi; bunlar Windows hoparlör ölçümü değildir.

## Düzeltme

http_handlers.h /playback-info handler, Android oyuncusunun duration=-1 bitiş
işaretini bağlantıyı kapatmak için kullanıyordu. Android dalı artık boş zaman
aralıklı geçerli bir idle plist döndürüyor; kontrol soketini kapatmıyor ve HLS
shutdown callback'i üretmiyor. Oynatıcı kendi medya bitişini zaten yönetiyor.
Android dışındaki UxPlay davranışı korunuyor. Gerçek TEARDOWN, kullanıcı durdurma,
eşleştirme, decoder ve ses ayarları değiştirilmedi.

## Test

tests/integration/playback_idle_keepalive.py; localhost HTTP fixture + adb reverse
ile gerçek native /play isteği, kısa MP4'ün Media3 üzerinde doğal bitişi ve aynı
TCP bağlantısında üç /playback-info sorgusu. Geçmiş callback günlük sayısının
artmasıyla doğal bitiş önkoşulu doğrulanıyor.

- Önce: after-EOS yanıtı boş; test başarısız (before-eos-test.txt).
- Sonra: üç geçerli idle plist aynı TCP bağlantısında alındı; test geçti
  (after-eos-test.txt).
- İlk, yalnız boşta alıcıya sorgu yapan deneme EOS önkoşulunu sağlamıyordu;
  before-test.txt bu nedenle kök hata kanıtı sayılmaz. Test doğal bitişi bekleyecek
  şekilde tamamlandı; eski kanıt dosyaları değiştirilmedi.
- x86_64 debug ve androidTest derlemesi geçti. Güncelleme emulator-5554'e
  install -r ile yüklendi; AynaTV ve köprü açık. Emülatör yeniden başlatılmadı.
- Gerçek iPhone ile video bitişi/geçiş kabul testi BEKLİYOR. Tam regresyon
  paketi bu değişiklikte çalıştırılmadı.

APK: artifacts/disconnect-20260909/aynatv-eos-keepalive-x86_64-debug.apk
SHA256: FFD15C664FDA1B0AD7B29C7662D1F38D39B2CC17A480724C80973B55A17121A3
