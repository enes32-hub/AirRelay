# Protokol çekirdeği ve bağımlılık kararı

İnceleme: 31 Ağustos 2026. Etkin ürün: [yalnız iPhone → Android TV](SCOPE.md).
**Karar: henüz üretim çekirdeği seçilmedi. Aşama 0 geçmedi.** Bu bir teknik ön incelemedir; hukuk görüşü, eksiksiz lisans denetimi veya cihaz uyumluluk raporu değildir.

## Kullanıcının maliyet tercihi

Kullanıcı 31 Ağustos 2026'da ücretli/ticari SDK lisansı bulunmadığını ve ürünün açık kaynakla yapılmasını istediğini belirtti. Bu nedenle ticari SDK satın alma veya satıcıyla görüşme proje kapsamında değildir. Bu tercih GPL gibi açık kaynak lisanslarını ortadan kaldırmaz: bu lisanslar ücretsiz olabilir, fakat kaynak kodu, değişiklikler ve gerekli bildirimlerle birlikte dağıtım yükümlülüğü getirir.

AirServer, Store'daki alıcılar ve benzeri son kullanıcı uygulamaları referans davranış sağlar; onların APK'ları veya tersine mühendislikle elde edilmiş protokol/varlıklar AynaTV'ye alınmayacaktır. Kullanıcının verdiği `uxplay-windows` örneği Windows'a yönelik bir UxPlay paketi olup Android TV çekirdeği değildir. Aynı UxPlay ailesindeki mevcut köken belirsizliği de bu örnekle çözülmez.

## İncelenen sabit kaynaklar

