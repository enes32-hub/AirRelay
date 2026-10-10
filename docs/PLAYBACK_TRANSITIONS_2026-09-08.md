# AynaTV — video geçişleri, oynatıcı ve doğrulama

## Son gerçek iPhone bulgusu — kabul başarısız

Bu bulgulardan sonra decoder/yüzey düzeltmeleri uygulandı; ayrıntı ve yeni test kanıtları `docs/FREEZE_RECONNECT_FIX_2026-09-08.md` içindedir. Gerçek iPhone tekrar kabulü hâlâ beklenmektedir.

2026-09-08 kullanıcı testinde video kapatıldıktan sonra telefon görüntüsüne dönüş dondu; bağlantı kesilip yeniden açılınca da ilk görüntü gelmeden bekleme göstergesinde kaldı. İki hata henüz çözülmedi. Birinci fotoğraf ve yeniden başlatmadan alınan decoder günlükleri `artifacts/iphone-acceptance-20260908/01-photos-return-reconnect/` altında korunuyor. Aşağıdaki yerel test başarıları bu gerçek iPhone senaryolarının geçtiği anlamına gelmez.

## Kapsam

iPhone → Android TV geliştirme sürümü. Hedef emülatör `aynatv_tv_api36`, seri `emulator-5584`; Xiaomi TV, ARM ve release kabulü yapılmadı. Emülatör yeniden başlatılmadı, uygulama güncelleme olarak kuruldu. Teslim APK'sı ve kesin özeti `artifacts/transition-fix/delivery.json` içinde tutulur.

Teslimde alıcı yeniden açıldı, Türkçe görünüm ve `hw.keyboard=yes` doğrulandı. Test sürecinin çökmesinden kalan İngilizce görünüm arayüzden Türkçeye geri alındı. Eşleştirme anahtarı özeti başlangıçla aynı. Windows köprü kontrolünde RTSP `/info` 200 ve mDNS yanıtı alındı (`bridge-check.json`); bu kontrol iPhone'dan yapılmadı.

## Değişiklikler

- Yayın ekranının ömrü tek videodan ayrıldı. Medya hazırlama/bitirme arasında ana sayfaya otomatik dönüş kaldırıldı. Açık yayın sırasında ayar taslağı uygulanamaz. Gerçek bağlantı sonu ve kullanıcının ana sayfaya dönmesi ayrı tutulur.
- Son kontrol bağlantısının kapanmasına iki saniyelik bekleme eklendi. Etkin mirroring veya yakın tarihli medya sorguları varsa yayın korunur. Gerçek mirroring bildirimi, sıradan kontrol sıfırlamasından önceliklidir. PIN'in ayrı ömrü korunur.
- Boyut bilgisi mirroring başlangıcından önce gelirse oturum hazırlanır; sonraki aynı başlangıç bildirimi decoder'ı tekrar başlatmaz.
- Decoder'ın adaptif boyut sınırı ilk dikey kareye sabitlenmez. Desteklenen boyut zarfı kullanılır; sınır dışı değişiklikte yapılandırılmış anahtar kare beklenir. SPS/PPS ve HEVC parametreleri önbelleğe alınır; yalnız yapılandırma paketi anahtar kare sayılmaz.
- Üç ardışık dolu giriş kuyruğu sonrasında decoder kontrollü sıfırlanır. Bu toparlanma göndericiden yeni anahtar kare gerektirir; kaynak anahtar kare üretmezse yeniden bağlanma gerekebilir. iPhone üzerinde bu hata yolu henüz doğrulanmadı.
- İlk kare oturum/decoder kökeniyle doğrulanmaya devam eder. Ekrana bağlı olmayan yüzeyde de dönüşüm matrisi güncellenir. Ses motorunun zamanlaması değiştirilmedi.
- Bekleme yazısı kaldırıldı, siyah zemin üzerinde dönen gösterge eklendi. Uzak videoda ilk gerçek kare ayrı izlenir. Sabit/duraklatılmış kare hareket yok diye bekleme durumuna alınmaz.
- Sarılabilir uzak videoda süre ve kumanda çubuğu eklendi: sağ/sol 10 saniyelik önizleme, OK uygulama, Geri iptal. Canlı DVR yalnız mevcut pencere içinde sarılır; canlı ekran/oyun yansıtmasına sarma çubuğu eklenmez.
- Düğmelerde Yukarı doğrudan sarma çubuğuna, çubukta Aşağı güvenli düğmeye gider. Odak görünür çerçeveyle belirtilir; yeni medya öğesinde kontrol odağı yenilenir. Önizleme/OK testi, yalnız tuş göndermekle kalmaz; görünen hedef süreyi ve gerçek oynatma konumunu kontrol eder.
- Media3 işlemleri ve dinleyicileri nesil kimliğiyle korunur. Eski bitiş/hata dinleyicisi yeni oynatımı etkileyemez. Hata kodu URL/erişim belirteci yazılmadan raporlanır.

