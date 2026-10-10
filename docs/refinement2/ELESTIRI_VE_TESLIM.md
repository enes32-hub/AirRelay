# AynaTV — İkinci ağır inceleme ve kaynak revizyonu

**Tarih:** 7 Eylül 2026  
**Referans:** `AynaTV_Ilk_4_Asama_Guncellenmis_Kaynak.zip`  
**Kapsam:** Önceden izin verilen 1–4. aşamaların ikinci kalite turu. 5. aşama ve sonrasına geçilmedi.  
**Teslim durumu:** Kaynak kodu düzenlendi; Android/Compose derlemesi, emülatör, APK/AAB, fiziksel cihaz veya mağaza kabulü yapılmadı.

## 1. Eleştiri: Önceki teslim neden yeterli değildi?

Önceki teslim teknik paneli daha anlaşılır hale getirmişti; ancak etkileşim sistemi henüz yeterince olgun değildi. Yuvarlatılmış kartlar ve koyu tema, basıldığını belli etmeyen düğmeleri, kaybolan odağı veya niyeti belirsiz ayar kaydetmeyi telafi etmez. Bu sürümü yalnız renkleri nedeniyle “modern ve akıcı” saymak doğru olmazdı.

En önemli problem, görünüş ile davranışın birbirini desteklememesiydi: işlem yapmayan rehber satırları odak alırken gerçek düğmeler basma tepkisi vermiyordu. Hazır alıcıda baskın düğmenin Durdur olması yanlış önceliklendirmeydi. Bir sayfadan dönünce kullanıcıyı ilk öğeye atmak da kumandalı cihazda gereksiz gezinme yaratıyordu.

Daha ciddi riskler de vardı. Kontrolleri açan tuşun bırakma olayı kaybolursa sonraki basışlar yutulabiliyordu. Birden fazla diyalog aynı anda çizilebiliyordu. Ayarların kalıcı diske yazılması ana iş parçacığındaydı. Debug toplama gereksiz yere çoğaltılmıştı. Bunlar sadece estetik sorunlar değildir.

Aşağıdaki bulgular yüklenen önceki kaynakla doğrudan karşılaştırılmıştır. Olası çalışma zamanı etkileri ölçülmüş sonuç olarak sunulmamıştır.

## 2. Bulgular ve uygulanan düzeltmeler

