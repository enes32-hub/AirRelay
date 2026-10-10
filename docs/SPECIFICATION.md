# Android TV iPhone Ekran Yansıtma Alıcısı
## Yapay Zekâ Geliştirme Ajanı İçin Ana Ürün, Mimari, Kalite ve Teslim Şartnamesi

> **Belge türü:** Bağlayıcı üretim şartnamesi  
> **Hedef platform:** Yalnız Android TV — alıcı uygulama televizyona kurulacaktır.  
> **Ana kullanım:** iPhone ekranını ve desteklenen sesi, iPhone'a ayrı uygulama kurmadan sistemin Ekran Yansıtma menüsünden AirPlay üzerinden aynı yerel ağdaki Android TV'ye yansıtmak.  
> **Çalışma adı:** AirRelay Receiver  
> **Belge tarihi:** 31 Ağustos 2026  
> **Sürüm numarası:** Kullanılmayacaktır  
> **Ürün modeli:** Tamamen ücretsiz, reklamsız, hesapsız, bulutsuz ve izleyicisiz  
> **Dağıtım hedefi:** Google Play Android TV, ayrıca imzalı APK  
> **Belgenin muhatabı:** Uygulamayı uçtan uca üretecek yapay zekâ yazılım geliştirme ajanı ve insan denetçiler

> **Kullanıcı kapsam düzeltmesi — 31 Ağustos 2026:** Tek uygulama hedefi Android TV alıcısıdır; gönderici iPhone'un yerleşik AirPlay / Ekran Yansıtma özelliğidir. iPhone uygulaması geliştirilmeyecek ve yardımcı uygulama gerekmeyecektir. Bu düzeltme, aşağıdaki eski geniş kapsam ifadelerinden önce gelir. iPad/macOS gönderici desteği, ayrı Google TV/Fire TV hedefi, telefon/tablet/masaüstü alıcısı ve Cast/Miracast/DLNA bu çalışma kapsamında değildir. Ayrıntılı etkin kapsam: `docs/SCOPE.md`. Gerçek cihaz, lisans, güvenlik ve kalite kapıları aynen geçerlidir.

---

# 0. AJANA VERİLEN ANA EMİR

Bu belge, fikir özeti veya tavsiye listesi değildir. Uygulamanın tasarımı, mimarisi, güvenliği, gerçek cihaz testleri ve teslim ölçütleri için bağlayıcı ana şartnamedir.

Ajan:

1. Bu şartnameden sapmayacaktır.
2. AirScreen veya başka bir ticari uygulamanın kodunu, görsellerini, marka unsurlarını, ekranlarını, metinlerini ya da mağaza materyallerini kopyalamayacaktır.
3. Aynı temel ihtiyacı karşılayan, bağımsız ve özgün bir Android TV ürünü geliştirecektir.
4. iPhone/iPad tarafına yardımcı uygulama kurdurmadan, sistemin **Ekran Yansıtma** seçicisinde televizyon alıcısının görünmesini hedefleyecektir.
5. Yalnızca aynı yerel ağdaki cihazlardan bağlantı kabul edecektir.
6. Korumalı yayınların dijital hak yönetimini aşmayacaktır.
7. “Çalışıyor” demek için yalnızca derleme almakla yetinmeyecektir.
8. Gerçek iPhone, gerçek Android TV donanımı ve gerçek ağ koşullarıyla uçtan uca test kanıtı sunacaktır.
9. Donma, kasma, görüntü bozulması ve ses kayması taleplerini ölçülebilir kalite eşiklerine dönüştürecektir.
10. Bu eşikleri karşılamayan ürünü tamamlandı olarak işaretlemeyecektir.
11. Her üçüncü taraf bağımlılığın lisansını ve kod kökenini doğrulamadan ürüne eklemeyecektir.
12. Sır, anahtar, sertifika veya belirsiz kökenli kriptografik materyal kullanmayacaktır.
13. Kullanıcı verisi, telemetri, reklam kimliği, analitik veya bulut hesabı eklemeyecektir.
14. Sahte test, yalnızca maket ekran, örnek akış veya yer tutucu medya ile teslim yapmayacaktır.
15. Uygulama, gerçek ekran yansıtma akışını baştan sona fiilen gerçekleştirmeden teslim edilmeyecektir.

Bu belgedeki **ZORUNLU**, **YASAK**, **MUTLAKA**, **P0** ve **teslim kapısı** ifadeleri yoruma açık değildir.

---

# 1. ÜRÜNÜN KISA TANIMI

AirRelay Receiver, Android TV veya Google TV cihazını yerel ağda bir iPhone/iPad ekran yansıtma alıcısına dönüştüren televizyon uygulamasıdır.

Kullanıcı akışı:

1. Kullanıcı televizyonunda AirRelay Receiver uygulamasını açar.
2. Uygulama ağ izinlerini ve alıcı hizmetini hazırlar.
3. Televizyon, ağda seçilebilir bir ekran yansıtma alıcısı olarak ilan edilir.
4. Kullanıcı iPhone/iPad Denetim Merkezi içindeki **Ekran Yansıtma** seçeneğini açar.
5. Televizyonda görünen alıcı adını seçer.
6. Güvenlik açıksa televizyondaki eşleştirme kodunu iPhone’a girer.
7. iPhone ekranı ve göndericinin sunduğu desteklenen ses, düşük gecikmeyle televizyona aktarılır.
8. Kullanıcı yansıtmayı iPhone’dan veya TV kumandasından sonlandırabilir.

Uygulamanın ana amacı, çok sayıda protokol eklemek değil, **iPhone/iPad ekran yansıtmasını olabilecek en kararlı, düşük gecikmeli ve kullanıcı dostu biçimde gerçekleştirmektir**.

---

# 2. ÜRÜN İLKELERİ

## 2.1. Kalite önce gelir

Yeni özellik eklemek, ekran yansıtmanın kararlılığını düşüremez. Ajan aşağıdaki sıraya göre öncelik verecektir:

1. Bağlantının güvenilir kurulması
2. Donanım hızlandırmalı video çözme
3. Ses-görüntü eşzamanı
4. Düşük gecikme
5. Paket kaybında kontrollü toparlanma
6. Uzun süreli kararlılık
7. Kolay kullanım
8. Görsel estetik
9. Ek özellikler

## 2.2. Yerel ve özel

Uygulamanın temel işlevi için:

- Hesap gerekmez.
- İnternet sunucusu gerekmez.
- Bulut altyapısı gerekmez.
- Uzak sunucuya cihaz bilgisi gönderilmez.
- Kullanım analitiği toplanmaz.
- Reklam gösterilmez.
- İzleme geçmişi tutulmaz.
- Görüntü ve ses kaydedilmez.
- Ekran akışı yerel ağdan dışarı çıkarılmaz.

## 2.3. Özgün ürün

Ürün işlevsel olarak AirScreen sınıfındaki bir alıcıyla aynı ihtiyacı karşılayabilir. Buna rağmen:

- “AirScreen klonu” olarak markalanmayacaktır.
- AirScreen adı, logosu, renk düzeni, ikonları veya ekran kompozisyonu kullanılmayacaktır.
- Apple logosu kullanılmayacaktır.
- “Apple tarafından onaylandı”, “resmî AirPlay”, “Works with AirPlay” veya benzeri sertifikasyon ifadesi, gerçekten gerekli lisans ve onay alınmadan kullanılmayacaktır.
- Mağaza açıklamasında en güvenli ifade “iPhone ve iPad için yerel ağ ekran yansıtma alıcısı” olacaktır.
- “AirPlay uyumlu” ifadesi ancak marka ve hukuk incelemesinden sonra kullanılacaktır.

## 2.4. Gerçekçi kalite beyanı

Hiçbir yazılım, bütün yönlendiricilerde, bütün TV yongalarında ve bütün kablosuz ağ koşullarında mutlak sıfır gecikme veya sıfır kare kaybı garanti edemez.

Bu nedenle:

- Ürün hiçbir yerde “her koşulda sıfır gecikme” iddiasında bulunmayacaktır.
- Kalite, bu belgede tanımlanan referans koşullar ve sayısal eşikler üzerinden doğrulanacaktır.
- Zayıf ağda uygulama sessizce bozulmak yerine kaliteyi kontrollü düşürecek, kullanıcıya anlaşılır ağ önerisi gösterecek ve mümkünse otomatik toparlanacaktır.

---

# 3. KAPSAM

## 3.1. P0 — İlk yayın için zorunlu işlevler

Aşağıdaki işlevler eksiksiz değilse ürün yayınlanamaz:

- Android TV/Google TV üzerinde çalışan alıcı uygulaması
- iPhone/iPad sistem Ekran Yansıtma seçicisinde alıcı adının görünmesi
- Aynı yerel ağdan güvenli bağlantı
- H.264 ekran yansıtma videosunun donanım hızlandırmalı çözülmesi
- Göndericinin sunduğu desteklenen sesin oynatılması
- AAC-ELD ve AAC-LC desteği
- Gereken alıcı akışlarında ALAC desteği
- 720p30, 1080p30 ve 1080p60 çalışma
- Otomatik çözünürlük ve yön değişimi
- Dikey ve yatay iPhone ekranının doğru oranla gösterilmesi
- PIN kodlu eşleştirme
- Güvenilen cihazları unutma
- Otomatik ve manuel alıcı başlatma
- D-pad/TV kumandasıyla tam kullanım
- Tam ekran yansıtma
- Fit, Fill ve 1:1 görüntü seçenekleri
- Ses aç/kapat
- Kullanıcı ayarlı ses gecikmesi düzeltmesi
- Ağ ve oynatma istatistikleri
- Hata teşhis ekranı
- Yerel ağ izni yönetimi
- Android foreground service kurallarına uygun arka planda hazır bekleme
- Uygulama kapandığında kaynakların eksiksiz bırakılması
- Uygulama yeniden açıldığında hizmetin güvenilir toparlanması
- Gerçek cihaz kabul testleri
- Google Play için AAB
- İmzalı evrensel test APK’sı
- 32 ve 64 bit Android TV mimarileri
- 16 KB bellek sayfası uyumu
- Gizlilik politikası
- Açık kaynak bildirimleri
- Yazılım malzeme listesi
- Lisans denetim raporu

## 3.2. P1 — P0 kararlılığı bozulmadan eklenecek işlevler

P0 tamamlandıktan sonra eklenebilir:

- AirPlay ses alıcısı olarak yalnızca müzik/ses oynatma
- Albüm kapağı ve parça bilgisinin gösterilmesi
- Fotoğraf gösterimi
- Korumasız URL/HLS medya oturumları
- Birden fazla kalite profili
- Ses çıkışı cihazı seçimi
- TV ana ekranında hızlı başlatma kısayolu
- Yerel tanılama raporunu USB/paylaşım yoluyla dışa aktarma
- İkinci dil olarak İngilizce
- Yerel ağdaki güvenilen cihazlar için hızlı yeniden bağlanma

## 3.3. P2 — Ayrı proje kararı gerektiren işlevler

Aşağıdakiler bu sürümün kapsamına dahil değildir. Sonradan ayrı gereksinim ve test planıyla ele alınabilir:

- Google Cast alıcısı
- Miracast alıcısı
- DLNA alıcısı
- Ekran kaydı
- Bulut üzerinden uzaktan yansıtma
- İnternet dışından bağlantı
- Birden fazla televizyona eşzamanlı yayın
- Birden fazla iPhone’dan eşzamanlı görüntü
- Çok odalı ses
- Mobil Android alıcı uygulaması
- Windows alıcı uygulaması
- macOS alıcı uygulaması
- Kullanıcı hesabı
- Üyelik
- Reklam
- Uygulama içi satın alma
- Abonelik
- Analitik
- Yapay zekâyla görüntü iyileştirme
- Dijital hak yönetimi aşma
- Korumalı yayınları kaydetme
- Kullanıcının iPhone’unu televizyondan uzaktan kontrol etme

---

# 4. DESTEK VE SINIRLAR

## 4.1. Desteklenen göndericiler

Yayın anında:

- Güncel kararlı iOS/iPadOS sürümü
- Önceki üç desteklenen ana iOS/iPadOS sürüm hattı
- Desteklenen macOS sürümleri, P1 doğrulaması kapsamında

Sürüm numaraları kod içinde sabit pazarlama iddiası olarak yazılmayacaktır. Her uygulama sürümünde gerçek cihaz matrisi güncellenecektir.

## 4.2. Aynı ağ zorunluluğu

Gönderici ve televizyon:

- Aynı yerel ağda olmalıdır.
- Tercihen aynı alt ağda olmalıdır.
- mDNS/multicast trafiği engellenmemelidir.
- Misafir ağı yalıtımı veya AP isolation etkin olmamalıdır.
- En iyi kalite için televizyon Ethernet’e, iPhone ise 5 GHz/6 GHz Wi‑Fi’a bağlı olmalıdır.
- İki cihazın da yoğun 2.4 GHz ağda çalışması desteklenebilir; ancak premium kalite hedefi olarak sertifikalandırılmaz.

