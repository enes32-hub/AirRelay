# Wi-Fi değişimi simülasyonu — 10 Eylül 2026

Gerçek Wi-Fi kapatılmadı, SSID seçilmedi, IP/ağ yönlendirmesi değiştirilmedi. Üretim APK'sı değiştirilmedi; emülatörde yalnız test APK'sı güncellendi. Android test koşusu uygulamanın servis/keşif durumunu değiştirdi ve sonunda eski kullanıcı tercihleri ile alıcıyı geri yükledi.

## Geçen kontroller

- Android uygulama ve gerçek servis başlangıcı.
- Yapay ağ kaybında NETWORK_WAIT, AirPlay/RAOP duyurularının IDLE olması; native alıcı tanıtıcısının korunması.
- Aynı WIFI türünde IP değişiminde yeni discoveryGeneration ve başarılı gerçek Android NSD kaydı.
- Yapay eski ağın kaybından farklı Wi-Fi ağ kimliğine geçiş; duyurunun yeniden kaydı.
- Kapanmış ağ izleyicisine geciken onLost callback'inin etkisiz kalması.
- Keşif hata bildirimi ve toparlanma.
- Windows köprüsü için 9 host testi: geçici bind hatasından sonra 3 saniyelik tekrar ve güncel Android kaydının yeniden okunması dahil.

Android koşusu 4 senaryo grubuyla geçti. Ayrıntı: artifacts/network-simulation-20260910/android-network-topology.log. Test ağ kimlikleri/örnek IP'ler yalnız kayıtlı olmayan bir LocalNetworkMonitor nesnesinin kendi networks/links tablolarına verildi. Bu nesnenin gerçek publish mantığı, uygulamanın gerçek ağ-değişti callback'ine bağlandı; onLost/close yolları da çağrıldı. OS Wi-Fi sürücüsünden gelen onCapabilitiesChanged/onLinkPropertiesChanged olaylarının uçtan uca testi değildir. Örnek IP'ler gerçek arabirime atanmadı; NSD gerçek mevcut emülatör ağında kaydoldu.

İlk iki derleme test verisi oluşturmak için kullanılan Android API'lerinin SDK'da bulunmaması nedeniyle başarısızdı; sonraki koşu gizli NetworkCapabilities metoduna erişemedi. Bu başarısız kanıtlar korunur. Son koşu gizli Android API'leri yerine yalnız uygulamanın kendi test nesnesini kullanarak geçti.

## Kabul sınırı

Fiziksel TV'nin Wi-Fi sürücüsü, yeni SSID'de DHCP, farklı yönlendirici, misafir ağındaki izolasyon veya iPhone'un yeni ağdaki keşfi sınanmadı. Ağ değişince eski TCP/video oturumu korunacağı söylenemez; yeniden bağlanmak gerekebilir. iPhone ile TV birbirine erişebilen aynı yerel ağda olmalıdır.

Windows geliştirme köprüsündeki mevcut düzeltme, geçici IP kullanılamama hatasında kapanmak yerine tekrar denemesidir. Windows IP'si kalıcı olarak başka adrese değişirse başlatma adresini otomatik seçen bir özellik eklenmedi. Bu Windows test aracı APK'nın parçası değildir.
