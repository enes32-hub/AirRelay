# Açık riskler ve sonraki işler

31 Ağustos 2026. Başlangıç araştırma paketi hazırlanmıştır; Aşama 0'ın ürün geliştirmeye geçiş kapısı henüz açık değildir.

| Öncelik / risk | Kanıt | Sonraki somut iş |
|---|---|---|
| P0: çekirdek kökeni | 185 dosya envantere alındı; UxPlay/RPiPlay kendi FairPlay bileşeninin hukuk durumunu belirsiz olarak bildiriyor | [Köken/hukuk inceleme sorularını](../legal/REVIEW_REQUEST.md) kaynak sahibi veya uygun incelemeciyle çöz; belirsiz materyali kullanma |
| P0: fiziksel bağlantı | Kullanıcı iPhone 14 Pro + Xiaomi TV Box 4K dedi; fiziksel ADB erişimi yok | iOS ve Android sürümü/build'i ile tam Xiaomi modelini doğrula; TV ADB yetkilendirmesi ve aynı ağ laboratuvarı hazırla |
| P0: Android uygulama toolchain doğrulaması | NDK r29 kuruldu; özgün C denemesi iki ARM ABI/16 KB biçim kontrolünden geçti; Android TV iskeleti debug APK olarak emülatörde derlendi/çalıştırıldı | Çekirdek seçilince native bağımlılık hash doğrulaması, ARM APK/AAB ve gerçek cihaz biçim doğrulaması |
| P0: kabul kanıtı yok | 102 kayıt NOT_RUN | Önce fiziksel bağlantı spike'ı, sonra ana ürün kodu ve ayrıntılı kabul matrisi |
| P0: ayrıntılı kapsam tamamlanmadı | İlk matris bölüm 3.1 ve 19'u kapsıyor | Bölüm 10–18, 20–27 ve 29–45 zorunluluklarını atomik REQ/Test eşleştirmeleriyle genişlet |
| P0: lisans/marka yayın kapısı | Dağıtım incelemesi yok | Dosya bazlı teknik envanteri hukuk denetimine hazırla; Apple onayı/uyumluluk iddiası yapma |
| P0: AAB/APK/ABI/16 KB | Henüz uygulama/ikili yok | Kapılar geçince build, native semboller, SBOM, signed test APK ve AAB doğrula |

Bu aşamada uygulamaya üçüncü taraf kaynak eklenmedi. Android wrapper'ın arayüzü, HLS/indirme/uzaktan kontrol özellikleri veya geniş izinleri otomatik taşınmayacak. Gerekirse yalnız denetlenmiş protokol çekirdeği özgün TV uygulamasına entegre edilecek.

NDK kurulumu tek başına çekirdek kökeni veya cihaz engelini kaldırmaz. Emülatör fiziksel iPhone/Android TV kabulünün yerine kullanılmayacak. Araştırmanın izin verici lisanslı bir alternatif bulamaması, belirsiz çekirdeği kullanmak için gerekçe değildir.

[Ek çekirdek araştırması](research/ALTERNATIVE_CORES.md) da geçiş sağlayamadı. İlerlemek için kaynak/kullanım haklarını çözen belgeler veya şartnameye uygun lisanslı bir çekirdek gerekiyor. [Cihaz bağlantısı hazırlığı](DEVICE_COMPATIBILITY.md) ve salt okunur `scripts/collect_tv_environment.py` hazır; bunlar protokol çekirdeğinin yerine geçmez.

## Tamamlanmayanlar

Çalışan alıcı, iPhone keşfi/PIN/video/ses, Android Studio uygulama projesi, cihaz uyumluluğu, 1080p60, 2/8 saat soak, APK/AAB, kaynak dağıtım lisansı, SBOM, fuzz, güvenlik ve gizlilik paket yakalama raporları henüz yok. Bu eksikler gizlenmedi; ürün tamamlandı veya yayına hazır değil.

## Dış bağımlılık nedeniyle duruş

31 Ağustos 2026 son kontrolünde ADB'de yalnız iki emülatör vardı; fiziksel Xiaomi bağlı değildi. Projede yeni çekirdek kaynak/hak belgesi veya edinilmiş uygun SDK bulunmadı. Aynı engeller ardışık hedef turlarında devam etti. Ek hazırlık işi üreterek bunlar çözülmüş sayılmayacak; nihai hedef küçültülmeden engelli olarak tutulacak.

Devam için iki ayrı koşul var:

1. Kullanıcının ücretsiz/açık kaynak tercihiyle uyumlu tam alıcı çekirdeğinin kriptografik kökenini çözen belgeler. Ticari SDK satın alma rotası kapsam dışıdır. Hazır [inceleme dosyası](../legal/REVIEW_REQUEST.md) hangi belgelerin gerektiğini açıklar; henüz kimseye gönderilmedi. Salt “devam et” onayı bu kanıtın yerine geçmez.
2. Gerçek iPhone 14 Pro katılımı ve yetkilendirilmiş fiziksel Xiaomi TV bağlantısı. Tam model, Android/iOS sürümleri doğru test yolunu belirler; sürüm bilgisi tek başına cihaz erişimi değildir. [Bağlantı yönergesi](DEVICE_COMPATIBILITY.md) hazırdır.

Bu aşamada üçüncü tarafla iletişim, satın alma, özel depoya erişim veya fiziksel cihaz ayarı yapılmadı. Uygun kaynak sağlandığında envanter güncellenir; köken kapısından sonra izole fiziksel spike, ardından şartnamedeki ana ürün geliştirme sırası izlenir.
