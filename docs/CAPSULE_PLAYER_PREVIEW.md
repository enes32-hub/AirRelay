# HTML kapsül tasarımı — emülatör önizlemesi

## Son düzeltme: kayıt düğmesi ve oynatma pencereleri

Kullanıcının açıklamasıyla kayıt başlat/durdur düğmesi her iki yayın türünde
Döndür'ün hemen sağına geri eklendi. Kaydedilenler kısayolu panelde yok;
kayıt penceresi yalnız kalite ve başlatma seçeneklerini gösterir, kütüphaneyi
yüklemez. Ana ekrandaki kayıtlar korunur. Oynatma sırasında açılan kayıt,
oynatma şekli, kalite/ses/altyazı ve onay pencerelerine yalnız renkleri
değiştiren mint/füme paleti uygulandı; ana ekran, boyutlar ve tipografi aynı.

Güncel APK: `artifacts/releases/AirRelay-R35-capsule-record-emulator.apk`.
Derleme ve Türkçe gerçek yerel video UI testi geçti: `capsule-record-build.log`,
`capsule-record-test.txt`. Test, Döndür'den sağ tuşla kayda erişimi, kayıt
kalite penceresini ve kütüphane bulunmamasını doğrular. Bu turda yeni MP4
kaydı başlatılmadı; mevcut kayıt motoru değiştirilmedi. Görsel kontrol:
`capsule-quality-mint.png`, `capsule-record-mint.png`. Yalnız emülatör güncellendi.

Aşağıdaki bölüm önceki önizlemenin tarihsel raporudur; kayıt düğmesiyle
ilgili kaldırma kararı yukarıdaki son düzeltmeyle geçersiz kılınmıştır.

Kaynak: kullanıcının `ayna_tv_onizleme.html` tasarımı. Yalnız yayın kontrolleri
Compose ile uyarlandı; HTML'nin örnek diyalogları, içerik bilgileri, demo
ayarları ve marka adı taşınmadı. Önceki kutulu cam tasarımı kullanılmadı.

## Değişiklik

- Yarı şeffaf kapsül, dairesel ikonlar, mint odak ve ortalanmış oynatma grubu.
- Doğrudan videoda gerçek yeteneklere bağlı sarma/parça kontrolleri;
  ekran yansıtmada küçük panel. Desteklenen PiP korunuyor.
- Kayıt başlat/durdur ve kayıt listesi izleme panelinde yok. Ana kayıt
  kütüphanesi, dosyalar ve kayıt altyapısı değiştirilmedi.
- Düğme açtığı diyalogdan geri dönüş odağını hatırlar; büyük yazı paneli
  yatay kaydırır. Metin boyutu küçültülerek erişilebilirlik ayarı bastırılmaz.
- Decoder, ses, AirPlay ve eşleştirme kodları değiştirilmedi. Gerçek zamanlı
  video blur/kopyalama eklenmedi. Mevcut gizleme ve hata politikası korunuyor.

## APK

`artifacts/releases/AirRelay-R35-capsule-preview-emulator.apk`

SHA-256: `2045F746B22ABB273B24FF79250FF46DF1B7C2DD697323F2B090EFFB34D87941`

R35 / versionCode 70 tabanlı ayrı önizleme. Eski `AirRelay-R35-emulator.apk`
değiştirilmedi. Yalnız emulator-5584'e veri silmeden güncelleme kuruldu;
televizyona dokunulmadı. Test APK'sı kaldırıldı, font ölçeği önceki 1.0'a
ve dil tercihi test öncesi değerine döndürüldü. Ana uygulama yeniden açıldı.

## Geçti

- x86_64 debug ve Android test derlemeleri: `artifacts/capsule-build.log`.
- Gerçek MediaCodec test görüntüsü ve gerçek yerel Media3 videosu üzerinde
  panel, ilk odak, diyalog dönüş odağı, kayıt düğmelerinin yokluğu,
  kumandayla parça/ayar/bitir düğmelerine erişim, sarma taslağı iptali,
  yukarıyla gizleme ve OK ile yeniden açma.
- Türkçe ve İngilizce: `capsule-ui-tr.txt`, `capsule-ui-en.txt`.
- Sistem yazı ölçeği 1.3 / Türkçe: `capsule-ui-large-tr.txt`; kaydırılarak
  sağdaki son düğmeye kadar erişim doğrulandı.
- Sarılamayan canlı yayın yetenekleri enjekte edildiğinde sarma düğmeleri
  gizleniyor. Bu, gerçek AirPlay canlı yayın kabulü değildir.
- Renkli, açık/koyu bölgeleri bulunan gerçek yerel video kareleri üzerinde
  ekran görüntüleri incelendi; yalnız siyah zemin kullanılmadı.

Görüntüler: `artifacts/capsule-direct-tr.png`, `capsule-mirror-tr.png`,
`capsule-large-tr.png`. İlk taslakla ilgili eski cam panel raporu ayrı tutulur.

## Bulunanlar ve sınırlar

Fare/erişilebilirlik tıklaması diyalogu açarken son odağı kaydetmiyordu;
düğme tıklamasında dönüş hedefi kaydedilerek giderildi. Testlerde diyalog
açılışını ve sarma taslağını beklememe, yerelleştirilmiş kaynak yerine temel
kaynak kullanma, erişilebilirlik odak önbelleği ve canlı yetenek enjeksiyonunun
gerçek video callback'iyle yarışması düzeltildi; son tekrarlar geçti.

Gerçek iPhone kabulü, PiP'nin gerçek cihazdaki davranışı, hata başına beş
vurgu ve beş saniyelik otomatik gizlemenin bu tasarımla ayrı zaman ölçümü
bu turda yapılmadı. Bu iki davranışın mevcut kodu korunmuştur; test edilmiş
gibi sunulmaz. ARM/TV APK üretilmedi.
