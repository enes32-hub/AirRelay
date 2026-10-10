# RC28 — kullanıcı talimatıyla durduruldu (15 Eylül 2026)

GÜNCEL DURUM: Son kullanıcı talimatı `YAPILACAK_GELISTIRMELER.md` dosyasındadır. RC25.1-test teslimi R28 adayı olarak ayrıldı; kullanıcı kabulü bekleniyor. Aşağıdaki kayıtlar tarihsel süreçtir, güncel kurulum talimatı değildir.

Öncelik: TV'de doğrulanan RC25 sürümünde iPhone VPN bağlantısı sırasında kopmayı teşhis et. TV'yi yeniden başlatma/kurma; önce mevcut kanıtı al.

Daha sonra yapılacaklar:
- RC25'in oynatma, ağ, ses ve video geçiş motorlarını koruyarak 720p/1080p sessiz kayıt ve dışarı aktarma.
- RC25'in eşleşmiş cihaz engellemesini koru. Cihazın ağda paket göndermesini tamamen önleyebildiğini iddia etme.
- Akıllı video kalitesini ancak kararlı motoru bozmadan mümkünse ekle; mümkün değilse açıkça kapsam dışında bırak.
- RC27'nin her kontrol soketine ayrı doğrulama getiren değişikliğini bu sürüme taşıma; bunun güvenlik/uyumluluk sınırını raporla.
- Testler emülatörde; kullanıcı TV'de RC25'i test ediyor. RC28'i TV'ye yükleme.

Çalışma durumu: RC27 kaynak yedeği artifacts/rc28-stable-base/rc27-source-before-rc28.zip içinde. Native dosyalar RC27 öncesine alındı, otomatik kalite kancaları kaldırıldı, kayıt ayrı servis olarak kaldı. Kaynak sürüm etiketi RC28, fakat son emülatör derlemesi başarısız; bitmiş RC28 teslimi yok. Değişiklikler yarım halde; yeniden başlarken derleme günlüğünü ve RC25 APK bytecode karşılaştırmasını incele. TV'de kurulu sürüm RC25 versionCode56; doğrulandı.

Kullanıcı karşılaştırması için 15 Eylül'de orijinal RC23 ARM APK TV'ye temiz kuruldu. SecureLine zaten açıkken yeni bağlantı çalışıyor; araştırılan olay devam eden yayın sırasında VPN'in açılması. RC23 test sonucu bekleniyor; RC28 çalışması hâlâ durduruldu.

Kullanıcı RC23'te VPN senaryosunda sorun olmadığını bildirdi. Karşılaştırmanın sonraki adımı olarak checksum doğrulanmış orijinal RC24 ARM APK TV'ye temiz kuruldu ve açıldı. RC24 sonucu bekleniyor; yeni özellik çalışması durdurulmuş durumda.

Yeni bekleyen istek: Video kaynağı erişimi reddetti hatasında Oynatma şekli düğmesi en az beş kez yanıp sönmeli. Önceki tek seferlik hata bildirimi davranışı korunmalı; aynı hata nedeniyle menü/diyalog dönüşünde tekrar tetiklenmemeli. Henüz uygulanmadı.
Kullanıcı RC24'te de sorun görmediğini bildirdi. Sıradaki karşılaştırma sürümü RC25 olarak seçildi.
RC25 orijinal ARM APK checksum doğrulaması sonrası TV'ye temiz kuruldu ve açıldı; versionCode56 / 0.1.0-rc25 doğrulandı. Kullanıcının tekrar testi bekleniyor.

Kullanıcı RC25 tekrar testinde de sorun görmediğini bildirdi. Önceki RC25 VPN kopma kaydı silinmedi; sonuç senaryoya bağlı ve yeniden üretimi tutarsız. Sonraki istenen karşılaştırma sürümü RC26.

RC26 orijinal ARM arşivi bulunmadı. RC26 x86_64 APK'sının 276 kod/kaynak girdisi aynen korunarak, RC26 raporunda değişmediği belirtilen çekirdeğin RC25 orijinal ARM kütüphaneleriyle karşılaştırma paketi oluşturuldu. NativeBridge bytecode aynı; 12 ARM kütüphanesi RC25'le byte eşit. İmza ve ZIP hizalama doğrulandı. artifacts/rc26-tv-compat/verification.json. TV'ye temiz kuruldu: versionCode57 / rc26 / armeabi-v7a. Bu tarihsel ARM derlemesi değil, uyarlanmış RC26 karşılaştırma paketidir. Kullanıcı testi bekleniyor; RC28 kaynakları kullanılmadı.

Kullanıcı RC26 ARM karşılaştırma paketinde sorun yaşadığını bildirdi. Geliştirme referansı RC25 olarak seçildi; orijinal RC25 APK tekrar TV'ye temiz kuruldu. Öncelik: doğrudan video erişim hatasından sonra oynatma şekli değiştirilince ekran yansıtmaya dönememe / hata ekranında kalma. Kullanıcı hatayı yeniden oluşturup haber verecek; o ana kadar TV'ye başka değişiklik veya restart gönderilmez. Yeni özellikler beklemede. RC26 testinin tüm farklarının kayıttan kaynaklandığı henüz kanıtlanmadı.
