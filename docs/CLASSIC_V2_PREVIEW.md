# AirRelay Klasik v2 önizlemesi

Kaynak: Kullanıcının `airrelay_onizleme.html`, `airrelay_simge_v2.svg` ve
`airrelay_logo_v2.svg` dosyaları; özgün kopyalar `design/airrelay-v2/` içinde.

## Uygulananlar

- Yalnız Klasik: kömür grisi yüzeyler, lavanta vurgu, açık odak rengi,
  solda gezinme, açık bağlantı yerleşimi ve yatay bağlantı adımları.
  Büyük yazıda içerik kaydırılır ve adımlar alt alta gelir.
- Beyaz/Karanlık renkleri ve üst gezinmeleri korunur. Yeni marka logosu,
  launcher simgesi, monokrom simge ve TV banner'ı Android vektörüdür.
- Örnek içerik/kişiler/kayıtlar taşınmadı. Gerçek alıcı adı, durum, kayıtlar,
  kayıt silme/izleme/dışa aktarma, yardım ve ayarlar korunur.
- İzleme panelindeki kayıt düğmesi doğrudan kayıt sürecini başlatır.
  Android ses yakalama onayı atlanmaz. Kalite, Ayarlar → Görünüm altında
  Kayıt ayarları ile 720p/1080p seçilir; varsayılan 720p korunur.
- Sürekli akış türü etiketi kaldırıldı. İsteğe bağlı metrikler korunur.
- Altyazı penceresinde − / Boyut / +; %70–160, kalıcı tercih.
  Yerel metin altyazı kutusu saydam, metin beyaz ve ince siyah konturludur;
  alt kenar payı %12. Videoya gömülü veya telefonda görüntüye dönüştürülmüş
  altyazıların biçimi alıcıdan değiştirilemez.

## Doğrulama

- Android x86_64 debug derlemesi: geçti.
- Gerçek MediaCodec ekran akışı ve yerel Media3 video: geçti.
- Her iki modda kayıt düğmesi → sistem onayı; kalite penceresi açılmaması ve
  iptalden dönüş: geçti. Bu çalışmada yeni MP4 ses/görüntü kabul testi yapılmadı.
- Altyazı boyutu artır/azalt, mevcut sarma/gizleme/parça pencereleri: geçti.
- Sarılamayan canlı arayüz: sentetik yetenek bilgisiyle geçti; gerçek canlı yayın değil.
- Ana ekran ve altyazı penceresi gerçek emülatör görüntüleri incelendi.
- Üç tema ve seçim penceresi testi geçti; tema değişimi alıcıyı yeniden
  başlatmadı. Farklı gezinme yerleşimleri arasında ayar sekmesi korunur.
- iPhone kabulü, fiziksel TV ve ARM derlemesi: bu teslimde çalıştırılmadı.

Önceki APK'lar korunur. Yeni paket ayrı `AirRelay-R35-classic-v2-preview-emulator.apk`.
Motor/JNI/AirPlay/eşleştirme kodunda bu tasarım çalışması değişiklik yapmaz.
