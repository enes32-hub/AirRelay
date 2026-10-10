# Uygulama notları — İlk dört aşama

## 1. Koruma sınırı

Temel: `AynaTV-Kaynak-Kod-ve-Devir-2026-09-07.zip`. Orijinal dosya hash'leri yeni uygulamanın dosyalarıyla karıştırılmaz. `PROTECTED_CORE_SHA256.json`, orijinal ZIP kimliğini ve 10.579 korunan dosyayı içerir. `check_source.py`, mevcut sınır değişikliklerini açık ters dönüşümlerle denetler; otomatik olarak yeni bir “orijinal” üretmez.

Korunan ağ ve medya davranışı:

| Bölüm | Durum |
|---|---|
| AirPlay/RAOP, UxPlay, eşleştirme ve şifreleme | Aynı kaynak ve özel yamalar. |
| Kontrol portu 7000, yansıtma portu 7100, UDP portları | Varsayılanlar ve native sözleşme aynı. |
| mDNS servis türleri ve TXT verisi | Native taraftan alınan aynı kayıtlar; Android kayıt sonucu artık gözleniyor. |
| MediaCodec seçim ve besleme, çözülmüş kare zamanlama algoritması | Aynı; ilk tampon gönderimi için gözlem ekleniyor. |
| Kalıcı SurfaceTexture/EGL görüntü yolu | Korundu; yeni sayfalar decoder giriş yüzeyinin sahibi değil. |
| ALAC/AAC, ses tamponlama, Oboe | İşleme yolu ve varsayılanlar aynı. |
| `/play`, scrub/rate/stop gibi alıcı uç noktaları | Silinmedi; canlı UI'da gereksiz oynatıcı kontrolleri gösterilmiyor. |
| Gradle, AGP/Kotlin/Media3 bağımlılıkları, ABI, manifest, ProGuard, CMake | Bu teslimde değiştirilmedi. |

### İncelenebilir beş sınır istisnası

`native_bridge.cpp`: UxPlay günlük mesajı Android logcat'e verilmeden önce `privacy_log.h` filtresinden geçer. `_log_callback` haricinde JNI, başlatma ve protokol işleme kodu değişmedi.

`android_raop_callbacks.c`: `_video_play` başındaki tek günlük satırı artık medya URL'sini yazmaz. URL değişkeni, JNI'ye geçirilen URL, kilitler, bekleme koşulu ve native işlem akışı aynıdır.

`log_sink.h`: Aynı teknik günlük, logcat'e ve Java günlük dinleyicisine maskelenerek gider. Bu dinleyici medya callback'i değildir; medya verisinin kendisi filtrelenmez.

`VideoRenderer.kt`: Var olan oturum başlat/bitir/serbest bırak yollarına gözlem kapısı bağlandı. Codec besleme, decoder seçme ve çıktı zamanlama metotları değişmedi.

`VideoPipeline.kt`: Aynı EGL `swap` çağrısının dönüşü ve oturum anahtarı gözlenir. Başarılı ilk tampon gönderimi bir kez bildirilir. Bu bildirim ekran panelinin tarama anını, gerçek görüntüleme FPS'sini veya uçtan uca gecikmeyi ölçmez. Gözlemin gerçek cihaz maliyeti ölçülmedi.

## 2. Yeni uygulama mimarisi

```text
MainActivity
  └─ ProductEnvironment (yalnız UI dili / yazı ölçeği / hareket tercihi)
      └─ MainScreen
          ├─ ReceiverHome
          ├─ ReceiverHelp → LogsScreen
          ├─ ReceiverSettings → gelişmiş SettingsScreen
          └─ kalıcı görüntü sunumu + ayrı tam ekran kontrol katmanı
                 │
            MainViewModel
                 │
            ReceiverController
                 │
            mevcut AirPlayService
                 ├─ ReceiverUiState (gözlenen ürün durumları)
                 ├─ LocalNetworkMonitor (pasif gözlem)
                 ├─ NsdServiceManager (aynı ilan + kayıt sonucu)
                 ├─ PendingReceiverSettings (gelecek başlangıç taslağı)
                 ├─ DiagnosticStore (sınırlı yerel kayıt)
                 └─ mevcut JNI → UxPlay → görüntü / ses yolları
```