## 4.3. Desteklenmeyen içerik

Aşağıdaki içeriklerin görüntüsünü açmak, çözmek veya korumasını aşmak bu ürünün görevi değildir:

- Netflix DRM korumalı video
- Apple TV+ DRM korumalı video
- Disney+ DRM korumalı video
- Diğer FairPlay/DRM korumalı yayınlar
- Korumalı uygulama içi içerik
- HDCP veya benzeri koruma gerektiren akışlar

Bu durumlarda uygulama:

- Koruma aşmaya çalışmayacaktır.
- Gizli anahtar kullanmayacaktır.
- Kullanıcıya “Korumalı içerik yansıtılamıyor olabilir” şeklinde açıklama gösterecektir.
- Uygulamanın genel bağlantısını çökertmeyecektir.
- Kullanıcı normal ekrana döndüğünde yansıtmayı otomatik sürdürmeye çalışacaktır.

## 4.4. AWDL ve doğrudan eşler arası bağlantı

Android platformunun resmî ve hukuken temiz bir API sağlamadığı yöntemlerle Apple Wireless Direct Link taklit edilmeyecektir.

İlk sürüm:

- Aynı Wi‑Fi/Ethernet yerel ağı üzerinden çalışacaktır.
- Wi‑Fi Direct/AWDL taklidi yapmayacaktır.
- Bluetooth beacon veya özel radyo protokolü P0 kapsamına alınmayacaktır.

---

# 5. HUKUK, MARKA, LİSANS VE KOD KÖKENİ

## 5.1. Zorunlu hukuk kapısı

Kamuya açık dağıtımdan önce aşağıdaki konular yazılı olarak incelenecektir:

- Apple ticari markalarının kullanımı
- “AirPlay” kelimesinin mağaza ve uygulama içi kullanımı
- Resmî MFi/lisans gereksinimi
- Seçilen protokol çekirdeğinin lisansı
- Kriptografik bileşenlerin kökeni
- Patent riski
- Üçüncü taraf codec ve FFmpeg lisansları
- GPL, LGPL, Apache, MIT ve BSD yükümlülükleri
- Google Play fikrî mülkiyet politikası

Bu belge hukuk görüşü değildir. Ajan hukuk değerlendirmesi yapmış gibi davranmayacak; teknik ve lisans envanterini hukuk denetimine hazır hâle getirecektir.

## 5.2. Protokol çekirdeği seçim kapısı

Ajan geliştirmeye başlamadan önce **DEPENDENCY_AND_LICENSE_DECISION.md** hazırlayacaktır.

İki ana uygulama rotası ayrı ayrı değerlendirilecektir:

### Rota A — GPL tabanlı çekirdek

Örnek referanslar:

- UxPlay
- UxPlay tabanlı Android AirPlay Receiver projeleri

Bu rota seçilirse:

- Ürünün türev eser yükümlülükleri eksiksiz karşılanacaktır.
- Dağıtılan ikilinin tam karşılık gelen kaynak kodu sağlanacaktır.
- GPL-3.0 bildirimleri eksiksiz olacaktır.
- Kapalı kaynak bileşenlerle lisans çatışması yaratılmayacaktır.
- OpenSSL/FFmpeg bağlantı biçimi ayrıca incelenecektir.
- Kullanıcıya kaynak koduna erişim yolu sunulacaktır.

### Rota B — İzin verici lisanslı veya temiz oda çekirdeği

Örnek araştırma referansı:

- Apache 2.0 beyanlı modern Android TV alıcı projeleri
- Topluluk tarafından belgelenmiş protokol açıklamaları

Bu rota seçilirse:

- Her dosyanın kökeni incelenecektir.
- Başka GPL projelerinden kopyalanmış kod olmadığından emin olunacaktır.
- Belirsiz kaynaklı anahtar, tablo veya kriptografik veri kullanılmayacaktır.
- “Apache lisans dosyası var” tek başına yeterli kanıt sayılmayacaktır.
- Gerçek cihaz uyumluluğu beta beyanına güvenilerek kabul edilmeyecektir.

### Kesin yasak

- GPL kodunu izin verici lisanslı projeye kopyalamak
- Lisans başlıklarını kaldırmak
- Belirsiz kökenli FairPlay/DRM anahtarı gömmek
- Apple ikili dosyası veya firmware verisi paketlemek
- Ticari AirScreen APK’sını tersine mühendislikle kopyalamak
- AirScreen ağ trafiğini temel alıp tescilli kod/varlık üretmek
- Decompile edilmiş kod kullanmak
- Lisans denetimi tamamlanmadan Play sürümü üretmek

## 5.3. Karar kriterleri

Seçilecek çekirdek aşağıdaki ağırlıklı puanlamadan geçecektir:

| Ölçüt | Ağırlık |
|---|---:|
| Gerçek iPhone bağlantı başarısı | %25 |
| 1080p60 kararlılık | %20 |
| Ses-görüntü eşzamanı | %15 |
| Güncel iOS uyumluluğu | %15 |
| Lisans ve kod kökeni açıklığı | %15 |
| Android TV entegrasyon maliyeti | %5 |
| Bakım yapılabilirlik | %5 |

Lisans/köken değerlendirmesinde “geçemedi” alan çekirdek, teknik puanı yüksek olsa bile kullanılamaz.

## 5.4. Resmî lisans yolu

Ürün resmî Apple sertifikasyon işareti kullanacaksa veya tescilli teknik spesifikasyonlara erişim gerektiriyorsa:

- Apple MFi programı değerlendirilmelidir.
- Gerekli lisans ve sertifikalar alınmadan resmî uyumluluk iddiası yapılmamalıdır.
- Açık kaynak uygulama, otomatik olarak resmî sertifikalı ürün sayılmamalıdır.

---

# 6. TEKNİK TABAN

## 6.1. Android sürüm hedefi

Başlangıç yapılandırması:

```text
minSdk = 26
compileSdk = 37
targetSdk = 37
```

Gerekçe:

- minSdk 26, eski fakat hâlâ kullanılan Android TV cihazlarına erişim sağlar.
- targetSdk 37, güncel yerel ağ izin modeli ve platform davranışlarını baştan doğru uygular.
- Uygulama Android TV kalite kriterlerindeki minSdk ≤31 koşulunu karşılar.

## 6.2. Araç zinciri

Belgenin tarihi itibarıyla başlangıç tabanı:

- Android Studio Quail 3 | 2026.1.3 Patch 1
- Android Gradle Plugin 9.3 kararlı hattındaki doğrulanmış son yama
- Kotlin 2.4.10
- Jetpack Compose for TV’nin kararlı sürümü
- AndroidX Media3 1.11.0
- Güncel kararlı Android NDK
- CMake/Ninja
- Java toolchain: seçilen AGP’nin resmî olarak desteklediği LTS JDK
- Gradle Wrapper
- Gradle Version Catalog
- Dependency verification
- Reproducible build ayarları

Ajan:

- Preview/canary kütüphaneyi P0’a eklemeyecektir.
- Sürüm numaralarını dinamik `+` biçiminde kullanmayacaktır.
- Tüm bağımlılıkları kilitleyecektir.
- Kullanılan tam sürümleri `libs.versions.toml` ve `BUILD_ENVIRONMENT.md` içinde yazacaktır.
- Yapı araçlarını geliştirmenin başında bir kez, teslimden önce bir kez resmî kaynaklardan doğrulayacaktır.

## 6.3. ABI ve yerel kod

Google Play TV dağıtımı için en az:

- `armeabi-v7a`
- `arm64-v8a`

Test geliştirmesi için:

- `x86_64` isteğe bağlıdır.

Zorunlu:

- 32 bit ve 64 bit uyum
- 16 KB bellek sayfa boyutu uyumlu yerel kitaplıklar
- Android App Bundle
- Yerel `.so` dosyalarında doğru ELF hizalaması
- Strip edilmiş yayın sembolleri
- Ayrı native debug symbols paketi
- AddressSanitizer/UndefinedBehaviorSanitizer test yapıları
- JNI sınırında açık sahiplik kuralları
- Native crash sembolleştirme

## 6.4. Uygulama paketleri

Önerilen kimlik:

```text
com.example.aynatv.receiver
```

Gerçek yayın kimliği proje sahibi tarafından değiştirilecektir.

Uygulama:

- Yalnız TV launcher kategorisinde görünmelidir.
- Dokunmatik ekran gerektirmemelidir.
- Dikey telefon uygulaması gibi davranmamalıdır.
- TV banner, 4K uyumlu ikon ve yüksek çözünürlüklü mağaza varlıkları içermelidir.

---

# 7. YÜKSEK SEVİYE MİMARİ

## 7.1. Modüler yapı

Önerilen modüller:

```text
:app-tv
:core-domain
:core-common
:core-ui-tv
:core-settings
:receiver-contract
:receiver-service
:receiver-native
:receiver-discovery
:receiver-session
:receiver-security
:media-video
:media-audio
:media-sync
:media-hls
:diagnostics
:benchmark
:test-fixtures
```

## 7.2. Sorumluluklar

### `:app-tv`

- Activity
- Navigation
- TV ekranları
- İlk kurulum
- D-pad etkileşimi
- İzin ekranları
- Ayarlar
- Tam ekran Surface konteyneri

### `:receiver-service`

- Alıcı yaşam döngüsü
- Foreground service
- Network callback
- Hizmeti başlat/durdur
- Aktif oturum yönetimi
- Güç ve multicast kilitleri
- Bildirim

### `:receiver-discovery`

- mDNS/DNS-SD ilanı
- `_airplay._tcp` ve `_raop._tcp` benzeri gereken hizmet kayıtları
- Ağ arabirimi değişiklikleri
- Kayıt yenileme
- İlan çakışması yönetimi
- Hizmet adı normalizasyonu

### `:receiver-session`

- RTSP/HTTP kontrol düzlemi
- Oturum durumu
- Zaman aşımı
- İstek sınırları
- Tek aktif yayın politikası
- Yeniden bağlanma

### `:receiver-security`

- PIN akışı
- Güvenilen denetleyiciler
- Android Keystore
- Oturum anahtarı yaşam döngüsü
- Eşleştirme hız sınırlama
- Anahtar silme
- Yetkilendirme

### `:receiver-native`

- Hukuken ve lisansça onaylanmış protokol çekirdeği
- JNI köprüsü
- Yerel paket ayrıştırıcıları
- Ses/video paket callbacks
- Native hata kodlarının güvenli Kotlin karşılığı

### `:media-video`

- H.264/HEVC yetenek sorgusu
- MediaCodec
- SurfaceView
- Düşük gecikmeli kuyruğa alma
- SPS/PPS/VPS işleme
- Çözünürlük ve yön değişimi
- Kare zamanlama
- Decoder kendini iyileştirme

### `:media-audio`

- AAC-ELD
- AAC-LC
- ALAC
- AudioTrack
- Ses odağı
- Sessizleştirme
- Çıkış gecikmesi
- Audio underrun toparlama

### `:media-sync`

- RTP/NTP saatleri
- A/V eşzamanı
- Drift ölçümü
- Jitter tamponu
- Frame release scheduler
- Kullanıcı ses offset’i

### `:diagnostics`

- Oturum ölçümleri
- Yerel log ring buffer
- Kişisel veri içermeyen rapor
- Ağ sağlık denetimi
- Codec yetenek raporu
- Dışa aktarılabilir tanılama paketi

## 7.3. Katman kuralları

- UI doğrudan native protokol koduna çağrı yapmayacaktır.
- UI yalnızca domain use-case ve state flow’larıyla konuşacaktır.
- Native callback ana iş parçacığını asla bloklamayacaktır.
- Video ham verisi UI katmanından geçmeyecektir.
- Bütün kuyruklar sınırlı olacaktır.
- Sınırsız `Channel`, `Queue`, `List` veya byte buffer yasaktır.
- Her kaynak için açık oluşturma/kapatma sahibi bulunacaktır.
- Bir modülün başka modülün iç uygulama sınıflarına erişmesi yasaktır.
- Domain modelleri Android framework sınıflarına bağımlı olmayacaktır.

---

# 8. İŞ PARÇACIĞI VE VERİ AKIŞI

## 8.1. İş parçacıkları

En az aşağıdaki bağımsız çalışma alanları bulunacaktır:

- Ana/UI iş parçacığı
- mDNS/discovery executor
- RTSP/control executor
- Video ingest executor
- MediaCodec callback thread
- Audio decode executor
- Audio output thread
- Clock/sync scheduler
- Diagnostics writer
- Native callback dispatcher

## 8.2. Yasak iş parçacığı davranışları

