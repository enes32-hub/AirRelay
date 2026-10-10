# Ekran yansıtmaya dönüş — 21 Eylül 2026

## Bulgu ve düzeltme

Kullanıcı oturumunun günlüklerinde `videoPtsZero=true` iken decoder ve yüzey
kare sayıları artıyor. Doğrudan oynatmayı kapattıktan sonra yeni ilk-kare
gözlemi oluşmuyor. Sıfır PTS ile boolean release kullanımı, ardışık dokuları
aynı zaman damgasıyla etiketliyor; eski dokuya karşı koruma onları yeniden
gösterilmiş önbellek karesi kabul edebiliyor.

Yalnız eksik/sıfır PTS için MediaCodec çıkışı monoton şimdiki zamanla hemen
serbest bırakılıyor. Aynı zaman yüzey gözlemine veriliyor; orijinal PTS
tanılama için tutuluyor. Geçerli PTS, planlı sunum, ses motoru, oturum
kimlikleri ve eski kareyi reddetme kuralları değiştirilmedi.

Zaten doğrulanmış ekran yansıtması sürerken oynatma tercihini ekran olarak
değiştirmek artık gereksiz video-sonlandırma ve hata durumuna sokmuyor.

## Doğrulama

- GEÇTİ: x86_64 debug ve Android test APK derlemesi.
- GEÇTİ: gerçek MediaCodec/EGL üzerinde sıfır PTS; üç ardışık dönüşte ve
  reddedilen uzak oynatma callback'inden sonra yeni kareyle mesaj temizleme.
- GEÇTİ: gecikmiş durdurma callback'i hata/dönüş mesajını yanlışlıkla silmiyor.
- GEÇTİ: decoder/yüzey hata toparlanması, gözlem kuyruğu taşmasından sonra
  yeniden bağlantı, 15 çözünürlük geçişi ve üç gerçek decoder oturumu.
  Ayrıntılar: `artifacts/mirror-return-decoder-regression.txt`.
- iPhone/YouTube kabul testi: ÇALIŞTIRILMADI. Telefonun YouTube hata ekranı
  tek başına nedenini kanıtlamaz. Ekran modunda eski uzak video hedefini
  kullanmaya devam eden göndericinin /play isteğine mevcut protokol 409
  döndürüyor; bu davranış bu yamada değiştirilmedi. Sahte başarılı yanıt yok.

APK: `artifacts/releases/AirRelay-R35-mirror-return-fix-emulator.apk`

SHA-256: `0C4BEFCF85543BB0D07C508A66949EBA9ED4A3F6B8DF597E61F1485D68E20DAD`

Kanıtlar: `artifacts/mirror-return-fix-build.log`,
`artifacts/zero-pts-return-test.txt`, `artifacts/mirror-return-late-stop-test.txt`.
Önceki APK korunmuştur. Yalnız emulator-5584 güncellenir; TV'ye kurulmaz.