UI medya paketlerini işlemez. Gelişmiş alıcı ayarlarını değiştirmek, canlı tercih dinleyicisine yazmaz. Kullanıcı tercihleri için `aynatv_experience`, sıradaki alıcı başlangıcı için `aynatv_next_receiver_start` kullanılır. `Prefs.kt` anahtarları ve aktarım varsayılanları değiştirilmedi.

Servis akışları tek görünüm modelinde olaylarla toplanır. Activity'nin 200 ms aralıkla durum sorgulaması kaldırıldı. Destek metrikleri yalnız geliştirici seçeneği açıkken ayrıca izlenir; gelen FPS'nin ekran FPS'si olmadığı metinde belirtilir.

## 3. Ekranlar ve kumanda davranışı

Ana ekran, alıcının gerçekten ilan ettiği adı gösterir. Android ad çakışması nedeniyle servis adını değiştirirse yeni ad kullanılır. Sonraki başlangıç için düzenlenen taslak ad, çalışan alıcının adıymış gibi sunulmaz.

Yerel ağ bilgisi Wi-Fi/Ethernet ayrımı yapar; internet doğrulaması istemez. Gözlem başarısızlığı “ağ yok” diye kesin yorumlanmaz. Telefonda listelenme, kayıt başarısıyla aynı iddia değildir; ekrandaki hazır bilgisi yerel alıcının hazır olmasını anlatır.

Bağlantı rehberi aynı yerel ağı, iPhone Ekran Yansıtma menüsünü ve TV adını seçmeyi anlatır. QR ile eşleştirme gibi protokolde bulunmayan bir davranış vaat edilmez. Yardım, cihazın görünmemesi, PIN, ses, kopma ve siyah görüntü durumlarını ayrı anlatır.

Arayüz lacivert/grafit zemin, tek turkuaz vurgu, okunaklı yazı ve görünür odak çerçevesi kullanır. Alt gezinmede Bağlan/Yardım/Ayarlar vardır. Teknik günlükler doğrudan ana görev değildir. Türkçe ve İngilizce 298 eşleşen metin kaynağı bulunur. Dil değişimi Activity'yi yeniden oluşturmaz ve native dil/codec görüşmesini tetiklemez.

Canlı görüntü doğal en-boy oranında gösterilir. Ana ekrana gidip gelmek aynı türdeki görünüm yüzeyinin kompozisyon anahtarını değiştirmez. Ayrı `/play` medya yoluna geçişin kendi yüzey anahtarı vardır; eski `VideoSurfaceView` callback'lerinin yanlış oynatıcıya bağlanması önlenir.

Canlı kontroller normal durumda beş saniye sonra gizlenir. OK/Enter veya yön tuşu kontrolleri geri açar. Açan tuşun tekrarları ve key-up olayı tüketilir; böylece ilk basış ayrıca seçili düğmeyi çalıştırmaz. Back önce kontrolleri gizler, sonraki Back ana ekrana döner. Oturumu bitirme onay ister. Canlı ekranda sarma, hız, indirme veya süre çubuğu yoktur. Gerçek ayrı medya oturumunda oynat/duraklat korunur.

Küçük genişlikte ana ekran tek sütuna döner; iç içe sınırsız dikey kaydırma bırakılmadı. Uzun kontrol satırlarında yatay kaydırma bulunur. Büyük yazı ve azaltılmış hareket tercihi eklendi. Bunlar kaynak düzeyinde düzenlemelerdir; ekranların TV'de odak/görsel kabulü yapılmadı.

## 4. Durum ve toparlanma

Ürün durumları: kapalı, hazırlanıyor, ağ bekleniyor, ağ ilanı bekleniyor, ilan hatası, hazır, bağlanıyor, eşleştiriliyor, görüntü bekleniyor, yansıtılıyor, ses bağlantısı, ayrı video oturumu ve başlatma hatası.