| Kimlik | Öncelik | Önceki eksik | Bu revizyonda uygulanan |
|---|---|---|---|
| R2-01 | Yüksek | Basma geri bildirimi yoktu; odaklanma ile basılma aynı deneyimdi. Önce: `ui/ReceiverComponents.kt:55` | Basma/ripple, odak ve fare üzerine gelme durumları ayrıldı; animasyonlu ölçek çizim katmanına taşındı. Sonra: `ui/ReceiverComponents.kt:10` |
| R2-02 | Orta | Rehber metinleri işlem yapmadığı halde kumanda odağı alıyordu. Önce: `ui/ReceiverHome.kt:138` | Rehber adımları pasif açıklama olarak bırakıldı; yardım düğmesi gerçek eylem olarak kaldı. Sonra: `ui/ReceiverHome.kt:134` |
| R2-03 | Yüksek | Sayfaya dönüşte ilk öğeye odaklanılıyor; sayfa durumu ve odak kimliği birlikte saklanmıyordu. Önce: `ui/ReceiverHelp.kt:31` | Sayfa başına SaveableStateProvider, kayıtlı odak kimlikleri, yerleşim karesi bekleme ve geçerli yedek hedefler eklendi. Sonra: `ui/TvFocusMemory.kt:21` |
| R2-04 | Orta | Hazır ve boş alıcıda en baskın eylem Durdur idi. Kullanıcı ana işi yaparken alıcıyı kapatabiliyordu. Önce: `ui/ReceiverHome.kt:100` | Başlat / İçeriğe dön / Bağlantı rehberi ayrıldı. Durdur ikincil ve uyarı renkli; meşgul oturumda onaylı. Sonra: `receiver/InteractionPolicy.kt:19` |
| R2-05 | Yüksek | Kontrolleri açan tuşun bırakma olayı kaybolursa aynı tuşun sonraki basışları yutulabiliyordu. Önce: `ui/MainScreen.kt:197` | Yeni fiziksel basış eski kilidi temizliyor; açılış basışı, tekrarı ve bırakması yeni düğmeyi yanlışlıkla çalıştırmıyor. Sonra: `receiver/InteractionPolicy.kt:26` |
| R2-06 | Yüksek | Kontrollerin kaybolması yalnız tuş sayacına bağlıydı; basılı kontrol, erişilebilirlik süresi ve pointer etkileşimi yeterince hesaba katılmıyordu. Önce: `ui/MainScreen.kt:181` | Odak, tıklama, dokunma ve basma süreyi yeniliyor. Bekleme, ağ sorunu, modal veya basılı kontrol varken otomatik gizleme yok. Sonra: `ui/MainScreen.kt:240` |
| R2-07 | Yüksek | Kurulum, PIN ve durdurma pencereleri ayrı koşullarla çiziliyordu; yerel ayar pencereleri de bağımsızdı. Önce: `ui/MainScreen.kt:154` | PIN öncelikli tek global pencere seçimi; alttaki sayfaya giriş engeli ve diyalog için ayrı etkin kontrol kapsamı. Sonra: `ui/MainScreen.kt:166` |
| R2-08 | Orta | Gelişmiş sayısal alanlar odak kaybında kaydediliyordu. Düzenleme ile uygulama niyeti belirsizdi. Önce: `ui/SettingsScreen.kt:489` | Açık Kaydet eylemi, geçersiz giriş açıklaması, geçersiz IME Done ile formun kaybolmaması. Sonra: `ui/SettingsScreen.kt:476` |
| R2-09 | Orta | Temel ayarlar uzun ve tek parça bir listeydi; bağlantı, başlangıç ve görünüm birbirine karışıyordu. Önce: `ui/ReceiverSettings.kt:40` | Üç kategori, geniş ekranda yan seçim, dar ekranda yatay seçim; ayrı liste konumları. Sonra: `ui/ReceiverSettings.kt:57` |
| R2-10 | Yüksek | Ayarların kalıcı commit işlemi alıcı başlatılırken ana iş parçacığında yapılıyordu. Önce: `service/AirPlayService.kt:454` | Kalıcı yazma IO üzerinde. Taslak yazma/discard kilit doluyken beklemiyor; reddedilen işlem kaydedilmiş gibi gösterilmiyor. İptal edilen başlangıç token ile canlanmıyor. Sonra: `service/AirPlayService.kt:471` |
| R2-11 | Yüksek | Debug bilgisi hem her servis güncellemesinde hem ayrı saniyelik döngüde toplanıyordu. Önce: `viewmodel/MainViewModel.kt:634` | Tek saniyelik toplama yolu; yalnız debug açık ve günlük sayfası STARTED yaşam döngüsündeyken. Sonra: `viewmodel/MainViewModel.kt:714` |
| R2-12 | Orta | Canlı günlük listesi okunurken değişebiliyor; temizleme ve dışa aktarım tekrar tekrar başlatılabiliyordu. Önce: `ui/LogsScreen.kt:26` | Sabit okuma anlık görüntüsü, açık yenileme ve isteğe bağlı takip; export/clear meşgul kapısı. Sonra: `ui/LogsScreen.kt:61` |
| R2-13 | Yüksek | Ağ servis kaydı hiç sonuçlanmazsa görünür bekleme belirsiz süre sürebiliyordu. Önce: `discovery/NsdServiceManager.kt:36` | 15 saniyelik yalnız gözlem süresi ve sonuç geri bildirimi; tekrar eden yenileme engeli; eski kayıt callbackleri yenisini etkileyemiyor. Sonra: `discovery/NsdServiceManager.kt:88` |
| R2-14 | Yüksek | Servis bağlantısı kesilince bazı video/oynatım alanları eski değerleri taşıyordu. Önce: `viewmodel/MainViewModel.kt:458` | Eski oynatım, bekleme, metadata, eylem zamanı ve debug durumu temizleniyor. Sonra: `viewmodel/MainViewModel.kt:513` |
| R2-15 | Orta | Bildirim izni çağrısı kurulumdan önce, kullanıcı arka plan tercihini yapmadan geliyordu. Önce: `MainActivity.kt:68` | İzin çağrısı kurulum sonrasına ve arka plan/boot tercihine bağlandı; uygulamanın tekrar istemesini önleyen kayıt eklendi. Sonra: `MainActivity.kt:67` |
| R2-16 | Yüksek | Activity dururken boşluk yalnız bağlantı sayısı üzerinden değerlendiriliyordu; bekleyen başlangıç/medya durumu eksik kalabiliyordu. Önce: `MainActivity.kt:158` | Arka plan kapalıysa durdurma kararı starting/running ve sessionBusy durumlarına dayanıyor; meşgul oturum korunuyor. Sonra: `MainActivity.kt:171` |
| R2-17 | Orta | Seçilecek alıcı adı kırpılabiliyor; kullanıcı tam eşleşme bilgisini kaybedebiliyordu. Önce: `ui/ReceiverHome.kt:76` | Ana kimlik kartında gerçek ilan edilen ad tam gösteriliyor; uzun ad ve büyük yazı için kaydırılabilir dar düzen eklendi. Sonra: `ui/ReceiverHome.kt:62` |
| R2-18 | Orta | Görsel sistem çok sayıda eşit ağırlıklı teknik kart ve düğmeye dayanıyordu; odak/sekme/seçim rolleri ayrışmıyordu. Önce: `ui/theme/Theme.kt:13` | Grafit tema, kontrollü turkuaz vurgu, asimetrik kimlik/rehber alanları, özel TV/telefon vektör sahnesi, kategori düzeni ve kısa giriş geçişleri. Sonra: `ui/ReceiverScene.kt:18` |

