# Test kurulumları — kullanıcı tercihi, 13 Eylül 2026

**14 Eylül güncellemesi:** Şimdilik yalnız `emulator-5584` / `aynatv_tv_api36` test hedefidir. Aşağıdaki TV seri numarası tarihsel örnektir; yeni kullanıcı talimatı olmadan TV'ye kurulum veya test gönderilmez.

Kullanıcı bundan sonraki geliştirme/test teslimlerinde AirRelay’in ilk kez açılmış gibi
hazırlanmasını istedi. Test güncellemesinde uygulama verileri, eşleştirmeler, ayarlar,
bildirim seçimi ve otomatik ekrana getirme izni sıfırlanır. Son durumda ilk kurulumun
ilk ekranı açık bırakılır; kullanıcının yerine kurulum tamamlanmaz.

Bu tercih normal ürün kullanımında her açılışta veri silmek anlamına gelmez.
Başka uygulamalar ve cihaz ayarları sıfırlanmaz. Hedef cihazın seri numarası mutlaka
doğrulanır; birden fazla cihaz bağlıyken varsayılan ADB hedefi kullanılmaz.

Kullanım (değerler açıkça verilmelidir):

```powershell
.\scripts\install_fresh_test.ps1 -Serial '192.168.31.131:5555' -Apk 'C:\AynaTVBuild6\app\build\outputs\apk\debug\app-debug.apk'
```

Android izinleri uygulama içindeki anahtarlarla taklit edilmez. İzinler ekranı gerçek
sistem durumunu okur ve Android ayarlarını açar. Normal ağ/servis/uyanıklık izinleri
Android tarafından otomatik verilir; uygulama bunlar için sahte sistem onayı istemez.