- Ağ okuma ana iş parçacığında yapılmayacaktır.
- Native callback içinde disk I/O yapılmayacaktır.
- Codec callback içinde UI işlemi yapılmayacaktır.
- Video paketi kopyaları zincir hâlinde çoğaltılmayacaktır.
- Ağ thread’i decoder beklerken sınırsız bloklanmayacaktır.
- AudioTrack yazımı video thread’inde yapılmayacaktır.
- Log yazımı gerçek zamanlı medya thread’ini engellemeyecektir.

## 8.3. Hedef veri yolu

```text
iPhone/iPad
   │
   ├─ mDNS discovery
   ├─ pairing / RTSP session
   ├─ video RTP/encrypted session payload
   └─ audio RTP/encrypted session payload
        │
        ▼
Receiver native/control core
        │
        ├─ bounded video packet queue
        ├─ bounded audio packet queue
        └─ clock / retransmit metadata
        │
        ▼
Video MediaCodec ───────► SurfaceView
Audio decoder ──────────► AudioTrack
        │                    │
        └──── Sync Clock ◄───┘
```

---

# 9. DURUM MAKİNESİ

## 9.1. Ana durumlar

```text
STOPPED
PERMISSION_REQUIRED
STARTING
NETWORK_UNAVAILABLE
ADVERTISING
READY
PAIRING
AUTHENTICATING
CONNECTING
NEGOTIATING
STREAMING_VIDEO_AUDIO
STREAMING_VIDEO_ONLY
STREAMING_AUDIO_ONLY
SHOWING_PHOTO
RECONNECTING
BUSY
STOPPING
RECOVERABLE_ERROR
FATAL_ERROR
```

## 9.2. Durum kuralları

- Aynı anda yalnızca bir ana durum etkin olacaktır.
- UI, protokol iç ayrıntılarını tahmin etmeyecektir.
- Her geçiş sebebi loglanacaktır.
- Her durumun zaman aşımı olacaktır.
- Geçersiz geçiş debug yapıda assert, release yapıda güvenli hata üretmelidir.
- `FATAL_ERROR` sonrası kullanıcı “Alıcıyı Yeniden Başlat” komutuyla temiz başlangıç yapabilmelidir.
- `RECONNECTING` sonsuza kadar sürmeyecektir.
- Alıcı hizmeti beş başarısız toparlama denemesinden sonra kullanıcıya açık hata gösterecektir.

## 9.3. Örnek geçişler

```text
STOPPED -> STARTING
STARTING -> PERMISSION_REQUIRED
STARTING -> NETWORK_UNAVAILABLE
STARTING -> ADVERTISING
ADVERTISING -> READY
READY -> PAIRING
PAIRING -> AUTHENTICATING
AUTHENTICATING -> NEGOTIATING
NEGOTIATING -> STREAMING_VIDEO_AUDIO
STREAMING_VIDEO_AUDIO -> RECONNECTING
RECONNECTING -> STREAMING_VIDEO_AUDIO
RECONNECTING -> READY
STREAMING_VIDEO_AUDIO -> READY
ANY -> STOPPING
STOPPING -> STOPPED
```

---

# 10. ANDROID İZİNLERİ VE MANIFEST

## 10.1. Zorunlu olabilecek izinler

İhtiyaca göre en küçük izin kümesi kullanılacaktır:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
<uses-permission android:name="android.permission.ACCESS_LOCAL_NETWORK" />
```

`ACCESS_LOCAL_NETWORK`:

- targetSdk 37+ için çalışma anında istenecektir.
- Reddedilirse ağ soketi açılmayacaktır.
- Kullanıcıya neden gerektiği TV uyumlu açıklamayla gösterilecektir.
- Kullanıcı ayarlara yönlendirilebilecektir.
- İzin sonradan kaldırılırsa alıcı güvenli biçimde duracaktır.

## 10.2. İzin verilmeyecek alanlar

Aşağıdakiler eklenmeyecektir:

- Kamera
- Mikrofon
- Kişiler
- Konum
- SMS
- Telefon
- Takvim
- Dosyalara geniş erişim
- Reklam kimliği
- Bluetooth; P0’da kullanılmıyorsa
- Accessibility service
- VPN
- Paket listesi sorgusu
- Cihaz yöneticisi
- Overlay

## 10.3. TV özellikleri

Manifest:

```xml
<uses-feature
    android:name="android.software.leanback"
    android:required="true" />

<uses-feature
    android:name="android.hardware.touchscreen"
    android:required="false" />