## Medya protokolü ve sözleşme

- `PlaybackSnapshot` ve servis görünümü medya kimliği, ilk kare, canlılık ve sarılabilir pencere bilgilerini taşır.
- İki ek JNI callback vardır: `onVideoPrepare(session, item)` ve `onVideoSelection(session, item, json)`. Mevcut JNI native metot imzaları değişmedi. Eşleştirme, kimlik doğrulama, şifreleme, portlar ve keşif özellik bitleri değiştirilmedi.
- Android HLS yolunda alternatif seslerin tek dile indirgenmesi kaldırıldı. `selectedMediaArray` doğrulanan oturum/öğe üzerinden aktarılır. Başlangıçtan önceki seçim saklanır; parça listesi hazır olunca uygulanır.
- Seçim çözümleyicisi kimlik, dil, isim ve grup alanlarını destekler. Eksik/belirsiz eşleştirmede ilk parça rastgele seçilmez. Yalnız indeks veya desteklenmeyen başka bir yapı gönderen istemciler için seçim uygulanmaz ve kayıt düşülür. Gerçek YouTube seçim mesajıyla uyumluluk henüz kanıtlanmadı.
- FCUP istek kimlikleri öğeler arasında benzersizdir; eski, tekrarlanan ve istenmemiş yanıtlar reddedilir. HTTP sonuç kodu log düzeyinden bağımsız kontrol edilir. Uygun kimlik taşıyan sarma/hız/durdurma komutları mevcut oturumla eşleştirilir; protokolün öğe kimliği taşımadığı komutlarda mutlak eski-komut ayrımı iddia edilmez.
- Yeni medya hazırlığında önceki videonun `duration=-1` bitiş bilgisi temizlenir. Medya kontrol soketi kapanması tek başına durdurma sayılmaz.
- Hazırlanırken durdurulan medya için bekleyen FCUP yanıtları iptal edilir; Kotlin de geç gelen başlatma callback'ini reddeder. Yeniden kullanılan tamamlanmış playlist yeni istek kimlikleriyle devam eder; yarım kalmış playlist tamamlanmış önbellek sayılmaz.
- Canlı yayın için belirtilmemiş başlangıç konumu Media3'ün canlı varsayılanına bırakılır. Süresi bilinmeyen yayın bitmiş sayılmaz. Takılı playlist yenilemesi yeniden denenebilir; medya hazırlığı 30 saniyede sonuçlanmazsa hata gösterilir.

## Doğrulama

Son, makine tarafından okunabilir Android sonuçları `artifacts/transition-fix/android-results.json`; ham test çalışmaları aynı klasördeki ayrı günlüklerdedir. Önceki teslim raporları değiştirilmedi.