Her bulgunun önce/sonra kod kesiti ve test sınırı `docs/refinement2/KAYNAK_KANITLARI.md` dosyasındadır. Kaynak farkı önceki teslim esas alınarak hazırlanmıştır; orijinal ilk ZIP ile karıştırılmamalıdır.

## 3. Görsel düzenin yeni yapısı

Ana ekran artık öncelikle alıcının kimliğini ve bağlanma yolunu gösterir. Bir tarafta başlık, tam alıcı adı ve doğrulanmış durum; diğer tarafta özel çizilmiş TV/telefon sahnesi ve üç kısa bağlantı adımı bulunur. Durdur birincil eylem değildir. Kullanıcının gerçek ihtiyacına göre Başlat, İçeriğe dön veya Bağlantı rehberi öne çıkar.

Grafit arka plan, tek turkuaz vurgu, ikincil leylak tonları, kontrollü çizgiler ve daha belirgin boşluklar kullanıldı. Kumanda odağı, seçili sekme ve basılı düğme farklı görsel durumlar olarak işlendi. Vektör sahnesi uygulamanın Compose Canvas kodudur; dışarıdan indirilecek bir görsel veya temsili Android ekran görüntüsü değildir.

Ana sayfa genişlik ve yazı ölçeğine göre tek sütuna döner. Tam alıcı adı kırpılmaz; en uzun adlar için satır kırılması ve kaydırma kullanılabilir. Ayarlar Bağlantı, Başlangıç ve Görünüm gruplarına ayrıldı. Gelişmiş ayarlar ayrı ekranda kaldı.

Basma tepkisinin 70 ms, odak ölçek geçişinin 130 ms, sayfa girişinin 160 ms sürmesi kodda tanımlı tasarım süreleridir. **Bu değerler ölçülmüş tıklama gecikmesi veya cihaz performansı değildir.** Azaltılmış hareket seçeneği ölçek ve sayfa geçişlerini kapatır. Sürekli çalışan bir arka plan animasyonu veya canlı görüntü üstüne bulanıklık eklenmedi.