```

Launcher:

- `CATEGORY_LEANBACK_LAUNCHER`
- TV banner
- Yatay tasarım
- Kumanda erişimi

## 10.4. Foreground service

Alıcı arka planda hazır bekliyorsa:

- `connectedDevice` foreground service tipi kullanılacaktır.
- Kullanıcı hizmeti açıkça etkinleştirmiş olmalıdır.
- Kalıcı ve anlaşılır bildirim gösterilecektir.
- Yayın sesi çalarken gereken durumda `mediaPlayback` tipi de kullanılacaktır.
- Android sürümlerindeki background-start kısıtları uygulanacaktır.
- Boot sonrası otomatik başlatma yalnız kullanıcı ayarı açıksa ve platform izin veriyorsa yapılacaktır.
- Platformun yasakladığı arka plan başlatma hileleri kullanılmayacaktır.

## 10.5. Kilitler

- MulticastLock yalnızca ilan için gerçekten gerektiğinde alınacaktır.
- WifiLock yüksek performans modu yalnızca aktif yayın sırasında ve ölçümle faydası kanıtlanırsa kullanılacaktır.
- WakeLock yalnızca aktif oturumda ve zaman aşımıyla tutulacaktır.
- Her kilidin `try/finally` veya yaşam döngüsü sahibi olacaktır.
- Uygulama durduğunda elde kalan kilit olmayacaktır.

---

# 11. AĞ VE HİZMET KEŞFİ

## 11.1. mDNS/DNS-SD

Alıcı, seçilen ve hukuken onaylanmış protokol uygulamasının gerektirdiği hizmetleri ilan edecektir.

Beklenen sınıflar:

- Ekran yansıtma alıcı hizmeti
- RAOP/ses hizmeti

Uygulama:

- Aktif Ethernet ve Wi‑Fi arabirimlerini doğru belirlemelidir.
- VPN veya hücresel arabirimi ilan için kullanmamalıdır.
- IPv4 ve uygun durumda IPv6 desteklemelidir.
- Hizmet adı çakışmasını çözmelidir.
- Ağ değişiminde eski kaydı kaldırıp yenisini oluşturmalıdır.
- TV uyku/uyanma sonrası ilanı yenilemelidir.
- DHCP adresi değiştiğinde yeniden kayıt yapmalıdır.
- mDNS paketi işleme sınırlarını doğrulamalıdır.

## 11.2. Alıcı adı

Varsayılan:

```text
AirRelay — Salon
```

Kurallar:

- Kullanıcı değiştirebilir.
- 1–32 görüntülenebilir karakter
- Kontrol karakteri yok
- Çok uzun Unicode dizileri sınırlandırılır
- mDNS için güvenli normalizasyon
- Aynı ağda çakışırsa görsel olarak anlaşılır sonek
- Gerçek Wi‑Fi MAC adresi kullanıcıya gösterilmez

## 11.3. Cihaz kimliği

- Kararlı fakat uygulamaya özel rastgele kimlik üretilir.
- Android Keystore veya uygulama özel depolamasında tutulur.
- Kullanıcının “Tüm güvenilen cihazları ve kimliği sıfırla” eylemiyle yenilenebilir.
- Reklam kimliği kullanılmaz.
- Donanım seri numarası kullanılmaz.
- MAC adresi analitik amacıyla toplanmaz.

## 11.4. Ağ bağlantı kuralları

- Yalnızca aktif yerel ağ arabirimine bind edilir.
- İnternete açık `0.0.0.0` dinleme davranışı güvenlik testinden geçirilir.
- WAN’dan erişim kesin olarak engellenir.
- UPnP port açma yapılmaz.
- NAT traversal yapılmaz.
- Uzak sunucu relay’i yapılmaz.
- İnternet DNS’i üzerinden hizmet kaydı yapılmaz.
- İzin verilen portlar belgelendirilir.
- Port çakışması olduğunda kullanıcıya anlaşılır hata verilir.

## 11.5. Bağlantı zaman aşımı

Önerilen üst sınırlar:

| İşlem | Zaman aşımı |
|---|---:|
| İlk TCP kabulü sonrası istek başlangıcı | 5 s |
| RTSP başlık okuma | 5 s |
| Eşleştirme adımı | 30 s |
| Kullanıcı PIN girişi | 60 s |
| Medya başlaması | 10 s |
| Paket sessizliği | 5 s uyarı / 12 s kopma |
| Yeniden bağlanma penceresi | en çok 20 s |
| Idle istemci | 30 s |

Değerler gerçek testle ayarlanabilir; sınırsız bekleme yasaktır.

## 11.6. Ağ sağlık denetimi

Hazır ekranında uygulama şu sinyalleri değerlendirecektir:

- Ethernet/Wi‑Fi
- 2.4/5/6 GHz bilgisi platform veriyorsa
- Link hızı platform veriyorsa
- Multicast ilan başarısı
- Yerel ağ izni
- IP adresi var/yok
- Aynı ağ erişim denemesi
- Paket kaybı ve jitter, yalnız aktif oturumda
- Ağ değişimi

Kullanıcıya teknik jargon yerine:

- **Mükemmel bağlantı**
- **İyi bağlantı**
- **Zayıf bağlantı**
- **Aynı ağ doğrulanamadı**
- **Yönlendirici cihazları birbirinden ayırıyor olabilir**

gibi mesajlar gösterilecektir.

---

# 12. EŞLEŞTİRME VE GÜVENLİK

## 12.1. Varsayılan güvenlik

PIN koruması varsayılan olarak **açık** olacaktır.

Gerekçe:

- Aynı ağdaki yetkisiz kişinin televizyona görüntü göndermesini önlemek
- Otel, yurt, iş yeri ve ortak ağlarda güvenlik
- Yanlış TV’ye yayın riskini azaltmak

## 12.2. Eşleştirme modları

### Her seferinde PIN

- Her yeni oturumda rastgele kod
- En güvenli mod
- Ortak ağlar için önerilir

### Güvenilen cihaz

- İlk bağlantıda PIN
- Kullanıcının açık onayıyla cihaz güvenilir kaydedilir
- Sonraki bağlantılarda hızlı eşleştirme
- Ayarlardan tek tek kaldırılabilir

### Korumasız mod

- Varsayılan değildir
- Kullanıcı iki aşamalı uyarıdan sonra açabilir
- Hazır ekranda belirgin “PIN kapalı” uyarısı
- Ortak ağlarda önerilmez

## 12.3. PIN kuralları

- Kriptografik güvenli rastgele üretim
- Tercihen 4 veya protokolün gerektirdiği uzunluk
- Belirli sayıda hatalı denemeden sonra geçici kilit
- IP ve denetleyici kimliği bazlı hız sınırı
- Kod loglanmaz
- Kod tanılama raporuna girmez
- Oturum kapandığında geçersiz olur
- Ekran okuyucu desteği bulunur

## 12.4. Anahtar saklama

- Uzun ömürlü özel anahtarlar Android Keystore ile korunur.
- Açık metin SharedPreferences içine anahtar yazılmaz.
- Native bellek anahtarları kullanım sonrası temizlenir.
- Backup’a kriptografik sır dahil edilmez.
- Uygulama kaldırıldığında anahtarlar silinir.
- Veri temizleme işleminde güven ilişkileri sıfırlanır.

## 12.5. Oturum güvenliği

- Bir aktif görüntü oturumu varsayılandır.
- İkinci gönderici “TV şu anda kullanımda” yanıtı alır.
- Oturum devralma yalnız TV kullanıcısının açık onayıyla yapılabilir.
- Yetkisiz kontrol istekleri reddedilir.
- Paket büyüklüğü ve başlık uzunluğu sınırlandırılır.
- Hatalı istekler parser’ı çökertmemelidir.
- Tekrarlanan kötü istekler bağlantıyı kapatmalıdır.
- Girişler fuzz testinden geçirilmelidir.
- Replay saldırılarına karşı protokolün nonce/session mekanizması doğru uygulanmalıdır.

## 12.6. Güvenlik varsayımları

- Yerel ağ güvenilir kabul edilmez.
- mDNS duyurusu kimlik doğrulama sağlamaz.
- İstemci tarafından bildirilen ad güvenilir veri değildir.
- Header ve SDP alanları kötü niyetli olabilir.
- Codec yapılandırması kötü niyetli olabilir.
- Boyut, süre, kanal, örnekleme ve çözünürlük değerleri doğrulanmalıdır.

---

# 13. PROTOKOL VE OTURUM KATMANI

## 13.1. Uygulama yaklaşımı

Ajan tescilli protokol ayrıntılarını sıfırdan tahmin ederek üretim kodu yazmayacaktır.

Aşağıdakilerden biri seçilecektir:

- Hukuken ve lisansça onaylanmış açık kaynak çekirdek
- MFi/lisanslı teknik uygulama
- Kod kökeni doğrulanmış temiz oda uygulaması

## 13.2. Gereken oturum yetenekleri

P0 çekirdeği:

- Hizmet keşfi
- Eşleştirme
- Kimlik doğrulama
- Oturum kurulumu
- Video ve ses kanal kurulumu
- Saat eşzamanı
- Yeniden iletim/packet loss geri bildirimi, protokol destekliyorsa
- Oturum kapanışı
- Bağlantı kopma toparlanması
- Yön/çözünürlük değişimi
- Tek aktif gönderici
- Hata kodlarını anlamlı domain hatasına çevirme

## 13.3. Parser standartları

Her parser:

- Maksimum istek boyutu belirler.
- Maksimum header sayısı belirler.
- Maksimum header satırı uzunluğu belirler.
- Negatif/taşan sayıları reddeder.
- Integer overflow kontrolü yapar.
- Sıfıra bölmeyi engeller.
- Tekrarlı header davranışını belirler.
- Hatalı Unicode’u güvenli işler.
- Beklenmedik method’u kontrollü reddeder.
- C/C++ tarafında güvenli bellek sahipliği kullanır.
- Fuzz corpus ve regression test içerir.

## 13.4. Kriptografi sınırı

- Oturum iletişimi için gerekli, hukuken temiz ve belgeli şifre çözme/şifreleme uygulanabilir.
- Korumalı film veya müzik DRM’ini aşan kod uygulanamaz.
- Uygulama, içerik DRM’i ile alıcı eşleştirme şifrelemesini birbirine karıştırmayacaktır.
- Kriptografi “ev yapımı” olmayacaktır.
- Onaylanmış kütüphaneler kullanılacaktır.
- Sabit IV, zayıf rastgelelik veya loglanan anahtar yasaktır.

---

# 14. VİDEO İŞLEME HATTI

## 14.1. Zorunlu codec

P0:

- H.264/AVC donanım çözme

P1/cihaz yeteneğine bağlı:

- H.265/HEVC donanım çözme

Yazılım video decoder’ı:

- Varsayılan yol olmayacaktır.
- Düşük donanımda cihazı ısıtıp kararsızlaştırıyorsa devre dışı bırakılacaktır.
- Kullanılırsa en fazla uyumluluk modu ve düşük çözünürlükte kullanılacaktır.
- Lisansı ayrıca incelenecektir.

## 14.2. MediaCodec

- Decoder, `MediaCodecList` üzerinden gerçek yetenek sorgusuyla seçilir.
- Donanım decoder önceliklidir.
- Güvensiz vendor decoder’lar cihaz uyumluluk listesiyle engellenebilir.
- Async callback modu tercih edilir.
- Düşük gecikme özelliği destekleniyorsa `FEATURE_LowLatency` sorgulanır.
- `KEY_LOW_LATENCY` yalnız desteklenen cihazda etkinleştirilir.
- Desteklenmeyen anahtar körlemesine verilmez.
- Decoder watchdog bulunur.
- Decoder stall olduğunda kontrollü flush/recreate yapılır.
- Sürekli yeniden oluşturma döngüsü engellenir.

## 14.3. Surface

- `SurfaceView` zorunlu varsayılan render yüzeyidir.
- `TextureView` yalnız ölçülmüş gerekçe varsa kullanılabilir.
- Video doğrudan Surface’a çözülür.
- Gereksiz Bitmap dönüşümü yapılmaz.
- Frame başına Java byte array kopyası yapılmaz.
- Mümkün olan en az kopyalı yol kullanılır.
- Surface kaybı, Activity yeniden oluşumu ve HDMI mode değişimi test edilir.

## 14.4. Akış yapılandırması

Uygulama dinamik olarak işleyecektir:

- SPS/PPS değişimi
- HEVC kullanılıyorsa VPS
- Çözünürlük değişimi
- En-boy oranı değişimi
- Dikey/yatay dönüş
- 30/60 fps geçişi
- Renk uzayı değişimi
- Keyframe kaybı
- Encoder reset
- Codec config paketinin yeniden gelmesi

Yanlış config durumunda:

- Bozuk kare gösterilmez.
- Decoder keyframe bekleme moduna alınır.
- Protokol izin veriyorsa yeniden senkron/keyframe talebi yapılır.
- Kullanıcıya donmuş son kare sonsuza kadar gösterilmez.

## 14.5. Video kuyrukları

Üç profil:

### Düşük gecikme

- En küçük güvenli jitter tamponu
- Eski gecikmiş kareleri bırak
- Keyframe’i koru
- Etkileşim ve sunum için

### Otomatik

- Ağ jitter’ına göre sınırlı uyarlama
- Varsayılan profil
- Donma yerine kontrollü kare bırakma

### Kararlı

- Daha büyük ama sınırlandırılmış tampon
- Film/video izleme için
- Gecikme pahasına daha az drop

Hiçbir profilde kuyruk sınırsız büyümeyecektir.

## 14.6. Kare bırakma politikası

- Decoder yetişemiyorsa en eski gösterilmemiş non-keyframe bırakılır.
- Yeni görüntü yerine saniyelerce eski görüntü gösterilmez.
- Keyframe kaybında delta kareleri körlemesine decoder’a beslemek yerine yeniden eşzaman beklenir.
- Bırakılan kareler sayılır.
- Kullanıcı istatistik ekranında drop yüzdesini görebilir.
- Kalite puanı bu ölçüme dahil edilir.

## 14.7. Görüntü ölçekleme

Kullanıcı seçenekleri:

- **Sığdır:** Tüm ekran görünür, siyah boşluk olabilir.
- **Doldur:** TV ekranını kaplar, kenarlar kırpılabilir.
- **1:1:** Kaynak piksel ölçeği korunur; desteklenmeyen durumlarda devre dışı.
- **Otomatik:** İçeriğe göre güvenli varsayılan.

Varsayılan **Sığdır** olacaktır.

## 14.8. Overscan

- Varsayılan 0
- Kullanıcı 0–10% ayarlayabilir
- TV güvenli alanı korunur
- Ayar canlı önizlenir
- Cihaza özel saklanır

## 14.9. Renk ve HDR

P0 hedefi:

- SDR
- BT.709
- doğru limited/full range işlemi
- siyah ve beyaz kırpılmaması

HDR:

- Gönderici, codec ve TV zinciri gerçekten destekliyorsa P1
- Yanlış HDR bayrağıyla soluk görüntü gösterilmeyecektir
- Gerekirse güvenli SDR fallback
- Renk doğruluğu test deseniyle doğrulanacaktır

## 14.10. Ekran gecikmesini azaltma

Destekleniyorsa:

- `Window.setPreferMinimalPostProcessing(true)`
- Uygun Surface frame rate bildirimi
- 59.94/60 ve 29.97/30 ayrımı
- TV oyun/düşük gecikme modu hakkında yardım ekranı

Uygulama, TV’nin üreticiye özgü görüntü işleme gecikmesini tamamen kontrol edemez. Yardım ekranı kullanıcıya “Oyun Modu”nu önerebilir.

---

# 15. SES İŞLEME HATTI

## 15.1. P0 codec desteği

- AAC-ELD
- AAC-LC
- ALAC; gereken oturumlarda

## 15.2. Decoder yolu

- MediaCodec desteği olan codec’lerde donanım/platform decoder
- ALAC için lisansı denetlenmiş decoder
- FFmpeg kullanılırsa yalnız gereken codec bileşenleri, doğru lisans ve minimal paket
- Her cihazda format desteği başlangıçta sorgulanır

## 15.3. AudioTrack

- Uygun sample rate
- Uygun channel mask
- Düşük gecikme modu cihaz destekliyorsa
- Sürekli AudioTrack recreate yapılmaz
- Underrun ölçülür
- Ses odağı doğru alınır/bırakılır
- TV mute ve sistem sesiyle tutarlı davranılır
- Oturum bitince kaynak bırakılır

## 15.4. Ses-görüntü eşzamanı

- Ortak saat modeli kullanılacaktır.
- Ses veya video PTS değerleri duvar saati yerine oturum saatiyle ilişkilendirilecektir.
- Drift sürekli ölçülecektir.
- Küçük drift yumuşak düzeltilecektir.
- Büyük sıçrama kontrollü yeniden eşzamanla çözülecektir.
- Ses patlaması veya hız değişimi kullanıcıya duyurulmayacaktır.
- Video çoğunlukla ses saatine hizalanacaktır.
- Gerektiğinde sessiz ses drop/insert stratejisi ölçülerek uygulanacaktır.

## 15.5. Kullanıcı ses offset’i

Aralık:

```text
-250 ms ... +500 ms
```

Adım:

```text
10 ms
```

Ayar:

- Anlık uygulanır.
- TV cihazına özel saklanır.
- “Sıfırla” seçeneği vardır.
- Varsayılan 0 ms’dir.

## 15.6. Ses hata davranışı

Decoder/key hatasında:

- Statik/parazit çalınmaz.
- PCM doğrulanmadan AudioTrack’e yazılmaz.
- Ses güvenli biçimde susturulur.
- Video mümkünse devam eder.
- Kullanıcıya “Ses akışı toparlanıyor” mesajı kısa ve müdahalesiz gösterilir.
- Oturum yenilenince ses yeniden başlar.

---

# 16. SENKRONİZASYON VE JITTER YÖNETİMİ

## 16.1. Saat modeli

Aşağıdaki saatler ayrıştırılacaktır:

- Gönderici medya saati
- RTP timestamp
- NTP/oturum zaman referansı
- Android monotonic clock
- AudioTrack playback head
- Surface/video release zamanı

Duvar saati değişimi medya zamanlamasını bozmamalıdır.

## 16.2. Jitter ölçümü

Aktif oturumda:

- Paketler arası geliş farkı
- Ortalama jitter
- p95 jitter
- Paket kaybı
- Sıra dışı paket
- Yeniden iletim başarısı
- Video queue depth
- Audio queue depth

ölçülecektir.

## 16.3. Uyarlama

- Jitter yükselirse Otomatik mod tamponu sınırlar içinde artırabilir.
- Ağ düzeldiğinde tampon birden değil kademeli azaltılır.
- Tampon büyümesi gecikme bütçesini aşamaz.
- Sürekli salınımı engelleyen hysteresis bulunur.
- Kullanıcı profil değiştirdiğinde geçiş yumuşak yapılır.

## 16.4. Toparlanma

Aşağıdakilerde otomatik toparlanma beklenir:

- Kısa Wi‑Fi paketi kesintisi
- 1–3% paket kaybı
- Yön değişimi
- Encoder reset
- TV Surface yeniden oluşturma
- iPhone ekranını kısa süre kilitleme/açma
- Uygulamadan ana ekrana dönme
- Ağ IP değişimi; protokol izin verirse yeniden bağlanma

---

# 17. KALİTE PROFİLLERİ

## 17.1. Otomatik — varsayılan

- Cihaz yeteneği belirlenir.
- Ağ ölçümü yapılır.
- En iyi güvenli çözünürlük/fps ilan edilir.
- Tampon otomatik ayarlanır.
- Gereksiz 4K iddiası yapılmaz.
- Kullanıcıya yalnız sorun olduğunda öneri gösterilir.

## 17.2. Düşük gecikme

- En küçük jitter buffer
- Geç kareleri daha agresif bırakma
- Minimal post-processing isteği
- Sunum, oyun dışı etkileşim ve ekran gösterimi
- Ağ çok kötüyse kullanıcı uyarılır

## 17.3. Kararlı görüntü

- Bir miktar daha yüksek tampon
- Film, fotoğraf ve video için
- Drop azaltma
- Gecikme artabilir

## 17.4. Uyumluluk

- 720p veya 1080p30 üst sınırı
- Sorunlu decoder için güvenli codec seçimi
- Düşük RAM ve düşük güçlü TV’ler
- Isınma ve codec hatasını azaltma

---

# 18. CİHAZ YETENEK KADEMELERİ

## 18.1. Kademe A — Premium

Beklenen:

- 1080p60 H.264 donanım decode
- Düşük gecikme codec özelliği
- 2 GB veya daha fazla kullanılabilir RAM
- Güçlü ağ
- 4K TV çıkışı
- İsteğe bağlı HEVC

## 18.2. Kademe B — Standart

Beklenen:

- 1080p30 veya 1080p60
- H.264 donanım decode
- Normal latency
- 1.5–2 GB RAM

## 18.3. Kademe C — Uyumluluk

Beklenen:

- 720p30
- Düşük RAM
- Eski/kararsız decoder
- Daha büyük jitter buffer
- Minimal UI animasyonu

## 18.4. Otomatik kademe seçimi

Uygulama ilk çalıştırmada:

- Codec yeteneklerini
- Maksimum çözünürlüğü
- Desteklenen profili/level’ı
- Low latency desteğini
- RAM sınıfını
- CPU ABI’yi
- Android sürümünü
- Surface/refresh rate seçeneklerini

ölçer.

Sonuç cihazın içinde saklanır; uygulama güncellemesi veya sistem güncellemesi sonrası yeniden ölçülebilir.

---

# 19. PERFORMANS VE KABUL EŞİKLERİ

## 19.1. Referans ağ koşulu

Premium kalite kabul testi şu koşullarda yapılacaktır:

- iPhone ve TV aynı alt ağda
- Televizyon Ethernet veya güçlü 5/6 GHz Wi‑Fi
- iPhone güçlü 5/6 GHz Wi‑Fi
- RSSI yaklaşık -60 dBm veya daha iyi
- Yerel kullanılabilir throughput en az 100 Mbps
- Paket kaybı < %0,5
- p95 jitter < 10 ms
- AP isolation kapalı
- İnternet bağlantısı gerekmeksizin yerel ağ çalışır

## 19.2. Keşif ve bağlantı

| Ölçüm | Hedef |
|---|---:|
| Uygulama açılışından ilan hazır durumuna p95 | ≤ 5 s |
| iPhone listesinde görünme p95 | ≤ 8 s |
| Seçimden eşleştirme ekranına p95 | ≤ 3 s |
| PIN sonrası oturum kurulumu p95 | ≤ 4 s |
| İlk video karesi p50 | ≤ 2 s |
| İlk video karesi p95 | ≤ 4 s |
| İlk ses p95 | ≤ 4 s |

## 19.3. Gecikme

Referans ağ ve Kademe A cihazında:

| Profil | Ölçüm | Hedef |
|---|---|---:|
| Düşük gecikme | uçtan uca p50 | ≤ 150 ms |
| Düşük gecikme | uçtan uca p95 | ≤ 250 ms |
| Otomatik | uçtan uca p95 | ≤ 350 ms |
| Kararlı | uçtan uca p95 | ≤ 500 ms |

Gecikme, yüksek hızlı kamera veya güvenilir zaman kodlu test düzeneğiyle ölçülecektir. Kullanıcının “hızlı görünüyor” yorumu ölçüm yerine geçmez.

## 19.4. Kare performansı

1080p60 referans testinde:

- Ortalama işlenen fps ≥ 59
- Kare kaybı < %0,5, 30 dakikalık testte
- 500 ms’den uzun görüntü donması: 0
- 250–500 ms arası donma: saat başına en fazla 1
- Bozuk/makrobloklu gösterilen kare: 0; kayıp kareyi bırakmak bozuk kare göstermekten iyidir
- Yanlış en-boy oranı: 0
- Yön değişiminden yeni doğru kareye p95 ≤ 1,5 s

1080p30 testinde:

- Ortalama işlenen fps ≥ 29,5
- Kare kaybı < %0,25

## 19.5. Ses-görüntü eşzamanı

- p95 A/V farkı: ±50 ms içinde
- Kesin üst sınır: ±100 ms
- 2 saat sonunda drift: ±50 ms içinde
- Ses underrun: 30 dakikada 0 hedef, en fazla 1 kısa olay
- Patlama, statik veya yüksek sesli bozuk PCM: 0

## 19.6. Toparlanma

| Olay | Hedef |
|---|---:|
| 500 ms paket kesintisi sonrası hareketli görüntü | ≤ 2 s |
| Decoder stall sonrası otomatik iyileşme | ≤ 2 s |
| Yön değişimi | ≤ 1,5 s |
| Surface yeniden oluşumu | ≤ 2 s |
| Kısa ağ roaming sonrası oturum | ≤ 8 s |
| Uygulama hizmeti kontrollü yeniden başlatma | ≤ 5 s |

## 19.7. Uzun süreli dayanıklılık

### 2 saatlik soak testi

- Crash: 0
- ANR: 0
- Decoder kilitlenmesi: 0
- Ses kayması üst sınır ihlali: 0
- Bellek büyümesi: başlangıç kararlı seviyesine göre <%10
- Native handle/fd sızıntısı: 0
- WakeLock sızıntısı: 0

### 8 saatlik dayanıklılık testi

En az bir Kademe A ve bir Kademe B cihazında:

- Crash: 0
- ANR: 0
- Sürekli bellek artışı: 0
- Oturum yeniden kurulamayacak bozulma: 0
- A/V drift üst sınır ihlali: 0

## 19.8. Termal davranış

- 30 dakika 1080p60 sonrası cihaz termal throttling durumu kaydedilir.
- Uygulama cihazı sürekli severe thermal seviyesine itmemelidir.
- Termal düşüşte 1080p30/720p uyumluluk moduna geçiş önerilebilir.
- Termal olay kullanıcıya teknik olmayan mesajla gösterilir.
- Yazılım decoder nedeniyle sürdürülemez CPU yükü kabul edilmez.

## 19.9. Bellek

Mutlak limit cihaz sınıfına göre ölçülecektir.

Kurallar:

- Low-RAM TV kalite sınırları aşılmayacaktır.
- Sınırsız paket tamponu olmayacaktır.
- Büyük Bitmap kullanılmayacaktır.
- Video kareleri Java heap’e kopyalanmayacaktır.
- Oturum kapandıktan sonra codec, Surface referansı, AudioTrack ve native buffer bırakılacaktır.
- 20 ardışık bağlan/ayrıl testinden sonra bellek başlangıç seviyesine dönmelidir.

## 19.10. UI performansı

- Hazır ekranı soğuk başlangıç p95 ≤ 2,5 s
- Kumanda odak hareketinde belirgin takılma yok
- Jank oranı kritik akışlarda < %1
- Baseline Profile bulunur
- Macrobenchmark raporu teslim edilir
- Yansıtma sırasında UI animasyonları medya thread’ini etkilemez

---

# 20. ARAYÜZ VE DENEYİM TASARIMI

## 20.1. Görsel yaklaşım

- Özgün
- Minimal
- Büyük ekrandan okunabilir
- Koyu, sakin arka plan
- Net bağlantı durumu
- Gereksiz poster/film arayüzü yok
- Reklam alanı yok
- Hareketli arka plan yok
- Yansıtma sırasında arayüz görünmez

Önerilen görsel kimlik:

- Koyu kömür arka plan
- Açık nötr metin
- Tek vurgu rengi
- Bağlantı durumunda yeşil
- Uyarıda amber
- Hata durumunda kırmızı
- Logo: iki üst üste ekran veya dalga/ayna kavramı; Apple/AirScreen ikonuna benzemeyecek

Renkler erişilebilir kontrast testinden geçecektir.

## 20.2. TV güvenli alanı

- Ana metinler ekran kenarından en az %5 içeride
- Overscan olasılığı dikkate alınır
- 720p, 1080p ve 4K’da test edilir
- Metin ölçeği bozulmaz
- Odak halkası kırpılmaz

## 20.3. D-pad

Tüm işlevler:

- Yukarı
- Aşağı
- Sol
- Sağ
- OK/Enter
- Back
- Play/Pause
- Home davranışları

ile kullanılabilmelidir.

Kesin yasak:

- Dokunma gerektiren kontrol
- Fare zorunluluğu
- Gizli hover
- Odaksız düğme
- Kumandayla ulaşılamayan ayar
- Sonsuz odak tuzağı

## 20.4. Odak tasarımı

- Her ekranda ilk odak belirlenir.
- Geri dönünce önceki odak korunur.
- Odaklı öğe ölçek, çerçeve ve kontrastla belirginleşir.
- Animasyon 100–180 ms aralığında ve hafif olmalıdır.
- Medya yayınında odak animasyonları devre dışı veya düşük maliyetli olabilir.
- TalkBack açıklamaları bulunur.

---

# 21. EKRANLAR

## 21.1. Açılış ekranı

Gösterim:

- Logo
- Uygulama adı
- En fazla 1,5 saniye
- Ağ veya protokol kurulumu bu ekranda UI’ı dondurmaz
- Splash sonrası doğrudan ilgili durum ekranı

## 21.2. İlk kurulum

Adımlar:

1. Ürünün ne yaptığı
2. Aynı ağ gereksinimi
3. Yerel ağ izni açıklaması
4. PIN güvenliği önerisi
5. TV alıcı adı
6. Hazır ekranına geçiş

Metin örneği:

> Bu uygulama iPhone veya iPad ekranını aynı yerel ağ üzerinden televizyonunuza yansıtır. Görüntü ve ses buluta gönderilmez.

## 21.3. Yerel ağ izin ekranı

- İzin neden gerekli açıklanır.
- “İzin Ver” ana eylemdir.
- “Şimdi Değil” seçilebilir.
- Red sonrası alıcı başlatılmaz.
- Tekrarlı taciz edici izin isteme yapılmaz.
- Kalıcı red durumunda Ayarlar’a gitme seçeneği.

## 21.4. Hazır ekranı

Ana bileşenler:

- Büyük durum: **Yansıtma için hazır**
- Alıcı adı
- iPhone’dan üç adımlı talimat
- Ağ durumu rozeti
- PIN koruması rozeti
- Ayarlar
- Yardım
- Alıcıyı durdur

Örnek:

```text
Yansıtma için hazır

