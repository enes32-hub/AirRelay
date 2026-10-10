# Telefondan ve TV'den sarma — kabul testleri

Kullanıcının 10 Eylül 2026 isteğiyle RC1 kabulüne eklendi. **Bu tablodaki gerçek iPhone testleri henüz çalıştırılmadı.** Yerel oynatıcıdaki sarma taslağı testi, iPhone'un AirPlay üzerinden konum komutu gönderdiğini veya iki cihazın ilerleme çubuğunun eşleştiğini kanıtlamaz.

Ön koşul: aynı ağda iPhone 14 Pro ve Android TV/Google TV, aynı AirPlay oturumu korunmuş olmalı. TV ve telefondaki uygulama/sürüm ile içerik türü kaydedilmeli. Süresi bilinen ve sarılabilen uzak video kullanılmalı; yalnız ekran görüntüsü olarak yansıtılan videodan alıcı tarafında evrensel sarma beklenmemeli.

| Kimlik | İşlem | Beklenen sonuç | Durum |
| --- | --- | --- | --- |
| SEEK-01 | Fotoğraflar videosunda telefondan ileri, sonra geri sar | TV aynı videoda hedef konuma geçer; görüntü ve ses devam eder; bağlantı/medya kimliği gereksiz değişmez. | ÇALIŞTIRILMADI |
| SEEK-02 | YouTube normal videoda telefondaki ilerleme çubuğuyla üç farklı konum seç | TV gönderilen hedeflere gider; telefon hareket edip TV'nin eski konumda devam etmesi başarısız sayılır. | ÇALIŞTIRILMADI |
| SEEK-03 | TV kumandasıyla ileri/geri sar, ardından telefondaki konumu izle | Telefonun konumu TV'nin konumuna güncellenir; gönderen sorgusu eski değeri geri dayatmaz. Gönderen arayüzünün destek sınırı ayrıca kaydedilir. | ÇALIŞTIRILMADI |
| SEEK-04 | Telefon → TV → telefon sırasıyla sar; bunu beş kez tekrarla | Son geçerli seçim uygulanır; geciken önceki komut oynatmayı eski konuma geri atmaz. | ÇALIŞTIRILMADI |
| SEEK-05 | Telefonda duraklat, telefondan sar, oynat; sonra aynı sırayı TV'den yap | Duraklama ve oynatma durumu korunur; ses kaybolmaz, yanlışlıkla yeni oturum açılmaz. | ÇALIŞTIRILMADI |
| SEEK-06 | Başlangıca ve sona yakın konumlara telefondan ve TV'den sar | Konum geçerli süre aralığına sınırlandırılır; negatif/taşan konum, sonsuz bekleme veya gereksiz ana sayfa dönüşü oluşmaz. | ÇALIŞTIRILMADI |
| SEEK-07 | Bir videoda sararken başka videoya geç, yeni videoda telefondan tekrar sar | Eski videonun geç kalan konum/bitiş bildirimi yeni videoyu bozmaz. | ÇALIŞTIRILMADI |
| SEEK-08 | DVR destekli canlı yayında telefondan ve TV'den pencere içinde sar; sonra canlıya dön | Yalnız geçerli canlı pencere kullanılır, pencere yenilenir; yayın bitmiş VOD sayılmaz. | ÇALIŞTIRILMADI |
| SEEK-09 | Sarılamayan canlı yayını aç | Alıcı yanıltıcı süre/sarma çubuğu sunmaz; desteklenmeyen komut bağlantıyı kapatmaz. | ÇALIŞTIRILMADI |
| SEEK-10 | Uzak videodan çıkıp telefon ekranına dön | Normal ekran yansıtma devam eder; eski videonun süre çubuğu veya donuk karesi kalmaz. | ÇALIŞTIRILMADI |
| SEEK-11 | Aynı testleri ikinci bağlantıda tekrar et | Yeni oturumda telefon kontrolleri çalışır; önceki oturumun konumu/komutu kullanılmaz. | ÇALIŞTIRILMADI |
| SEEK-12 | Video daha hazırlanırken telefondan konum seç; ardından görüntünün başlamasını bekle | Erken komutun uygulanıp uygulanmadığı açıkça gözlenir; uygulanmayan hedef başarılı sayılmaz. Hazırlık ve hazır durumdaki sonuçlar ayrı tutulur. | ÇALIŞTIRILMADI |
| SEEK-13 | TV'de sarma taslağı açıkken telefondan başka konuma sar; TV'de önce Geri, tekrarında OK kullan | Geri taslağı iptal eder; OK en son onaylanan TV hedefini uygular. Taslak ile gerçek oynatma konumu karıştırılmaz; eski taslak telefonun komutunu sessizce geri almamalıdır. | ÇALIŞTIRILMADI |

