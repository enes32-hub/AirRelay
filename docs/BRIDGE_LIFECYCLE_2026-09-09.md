# Windows emülatör köprüsü: durdurma ve yeniden başlatma

## Bulgular ve değişiklikler

- Köprü Android servisinden bağımsız, sürekli ilan yapıyordu. Aktif Android NSD
  kayıtlarını bir saniye aralıkla izleyen yaşam döngüsü eklendi. Kapanışta mDNS
  kayıtları, TCP/UDP dinleyicileri ve konsol yönlendirmeleri kaldırılıyor.
- Android `pw=false, sf=0x4` iken köprü sabit `pw=true, sf=0x8c` ilan ediyordu.
  İsim, kimlik, public key ve bütün TXT değerleri artık aktif kayıttan okunuyor.
- Windows ad çakışmasında Receiver(1) kaydedebiliyor. Eski kod özgün isimle
  deregister ediyordu; artık kayıt callback'inin döndürdüğü gerçek instance saklanıyor.
  Önceden kalmış, ağda doğrulanan AynaTV ilanları ayrıca kaldırıldı.
- Windows ilanının SRV hedefi yerel alan adı değildi. `aynatv-emulator.local`
  kullanıldı; Windows tek başına A sorgusunu yanıtlamadığından sadece bu host için
  mDNS adres yanıtı ve periyodik duyuru eklendi. Gerçek sorgu 192.168.31.32 döndü.
- Tanı sırasında /info sorgusunun bile ClientConnected/Disconnected/video_flush
  ürettiği görüldü. Periyodik sağlık kontrolünde RTSP probu kullanılmıyor.

## Gerçek doğrulama

- GEÇTİ: 5 Python regresyon testi; geçmiş NSD kaydı, eksik/yeni ID, yanlış
  AirPlay/RAOP eşleştirmesi, TXT korunması ve raw goodbye üretimi.
- GEÇTİ: emulator-5554 üzerinde uygulamanın Alıcıyı durdur düğmesiyle durdurma;
  LAN 7000/7001/7100 dinleyicileri yok, 6 saniyelik mDNS sorgusunda ilan yanıtı yok.
- GEÇTİ: aynı emülatörde Alıcıyı başlat; UI Bağlanmaya hazır, mDNS SRV port 7000,
  hedef aynatv-emulator.local. Windows son denemede tek aktif adı
  `AynaTV Receiver(1)` olarak döndürdü. Ad ekinin oluşması tamamen giderilmiş değil.
- GEÇTİ: gerçek host A sorgusunda 192.168.31.32 yanıtı.
- ÇALIŞTIRILMADI: bu düzeltmelerden sonra gerçek iPhone eşleşme/görüntü/ses testi.
  Bağlantı probleminin tümü çözüldü iddiası yok.

APK, codec, ses motoru, PIN tercihi, eşleştirme anahtarı ve uygulama verileri
değiştirilmedi. Emülatör yeniden açılmadı; başka projenin emulator-5582 cihazına
dokunulmadı. Köprü PID 13628, Wi-Fi 192.168.31.32, interface 7. Alıcı açık bırakıldı.
Kanıtlar: artifacts/bridge-lifecycle-20260909 altında bridge-v3 günlükleri,
stopped-mdns.txt, restarted-mdns.txt, tests.txt.

Sınırlar: NSD dökümü ayrıştırıcısı mevcut API 36 biçimine bağlı; tanınmayan
biçimde güvenli şekilde ilanı kapatır. iOS önbelleği nedeniyle listeden kaybolma
anlık olmayabilir. DNS-SD kayıt hatası gibi başlangıç hataları ayrıca ele alınmalıdır.
