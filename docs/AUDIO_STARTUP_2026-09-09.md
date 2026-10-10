# Shorts / normal video sonrası geç gelen ses

Kullanıcı ilk birkaç Shorts ve normal video geçişinden sonra yaklaşık 30 saniye
ses duymadığını, ardından sesin başladığını bildirdi. Ek soruya verilen “evet”
yanıtı görüntünün akmaya devam ettiği şeklinde yorumlandı; yanıt kısa olduğundan
bu ayrıntı kesin ölçüm sayılmaz.

## Bulgular

Mevcut oturum günlükleri artifacts/audio-delay-20260909/session.log ve process.log
altında korundu. 08:07:58.128 media=4 oynatma isteği, 08:07:58.607 ilk görüntü,
08:07:59.607 ses codec oluşturma başlangıcı görüldü. Bunlar hoparlörde ses çıktığının
kanıtı değildir. Başka medya öğelerinde de ses çözücüsü yaklaşık bir saniye içinde
açılıyor. Günlüklerde 30 saniyelik sessizliğin kesin nedeni saptanamadı.

Geçişlerde goldfish VP9 yüzeyinin bırakılması, ardından Media3 asynchronous codec
flush işleminin Released durumda çalışması ve BaseRenderer.release IllegalStateException
hataları da görüldü. Bunların ses gecikmesine neden olduğu henüz kanıtlanmadı;
bu tanılama değişikliğinde codec veya yüzey yaşam döngüsüne müdahale edilmedi.

## Yapılan değişiklik

AirPlayVideoPlayer mevcut medya/oynatıcı nesliyle doğrulanan AnalyticsListener
kayıtları eklendi: ses çözücüsü hazırlık süresi, AudioTrack konumunun ilk ilerlemesi,
underrun, sink hata türü ve seçili ses grubu sayısı. Seçili ses varken üç saniye
içinde playout başlamadıysa yalnız bir başlangıç uyarısı; durum, buffer, video ilk
kare, oynatıcı sesi ve suppression bilgileri içeriyor. Signed URL veya hata mesajı
kaydedilmiyor. Android playout başlangıcı, Windows hoparlörünün duyulduğu anlamına gelmez.

## Doğrulama ve kabul sınırı

- GEÇTİ: x86_64 debug ve androidTest APK derlemesi (31 saniye).
- GEÇTİ: emulator-5554 uygulama verilerini koruyan `install -r`; uygulama yeniden
  açıldı, PID 8155 ve port 7000 hazır. Köprü kaydı otomatik yeniden oluştu.
- ÇALIŞTIRILMADI: yeni analytics kayıtlarıyla gerçek iPhone Shorts/normal video testi.
- ÇALIŞTIRILMADI: bu değişiklik için tam Android regresyon testi.
- Ses gecikmesi **çözülmüş sayılmıyor**. Bu APK tanılama sürümüdür.

APK: artifacts/audio-delay-20260909/aynatv-audio-diagnostics-x86_64-debug.apk
SHA-256: 14718215D3827FD57EEDF4849E578506A9C700B5AF13A016A0E3F6168ED5F58F

Sonraki deney: birkaç Shorts değiştir, normal videoda en az 40 saniye bekle.
Sessizlikte `Audio playout advancing` erken varsa Android sonrası Windows ses çıkışı
veya içeriğin sessiz PCM üretimi araştırılmalı. Olay geçse decoder/track hazır olma,
buffer/suppression/underrun zamanları ayrılmalı. Yeni oturum loglarını önceki
kanıtların üstüne yazma. Diğer projeye ait emulator-5582'ye dokunma.