`ReceiverPreviews.kt` içinde hazır, durmuş, ağsız, uzun adlı ve büyük yazılı görünümler için Preview tanımları vardır. Bunlar bu ortamda Android Studio ile render edilmedi. Teslime uygulama ekran görüntüsü diye sunulan bir maket konulmadı.

## 4. İşlevsel kararlar

**Kumanda:** Gizli kontrolleri açan OK/yön basışı aynı anda yeni düğmeyi çalıştırmaz. Tekrar ve bırakma olayları korunur; eski bırakma olayı kaybolmuşsa yeni fiziksel basış kullanılabilir. Ses ve medya tuşları mevcut Android medya oturumunda kalır. Back, alt sayfada üst sayfaya döner; oynatma görünümünde önce kontrolleri gizler, sonra ana sayfaya döner.

**Odak ve konum:** Sayfa durumu kaydedilir. Son seçili kontrol halen oluşturulmuş ve etkinse öncelikle ona dönülür; değilse geçerli yedek hedefler denenir. Odak isteğinin hata vermemesi başarı sayılmaz; gerçek `onFocusChanged` sonucu beklenir. Bu mantığın aday sırası testlidir; Compose'un gerçek cihazdaki odak geometrisi henüz testli değildir.

**Pencereler:** PIN, kurulum ve durdurma onayları tek global öncelik sırasından seçilir. PIN global olarak önceliklidir. Yerel düzenleme/onay pencereleri sırasında alttaki sayfanın kontrolleri devre dışıdır; diyalog kontrolleri ayrı etkin kapsamda kalır. Yıkıcı onaylarda güvenli iptal düğmesi ilk odağı alır.

**Ayarlar:** Aktarımı etkileyen ayarlar yine taslaktır; yayın sürerken sessiz yeniden başlatma yoktur. Bekleyen değişiklikler açıkça uygulanabilir veya taslaklardan vazgeçilebilir. Kilitli kalıcı yazma sırasında kabul edilmeyen ayar değişikliği arayüzde kaydedilmiş gibi gösterilmez. İptal edilen başlatma, eski disk işlemi tamamlandığında native alıcıyı yeniden başlatamaz. Tamamlanmış disk işlemi sonrası bekleyen-ayar durumu ayrıca tazelenir.

**Keşif:** Yeni 15 saniyelik süre sadece Android servis kaydı sonucunu gözler. AirPlay paket zaman aşımı değildir, protokol TXT alanını değiştirmez, bağlantıyı kesmez ve alıcıyı yeniden başlatmaz. Zaman aşımından sonra Android geç de olsa başarı bildirirse gerçek kayıt sonucu gösterilebilir. Bu turun geliştirilmesi sırasında, eski unregister callbackinin yeni gözlem süresini iptal etme riski de bulunup giderildi; buna özel host regresyon kontrolü eklendi.

**Günlükler:** Kullanıcı okumayı seçtiğinde satırlar sabit kalır. Takip açık olsa bile günlük listesi odaklıyken otomatik kaydırma yapılmaz. Dışa aktarım ve temizleme eşzamanlı/tekrarlı başlatılmaz. Debug native anlık görüntüsü yalnız debug açık, günlük sayfası mevcut ve Activity en az STARTED durumundayken saniyede bir istenir. Önceki gizlilik filtreleri korunur.

**Kurulum ve izin:** Açılış deneyimi sadeleştirildi. Bildirim izni çağrısı kurulum sonrası, kullanıcının boot/arka plan tercihi olduğunda ve uygulama henüz istememişse yapılır. İzin reddi alıcının çalıştığına dair yanlış bir iddiaya dönüştürülmez. Sistem izin ekranının gerçek Android davranışı bu turda sınanmadı.

## 5. Aktarım çekirdeğinin korunması

