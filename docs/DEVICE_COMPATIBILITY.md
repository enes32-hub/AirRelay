# Fiziksel test cihazları

Kullanıcı 31 Ağustos 2026'da ilk test çiftini **iPhone 14 Pro** ve **Xiaomi TV Box 4K (son nesil olarak tarif edilen)** cihazları olarak belirtti.

Henüz iOS sürümü/build'i, Xiaomi'nin tam model numarası, Android sürümü/build'i ve fiziksel ADB erişimi doğrulanmadı. [Makine okunur kayıt](DEVICE_COMPATIBILITY.json) bu alanları bilinmiyor olarak tutar. Burada destek onayı verilmez.

TV kutusunun ürün etiketindeki tam model numarası ile Ayarlar → Cihaz tercihleri/Sistem → Hakkında bölümündeki model ve Android sürümü kullanılacak; menü adları cihaz sürümüne göre değişebilir. iPhone sürümü Ayarlar → Genel → Hakkında ekranından okunabilir. Cihaz seri numarası, hesap bilgisi, MAC adresi veya PIN paylaşılması gerekmez.

İlk fiziksel deneme, iPhone'daki **Denetim Merkezi → Ekran Yansıtma** akışını kullanacak. iPhone'a yardımcı uygulama kurulmayacak. Xiaomi kutusunun Android TV tabanındaki arayüz markası, ayrıca başka platform uygulaması geliştireceğimiz anlamına gelmez.

Bir cihaz çiftinin geçmesi yalnız o model/OS/ağ birleşimini kanıtlar. Şartnamedeki daha geniş iPhone sürüm ve Android TV donanım matrisi, 2/8 saat dayanıklılık ve diğer kabul eşikleri ayrıca sağlanmalıdır.

## Test sırası

Kullanıcının 31 Ağustos 2026 talimatı uyarınca, günlük geliştirme ve ara doğrulama Android TV emülatörlerinde yapılacaktır. Gerçek Xiaomi TV yalnız nihai fiziksel kabul aşamasında kullanılacak; bundan önce TV'de sürekli kurulum veya deneme istenmeyecektir. Emülatör sonuçları D-pad, arayüz, servis yaşam döngüsü ve temel paket kurulum kanıtıdır; iPhone keşfi, H.264 donanım çözme, ses/video kalitesi veya gerçek ağ davranışı kabulünün yerine geçmez.

## Bilgisayara test bağlantısı

Önce tam model ve Android sürümü doğrulanmalı. [Resmî ADB belgesi](https://developer.android.com/tools/adb) eşleştirme koduyla kablosuz hata ayıklama için TV'de Android 13+ koşulunu belirtiyor; telefondaki Android 11 sınırı TV'ye uygulanmamalı. Üreticinin menüsü bu özelliği sunmuyorsa modelin desteklediği bağlantı yolu ayrıca belirlenmeli. Rastgele IP taraması, port yönlendirme veya yetkilendirme atlama yapılmayacak.

Güvenilir yerel ağda cihaz sahibi TV'nin geliştirici seçeneklerinden hata ayıklamayı açıp yalnız kendi bilgisayarını yetkilendirir. Kod gereken bağlantıda `adb pair IP:ESLESTIRME_PORTU` kullanılır; kod yerel terminaldeki isteme girilir, sohbete veya dosyaya yazılmaz. USB yolunda TV'deki RSA onayı cihaz sahibi tarafından verilir. Bu hazırlık TV'yi değiştirip uygulama kurmaz; test bitince hata ayıklama kapatılmalı ve bilgisayar yetkisi kaldırılmalıdır.

Bağlantı kurulduktan sonra yalnız seçilen TV'nin model, OS, ABI ve TV özelliklerini okumak için:

```powershell
python scripts/collect_tv_environment.py --serial 'ADB_LISTESINDEKI_TV'
```

Araç otomatik eşleştirme/bağlantı yapmaz, uygulama kurmaz, bütün sistem özelliklerini veya logları toplamaz. ADB adresi/seri numarası rapora yazılmaz. Çıktı Git dışında tutulan `artifacts/local/tv-environment.json` dosyasındadır. Emülatörler reddedilir; sıfır çıkış kodu yalnız metadata okunduğunu belirtir, fiziksel cihaz kimliği ve yansıtma başarısını kanıtlamaz. Şu anda fiziksel Xiaomi erişimi doğrulanmış değildir.