iPhone'unuzda:
1. Denetim Merkezi'ni açın
2. Ekran Yansıtma'ya dokunun
3. "AirRelay — Salon"u seçin

Ağ: İyi        PIN: Açık
```

## 21.5. PIN ekranı

- 4 haneli kod çok büyük gösterilir.
- Kalan süre gösterilebilir.
- Gönderici adı güvenilmez metin olarak sanitize edilir.
- “Reddet” düğmesi bulunur.
- “Bu cihazı güven” seçeneği, bağlantı başarıyla kurulduktan sonra sorulur.
- Kod ekran görüntüsü olarak saklanmaz.

## 21.6. Bağlanıyor ekranı

Aşamalar teknik olmayan biçimde:

- Cihaz doğrulanıyor
- Görüntü hazırlanıyor
- Ses eşzamanlanıyor

10 saniyeyi aşarsa:

- Ağ kontrol önerisi
- İptal
- Tanılama

## 21.7. Tam ekran yansıtma

Varsayılan:

- Sadece video
- Siyah arka plan
- Sistem çubukları gizli
- Cursor yok
- Bilgi katmanı 3 saniye sonra kaybolur

Kumandada OK:

- Geçici kontrol katmanı

Back:

- İlk basışta kontrol katmanı
- İkinci basışta “Yansıtmayı durdur?” onayı

## 21.8. Hızlı kontrol katmanı

- Yansıtmayı durdur
- Sığdır/Doldur
- Ses aç/kapat
- Kalite profili
- İstatistik
- Ayarlar

Katman:

- Görüntüyü tamamen kapatmaz
- 5 saniye işlem yoksa kaybolur
- Ana thread’i yormaz

## 21.9. İstatistik ekranı

Kullanıcı modu:

- Çözünürlük
- FPS
- Bağlantı kalitesi
- Ses durumu
- Gecikme sınıfı
- Paket kaybı sınıfı

Geliştirici modu:

- Codec adı
- Video bitrate
- Audio codec
- p50/p95 jitter
- Drop count
- Decoder queue
- Audio underrun
- A/V offset
- Termal durum
- Memory
- Active interface
- Session duration

Geliştirici istatistiği varsayılan kapalıdır.

## 21.10. Ayarlar

Bölümler:

### Alıcı

- Alıcı adı
- Alıcıyı otomatik başlat
- Uygulama açıkken hazır bekle
- Arka planda hazır bekle
- Tek aktif cihaz politikası

### Güvenlik

- PIN modu
- Güvenilen cihazlar
- Tüm güvenleri sıfırla
- Yeni alıcı kimliği oluştur

### Görüntü

- Otomatik/Düşük gecikme/Kararlı/Uyumluluk
- Maksimum çözünürlük
- Maksimum FPS
- Sığdır/Doldur
- Overscan
- Minimal post-processing
- Sorunlu decoder uyumluluk listesi

### Ses

- Ses açık
- Ses gecikmesi
- Kanal modu
- Ses çıkışını yeniden başlat

### Tanılama

- Ağ testi
- Codec testi
- İlan testi
- Log dışa aktar
- İstatistik katmanı
- Uygulama bilgisi
- Açık kaynak lisansları

### Gizlilik

- Veri işleme açıklaması
- Hiçbir analitik olmadığının açıklaması
- Yerel kayıtları sil

## 21.11. Yardım ve sorun giderme

Sorunlar:

- TV iPhone listesinde görünmüyor
- PIN çıkmıyor
- Görüntü siyah
- Ses yok
- Ses geriden geliyor
- Görüntü takılıyor
- Bağlantı kopuyor
- Telefon ve TV farklı ağda
- Misafir ağı/AP isolation
- 2.4 GHz yoğunluğu
- DRM korumalı içerik
- Yerel ağ izni kapalı
- TV güç tasarrufu

Yardım çevrimdışı uygulama içinde bulunacaktır.

---

# 22. HATA YÖNETİMİ

## 22.1. Hata kategorileri

```text
PERMISSION_DENIED
NO_NETWORK
MULTICAST_BLOCKED
PORT_IN_USE
ADVERTISE_FAILED
PAIRING_FAILED
AUTH_FAILED
UNSUPPORTED_SENDER
UNSUPPORTED_CODEC
DECODER_INIT_FAILED
DECODER_STALLED
AUDIO_INIT_FAILED
AUDIO_UNDERRUN
CLOCK_SYNC_FAILED
PACKET_LOSS_HIGH
DRM_OR_PROTECTED_CONTENT
NETWORK_CHANGED
SESSION_TIMEOUT
NATIVE_CRASH_RECOVERED
UNKNOWN
```

## 22.2. Kullanıcı mesajı ilkeleri

- Teknik exception metni gösterilmez.
- Kullanıcıya ne olduğu ve ne yapabileceği söylenir.
- Suçlayıcı dil kullanılmaz.
- “Bilinmeyen hata 127” gibi mesajlar yoktur.
- Her kullanıcı mesajının tanılama kodu olabilir.
- Tanılama kodu kişisel veri içermez.

Örnek:

> Televizyon ve iPhone birbirini göremiyor. İki cihazın aynı Wi‑Fi ağında olduğundan ve yönlendiricide “AP isolation” özelliğinin kapalı olduğundan emin olun.

## 22.3. Hata sonrası davranış

- Parser hatası tüm uygulamayı çökertmez.
- Tek istemci hatası alıcı hizmetini bozmaz.
- Decoder hatası oturumu kontrollü yeniden başlatır.
- Üç tekrar sonrası uyumluluk moduna geçiş önerilir.
- Native crash tekrar ederse otomatik döngü durdurulur.
- Kullanıcıya log dışa aktarma sunulur.

---

# 23. GİZLİLİK

## 23.1. Toplanmayacak veriler

- Ekran görüntüsü
- Ses kaydı
- Yansıtılan içerik
- Apple hesabı
- Kişiler
- Fotoğraflar
- Cihaz içeriği
- Reklam kimliği
- Konum
- IP adresi geçmişi
- Kullanım analitiği
- Çökme telemetrisi; kullanıcı göndermedikçe
- İzleme süresi
- Uygulama listesi

## 23.2. Yerel tutulan veriler

Yalnızca:

- Kullanıcı ayarları
- Alıcı adı
- Güvenilen cihaz kayıtları
- Kriptografik kimlik
- Son tanılama ring buffer’ı
- Cihaz uyumluluk ayarları

## 23.3. Loglar

- Varsayılan olarak minimum
- Ring buffer
- Boyut sınırı
- Süre sınırı
- Anahtar/şifre/PIN yok
- Tam IP/MAC varsayılan olarak maskeli
- Medya payload yok
- Kullanıcı açıkça dışa aktarmadıkça cihazdan çıkmaz
- Dışa aktarılan paket kullanıcıya önizlenebilir

## 23.4. Ağ çıkışı denetimi

Yayın yapısı:

- Uygulamanın internet sunucusuna çağrı yapmaması beklenir.
- CI testi, uygulama çalışırken yerel alt ağ dışına paket çıkmadığını doğrular.
- Güncelleme kontrolü uygulama içinde yapılmaz; mağaza yönetir.
- Crash raporlama SDK’sı eklenmez.
- CDN, reklam, analitik ve config servisi yoktur.

---

# 24. ERİŞİLEBİLİRLİK

- TalkBack ile bütün kurulum akışı kullanılabilir.
- PIN rakamları doğru okunur.
- Durum yalnız renkle anlatılmaz.
- Kontrast en az WCAG AA düzeyinde hedeflenir.
- Metin boyutu TV uzaktan izleme için yeterlidir.
- Odak sırası mantıklıdır.
- Hareket azaltma tercihi dikkate alınır.
- Yanıp sönen içerik yoktur.
- Sesli uyarı tek başına kullanılmaz.
- Hata mesajları ekran okuyucu tarafından duyurulur.
- Kumanda düğmeleri semantik etiket içerir.

---

# 25. YERELLEŞTİRME

P0:

- Türkçe

P1:

- İngilizce

Kurallar:

- Metinler kaynak dosyasında
- Hard-coded UI metni yok
- Çoğul kuralları
- RTL yerleşim bozmayacak temel yapı
- Tarih/sayı yerel biçim
- Teknik codec isimleri çevrilmez
- Kullanıcı metni kısa ve televizyon mesafesinden okunabilir

---

# 26. DEPOLAMA VE VERİ MODELLERİ

## 26.1. ReceiverSettings

```kotlin
data class ReceiverSettings(
    val receiverName: String,
    val autoStartEnabled: Boolean,
    val backgroundReadyEnabled: Boolean,
    val pinMode: PinMode,
    val qualityProfile: QualityProfile,
    val maxResolution: ResolutionLimit,
    val maxFrameRate: Int,
    val scalingMode: ScalingMode,
    val overscanPercent: Int,
    val audioEnabled: Boolean,
    val audioOffsetMs: Int,
    val diagnosticsOverlayEnabled: Boolean,
)
```

## 26.2. TrustedController

```kotlin
data class TrustedController(
    val controllerId: String,
    val displayName: String?,
    val publicKeyFingerprint: String,
    val createdAtEpochMs: Long,
    val lastConnectedAtEpochMs: Long?,
)
```

Private key materyali bu modelde tutulmaz.

## 26.3. ReceiverStatus

```kotlin
sealed interface ReceiverStatus {
    data object Stopped : ReceiverStatus
    data object PermissionRequired : ReceiverStatus
    data object Starting : ReceiverStatus
    data class NetworkUnavailable(val reason: NetworkReason) : ReceiverStatus
    data class Ready(val receiverName: String, val networkHealth: NetworkHealth) : ReceiverStatus
    data class Pairing(val senderLabel: String?, val pin: String) : ReceiverStatus
    data class Connecting(val stage: ConnectionStage) : ReceiverStatus
    data class Streaming(val metrics: StreamSummary) : ReceiverStatus
    data class Recovering(val reason: RecoveryReason) : ReceiverStatus
    data class Error(val code: ErrorCode, val recoverable: Boolean) : ReceiverStatus
}
```

## 26.4. StreamMetrics

```kotlin
data class StreamMetrics(
    val width: Int,
    val height: Int,
    val nominalFps: Double,
    val renderedFps: Double,
    val videoCodec: String,
    val audioCodec: String?,
    val bitrateBps: Long?,
    val packetLossPercent: Double,
    val jitterP95Ms: Double,
    val droppedFrames: Long,
    val avOffsetMs: Double,
    val audioUnderruns: Long,
    val decoderQueueDepth: Int,
    val sessionDurationMs: Long,
)
```

---

# 27. DOMAIN ARAYÜZLERİ

## 27.1. ReceiverController

```kotlin
interface ReceiverController {
    val status: StateFlow<ReceiverStatus>
    val metrics: StateFlow<StreamMetrics?>