Bu turda native aktarım kaynakları, JNI bildirimleri, ses yolu, codec/renderer sınıfları, kalıcı VideoSurfaceView, port/codec varsayılanları, Gradle ve Android manifesti değiştirilmedi. `docs/refinement2/PREVIOUS_DELIVERY_PROTECTED_SHA256.json`, **10.581 dosyanın** önceki teslimle bayt düzeyinde aynı olduğunu denetler. Bu liste üçüncü taraf kaynaklarını ve ilgili yapılandırma dosyalarını da kapsar; sayı yalnız C++ dosyası sayısı değildir.

Önceki ürünleştirme koruması da tekrar çalıştırıldı: **10.579 orijinal korunan dosya**, beş dosyadaki önceki sınır eklemelerinin tersine çevrilebilirliği ve servis JNI çağrılarının ad/argüman/adet sözleşmesi geçti. Bu beş önceki ekleme log gizliliği ve ilk tampon gönderimi gözlemiydi; bu turda değiştirilmediler. İki listedeki sayılar büyük ölçüde örtüşür; toplanacak ayrı dosya sayıları değildir.

Android tarafında servis başlatma ayar hazırlığı ve NSD sonuç gözlemi düzenlendi. Bu nedenle “tek bir servis satırına bile dokunulmadı” denmiyor. Korunan şey protokol/paketler, medya motoru, varsayılan aktarım parametreleri ve native çağrı sözleşmesidir. Dosya hashlerinin eşit olması tek başına uçtan uca bağlantı kalitesi ispatı değildir.

## 6. Gerçekte çalıştırılan kontroller

| Kontrol | Sonuç | Kanıtladığı ve kanıtlamadığı |
|---|---|---|
| Önceki Kotlin/JVM host kontrolleri | 76 geçti | Durum, taslak, komut yönlendirme, günlük mantığı; Android uygulama derlemesi değil. |
| Yeni revizyon host kontrolleri | 58 geçti | Gerçek karar sınıfları, taslak kilit davranışı, odak aday sırası ve NSD sarmalayıcı; Android sınıfları açık test doubles. |
| C++ günlük çıkış adaptörü | 15 geçti | Bağımsız gizlilik adaptörü; alıcı/native kitaplığın Android derlemesi değil. |
| Mevcut Python hazırlık testleri | 44 geçti | Hazırlık araçlarının mantığı; medya performansı testi değil. |
| Kotlin sözdizimi | 54 dosya, 0 ayrıştırıcı hatası | Tam Android/Compose tip çözümleme veya bağımlılık doğrulaması değil. |
| XML ve dil kaynakları | 19 XML, 336 TR/EN metin çifti | Kaynak ayrıştırma ve referans tutarlılığı; TV görünüm kabulü değil. |
| Yeni statik etkileşim korumaları | 16 geçti | Önemli kod yollarının bağlı ve korumaların mevcut oluşu; kullanıcı etkileşimi yürütmesi değil. |
| Çekirdek ve teslim bütünlüğü | Geçti | Dosya karşılaştırması; gerçek iPhone akışı değil. |

Yeni host paketinde 5.000 deterministik açma/tuş bırakma dizisi ve 32 otomatik gizleme politika kombinasyonu da vardır. Bunlar 58 test başlığının içindeki döngülerdir; “5.000 TV testi” değildir. Host Kotlin derleyicisi 1.9.0'dır; projenin Android Kotlin/Compose zincirinin yerine geçmez.

Sonuç kayıtları `docs/refinement2/check-results/` altındadır. Testler geçsin diye aktarım koruma listeleri gevşetilmedi. Önceki `check_source.py` içindeki tek eski arayüz yapısı kontrolü, ayrıştırılan `RemoteWakeGuard` karar sınıfına işaret edecek şekilde güncellendi; asıl hash ve JNI denetimleri aynen korundu. `docs/productization/` ilk turun tarihsel kanıtlarıdır; bu revizyon için `docs/refinement2/` esas alınmalıdır.

Tekrar çalıştırma (proje kökünde):

