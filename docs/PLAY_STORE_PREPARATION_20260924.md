# AirRelay Play Store hazırlığı — 24 Eylül 2026

## Tamamlanan
- ~~Kullanıcının svgmaker-editor.svg dosyası design/airrelay-svg-logo/source.svg olarak korundu.~~
  **Düzeltme (R40):** bu dosya depoda hiç bulunmadı. Kaynak, uygulamanın vektör çiziminden
  (`app/src/main/res/drawable/airrelay_logo_vector.xml`) türetilerek yeniden oluşturuldu ve
  güvenli bölge kurallarıyla birlikte `design/airrelay-svg-logo/README.md` içinde belgelendi.
  Özgün yazar dosyası bulunursa `source.svg` onunla değiştirilmelidir.
- Beş yol Android VectorDrawable'a taşındı; ana ekranda tema metin rengiyle, TV simgesinde açık renkle kullanılıyor.
- Oynatma, bağlantı ve altyazı motorları değiştirilmedi.

## Yayını engelleyen doğrulanmış durumlar
- Play Console mevcut oturumda /signup (geliştirici hesabı oluşturma) ekranına yönlendi. Başka bir yayın hesabı varsa kullanıcı seçmeli; mevcut hesapta kayıt tamamlanmalı.
- local.properties yayın storeFile yapılandırması içermiyor. Mevcut kurulumlar debug imzalı; yayın anahtarıyla güncelleme uyumluluğu ayrıca planlanmalı. Anahtar/parola sohbet içinde paylaşılmamalı.
- build_receiver.ps1 derleme alanının local.properties dosyasını SDK/CMake bilgileriyle yeniden oluşturuyor. Yayın imzası bu yola güvenerek eklenmemeli; ayrı güvenli imzalama akışı gerekli.
- legal/NOTICE.md ve GPL-3.0 LICENSE kaynak dağıtımı yükümlülükleri içeriyor. Ücretli satış mümkündür; ancak tüm kaynak kullanımını yasaklayan kapalı lisans yaklaşımı mevcut GPL çekirdeğiyle uyumlu kabul edilemez. Karşılık gelen kaynak, bildirimler ve lisans incelemesi tamamlanmalı.

## Henüz doğrulanmayan yayın gereklilikleri
- İmzalı AAB, sürüm kodu/ismi ve Play App Signing seçimi.
- Gizlilik politikası URL'si, geliştirici iletişimi ve gerçek veri akışına uygun Veri Güvenliği beyanı.
- TV ekran görüntüleri, mağaza simgesi/grafikleri ve açıklamalar.
- İçerik derecelendirmesi, hedef kitle, reklam yok beyanı ve ön plan hizmeti izin açıklamaları.
- Release/minify regresyonları, 16 KB bellek sayfası uyumluluğu ve Play ön lansman testleri.
- Hesaba uygulanan kapalı test/üretim erişimi koşulları Console üzerinden doğrulanmalı.

## Mağaza açıklaması taslağı (gönderilmedi)
AirRelay — iPhone, iPad veya Mac ekranınızı Android TV'nize yansıtın. Aynı yerel ağ üzerinden ekran paylaşımı, uyumlu içerikte doğrudan video oynatma ve TV kumandasıyla oynatma kontrolleri. İçeriğe ve kaynak uygulamaya göre özellikler değişebilir; korumalı içerik desteklenmeyebilir. Google Cast, Miracast veya DLNA desteği vaat edilmez.

Yükleme veya yayın gönderimi yapılmadı. Geliştirici hesabı ve imzalama tamamlanmadan dosyalar mağazaya hazır sayılmaz.