| Aday | İncelenen commit | Rota / mevcut karar |
|---|---|---|
| [UxPlay](https://github.com/FDH2/UxPlay/tree/fd5192b06f0c2ab2f664b8ab778ac6f772e4d741) | `fd5192b06f0c2ab2f664b8ab778ac6f772e4d741` | A, GPL; köken ve Android entegrasyonu incelemesi bekliyor |
| [android-airplay-server](https://github.com/jqssun/android-airplay-server/tree/c8defdd70d7e6a04f4f1b71d353653682d594106) | `c8defdd70d7e6a04f4f1b71d353653682d594106` | A, GPL; Android fizibilitesi için ilk inceleme adayı, onay değil |
| [PhairPlay](https://github.com/mazer666/PhairPlay/tree/4a3948c51d7f9d050d1f7437c0bd88877ef6db0f) | `4a3948c51d7f9d050d1f7437c0bd88877ef6db0f` | B olarak kabul edilmedi; salt Apache/temiz oda varsayımı desteklenmiyor |

Araştırma kilidi: [candidates.lock.json](research/candidates.lock.json). Dosya hashleri ve karşılaştırma sonucu: [provenance-evidence.json](research/provenance-evidence.json). Araç kaynak dosyalarını yalnız bellekte okur; aday kodu, anahtar veya tablolar projeye kopyalanmaz, derlenmez, çalıştırılmaz.

## Rota A

UxPlay GPL-3.0 beyan eder ve [playfair alt dizininde GPLv3 lisansı](https://github.com/FDH2/UxPlay/blob/fd5192b06f0c2ab2f664b8ab778ac6f772e4d741/lib/playfair/LICENSE.md) taşır. Okunan master README deneysel geliştirmeyi de içeriyor; bu HEAD otomatik olarak kararlı P0 bağımlılığı seçilemez. Kararlı bir sürümün tam commit'i ayrıca değerlendirilecek.

Android uyarlamasının [CMake dosyası](https://github.com/jqssun/android-airplay-server/blob/c8defdd70d7e6a04f4f1b71d353653682d594106/app/src/main/cpp/CMakeLists.txt) UxPlay/playfair, libplist, OpenSSL, FFmpeg ve Oboe kullanır. README'deki MediaCodec/SurfaceView ve ses desteği aday seçimine yardımcı bir beyan; bizim cihaz testimiz değildir.

[Git submodule kayıtları](https://github.com/jqssun/android-airplay-server/tree/c8defdd70d7e6a04f4f1b71d353653682d594106/app/src/main/cpp/third_party):

| Bileşen | Sabit commit / yapılandırma | Eksik inceleme |
|---|---|---|
| UxPlay | `462153392f2e30937424922039ff9f0cda5e7b1a` | Araştırılan master'dan farklı; dosya ve kriptografik köken denetimi |
| FFmpeg | `38b88335f99e76ed89ff3c93f877fdefce736c13` | Etkin codec/derleme bayrakları, LGPL/GPL yükümlülükleri |
| libplist | `f41b1ea67045e0c09339974d83e389972d84f166` | Dosya bazlı lisanslar, statik bağlantı koşulları |
| openssl-cmake | `4edd36a8dab5f85a8f92650b5bdf6e0cab13aab8` | Wrapper lisansı, indirilen kaynak ve hash |
| OpenSSL | CMake'de `3.4.4` | Bu sürüm AynaTV için seçilmedi; güvenlik ve kaynak denetimi |
| Oboe ve Gradle bağımlılıkları | Henüz tam envanter çıkarılmadı | Transitive graph, sürüm kilitleri, kaynak ve lisans dosyaları |

Rota A seçilirse tam karşılık gelen kaynak, değişiklik kayıtları, lisans/NOTICE dosyaları ve yeniden üretim talimatı dağıtım planına dahil edilecek. Bu yükümlülüklerin gerçekten karşılandığı dağıtım öncesi incelenecek. Ticari rakip kodu veya varlıkları alınmayacak. Ürün genel lisansı şu aşamada atanmadı.

## Rota B

PhairPlay kök [LICENSE](https://github.com/mazer666/PhairPlay/blob/4a3948c51d7f9d050d1f7437c0bd88877ef6db0f/LICENSE) dosyası Apache 2.0 metni içeriyor. Buna karşılık [native CMake dosyası](https://github.com/mazer666/PhairPlay/blob/4a3948c51d7f9d050d1f7437c0bd88877ef6db0f/app/src/main/cpp/CMakeLists.txt) playfair'i RPiPlay kökenli olarak tanımlıyor ve bu kaynakları `libplayfair.so` içine derliyor. Bu dosyalar UxPlay'in GPLv3 playfair diziniyle hash üzerinden karşılaştırıldı. Yedi ortak kaynak dosyasından beşi (`playfair.c`, `hand_garble.c`, `omg_hax.c`, `omg_hax.h`, `playfair.h`) byte düzeyinde aynı SHA-256 değerine sahip; diğer ikisi farklı. Ayrıntılı sonuç kanıt JSON'undadır.

**Çıkarım:** kök Apache metni tüm paketin izin verici lisansla ve temiz oda yöntemiyle üretildiğini kanıtlamıyor. Bu, mahkeme kararı veya bütün projeye yönelik ihlal hükmü değildir; AynaTV'nin şartname 5.2 kapısını geçmek için kanıt yetersizdir. Aday bu haliyle Rota B'ye alınmayacak. Anahtar ve tabloların hukuken temiz kökeni ayrıca belgelenmedikçe kullanılmayacak.

Temiz oda alternatifi protokolü tahmin ederek yazmak anlamına gelmez. Bağımsız kaynak geçmişi, belgelenmiş protokol ve hukuken kullanılabilir kriptografik bileşen gerekir. Mevcut araştırma bunları sağlamıyor. [Ek aday incelemesi](research/ALTERNATIVE_CORES.md) iki başka açık depo ve iki ticari SDK rotasını ele alır; hiçbiri onaylanmadı. Apple MFi SSS'de belirtilen AirPlay audio kapsamı, Android ekran yansıtma SDK'sına erişim bulunduğunu kanıtlamaz. Üyelik, sertifika veya sözleşme bulunduğu varsayılmadı.

## Teknik puanlama ve geçiş şartı

| Ölçüt | Ağırlık | Tüm adayların AynaTV test sonucu |
|---|---:|---|
| Gerçek iPhone bağlantısı | 25 | Ölçülmedi |
| 1080p60 kararlılık | 20 | Ölçülmedi |
| A/V eşzamanı | 15 | Ölçülmedi |
| Güncel iOS uyumluluğu | 15 | Ölçülmedi |
| Lisans ve kod kökeni | 15 | Hiçbir aday kapıyı geçmedi |
| Android TV entegrasyon maliyeti | 5 | Derlenmiş spike yok |
| Bakım yapılabilirlik | 5 | Yalnız ön inceleme |

Puanlar tahminle doldurulmadı. Lisans/köken kapısı başarısız veya bekleyen adaya ağırlıklı toplamla onay verilemez. Android uyarlaması yalnız inceleme sırasının ilk adayıdır; ana uygulama için seçilmiş çekirdek değildir.

Bir sonraki çekirdek kararı için: sabit kararlı kaynak → tam dosya/submodule lisans envanteri → kriptografik köken incelemesi → bağımlılık/hash doğrulaması → izole fiziksel iPhone/Android TV spike → ölçüm → yazılı karar. Bu kapılardan önce AynaTV adına işlevsel APK veya hazır mDNS alıcısı ilan edilmeyecek.