```sh
python scripts/productization/check_source.py
python scripts/refinement2/check_source.py
python scripts/productization/check_syntax.py
python scripts/productization/check_host.py
python scripts/refinement2/check_interactions.py
PYTHONDONTWRITEBYTECODE=1 python -m unittest discover -s tests -p 'test_*.py' -v
python scripts/productization/verify_delivery.py
```

Bu komutlar Gradle, Android SDK, emülatör veya cihaza başvurmaz. Yerel Python, Kotlin/JVM, Java ve C++ derleyicisi gerektirir. Manifest doğrulaması, kaynak dosyalar değiştirilmeden önce yapılmalıdır. Test kayıtlarını veya kaynakları değiştirip tekrar kaydetmek teslim hashlerini doğal olarak değiştirir.

## 7. Açık kalan sınırlar — bunlar gizlenmemeli

**Native başlatma/durdurma hâlâ mevcut ana iş parçacığı yolundadır.** Kalıcı ayar yazmasını IO'ya ayırdım; codec seçimi ve native ilk başlatma/durdurmayı farklı iş parçacığına taşımadım. Bu işlemler donanıma göre kısa süreli takılma yaratabilir. Gerçek Android profil ölçümü ve motorun iş parçacığı sözleşmesi doğrulanmadan “artık her işlem su gibi akıyor” demek desteklenmez. Mevcut medya motorunu, doğrulanmamış bir thread değişikliğiyle riske atmadım.

**Android/Compose tip çözümlemesi, çalışma zamanı odak geometrisi, sistem izinleri, büyük yazı taşmaları, Surface davranışı ve tıklama kare süreleri doğrulanmadı.** Kaynakta bunlara yönelik düzenleme bulunması cihaz kabulü yerine geçmez. Sonraki derlemede çıkabilecek bir Compose/API uyumsuzluğu bu host kontrollerince yakalanmış sayılmaz.

**PIN iptali önceki tasarımdaki gibi alıcıyı durdurur.** Native API'de tek eşleşme isteğini iptal eden bir komut eklenmedi. Bu davranış arayüzde açıklanır. “Sadece pencereyi kapattık, oturum açık kaldı” yaklaşımı kullanılmaz.

**Ayar taslakları sonraki alıcı başlangıcında uygulanır; yalnız telefon bağlantısının bitmesi otomatik restart doğurmaz.** Yeni bir aktarım veya otomatik eşleşme protokolü eklenmedi.

**Satış, lisans, imzalama, ARM/release ve fiziksel performans kabulü yapılmadı.** Önceki lisans değerlendirmesindeki açık konular çözülmüş sayılmaz. Teslim yeni APK değildir ve ticari yayına hazır diye etiketlenmemiştir.

## 8. Teknik tasarım başvuruları

Tüm bulgular yerel kaynak incelemesine dayanır. Aşağıdaki resmi belgeler yalnız odak/etkileşim/çizim yaklaşımına yardımcı referanstır; mevcut uygulamanın test sertifikası değildir. Erişim tarihi: 7 Eylül 2026.

- Android Developers, Change focus behavior: https://developer.android.com/develop/ui/compose/touch-input/focus/change-focus-behavior
- Android Developers, Handling user interactions: https://developer.android.com/develop/ui/compose/touch-input/user-interactions/handling-interactions
- Android Developers, Compose performance best practices: https://developer.android.com/develop/ui/compose/performance/bestpractices

## 9. Son değerlendirme

Bu revizyonun değeri yalnız yeni renkte değil; ölü odak duraklarını kaldırmasında, güvenli eylemi öne almasına, sayfaya dönüşü hatırlamasına, tuş olaylarını ayırmasına ve ayar/günlük işlerini açık kurallara bağlamasındadır. Kaynak, önceki teslimden daha tutarlı bir ürün deneyimi hedefleyecek şekilde düzenlenmiştir. Nihai akıcılık ve görsel kalite kararı yine derlenmiş uygulamanın gerçek TV kabulüyle verilebilir; bu çalışma o kabulün yapıldığı iddiasında değildir.
