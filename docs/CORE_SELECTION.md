# AirPlay çekirdeği seçimi

## Seçilen entegrasyon adayı

- Depo: [`jqssun/android-airplay-server`](https://github.com/jqssun/android-airplay-server)
- Sabit kaynak: `c8defdd70d7e6a04f4f1b71d353653682d594106`
- Lisans: GPL-3.0
- Hedef: Android TV'de iPhone'un yerleşik **Denetim Merkezi → Ekran Yansıtma** akışında görünen alıcı.

Bu aday Android TV, JNI, UxPlay, MediaCodec/SurfaceView, ses ve PIN yapılarını içerdiğini beyan eder. Bu beyan henüz AynaTV'nin cihaz testi değildir. Apple TV uygulaması gibi DRM'li içerikler kabul kapsamı dışındadır.

## Dağıtım sonucu

Ticari SDK veya ücretli lisans alınmayacak. AynaTV bu adaydan türetilerek dağıtılırsa GPL-3.0 şartları uygulanır: karşılık gelen kaynak kod, değişiklikler, üçüncü taraf bildirimleri ve yeniden üretim yönergeleri teslim paketine eklenir.

## 31 Ağustos 2026 doğrulama durumu

- Kaynak ve alt modüller indirildi.
- Android Gradle yapılandırması x86_64 emülatör için çalıştırıldı.
- Windows yol kodlaması, Python ve patch araçları için yalnız araştırma/derleme kopyasında uyumluluk ayarları yapıldı.
- Windows x86_64 spike'ı Gradle, CMake, UxPlay, libplist, FFmpeg ve OpenSSL ile tamamlandı. OpenSSL 3.4.4 arşivi resmi SHA-256 değeriyle doğrulandı; Windows/NDK r29 araç adı farkları yalnız geçici araştırma derleme kopyasında uyumlulaştırıldı.
- `io.github.jqssun.airplay` hata ayıklama APK'sı üretildi; yalnız x86_64 emülatör ABI'sini içerir ve fiziksel TV için paket değildir.
- APK, Android TV emülatörüne kuruldu. TV arayüzü açıldı; kumanda orta tuşuyla alıcı durdurulup yeniden başlatıldı. Ön plan hizmeti çalıştı, yerel alıcı başlatıldı ve AirPlay ile RAOP mDNS kayıtları yayımlandı. Çökme kaydı görülmedi.
- Çalışan çekirdek ana AynaTV `app` modülüne taşındı. Eski gerçek işlevi olmayan başlangıç iskeleti `prototype/initial-skeleton/` altında korunur. Yüklenen AynaTV paket kimliği `com.aynatv.receiver` ve görünen adı AynaTV Receiver'dır.
- Windows'la sınırlı native yapı uyarlamaları kaynak içinde belgelenmiştir. `scripts/build_receiver.ps1`, kaynakları ASCII geçici çalışma yoluna alır ve x86_64 emülatör APK'sını orada üretir; bu gereksinim ürün veya iPhone testi değildir.
- Ana kaynak entegrasyonu, `com.aynatv.receiver` olarak emülatöre kuruldu. UxPlay, FFmpeg, OpenSSL, libplist ve JNI'den oluşan x86_64 native alıcı temiz kaynaklardan CMake/Ninja ile derlendi ve bu kütüphane APK'ya paketlendi. Arayüzde alıcı bekleme durumu ile D-pad orta tuşundaki durdur/başlat geçişi gözlendi; başlatmanın ardından 7000 portunda AirPlay ve RAOP mDNS kayıtları ilan edildi. ARM32/ARM64 ve 16 KB paketleri yine ayrı doğrulama kapısıdır.
- WSL Ubuntu'da make var, ancak Java ve Android SDK kurulu değil. Bu nedenle bu oturumda doğal Linux derlemesi başlatılamadı.
- iPhone 14 Pro → Xiaomi TV Box 4K kabul testi, kullanıcı tercihi gereği günlük emülatör çalışmalarından sonra tek final aşamasında yapılacak.

Bu dosya bir seçim ve deneme kaydıdır; gerçek iPhone ekran yansıtma kanıtı değildir. iPhone 14 Pro → Xiaomi TV Box 4K kabul testi, video/ses/senkron ve bağlantı doğrulaması için zorunlu son kapıdır.

