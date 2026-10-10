# Yarı şeffaf yayın kontrolleri — 2026-09-21

Durum: Kullanıcının isteğiyle geri alındı. Önceki R35 emülatör APK'sı tekrar
kuruldu; kaynak ve test değişiklikleri geri çevrildi. Aşağıdaki bilgiler
yalnız geri alınan denemenin tarihsel kaydıdır.

R35 üzerinde yalnız oynatma arayüzü değiştirildi. Önceki R35 APK korunuyor;
yeni emülatör APK'sı `artifacts/releases/AirRelay-R35-glass-preview-emulator.apk`.
SHA-256: `FE0660CA2AB9F3138E06EEE135EEEC3D3B4D6F9D0B1D865C1D828B6DDA3CF47A`.

Panel, yarı şeffaf gradyan, yuvarlak kenarlar, ikon/etiket düğmeleri ve açık
mavi odak çerçevesi kullanır. Gerçek zamanlı video bulanıklaştırma eklenmedi.
Oynatma şekli, sığdırma, döndürme, kayıt başlat/durdur, kayıtlar, desteklenen
PiP, kalite, altyazı, ses, ayarlar ve yayını bitirme callback'leri korunur.
Sarılan videolarda mevcut sarma aralığına sınırlı ±10 saniye düğmeleri vardır.
Dar ekranlarda şerit kumandayla yatay kaydırılabilir. Ekran yansıtmada ilk odak
döndürmede, doğrudan videoda oynat/duraklattadır. Hata odağı önceliği korunur.

## Doğrulama

- Geçti: x86_64 debug ve Android test APK derlemeleri.
- Geçti: emülatöre veriler silinmeden güncelleme kurulumu.
- Geçti: ekran yansıtma kontrol odağı, yukarıyla gizleme, OK ile açma,
  ayarlara geçerken alıcı ve sunum durumunun korunması.
- Geçti: doğrudan video panelinin gerçek UI sınırlarıyla ortalanması,
  oynat/duraklat odağı, gizleme ve yeniden açma.
- Emülatör görüntüleri incelendi; ilk sürümdeki etiket bölünmeleri düzeltildi.
- İlk doğrudan kontrol testi oynat yerine duraklat etiketini gösteren test
  durumuyla uyuşmadığı için başarısızdı. Test iki oynatma durumunu tanıyacak
  şekilde düzeltildi ve tekrar geçti.
- Testler sentetik oturum durumu kullanır; yeni panelle gerçek iPhone video,
  sarma, kayıt, tüm parça seçimleri ve tüm TV modelleri kabulü yapılmadı.

Kanıtlar: `artifacts/glass-player-build.log`, `glass-mirror-test.txt`,
`glass-direct-test.txt`, `glass-mirror-controls.png`, `glass-direct-controls.png`.
Test APK'sı kaldırıldı; ana uygulama yeniden açıldı. TV'ye yükleme yapılmadı.