TCP bağlantısı yalnız `CONNECTING`; PIN `PAIRING`; görüntü boyutu `WAITING_VIDEO`; o oturuma ait ilk başarılı tampon gönderimi `MIRRORING` üretir. Eskiden kalmış bir gözlem anahtarı yeni oturumu canlı yapamaz. Ağ yoksa kullanıcıya bağlantı kısıtı görünür. RAOP ilanının ayrı başarısızlığı, AirPlay hazır olsa bile ses uyarısı üretir.

NSD callback'leri nesil ve kayıt kimliğiyle korunur. Durdurulan veya değiştirilen ilan yöneticisinin geç callback'i yeni alıcıyı yanlışlıkla hazır yapamaz. Yardım'daki tekrar deneme yalnız ilanları yeniler; çalışan native alıcıya stop/start göndermez. Otomatik ağ değiştirme, yönlendirme bağlama veya protokol değiştirme eklenmedi.

İlan sonucu ve hata kodları hassas veri içermeyen destek olayları olarak kaydedilir. Uygulama router istemci izolasyonu gibi ölçmediği nedenleri kesin teşhis diye sunmaz.

## 5. PIN ve güvenli iptal

PIN üretimi, doğrulama, kriptografi ve native eşleştirme değişmedi. Ürün düzeyinde iki dakikalık bekleme sınırı vardır. PIN bitişi, kopma, reset, durma ve başlatma hatasında pencere temizlenir. Bu zamanlayıcı protokol paketlerindeki zaman aşımı değeri değildir; uygulamanın bekleme/iptal politikasıdır.

Mevcut native arayüz tek istemciyi güvenli iptal etmeye yönelik bir komut sunmadığı için iptal alıcıyı bütünüyle durdurur. Kullanıcıya bunun tüm bağlantıları sonlandırdığı açıkça bildirilir. Yeni deneme elle başlatılır; kullanıcı tarafından durdurma tercihi otomatik açılmayla bozulmaz.

PIN penceresi güvenli pencere bayrağı kullanır. Bildirim PIN değerini içermez; kullanıcıyı uygulamadaki koda yönlendirir. PIN doğrulamasını kapatmak ayrıca onay ister ve sonraki başlangıca bırakılır. Gelişmiş ayarlardaki bu onayı atlatan yinelenen PIN anahtarı kaldırıldı.

İkinci telefonun mevcut yayını değiştirme varsayılanı **bilinçli olarak korunmuştur**. Açıklama, yeni bir bağlantı girişiminin mevcut yayını kesebileceğini açıkça söyler. Burada yeni bir native oturum sahipliği veya izin protokolü tasarlanmadı.

## 6. Ayar taslakları

İsim, port, codec, ilan, PIN, alıcı çözünürlüğü/FPS ve ses tamponu gibi aktarımı etkileyen tercihler ayrı depoda tutulur. Mevcut canlı `SharedPreferences` dinleyicilerine hemen iletilmez.

Normal kaydetme stop/start üretmez. Boşta açık “yeniden başlat ve uygula” eylemi mümkündür. Bağlantı, PIN, ses, yansıtma veya bekleyen medya olayı varsa bu eylem kapalıdır. Taslak yalnız alıcı yeniden başlarken yazılır. Kalıcı yazma başarısızsa alıcı başlatılmaz. Aktif ayara veya henüz dosyaya yazılmamış varsayılana geri dönmek gereksiz taslağı kaldırır.

Başlangıçta taslak isim uygulandıktan sonra yeniden okunur; boot yolu eski adı kullanmaz. İlk kurulum tamamlanmadan ayar paneli üzerinden alıcı başlatılamaz. Başlatma komutu kullanıcının bilinçli durdurma tercihini doğru şekilde sıfırlar.

İsim boş, kontrol karakterli, başı/sonu boşluklu veya 48 UTF-8 baytından uzun olamaz. Port 1–65535; UI üzerinden yeni FPS 1–240; özel boyutlar 16–16384 sınırında doğrulanır. Bu sınırlar kullanıcı girdisini doğrular; varsayılan medya parametrelerini veya native decoder sınırlarını değiştirmez. Yüksek FPS/çözünürlük seçilebilmesi cihaz performansına dair garanti değildir.

