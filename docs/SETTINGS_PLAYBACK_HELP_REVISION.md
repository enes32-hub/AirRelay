# Ayarlar, tema odağı ve kayıt oynatma

- Bağlantı/güvenlik: alıcı adı, PIN, yeni cihazın yayını devralması, cihaz erişimi.
- Görüntü/oynatma: kalite profilleri, özel profil kısayolu, tam ekran, ekranı açık tutma, yayın metrikleri.
- Kayıt ayarları: 720p/1080p. Ana menüdeki Kayıtlar dosya kitaplığı olarak kalır.
- Başlangıç: uygulama/TV açılışı, arka planda hazır kalma, gelen yayını öne getirme.
- İzinler: mevcut Android izinleri. Görünüm: tema, dil, yazı ve hareket. Hakkında ve gelişmiş seçenekler korunur.

Tema penceresi kapandığında (seçim veya iptal) tema satırına odak döner.
Kayıt oynatıcısında Yukarı süre çubuğuna gider; Sağ/Sol 10 saniyelik hedef
adımları, OK Media3 zaman çubuğunun sarma işlemini uygular. Aşağı oynat/duraklat
düğmesine döner. Sarma sırasında otomatik gizleme durur.

Yardım: keşif, PIN, ses, oynatma türleri, erişim reddi/korumalı içerik,
VPN/ağ/gecikme, parça seçenekleri ve kayıt kullanımı. Gereksiz destek günlükleri eklenmedi.

Doğrulama: x86_64 derleme geçti. Üç tema arasında odak ve alıcı ömrü testi
geçti (`settings-theme-focus-test.txt`). Gerçek yerel MP4 ile kumandadan
ileri/geri sarma, duraklatma, kaynak bırakma ve kitaplık testi geçti
(`recording-timeline-test.txt`). Testte yalnız oluşturulan kopyalar silindi.
Gerçek iPhone/TV kabulü bu çalışmada yapılmadı; yalnız emulator-5584 güncellendi.

Decoder motorları değiştirilmedi: mirroring MediaCodec otomatik seçim ve
başlatma hatasında uygun yazılım yedeği; doğrudan video/kayıt Media3 varsayılan
seçimi. Kullanıcıya açık video donanım/yazılım anahtarı yok. Yazılım ALAC
seçeneği yalnız sestir. Emülatör decoder'ı fiziksel TV performansını kanıtlamaz.
