# Videodan dönüşte donma ve yeniden bağlantı düzeltmesi

Bu çalışma, kullanıcının Fotoğraflar videosunu kapatınca eski görüntünün kalması ve bağlantıyı yeniden açınca bekleme göstergesinden çıkamama bildirimini ele alır. Önceki gerçek iPhone başarısızlık kaydı ve fotoğraf değiştirilmeden korunmuştur.

## Yapılan değişiklikler

- `c2.goldfish` emülatör decoder'ında adaptif boyut değişimi kapatıldı. Gerçek denemede 498 piksel kodlanmış genişliğe karşılık 1919 sağ kırpma sınırı bildirilmiş, ardından giriş kuyruğu dolmuştu. Bu decoder artık boyut değişiminde yeni yapılandırma ve uygun anahtar kareyle başlar. Gerçek TV decoder politikası değiştirilmedi.
- Yeni decoder başlarken eski SurfaceTexture ve üretici kuyruğu bırakılıp yenisi oluşturulur. Böylece önceki oturumdan kalan belirsiz ilk kare takibi yeni bağlantının bekleme göstergesini kalıcı olarak açık tutamaz. Oturum kimliği ve görünen yüzey korunur; yalnız görünen yüzeyi ayırıp bağlamak decoder'ı yeniden başlatmaz.
- EGL çizim alanı her çizimde güncel yüzey boyutundan alınır; boyutu değişen yüzeyde eski çizim alanının dar bir şerit bırakması önlenir.
- Başarıyla yüzeye sunulan ilk kare, yalnız oturum numarasıyla tanılama günlüğüne yazılır. PIN veya medya URL'si bu yeni kayda dahil edilmez.

Native protokol, keşif kimliği, sabit portlar, eşleştirme ve ses zamanlaması bu düzeltmede değiştirilmedi. Emülatör kapatılmadı; uygulama verileri korunarak APK güncellendi.

## Kanıt ve test kapsamı

`artifacts/freeze-fix-20260908/` geri dönüş APK'sını, kaynak yedeğini ve yeni test günlüklerini içerir.

- **Eski sürümde hata üretildi:** 300 tüketilmemiş decoder gözlemiyle ilk kare takibi belirsizleştirildikten sonra yeniden bağlantının ilk kare bildirimi gelmedi (`baseline-android.log`). Bu kontrollü hata enjeksiyonudur; iPhone'daki ikinci hatanın kesin olarak aynı yoldan oluştuğunun kanıtı değildir. İlk test sürümünün günlük öneki sonuç hesaplanmadan yazıldığı için bu kayıtta `PASS OBSERVATION_RECOVERY` arkasından istisna gelir; sonuç başarısızdır. Son test kodu bu günlük kusurunu giderir.
- **İlk yeni sürüm testi geçti:** aynı hata enjeksiyonundan sonra gerçek AVC decoder, EGL ve piksel çıktısıyla yeni oturumun ilk karesi doğrulandı (`fixed-android.log`).
- **Host kontrolleri geçti:** 15 ekran oturumu, 17 ilk kare gözlemi ve 6 günlük zamanlama kontrolü (`artifacts/rev2-integration/freeze-fix-20260908/`).
- Son Android testi, üç turda toplam 15 boyut adımını kapsar: `498×1080 → 1920×1080 → 498×1080 → 1920×886 → 498×1080`. Her adım sırasında görünen yüzey ayrılıp yeniden bağlanır; gerçek hareketli piksel çıktısı ve sıfır giriş düşüşü aranır. Ayrıca üç yeni AVC oturumunda ilk kare ve hareket doğrulanır. Kesin sonuç `final-android.log` içinde saklanır.

## Kabul sınırı

Son Android çalıştırması **geçti**: hata enjeksiyonu sonrası toparlanma, yüzey ayırma/bağlama içeren 15 boyut adımı ve üç yeni decoder oturumunda gerçek hareketli çıktı doğrulandı (`final-android.log`). Uygulama Türkçe “Bağlanmaya hazır” ekranında yeniden açıldı; klavye desteği `yes` olarak kaldı. Kurulu APK eşleşmesi ve eşleştirme anahtarı koruması `delivery.json` içinde doğrulandı.

Bu yerel testler gerçek iPhone, Fotoğraflar veya YouTube akışının yerine geçmez. Yeni APK ile iPhone'dan video kapatma, telefon ekranına dönüş, bağlantıyı kesme ve yeniden bağlanma tekrar doğrulanmalıdır. Önceki iPhone hataları, yalnız yerel test geçtiği için gerçek cihazda çözülmüş sayılmamıştır. Xiaomi TV testi yapılmadı.