## 7. Yerel günlükler

Kuyruk 256 mesaj, görünür bellek tamponu 600 satır ile sınırlıdır. Dosya yazımı medya callback'inde yapılmaz; arka planda yaklaşık 500 ms aralıkla toplu yazılır. Taşan giriş sayılır; sınırsız bellek büyümesi olmaz. Güncel ve önceki dosyanın her biri en çok 262.144 bayttır. Büyük toplu yazım tam satırlarla kırpılır; UTF-8 karakteri ortadan kesilmez.

Uygulama destek filtresi PIN, erişim bilgisi, anahtarlar, medya URL'leri, ağ adresleri ve medya metaverisi için koruma uygular. Native günlük çıkışlarında bilinen PIN yolu ve `/play` URL yolu kapatılır; teknik `LogSink` aynı hassas çıkış filtresini kullanır. Filtrelenen değerler hiçbir zaman native protokole geri verilmez.

Eski `airplay_logs.txt` dosyaları yeni günlüğe aktarılmadan silinir. Açık kullanıcı eylemiyle destek metni hazırlanır; ayrıca sistem paylaşım seçicisi açılır. Otomatik yükleme/telemetri eklenmedi. Dosya ve paylaşım hataları arayüzde görünür. Temizleme güncel/önceki dosyayı ve hazırlanmış yerel paylaşımı siler; dışarı gönderilmiş kopyalar geri alınamaz.

**Sınır:** Bu çalışma bütün Android logcat içeriğinin veya değiştirilmeyen üçüncü tarafların ürettiği tüm logların anonim olduğunu garanti etmez. Destek dosyası paylaşılmadan önce gözden geçirilmelidir. Giriş metrikleri fiziksel TV performans sonucu sayılmaz.

## 8. Doğrulama ve kalan sınırlar

`check-results/` altındaki dosyalar gerçekten çalıştırılmış kontrol çıktılarıdır. 76 host mantık kontrolü üretim `ReceiverUiState`, `FirstFrameGate`, `ReceiverInput`, `PendingReceiverSettings`, `ReceiverController`, `LogRedactor`, `BoundedLogBuffer` ve `DiagnosticStore` kodunu kullanır. Context/SharedPreferences ve servis komut kaydı için açık host test çiftleri bulunur. Bu çiftler Android davranışını taklit ettiklerini iddia etmez.

15 C++ kontrolü yalnız yeni günlük çıkış yardımcısını çalıştırır. Native kütüphane, JNI veya gerçek ağ akışı derlenip çalıştırılmadı. 44 eski Python testinin sonuçları korunur; onlar hazırlık kanıtı testleridir.

Kotlin ayrıştırıcısı 51 uygulama dosyasında sözdizimi hatası bulmadı. Bu, eksik Android bağımlılıkları, Compose eklenti kuralları veya Android API tür hatalarını dışlamaz. 17 XML dosyası ayrıştırıldı; 298 TR/EN metin çifti, biçim belirteçleri ve kaynak başvuruları eşleşti. Android kaynak derleyicisi çalıştırılmadı.

Altı vektör kaynağı kaynak incelemesi için SVG üzerinden çizdirildi; ekli görsel gerçek uygulama ekran görüntüsü değildir. Gerçek 1080p/4K yerleşim, kumanda dolaşımı, PIN diyalogu, dil/yazı ölçeği geçişi ve PiP davranışı emülatör/cihazda kabul edilmedi.

**Önemli:** Kaynak düzeyinde düzeltilen Android sınırları (servis durumları, PIN zamanlayıcısı, NSD callback'leri, yüzey bağlama) gerçek Android çalıştırmasıyla doğrulanmadı. Beşinci aşamanın Android kalite/test ve paket işleri özellikle yapılmadı. Önceki lisans/FairPlay köken riskleri açık kaldı; burada yeni bir hukuki uygunluk kararı veya ticari yayın onayı verilmedi.