    suspend fun start(): Result<Unit>
    suspend fun stop(): Result<Unit>
    suspend fun rejectPendingSender(): Result<Unit>
    suspend fun stopActiveSession(): Result<Unit>
    suspend fun restartReceiver(): Result<Unit>
}
```

## 27.2. TrustedControllerRepository

```kotlin
interface TrustedControllerRepository {
    fun observeAll(): Flow<List<TrustedController>>
    suspend fun trust(controller: TrustedController): Result<Unit>
    suspend fun revoke(controllerId: String): Result<Unit>
    suspend fun revokeAll(): Result<Unit>
}
```

## 27.3. DiagnosticsRepository

```kotlin
interface DiagnosticsRepository {
    val liveMetrics: StateFlow<StreamMetrics?>
    suspend fun runNetworkCheck(): NetworkCheckResult
    suspend fun runCodecCheck(): CodecCheckResult
    suspend fun exportRedactedBundle(destination: Uri): Result<Unit>
    suspend fun clearLogs(): Result<Unit>
}
```

---

# 28. PROJE KLASÖR YAPISI

```text
/
├── app-tv/
├── core-common/
├── core-domain/
├── core-ui-tv/
├── core-settings/
├── receiver-contract/
├── receiver-service/
├── receiver-discovery/
├── receiver-session/
├── receiver-security/
├── receiver-native/
├── media-video/
├── media-audio/
├── media-sync/
├── media-hls/
├── diagnostics/
├── benchmark/
├── test-fixtures/
├── docs/
│   ├── ARCHITECTURE.md
│   ├── PROTOCOL_SCOPE.md
│   ├── SECURITY.md
│   ├── PRIVACY.md
│   ├── DEPENDENCY_AND_LICENSE_DECISION.md
│   ├── DEVICE_COMPATIBILITY.md
│   ├── PERFORMANCE_METHOD.md
│   ├── TEST_PLAN.md
│   ├── RELEASE_CHECKLIST.md
│   └── KNOWN_LIMITATIONS.md
├── legal/
│   ├── OPEN_SOURCE_NOTICES.md
│   ├── THIRD_PARTY_LICENSES/
│   └── TRADEMARK_REVIEW.md
├── scripts/
│   ├── build-release.sh
│   ├── verify-16kb.sh
│   ├── verify-abis.sh
│   ├── collect-device-logs.sh
│   ├── run-soak-test.sh
│   └── generate-sbom.sh
├── .github/workflows/ veya eşdeğer CI
├── gradle/
├── libs.versions.toml
├── settings.gradle.kts
├── build.gradle.kts
├── README.md
├── README_USER_TR.md
├── CHANGELOG.md
└── LICENSE
```

---

# 29. TEST STRATEJİSİ

## 29.1. Test piramidi

### Unit test

- Durum makinesi
- Süre aşımı
- İzin akışı
- Ayarlar
- İsim normalizasyonu
- PIN rate limit
- Paket sıralama
- Jitter hesabı
- Clock conversion
- A/V drift algoritması
- Queue drop politikası
- Error mapping
- Log redaction

### Native test

- Parser
- Boyut sınırları
- Crypto wrapper
- Buffer ownership
- Packet reordering
- Malformed payload
- Integer overflow
- Memory lifecycle

### Fuzz test

- RTSP request
- HTTP header
- SDP
- Pairing payload
- Video config NAL
- Audio config
- Metadata plist/binary plist
- Service TXT record

### Instrumented test

- Permission
- Service lifecycle
- TV navigation
- Activity recreation
- Surface recreation
- AudioTrack lifecycle
- MediaCodec lifecycle
- Network loss
- Background/foreground
- Process death recovery

### Gerçek cihaz E2E

- iPhone listesinde görünme
- PIN
- İlk kare
- Ses
- Yön değişimi
- Video oynatma
- Uygulama geçişi
- Kilit/aç
- Kopma/toparlanma
- Uzun süre

## 29.2. Sahte başarı yasağı

Aşağıdakiler kabul testi değildir:

- Önceden kaydedilmiş MP4 oynatmak
- Lokal H.264 dosyası çözmek
- Emulator’da sahte paket göndermek
- UI’da “Bağlandı” yazdırmak
- Mock receiver
- Yalnız unit test
- Yalnız derleme
- Bir ekran görüntüsü
- Bir kez 10 saniye çalıştırmak

Gerçek kabul:

- Gerçek iPhone/iPad
- Sistem Ekran Yansıtma menüsü
- Gerçek yerel ağ
- Gerçek Android TV donanımı
- Canlı ekran ve ses
- Ölçümlü süre

---

# 30. GÖNDERİCİ TEST MATRİSİ

Her yayında en az:

- Küçük/eski iPhone donanım sınıfı
- Orta nesil iPhone
- Güncel iPhone
- Güncel Pro sınıfı iPhone
- Bir iPad
- Güncel kararlı iOS/iPadOS
- Önceki desteklenen sürüm hattı
- En az bir macOS cihazı, P1 ise

Gerçek cihaz modeli ve OS build numarası raporda yazılacaktır.

Test içerikleri:

- Ana ekran
- Ayarlar ekranı
- Safari hızlı kaydırma
- Fotoğraflar
- Kamera önizleme
- Harita hareketi
- Metin yazma
- Dikey/yatay döndürme
- 30 fps video
- 60 fps video
- Sistem sesi
- Bildirim sesi
- Ses kapalı akış
- Korumalı içerik denemesi; aşılmaması doğrulanır

---

# 31. ANDROID TV TEST MATRİSİ

En az altı fiziksel alıcı sınıfı:

1. Güncel Google TV referans cihazı
2. Android TV kutusu
3. Android TV stick
4. Sony/TCL/Philips benzeri yerleşik TV sınıfı
5. Eski/düşük RAM Android TV
6. Premium güçlü cihaz

Kapsanacak:

- Android 8/9 sınıfı mümkünse
- Android 10/11
- Android 12/13
- Android 14+
- Android 17 yerel ağ izin testi, TV görüntüsü mevcut olduğunda
- armv7a
- arm64
- 1 GB/low-RAM
- 2 GB+
- farklı vendor MediaCodec’ler
- Ethernet
- Wi‑Fi

Emülatör yalnız UI ve API davranışı testidir; gerçek codec/ağ kabulünün yerine geçmez.

---

# 32. AĞ BOZULMA TESTLERİ

Kontrollü ağ laboratuvarında:

| Senaryo | Değerler |
|---|---|
| Paket kaybı | %0, %0,5, %1, %3, %5 |
| Jitter | 0, 10, 20, 50, 100 ms |
| Ek gecikme | 0, 20, 50, 100 ms |
| Bant genişliği | 100, 50, 20, 10 Mbps |
| Kesinti | 100, 500, 1000, 3000 ms |
| Reordering | %0, %1, %3 |
| Duplication | %0, %1 |
| Ağ değişimi | Wi‑Fi roaming, DHCP IP değişimi |
| Multicast | açık/engelli |
| AP isolation | açık/kapalı |

Beklenti:

- Uygulama çökmez.
- Kuyruk sınırsız büyümez.
- Bozuk ses üretmez.
- İyi ağ geri geldiğinde toparlanır.
- Kullanıcıya doğru öneri verir.

---

# 33. ZORUNLU UÇTAN UCA KABUL SENARYOLARI

## E2E-001 — Temiz ilk kurulum

1. Uygulama ilk kez kurulur.
2. Yerel ağ izni verilir.
3. PIN açık bırakılır.
4. iPhone listesinde alıcı görünür.
5. PIN girilir.
6. Görüntü ve ses başlar.
7. 30 dakika sürer.
8. Yansıtma iPhone’dan kapatılır.
9. TV hazır ekranına döner.

Beklenti: Crash/ANR/hata yok.

## E2E-002 — İzin reddi

1. Yerel ağ izni reddedilir.
2. Uygulama dinleme soketi açmamalıdır.
3. Hazır gibi görünmemelidir.
4. Kullanıcıya ayar yolu sunmalıdır.
5. İzin verildikten sonra yeniden başlatmadan çalışabilmelidir.

## E2E-003 — Güvenilen cihaz

1. İlk bağlantı PIN ile kurulur.
2. TV’de cihaz güvenilir yapılır.
3. Oturum kapatılır.
4. Yeniden bağlantı doğrulanır.
5. Güven kaydı silinir.
6. Sonraki bağlantıda PIN tekrar gerekir.

## E2E-004 — İkinci gönderici

1. Bir iPhone yansıtma yapar.
2. İkinci iPhone bağlanmaya çalışır.
3. İlk oturum kesilmez.
4. İkinci cihaz meşgul yanıtı alır.
5. TV kullanıcısı isterse devralmaya onay verebilir.

## E2E-005 — Dikey/yatay

1. Dikey ana ekran
2. Yatay video
3. Tekrar dikey
4. En az 20 dönüş

Beklenti: Çökme, eğrilme, eski oran, yeşil kare yok.

## E2E-006 — 1080p60

1. 60 fps hareketli test içeriği
2. 30 dakika
3. FPS/drop/latency ölçümü

Beklenti: Bölüm 19 eşikleri.

## E2E-007 — Ses senkronu

1. Görsel clap/timecode test videosu
2. 2 saat
3. Başlangıç, 30, 60, 120. dakikada ölçüm

Beklenti: ±50 ms p95.

## E2E-008 — Kısa ağ kesintisi

1. 500 ms kesinti
2. Ağ geri gelir
3. Oturum toparlanır

Beklenti: ≤2 s hareketli görüntü.

## E2E-009 — Yoğun kötü ağ

1. %3 kayıp ve 50 ms jitter
2. 15 dakika

Beklenti: Crash yok; kontrollü drop; kullanıcı uyarısı; bellek büyümesi yok.

## E2E-010 — Surface yaşam döngüsü

1. Kumandayla Home
2. Uygulamaya dön
3. TV ekran çözünürlüğü değişimi
4. Activity recreation

Beklenti: Kaynak sızıntısı yok; oturum ya devam eder ya anlaşılır biçimde yeniden bağlanır.

## E2E-011 — Korumalı içerik

1. Korumalı uygulamadan yayın denemesi
2. Ürün korumayı aşmaz
3. Uygulama çökmez
4. Normal ana ekrana dönünce yansıtma geri gelir

## E2E-012 — 20 bağlantı döngüsü

20 kez:

- Bağlan
- 2 dakika yayın
- Ayrıl

Beklenti: Bellek, fd, thread, codec ve AudioTrack sızıntısı yok.

## E2E-013 — 8 saat

- Sürekli ekran yansıtma
- Periyodik yön değişimi
- Periyodik uygulama geçişi

Beklenti: Bölüm 19 dayanıklılık hedefleri.

## E2E-014 — İnternetsiz yerel ağ

1. Router internet bağlantısı kesilir.
2. Yerel Wi‑Fi devam eder.
3. Uygulama keşif, PIN, görüntü ve sesi çalıştırır.

Beklenti: Bulut bağımlılığı olmadığı kanıtlanır.

## E2E-015 — Dış ağ trafiği

1. Uygulamanın paketleri yakalanır.
2. Aktif ve idle durum analiz edilir.

Beklenti: Yerel ağ dışına uygulama kaynaklı veri çıkışı yok.

---

# 34. OTOMASYON VE CI/CD

Her pull request:

- Kotlin compile
- Native compile
- Unit test
- Native test
- Android lint
- Detekt
- Ktlint
- C/C++ static analysis
- Dependency verification
- License scan
- Secret scan
- Vulnerability scan
- SBOM üretimi
- Debug APK
- Release-like APK
- ABI denetimi
- 16 KB page denetimi

Ana dal/gece:

- Instrumented TV emulator test
- Fuzz corpus
- Sanitizer test
- Macrobenchmark
- Baseline Profile üretim doğrulaması
- Reproducible build hash karşılaştırması

Release adayı:

- Fiziksel cihaz laboratuvarı
- 2 saat soak
- 8 saat soak
- Ağ bozunma testleri
- Paket yakalama
- Gizlilik kontrolü
- Lisans kapısı
- Play pre-launch raporu
- Manuel D-pad erişilebilirlik testi

---

# 35. KOD KALİTESİ

## 35.1. Genel

- Kotlin strict nullability
- Açık hata tipleri
- `Result` veya sealed error modeli
- Global mutable state yok
- Singleton yalnız açık gerekçeyle
- Main thread bloklama yok
- Coroutine cancellation doğru
- Scope yaşam döngüsüne bağlı
- Native pointer sahipliği belgeli
- Her public API KDoc
- Karmaşık algoritma testli
- TODO/FIXME release yapıda 0
- Debug backdoor 0
- Sabit gizli anahtar 0

## 35.2. Dosya ve sınıf boyutu

Hedef:

- Kotlin dosyası tercihen ≤400 satır
- 550 satır hard review eşiği
- Fonksiyon tercihen ≤40 satır
- Büyük state machine ayrı reducer
- UI composable’ları küçük ve önizlenebilir
- Native parser ve transport ayrılır

## 35.3. Hata yutma yasağı

Yasak:

```kotlin
catch (e: Exception) {
    // ignore
}
```

Her hata:

- Yeniden fırlatılır
- Domain hatasına çevrilir
- Kontrollü loglanır
- Kullanıcı davranışı belirlenir

## 35.4. Zaman ve rastgelelik

- Medya zamanlaması için monotonic clock
- Test edilebilir Clock soyutlaması
- Güvenlik için SecureRandom
- Testlerde deterministic fake
- Duvar saati medya sync için kullanılmaz

---

# 36. GÜVENLİK TESTLERİ

Zorunlu:

- Malformed RTSP fuzzing
- Header bomb
- Oversized payload
- Negative length
- Integer overflow
- Use-after-free analizi
- Double free analizi
- JNI invalid reference
- Connection flood
- PIN brute-force
- mDNS name injection
- Log injection
- Unicode bidi/terminal escape
- Session hijack denemesi
- Replay denemesi
- WAN erişim denemesi
- Background permission revoke
- Keystore reset
- Data backup/restore davranışı
- Native library tamper testi
- Release build debuggable=false
- Network Security Config denetimi

P0 güvenlik açığıyla yayın yapılamaz.

---

# 37. BAŞLANGIÇ, ARKA PLAN VE GÜÇ

## 37.1. Varsayılan davranış

- Uygulama açılınca alıcı başlar.
- Kullanıcı kapatırsa kapalı kalır.
- Arka planda hazır bekleme varsayılan kapalı veya açık seçimi ürün sahibince belirlenebilir; güvenli varsayılan kapalıdır.
- İlk kurulumda kullanıcıya açıklanır.

## 37.2. Otomatik başlatma

- Kullanıcı açıkça etkinleştirir.
- Boot receiver yalnız platform kurallarına uygunsa çalışır.
- Yasak foreground service başlatma hilesi yoktur.
- Uygulama güncellemesi sonrası ayar korunur.
- Ağ hazır olmadan ilan yapılmaz.

## 37.3. TV uyku

- TV uykuya girerse oturum güvenli sonlanabilir.
- Uyanınca alıcı durumunu yeniler.
- Eski socket/port bırakılır.
- Kullanıcı tercihi açıksa yeniden ilan eder.
- Sonsuz wake lock ile TV uyanık tutulmaz.

---

# 38. TANILAMA PAKETİ

Kullanıcı açıkça dışa aktardığında ZIP:

```text
diagnostics/
├── summary.txt
├── app-version.txt
├── android-build.txt
├── codec-capabilities.json
├── network-redacted.json
├── session-metrics.csv
├── receiver-state.log
├── native-crash.txt
├── memory-summary.txt
└── privacy-manifest.txt
```

İçermeyecek:

- Medya paketi
- Ekran görüntüsü
- Ses
- PIN
- Özel anahtar
- Tam MAC
- Apple hesabı
- Kullanıcı fotoğrafı
- Uygulama içeriği
- Tam SSID; varsayılan maskeli
- Tam IP; varsayılan maskeli

---

# 39. MAĞAZA VE DAĞITIM

## 39.1. Paketler

Teslim:

- `AirRelay-release.aab`
- `AirRelay-universal-test.apk`
- Native debug symbols
- Mapping file
- Kaynak kodu
- Build talimatı
- SBOM

## 39.2. Google Play

- TV için Android App Bundle
- TV ekran görüntüleri
- TV banner
- Doğru içerik derecelendirmesi
- Veri Güvenliği formunda gerçek beyan
- Reklam yok
- Veri toplama yok
- Yerel ağ izin açıklaması
- DRM sınırı mağaza açıklamasında
- “Bütün uygulamaları yansıtır” gibi yanlış iddia yok
- Apple veya AirScreen resmî ilişkisi iddiası yok

## 39.3. Ücretsiz model

- Reklam yok
- Satın alma yok
- Abonelik yok
- Günlük kullanım sınırı yok
- Filigran yok
- Maksimum süre kısıtı yok
- Kaliteyi ücret karşılığı açma yok
- Kullanıcı hesabı yok

---

# 40. TESLİM DOSYALARI

Ajan aşağıdakilerin tamamını teslim edecektir:

1. Tam kaynak kodu
2. Git geçmişi veya anlamlı commit yapısı
3. Çalışan Android Studio projesi
4. AAB
5. İmzalı test APK’sı
6. Build script
7. Keystore üretim talimatı; gerçek özel yayın anahtarı teslim paketine gömülmez
8. Mimari dokümanı
9. Protokol kapsam dokümanı
10. Güvenlik dokümanı
11. Gizlilik politikası
12. Kullanıcı kılavuzu Türkçe
13. Geliştirici kurulum kılavuzu
14. Açık kaynak bildirimleri
15. Üçüncü taraf lisans dosyaları
16. SBOM, SPDX veya CycloneDX
17. Bağımlılık ve lisans karar raporu
18. Test planı
19. Unit test raporu
20. Native test raporu
21. Fuzz test raporu
22. Gerçek cihaz matrisi
23. E2E kabul raporu
24. 2 saat soak raporu
25. 8 saat soak raporu
26. Gecikme ölçüm raporu
27. A/V sync raporu
28. Kare kaybı raporu
29. Ağ bozunma raporu
30. Bellek/CPU/termal raporu
31. 16 KB page uyum raporu
32. ABI raporu
33. Güvenlik tarama raporu
34. Paket yakalama/gizlilik raporu
35. Bilinen sınırlamalar
36. Sürüm kontrol listesi
37. Mağaza metni taslağı
38. TV ekran görüntüleri
39. App icon ve banner kaynakları
40. Son kabul özeti

---

# 41. P0 HATA SINIFLARI

Aşağıdaki hatalardan biri varsa ürün yayınlanamaz:

- iPhone listesinde alıcı görünmüyor
- Güncel iOS gerçek cihazında bağlantı kurulmuyor
- PIN eşleştirme çalışmıyor
- Görüntü başlamıyor
- Ses sürekli çalışmıyor
- 1080p60 referans cihazda kabul eşiğini karşılamıyor
- 500 ms’den uzun düzenli donmalar
- Bozuk kare/makroblok
- A/V sync >100 ms
- 30 dakikada crash/ANR
- Decoder stall toparlanmıyor
- 20 bağlantı döngüsünde kaynak sızıntısı
- Native güvenlik açığı
- Belirsiz lisans
- DRM aşma
- Yerel ağ dışına izinsiz trafik
- Reklam/analitik
- İzin reddinde gizlice ağ erişimi
- D-pad ile ulaşılamayan ana işlev
- 16 KB page uyumsuz native library
- Eksik 32/64 bit yapı
- Uygulama AirScreen/Apple marka görünümünü kopyalıyor
- Test kanıtı yok
- Sahte veya yalnız simüle edilmiş kabul testi

---

# 42. AJANIN UYGULAMA SIRASI

## Aşama 0 — Araştırma ve karar

Teslim:

- `DEPENDENCY_AND_LICENSE_DECISION.md`
- Protokol çekirdeği aday testi
- Lisans ve köken envanteri
- Fiziksel iPhone bağlantı spike’ı
- Teknik risk listesi

Bu aşama geçmeden ana ürün kodu seçilmez.

## Aşama 1 — İskelet

- Android TV proje
- Compose for TV
- D-pad
- Ayarlar
- Yerel ağ izni
- ReceiverService
- Durum makinesi
- CI

## Aşama 2 — Keşif ve eşleştirme

- mDNS
- Alıcı adı
- iPhone’da görünürlük
- PIN
- Güvenilen cihaz
- Güvenlik testleri

## Aşama 3 — Video

- H.264 ingest
- MediaCodec
- SurfaceView
- Yön/çözünürlük
- Queue/drop
- Watchdog
- 1080p30
- 1080p60

## Aşama 4 — Ses ve sync

- AAC-ELD
- AAC-LC
- ALAC
- AudioTrack
- Ortak saat
- A/V drift
- Offset

## Aşama 5 — Dayanıklılık

- Kopma
- Reconnect
- Ağ değişimi
- Decoder recovery
- 20 loop
- 2h/8h soak

## Aşama 6 — UX ve tanılama

- Hazır ekranı
- PIN
- Overlay
- Ayarlar
- Hata yardım
- Metrics
- Export

## Aşama 7 — Güvenlik ve yayın

- Fuzz
- Static analysis
- SBOM
- License
- 16KB
- ABI
- Privacy packet capture
- AAB
- Play checklist

---

# 43. AJANIN ÇALIŞMA KURALLARI

- Kullanıcıya her küçük teknik kararı sormak yerine bu belgedeki varsayılanları uygula.
- Gerçek bir engel yoksa yer tutucu bırakma.
- Her P0 gereksinimi için test kimliği oluştur.
- Her bug’a regression testi ekle.
- Hata düzeltirken kalite eşiğini gevşetme.
- Ölçüm kötü çıkarsa sonucu gizleme.
- Desteklenmeyen cihazı destekleniyor diye işaretleme.
- Test edilmeyen 4K desteğini mağazada yazma.
- DRM içeriği “bug” diye çözmeye çalışma.
- Lisans belirsizse o bileşeni kaldır.
- Native code’da bellek güvenliğini performansa feda etme.
- Performans için güvenliği kapatma.
- Uygulamanın ücretsiz/izleyicisiz niteliğini bozma.
- “Aynısı” talebini kod ve görsel kopyalama olarak yorumlama.
- Kullanıcının temel beklentisini işlevsel eşdeğerlik ve yüksek kalite olarak uygula.

---

# 44. GEREKSİNİM İZLENEBİLİRLİĞİ

Her gereksinim:

```text
REQ-ID
Açıklama
Öncelik
Uygulama modülü
Test-ID
Test sonucu
Kanıt dosyası
```

formatıyla `REQUIREMENTS_TRACEABILITY.csv` içinde bulunacaktır.

Örnek:

```text
REQ-VID-001,H.264 donanım çözme,P0,media-video,E2E-006,PASS,artifacts/e2e-006-report.pdf
REQ-SEC-004,PIN brute-force sınırlama,P0,receiver-security,SEC-014,PASS,artifacts/security-report.html
REQ-PRV-002,Yerel ağ dışı trafik yok,P0,app,PRV-003,PASS,artifacts/packet-capture-summary.txt
```

“Test edilmedi” satırı P0’da olamaz.

---

# 45. TAMAMLANDI TANIMI

Ürün ancak aşağıdakilerin tamamı sağlandığında tamamlandı sayılır:

- Bütün P0 işlevleri uygulanmış
- Gerçek iPhone ile sistem Ekran Yansıtma menüsünden bağlanmış
- Gerçek Android TV’de görüntü ve ses çalışmış
- 1080p60 kalite eşiği referans cihazda geçmiş
- A/V eşzamanı geçmiş
- 2 saat ve 8 saat testleri geçmiş
- Ağ bozulma testleri geçmiş
- Kaynak sızıntısı yok
- P0 güvenlik açığı yok
- DRM aşma yok
- Lisans kararı yazılı
- Üçüncü taraf bildirimleri tam
- AAB üretilmiş
- APK üretilmiş
- 32/64 bit ve 16 KB uyumu doğrulanmış
- D-pad testi geçmiş
- Erişilebilirlik testi geçmiş
- Gizlilik paket yakalama testi geçmiş
- Belgeler eksiksiz
- Bilinen sınırlamalar dürüstçe yazılmış
- Test raporları teslim edilmiş
- Projede release `TODO`, sahte veri ve debug backdoor kalmamış

---

# 46. SON TESLİM RAPORU ŞABLONU

Ajan son cevapta aşağıdaki başlıkları dolduracaktır:

```markdown
# Son Teslim Raporu

