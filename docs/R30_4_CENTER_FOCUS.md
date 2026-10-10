# R30.4 merkez odağı

Kontrol paneli kullanıcı tarafından normal biçimde açıldığında ilk kumanda odağı, ana sıranın ortasındaki **Döndür** düğmesine gelir. Video kaynağı erişim hatası yönlendirmesi bu davranıştan ayrıdır: hata halinde odak **Oynatma şekli** düğmesine gelir ve mevcut beş vurguluk yardım davranışı korunur.

Bu sürüm oynatma, decoder, ses, AirPlay keşfi veya bağlantı motorunu değiştirmez.

Doğrulama:

- ARM32/ARM64 debug APK ve Android test APK derlemesi geçti.
- Xiaomi TV üzerinde normal panel açılışı testi geçti; erişilebilirlik odağı Döndür düğmesinde doğrulandı.
- Xiaomi TV üzerinde hata yönlendirme testi geçti; tek seferlik açıklama ve her hatada beş vurgu korundu.
- Python hazırlık ve paket doğrulama testleri: 57 geçti.
- APK imzası doğrulandı; ARM64 ELF ve APK ZIP 16 KB hizalaması geçti.
- Gerçek iPhone ile görsel kabul testi bekliyor.

APK: `artifacts/releases/AirRelay-R30.4-center-focus.apk`

SHA-256: `C597E9F3ED5F179276AC918BC14C9A0F723AB5756C47B93F743EDE1EB365400C`

Android sürümü: 0.1.0-rc30.4-test / 65. Önceki paketler korunur.
