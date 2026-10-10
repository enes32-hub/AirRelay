# Metin odağı ve tekrar başlatmada keşif

## Keşif kök nedeni

Aktif NSD satırının başındaki istemci istek numarası ile süslü parantezdeki
global NSD kayıt numarası farklıdır. Gerçek örnek: `6: Advertiser ... {22,
startTime ...}`; Adding service günlüğü `with ID 22` taşır. Önceki ayrıştırıcı
6 numarasını aradığı için tekrar başlatma/güncellemelerden sonra açık alıcıyı
kapalı sanıyordu. Artık global kayıt numarası eşleştiriliyor. İstemci/global
numaraları farklı olan durum için regresyon testi eklendi; altı test geçti.
Önce aynı mevcut NSD dökümü False, düzeltme sonrası True olarak ayrıştırıldı.

Uygulama durdurulunca gerçek mDNS sorgusu yanıtsız ve LAN 7000/7001/7100
dinleyicileri kapalı. Yeniden başlatınca AynaTV Receiver, port 7000,
aynatv-emulator.local geri geldi. Güncellemeler boyunca köprü süreci açık kaldı.
Telefonun önbelleğinde kısa süre eski isim kalması hâlâ mümkündür; iPhone
ekranının anlık güncellendiği iddiası yok.

## Klavye

İlk aday yalnız KeyboardOptions.showKeyboardOnFocus=false kullandı. Gerçek
emülatör testinde metin alanı odaklanınca IME yine açıldı; başarılı sayılmadı.
Son aday bütün beş metin alanını ortak TvManualTextField'e taşıdı. Gezinmede
readOnly; OK/Enter veya pointer tıklamasıyla düzenleme aktif. Odak başka yere
geçince düzenleme kapanır. Fiziksel klavye/AVD hw.keyboard=yes korunur.

Gerçek UI testi: gelişmiş ayarlarda Alıcı portu=7000 alanı focused=true,
mInputShown=false. Aşağı/yukarı sonrası yine false. OK sonrası true. Değer
değiştirilmedi. İsim/çözünürlük alanlarının her birinde ve dokunma yolunda ayrı
uçtan uca test henüz yapılmadı; aynı bileşen kullanılıyor.

## Teslim

Derleme geçti (26 saniye), install -r ile emulator-5554 güncellendi; emülatör
kapatılmadı. Diğer projenin emulator-5582'sine dokunulmadı. Ana sayfaya dönüldü,
alıcı açık bırakıldı. Gerçek iPhone bağlantı kabul testi bekliyor.

APK: artifacts/focus-discovery-20260909/aynatv-manual-focus.apk
SHA256: 8D4CAE22FB6E86673334403185B31AC3BF2A8B62D0D637FA90F46571B9A42C43
Keşif kanıtları ve build günlükleri aynı artifacts klasöründe. Tam Android
regresyon paketi bu değişiklikte çalıştırılmadı.