## 1. Üretilen paketler
- AAB:
- APK:
- Kaynak:
- SHA-256:

## 2. Protokol çekirdeği ve lisansı
- Seçilen rota:
- Kaynak commit:
- Lisans:
- Köken denetimi:
- Açık yükümlülük:

## 3. Destek matrisi
- iOS/iPadOS:
- Android TV:
- Codec:
- Çözünürlük/FPS:

## 4. Gerçek cihaz sonuçları
- Keşif p95:
- İlk kare p95:
- Gecikme p50/p95:
- Drop:
- A/V sync:
- 2 saat:
- 8 saat:

## 5. Güvenlik
- Fuzz:
- Static analysis:
- Secret scan:
- WAN erişim:
- DRM sınırı:

## 6. Gizlilik
- Dış ağ trafiği:
- Analitik:
- Toplanan veri:
- Log redaction:

## 7. Yayın uyumu
- targetSdk:
- ABI:
- 16 KB:
- AAB:
- TV kalite:

## 8. Bilinen sınırlamalar

## 9. Eksik kalanlar
P0 eksikse ürün tamamlandı sayılmayacaktır.

## 10. Kanıt dosyaları
```

---

# 47. KULLANICIYA GÖSTERİLECEK TEMEL SINIRLAMA METNİ

Uygulama içindeki “Hakkında / Uyumluluk” bölümünde aşağıdaki anlama gelen açık metin bulunacaktır:

> AirRelay Receiver, iPhone ve iPad ekranını aynı yerel ağdaki Android TV’ye yansıtmak için tasarlanmıştır. Kalite; televizyon donanımı, Wi‑Fi koşulları ve yönlendirici ayarlarına bağlıdır. En iyi sonuç için televizyonu Ethernet’e, iPhone’u 5 GHz veya 6 GHz Wi‑Fi’a bağlayın. Netflix, Apple TV+ ve benzeri hizmetlerdeki DRM korumalı içerikler yansıtılamayabilir. Uygulama bu korumaları aşmaz. AirRelay Receiver, Apple Inc. veya AirScreen geliştiricisiyle bağlantılı değildir.

Metin hukuk denetiminde güncellenebilir; içerdiği gerçek sınırlar gizlenemez.

---

# 48. KAYNAK VE TEKNİK DAYANAKLAR

Bu kaynaklar ürün gereksinimlerini güncellemek ve doğrulamak için başlangıç referanslarıdır. Bir kaynağın listelenmesi, kodunu kullanma veya marka hakkı vermez.

## Resmî Apple

1. Apple AirPlay ürün açıklaması  
   https://www.apple.com/airplay/

2. Apple MFi Programı  
   https://mfi.apple.com/

## Resmî Android

3. Android yerel ağ izni  
   https://developer.android.com/privacy-and-security/local-network-permission

4. Android 17 hedefleyen uygulamalardaki davranış değişiklikleri  
   https://developer.android.com/about/versions/17/behavior-changes-17

5. Android TV uygulama kalite kriterleri  
   https://developer.android.com/docs/quality-guidelines/tv-app-quality

6. Android TV yayın kontrol listesi  
   https://developer.android.com/training/tv/publishing/checklist

7. Foreground service türleri  
   https://developer.android.com/develop/background-work/services/fgs/service-types

8. Android 11 düşük gecikmeli MediaCodec özelliği  
   https://developer.android.com/about/versions/11/features

9. AndroidX Media3 sürümleri  
   https://developer.android.com/jetpack/androidx/releases/media3

10. Android Studio sürümleri  
    https://developer.android.com/studio/releases

## Açık kaynak araştırma referansları

11. UxPlay  
    https://github.com/FDH2/UxPlay

12. Android AirPlay Receiver — UxPlay tabanlı araştırma referansı  
    https://github.com/jqssun/android-airplay-server

13. PhairPlay — modern Android TV alıcı araştırma referansı  
    https://github.com/mazer666/PhairPlay

14. Topluluk AirPlay protokol dokümantasyonu  
    https://openairplay.github.io/airplay-spec/

## Rakip kapsam araştırması

15. AirScreen Google Play sayfası  
    https://play.google.com/store/apps/details?id=com.ionitech.airscreen

---

# 49. SON BAĞLAYICI KARARLAR

1. Uygulama Android TV/Google TV içindir.
2. Ana amaç iPhone/iPad ekran yansıtmasıdır.
3. iPhone’a yardımcı uygulama kurulması gerekmemelidir.
4. Uygulama ücretsizdir.
5. Reklam yoktur.
6. Hesap yoktur.
7. Bulut yoktur.
8. Telemetri yoktur.
9. Ekran kaydı yoktur.
10. Aynı yerel ağ zorunludur.
11. PIN varsayılan açıktır.
12. H.264 donanım çözme P0’dır.
13. 1080p60 referans cihazda P0’dır.
14. 4K evrensel garanti değildir.
15. DRM korumalı içerik desteklenmez ve koruma aşılmaz.
16. AirScreen’in kodu ve tasarımı kopyalanmaz.
17. Apple sertifikası varmış gibi davranılmaz.
18. Lisans ve kod kökeni denetimi zorunludur.
19. Gerçek cihaz testi zorunludur.
20. Donma/kasma beklentisi sayısal kabul eşikleriyle doğrulanır.
21. Eşikleri geçmeyen ürün tamamlandı sayılmaz.
22. Uygulama dış ağa veri göndermeyecektir.
23. Google Play TV için 32/64 bit ve 16 KB uyumu zorunludur.
24. Uygulama D-pad ile eksiksiz kullanılacaktır.
25. Son teslimde kaynak, AAB, APK, test raporları, SBOM ve lisanslar birlikte verilecektir.

---

# 50. AJANA SON TALİMAT

Bu ürünü yalnızca güzel görünen bir Android TV arayüzü olarak üretme.

İstenen sonuç:

- iPhone’un gerçek sistem ekran yansıtma menüsünde görünen,
- güvenli biçimde eşleşen,
- canlı ekranı ve desteklenen sesi alan,
- donanım decoder kullanan,
- düşük gecikmeyle gösteren,
- paket kaybında toparlanan,
- saatlerce çalışabilen,
- kullanıcıyı izlemeyen,
- korumalı içeriği aşmayan,
- lisansı temiz,
- Android TV yayın standartlarına uyan,
- ölçüm ve gerçek cihaz testleriyle kanıtlanmış

tam bir alıcı uygulamadır.

Herhangi bir P0 maddeyi uygulamadan veya test etmeden “proje tamamlandı” deme.

---

**Naming note.** This is the original product specification. It was written while the project
used the working name *AynaTV Receiver*; the shipped product name is **AirRelay Receiver**
(package `com.airrelay.receiver`). Requirement identifiers, thresholds and acceptance criteria
are unchanged by the rename. The note is appended at the end so that the source line numbers
referenced by REQUIREMENTS_TRACEABILITY.csv stay valid.