Her sarma için hedef konum, TV'de gözlenen konum, telefonda görülen konum, geçişin kaç saniye sürdüğü ve sesin devam edip etmediği kaydedilir. Sabitlenmiş VOD testinde hedef ile oynatıcı konum farkı oynatma ilerlemesi hesaba katılarak en fazla 2 saniye olmalı; kaynak/anahtar-kare sınırı farklı bir sonuç gerektiriyorsa fark gerekçesiyle ayrı raporlanmalı, otomatik başarılı sayılmamalı. Her iki cihazdaki arayüz tepkisi kadar gerçek görüntünün hedefe geçtiği de kontrol edilir.

## Hata olduğunda ayrıştırma

1. Telefon gerçekten konum komutu gönderiyor mu? Göndermiyorsa gönderen uygulama/aktarım türü ayrılır.
2. Native `/scrub` isteği doğru oturum ve geçerli saniye değeriyle geliyor mu?
3. Kotlin oynatıcıya doğru medya öğesi için komut aktarılıyor mu?
4. Oynatıcının mevcut konumu ve gönderene dönen durum güncelleniyor mu?
5. Ses ve ilk yeni görüntü istenen konuma geliyor mu; eski yanıt yeni konumu geri alıyor mu?

Tanılamada PIN, eşleştirme anahtarı, erişim belirteci veya imzalı içerik adresi kaydedilmez. Gerçek telefondan komut alınmadan “telefondan sarma geçti” yazılmaz.

## Son RC'de çalıştırılan yerel protokol kontrolü

`tests/integration/playback_idle_keepalive.py --seek` son emülatör APK'sında yeniden çalıştırıldı. **GEÇTİ:** gerçek native HTTP üzerinde GET `/scrub`, gönderen benzeri POST ile 2. saniyeye sarma, bozuk sorgu, eski oturum ve değişen oturuma HTTP 400; video sonunda aynı TCP bağlantısında üç durum sorgusu. Kanıt: `artifacts/product-polish-20260910/native-sender-seek-final2.log`. Bu test gerçek iPhone arayüzü, dublaj/ses veya canlı DVR kabulü değildir.

Salt okunur kod incelemesinde iki açık sınır bulundu: native `/playback-info` Media3'ün gerçek sarılabilirlik/aralık bilgisini taşımadan süreyi sarma aralığı olarak bildiriyor; zaman çizelgesi hazır olmadan gelen sarma isteği ertelenmek yerine atlanıyor. Bu iki durum **düzeltilmiş veya kabul edilmiş sayılmadı**; SEEK-08/09/12 ile ayrı doğrulanmalı ve genel dağıtım öncesinde ele alınmalı. “Sender seek applied” günlüğü TV'nin kendi sarma işleminde de üretilebildiği için yalnız bu satır telefondan komut geldiğinin kanıtı değildir.

## Eski/yeni TV kontrol matrisi

Yaş tek başına uyumluluk ölçütü değildir. Minimum Android 7/API 24, Android TV/Google TV işletim sistemi ve ARM32/ARM64 gerekir. Samsung Tizen, LG webOS gibi Android olmayan TV sistemleri bu APK'yı çalıştırmaz; bağlı Android TV kutusu kendi özellikleriyle değerlendirilir.

Gerçek kabulde en az bir ARM32/eski Android TV ve bir ARM64/güncel Google TV üzerinde kurulum, açılış, ekran yansıtma, ses, yukarıdaki sarma senaryoları ve yeniden bağlantı ayrı sınanmalı. Decoder adı/donanım desteği, çözünürlük, RAM ve ağ türü kaydedilmeli. Eski cihazda yüksek çözünürlük/HEVC/4K/60 FPS garanti edilmez; desteklenen profil seçilir. TV açılışı ve arka plandan otomatik görünürlük ayrıca üretici izinlerine bağlıdır.
