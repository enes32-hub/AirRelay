# Canlı 2004, gönderici sarma ve optimizasyon menüsü

## Bulgular

process.log: 08:36:30.631 HlsMediaChunk.load -> InvalidResponseCodeException 404;
08:36:30.681 Media3 code=2004, media=15. Bu olay decoder değil medya parçası
erişim hatasıdır. Segmentin neden 404 olduğu (yayın penceresi dışı, kaynak silinmesi
veya başka erişim koşulu) kesinleştirilmedi. Eski Goldfish decoder hataları ayrı olaydır.

GET /scrub yönlendirmesi ve süre/konum cevabı eksikti. POST sorgu ayrıştırıcısı
ilk eşittir işaretine güveniyor, bozuk sorguda geçersiz pointer üretebiliyordu.
Gönderici oturum doğrulaması korunarak açık position parametresi, sonlu/pozitif
aralık kontrolü ve sarma kabul/ret günlükleri eklendi. GET durum sorgusu sarmaz.

Ek testte aynı TCP bağlantısında session identity değiştirmek native assert ile
uygulamayı kapatıyordu. Artık 400 yanıtı veriyor; kimlik değişimini kabul etmiyor.

## Yapılanlar

- Live olduğu doğrulanmış Media3 öğesinde terminal HTTP404/behind-live-window
  hatasında bir saniye sonra canlı varsayılan konuma seek+prepare. Medya başına
  en fazla iki girişim, girişimler arası en az 30 saniye. Başka medyaya geçilirse
  gecikmiş recovery geçersizleşir. İlk toparlama hemen başarısızsa hata gizlenmez.
  VOD404, auth ve diğer hatalarda otomatik başa sarma yapılmaz.
- GET /scrub süre/konum; POST /scrub position; bozuk parametre koruması.
- Menü: Dengeli / Görüntü kalitesi / Akıcılık + Özel. Eski stable tercihi yalnız
  seçiliyse korunarak gösteriliyor. TR/EN açıklamalarda gerçek sınırlar var.
- Uzak videoda Media3 video seçimine profile bağlı 8/12/4 Mbps ve 1080p/720p
  tercihleri. Kaynakta uygun alternatif varsa varsayılan adaptif seçim çalışır;
  tek varyant yeniden kodlanmaz. Bunlar Media3 tercihleri, kesin ağ Mbps garantisi
  değildir; uygun varyant yoksa oynatıcı sınırları aşabilir. Audio override ayrıdır.
- Ekran bitrate/dinamik FPS denetleyicisi EKLENMEDİ. Menü bunu açıklar. Tam
  adaptif kontrol ve yeni doğru FPS sayaçları önceki analizde kalan işlerdir.
- Özel tercih değiştirilmedi; yeni profil kullanıcı tarafından seçilebilir.

## Test ve teslim

Eski APK GET /scrub yanıt testi başarısız. İlk adayda GET router eksikliği testle
yakalandı, ikinci adayda tamamlandı. Geçersiz oturum testi native assert'i
ortaya çıkardı; son APK'da 400 dönüşü doğrulandı. Eski başarısız çıktılar korundu.

GEÇTİ: gerçek emülatör/native HTTP üzerinden GET /scrub, POST ile 2. saniyeye
sarma, bozuk parametre, ayrı bağlantıdaki eski oturumun reddi, aynı bağlantıda
kimlik değiştirmeye 400 ve bağlantının sürmesi. MP4 doğal bitişi sonrası üç
playback-info sorgusu aynı TCP bağlantısında geçti. acceptance-local.txt.

GEÇTİ: Android debug+androidTest derlemesi; güncelleme install -r ile emulator-5554'e
yüklendi. Yeni optimizasyon etiketleri gerçek UI dump'ında görüldü.
ÇALIŞTIRILMADI: live404 sentetik toparlama testi, gerçek YouTube canlı kabul testi,
iPhone uygulamasının gerçek sarma isteğiyle kabul testi, kısıtlanmış ağda ABR
varyant geçiş testi, büyük yazı/tüm odak UI testi ve tam regresyon paketi.

Emülatör açık, başka projenin emulator-5582'sine dokunulmadı. Kaynak hata nedenine
ilişkin belirsizlikler nedeniyle “canlı yayındaki bütün kopmalar çözüldü” denemez.

APK: artifacts/live-seek-20260909/aynatv-live-seek-final-x86_64-debug.apk
SHA256: 62BEF20764A71E18441E4CF0FC0056146E896A09BF740A39159A26275FAD8856
