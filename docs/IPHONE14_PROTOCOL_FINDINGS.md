# iPhone 14 Pro AirPlay laboratuvar bulguları

Bu kayıt, Windows üzerinde gerçek iPhone 14 Pro ile yapılan ara protokol testinden Android TV alıcısına taşınan davranışları belgeler. Fiziksel Xiaomi TV kabul kanıtı değildir.

## Doğrulanan oturum yolu

- iPhone alıcıyı `_airplay._tcp` ve `_raop._tcp` DNS-SD kayıtlarıyla buldu.
- PIN eşleştirme, pair-verify, FairPlay kurulumu ve ekran yansıtma oturumu tamamlandı.
- iOS bağlantıda IPv6 link-local adresi kullanabildi; alıcı yalnız IPv4 varsaymamalıdır.
- Aynalama videosu H.264 olarak yaklaşık 498 × 1080 boyutunda ve içeriğe göre 30/60 FPS geldi.
- Ekran sesi AAC-ELD (`ct=8`, 44.1 kHz, 480 örnek/kare) yolunu kullandı.

## Android'e taşınan oynatma kararları

- Video sunumu ses saatinden ayrıdır. MediaCodec çıktısı varsayılan olarak hemen çizilir; uzak NTP zaman damgasına göre Surface çıkışı bekletilmez.
- Ses, video çizimini engellemeden kendi NTP zaman çizelgesinde jitter tamponuyla oynatılır.
- AAC-ELD yedekli paket akışında sıra deliği tüm kuyruğu durdurmaz. ALAC ve AAC-LC için RTP resend davranışı korunur.
- RAOP FLUSH, eski sesi oynatmak yerine PCM kuyruğunu ve saat sabitlemesini sıfırlar, çıkışı yeniden tamponlar.
- Android çıkışı Oboe düşük gecikme yolunu varsayılan kullanır; gerçek cihazın verdiği AAudio/OpenSL ES, burst ve tampon değerleri tanı günlüğüne yazılır.

## Emülatör köprüsünde görüntü kanalı bulgusu

- Kontrol/PIN oturumu TCP 7000 üzerinden tamamlanırken görüntü gelmemesinin decoder veya Surface kaynaklı olmadığı doğrulandı. Oturumda `mirror running=1` görülüyor, fakat `onVideoSize`, SPS/PPS veya video kare geri çağırımı hiç oluşmuyordu.
- UxPlay yerel medya portları sıfır bırakıldığında işletim sisteminden dinamik port seçiyor. Windows emülatör köprüsü ise yalnız sabit `7100` görüntü, `7011` zamanlama ve `6000/6001` ses portlarını iletiyordu. iPhone'un dinamik görüntü portuna açtığı bağlantı bu nedenle emülatöre ulaşmıyordu.
- Android çekirdeği başlamadan önce portlar `TCP 7100/7000` ve `UDP 7011/6001/6000` olarak sabitlendi. Windows köprüsü ile Android alıcının port sözleşmesi artık aynı.
- Android log seviyesi eşlemesi UxPlay'in syslog sırasına göre düzeltildi. INFO kayıtları artık hata olarak etiketlenmiyor ve başlatma kaydı tüm sabit portları gösteriyor.
- Bu düzeltmenin APK derlemesi ve emülatörde sunucu başlangıcı doğrulandı. Gerçek görüntü karelerinin iPhone'dan gelişi sabah yapılacak yeni ekran yansıtma oturumunda doğrulanacaktır.

## Android TV canlı yayın arayüzü

- Ekran yansıtma görünümünden müzik kapağı, süre çubuğu, ileri/geri sarma, önceki/sonraki ve oynat/duraklat kontrolleri kaldırıldı.
- Hazır durumda iPhone Ekran Yansıtma adımı, bağlantı kurulurken ilerleme göstergesi, aktif görüntüde `LIVE` rozeti gösterilir.
- PIN penceresi eşleştirme bitmeden kapatılamaz. TV açılış odağı alıcıyı yanlışlıkla durdurabilecek `Stop` düğmesi yerine `Overview` sekmesine gider.

## Sonraki doğrulama

Bu kararlar önce x86_64 Android TV emülatöründe bağlantı, sürekli video ve ses-kesilmesi toparlanması açısından sınanır. Gecikme ve son kalite ölçümü yalnız nihai iPhone 14 Pro + Xiaomi TV Box 4K testinde kabul kanıtı sayılır.