| Kontrol | Sonuç / sınır |
|---|---|
| Android x86_64 debug ve test APK derlemesi | Geçti |
| Host ürün mantığı / etkileşim / Python | 76 / 58 / 44 kontrol geçti |
| C++ günlük gizliliği | 15 kontrol geçti |
| Ekran oturumu / ilk kare / günlük zamanı | 15 / 17 / 6 kontrol geçti |
| Annex B yapılandırma ve anahtar kare ayrımı | 9 kontrol geçti; gerçek HEVC decoder testi değildir |
| Native FCUP kimliği, eski/tekrar/sırasız yanıt | Yerel C testi geçti |
| Gerçek Activity/Service, PIN, odak ve medya geçişleri | Android instrumentation senaryoları geçti; oturum girişleri sentetiktir |
| Gerçek AVC decoder → SurfaceTexture → EGL | Üç oturumda değişen RGBX görüntü doğrulandı |
| 498×1080 → 1920×886 → 498×1080 | Üç tur, dokuz geçiş geçti; her geçişte değişen görüntü, sıfır giriş karesi düşmesi |
| Media3 üzerinde ardışık video | 10 yerel HTTP videosu geçti; eski dinleyici çağrıları yeni öğeyi bitirmedi |
| Alternatif ses | İki yerel HLS ses parçası seçimi doğrulandı; işitilen ses veya iPhone komutu kabulü değildir |
| Canlı HLS | Liste yenilenmesi ve en az 12 saniye devam eden decoder çıktısı doğrulandı; gerçek YouTube canlı yayını değildir |
| Performans | Ana iş parçacığındaki doğrudan başlat/durdur çağrıları ölçüldü; değerler Android raporunda. FPS, TV performansı veya uçtan uca gecikme kabulü değildir |
| Gerçek iPhone Fotoğraflar/YouTube/oyun | Çalıştırılmadı; kullanıcı kabulü bekliyor |

Instrumentation çalışırken alınan `gfx-during-validation.txt` örneğinde 732 arayüz karesinin 185'i janky (%25,27); p50 23 ms, p95 117 ms. Bu, hızlı başlangıç/durdurma, sayfa geçişleri ve yerel testlerin birlikte yürüdüğü debug emülatör örneğidir; normal yansıtmada video FPS'si veya TV performansı değildir. Arayüz performansı kusursuz/nihai kabul edilmiş sayılmaz.

İlk iki medya testi denemesi başarısızdı: test sunucusunun IPv4/IPv6 adres uyumsuzluğu ve test ImageReader piksel biçimi. Hatalı denemelerin günlükleri saklandı; son başarılı sonuçlarla üzerleri yazılmadı. Test görüntüleri `screenshots` klasöründedir; renkli desen uygulamaya gömülü demo ekranı değil, Android test paketinden oynatılan gerçek yerel test videosudur.

Sonraki daha sıkı kumanda kontrolü sırasında odak yönlendirmesi açık hale getirildi. Eski test yalnız iptal sırasında konumun korunmasını kontrol ediyordu; genişletilmiş test önizlemeyi ve OK ile konum değişimini de doğrular. Erişilebilirlik önbelleğinin ekrandaki yeni hedef yerine eski `0:00` değerini döndürdüğü, ekran görüntüsü ve düğüm dökümü karşılaştırılarak saptandı. Doğrulama düğümleri `refresh()` ile yeniler ve sayısal `RangeInfo` değerini okur. Hedefli üç video testinde önizleme, iptal ve OK ile sarma geçti. Başarısız denemeler de ayrı günlüklerde korunur.

## Geri dönüş ve iPhone kabulü

Kaynak yedeği `before-source-verified.zip`, önceki kurulu paket `before-installed.apk`. Yedek doğrulaması `backup.json`; korunmuş kaynaklar `source-audit.json`. İlk arşiv aracının yinelenen manifest hatası giderilirken asıl manifest korundu, ek dışa aktarma manifesti ayrı adla saklandı.

Telefon testinde bağlantıyı kesmeden üç kez şu sıra izlenmeli: telefon ekranı → Fotoğraflar'da üç video → telefon ekranı → YouTube'da iki video → telefon ekranı. Ardından canlı yayın, iki farklı dublaj seçimi, dikey/yatay geçiş, sorunlu oyun, durdurma/yeniden bağlanma ve 10 dakika normal yansıtma kontrol edilmeli.

Açılmayan YouTube canlı yayınının bağlantısı ve sorunlu oyunun adı henüz verilmedi. Bu örnekler denenmeden onların düzeldiği söylenemez. Oyunun/iOS'un kendi harici ekran davranışını alıcının kesin kontrol edebildiği veya DRM desteği eklendiği iddia edilmez.
