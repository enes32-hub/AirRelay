# R30.3 kontrol paneli

Yayın ekranından `Kontrolleri gizle` ve `Ana ekrana dön` kaldırıldı. Ana sıra şu düzendedir: Oynatma şekli, ekran ölçeği, döndürme, yayını bitirme ve Ayarlar. Ayarlar yayını ve alıcıyı durdurmadan Ayarlar sayfasını açar. Kumandadaki Geri düğmesi görünür paneli kapatmaya devam eder.

Doğrudan video sırasında oynat/duraklat, görüntü içinde görüntü, kalite, altyazı ve ses seçenekleri ikinci sırada gösterilir. İki sıra ortak koyu panel, ölçülü kenarlık, eşit ana düğme ölçüleri ve mevcut tema renklerini kullanır. Odak açıldığında Oynatma şekli düğmesine gelir. Hata durumundaki beş vurgunun davranışı korunur.

Doğrulama:

- ARM32/ARM64/x86_64 debug ve Android test APK derlemesi geçti.
- Xiaomi TV üzerindeki odak testi geçti: kaldırılan iki düğme yok; beş ana düğme mevcut; Ayarlar açılıyor; alıcı ve yayın durumu korunuyor.
- Doğrudan video kalite, altyazı ve ses testi geçti.
- Doğrudan video döndürme testi geçti: 12 sabit kare dönüşü ve 12 canlı dönüşte yüzey değişmedi; 44 decoder karesi ilerledi.
- Python testleri: 57 geçti.
- Gerçek iPhone ile görünüm ve kumanda kabulü bekliyor.

APK: `artifacts/releases/AirRelay-R30.3-modern-controls.apk`

SHA-256: `13FC6133EF5D514BDA784E51D67477F66B7AA2EB1E7F0709B9F62BC02C68587F`

Android sürümü: 0.1.0-rc30.3-test / 64. Önceki paketler korunur.
