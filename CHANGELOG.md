# Çalışma kaydı

## 6 Ekim 2026 — R39 yayın öncesi inceleme adayı

- Son kullanıcı marka varsayılanları, Compose örnekleri ve indirilen dosya adları AirRelay ile tutarlı hale getirildi; kalıcı paket/JNI/tercih kimlikleri korundu.
- Mikrofon donanımı zorunluluğu kaldırıldı; kayıt sesi başlangıç hatasında kaynak sahipliği ve izin kontrolü düzeltildi.
- Android 7–10 ağ izleme için API 30 öncesi NetworkRequest uyumluluğu eklendi; PiP ve kayıt remux API sınırları açıkça belirtildi.
- Bildirim güncellemelerinde Android 13 izni gözetilir; medya oturumu istisnası korunur. Foreground servis başlangıç davranışı değiştirilmez.
- Mevcut Media3 kararsız API kullanımları açık opt-in ile işaretlendi; oynatma çekirdeği değiştirilmedi.
- Kayıt tarihleri Compose'un seçili dil yapılandırmasını izler. AAB dil bölme kapatılarak tüm 16 dilin çevrimdışı seçimde kalması sağlandı.
- Bu aday test imzalıdır; gerçek iPhone/TV son kabulü ve üretim imzalı mağaza paketi ayrı adımlardır.

## 1 Eylül 2026 — Windows prototipinden Android TV alıcısına aktarım

- iPhone 14 Pro oturumunda doğrulanan hibrit oynatma modeli Android'e taşındı: MediaCodec video kareleri varsayılan olarak hemen yüzeye bırakılır, ses kendi NTP zaman çizelgesinde kalır.
- AAC-ELD'nin üçlü/yedekli RTP akışında kayıp sıra numarası için 256 paket beklenmesi kaldırıldı; bu akışta eksik sıra atlanırken ALAC ve AAC-LC yeniden gönderim korumasını kullanmaya devam eder.
- RAOP `audio_flush` artık boş bir geri çağırım değildir. Eski PCM'i atar, ses zamanını yeniden sabitler, jitter tamponunu yeniden doldurur ve etkin Oboe çıkışını güvenli biçimde yeniden açar.
- Android TV için Oboe düşük gecikme modu yeni kurulumlarda varsayılan yapıldı. Kullanıcı ayarı geliştirici seçeneklerinden değiştirilebilir.
- Emülatörde kontrol oturumu kurulup görüntü gelmemesine neden olan dinamik UxPlay medya portları sabitlendi; Android çekirdeği ile Windows köprüsü artık TCP `7000/7100` ve UDP `7011/6001/6000` üzerinde aynı sözleşmeyi kullanıyor.
- Android TV ekranı canlı yansıtma odağıyla yenilendi. Müzik oynatıcısı, zaman çizgisi, sarma ve parça kontrolleri kaldırıldı; hazır/bağlanıyor/canlı durumları ve iPhone yönlendirmesi eklendi.
- PIN penceresi eşleştirme tamamlanmadan kapatılamaz hale getirildi; açılış TV odağı yanlışlıkla `Stop` seçmeyecek şekilde `Overview` sekmesine taşındı.
- x86_64 debug APK temiz ve artımlı derlemelerde başarıyla üretildi, yalnız `emulator-5584` üzerine yerinde kuruldu ve 44 regresyon testi geçti.

## 31 Ağustos 2026

- Ana şartname okundu; kullanıcının yalnız Android TV alıcısı ve iPhone sistem AirPlay göndericisi kapsamı işlendi.
- İlk hedef Aşama 0 araştırma/denetim başlangıç paketi olarak belirlendi.
- Üç protokol adayı sabit commit'lerle ve kaynak dosyası hashleriyle incelendi; çekirdek seçimi açık bırakıldı.
- Android TV mimarisi, araç zinciri, fiziksel spike ve performans ölçüm yöntemi belgelendi.
- 33 üst seviye P0 + 69 performans/ağ kabul kaydı ve 58 test tanımı oluşturuldu. Hiçbir kabul sonucu PASS yapılmadı.
- Ortam/provenance/kayıt tutarlılığı araçları ve 24 regresyon testi eklendi.
- Hazırlık kayıt kontrolü geçti; yayın kontrolü eksik P0 ve Aşama 0 kapıları nedeniyle beklenen şekilde reddedildi.
- Bu çalışma çalışan ekran yansıtma ürünü veya tamamlanmış Aşama 0 olarak sunulmadı.

## 31 Ağustos 2026 — Nihai hedefe devam

- Etkin hedef nihai uygulama ve doğrulanmış teslim paketi olarak korundu; hazırlık hedefin yerine geçmiyor.
- Kullanıcının iPhone 14 Pro ve Xiaomi TV Box 4K cihaz çifti kaydedildi; model/OS ayrıntıları varsayılmadı.
- Yedi sabit upstream depodan 185 dosya hash ve lisans sinyalleriyle envantere alındı. UxPlay/RPiPlay kaynaklarının FairPlay hukuk belirsizliği beyanı doğrulandı.
- NDK r29 kuruldu. Özgün C aracı iki ARM ABI için CMake/Ninja ile derlendi; 16 KB ELF/ZIP biçimi Python denetimi, llvm-readelf ve zipalign ile doğrulandı. Alıcı veya fiziksel cihaz testi yapılmış sayılmadı.
- Native envanter ve ELF/ZIP denetim araçları eklendi; hazırlık regresyon testleri 44'e çıktı ve geçti.
- Çekirdek kökeni, fiziksel bağlantı ve gerçek uygulama build/kabul kapıları açık kaldı. Hiçbir P0 ürün kaydı PASS yapılmadı.
- İki ek açık depo ve iki ticari SDK'nın birincil belgeleri incelendi; özel modül ve belgelenmemiş kullanım hakları nedeniyle çekirdek seçilmedi. MFi audio kapsamının ekran yansıtma SDK erişimini kanıtlamadığı açıklandı.
- Tek açıkça seçilmiş ADB cihazından sınırlı teknik bilgi okuyan araç ve TV bağlantı yönergesi eklendi. Araç emülatörü reddeder; otomatik bağlantı, uygulama kurulumu veya kabul sonucu üretmez.
- Kullanıcının ücretli SDK kullanmama tercihi kaydedildi. Açık kaynak/GPL rota incelenmeye devam eder; ticari son kullanıcı uygulamalarından APK, kod veya varlık alınmayacak.
- Android TV uygulama iskeleti eklendi: sabit Gradle wrapper, Leanback başlatıcı, D-pad ilk odağı, foreground ReceiverService ve yerel durum ekranı. Emülatörde debug APK kuruldu/açıldı; D-pad OK ile foreground servis doğrulandı. AirPlay çekirdeği veya iPhone akışı eklenmedi.
- Alıcı oturumuna `SecureRandom` ile dört haneli PIN eklendi. PIN yalnız çalışmakta olan oturumda TV ekranında gösterilir; alıcı durduğunda bellekten temizlenir. Emülatörde başlatma/gösterim/durdurma akışı doğrulandı; PIN kanıt dosyasına yazılmadı.
- MediaCodecList tabanlı H.264/AVC yetenek sorgusu eklendi. Uygulama uygun decoder'ın donanım ve düşük gecikme özelliklerini yalnız cihazın gerçek codec raporundan gösterir; video paketi veya yazılım fallback eklenmedi.
