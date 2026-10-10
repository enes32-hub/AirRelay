# Ek çekirdek araştırması

31 Ağustos 2026. Sonuç: **ek adaylardan hiçbiri seçilmedi**. Bu çalışma erişilebilir birincil belgelerin teknik değerlendirmesidir; hukuk görüşü veya uyumluluk testi değildir. Aday kaynakları uygulamaya alınmadı, özel depolara erişim denenmedi, satın alma veya satıcıyla iletişim yapılmadı.

| Aday / sabit kaynak | Gözlenen durum | Proje kararı |
|---|---|---|
| [lekandigital/android-tv-airplay-receiver](https://github.com/lekandigital/android-tv-airplay-receiver/blob/7306986360aa946650970c21c0122ca67859a6aa/docs/vendor-audit.md), `7306986360aa946650970c21c0122ca67859a6aa` | Kendi bileşen envanteri playfair ve FDK AAC içeriyor; playfair için GPLv3 bildiriyor. | Farklı Android arayüzü, mevcut kriptografik köken sorusunu çözmüyor. Tam kaynak/hak incelemesi gerekiyor. |
| [air-display/apsdk-public](https://github.com/air-display/apsdk-public/blob/e7aba6204dd949558c30db286d5a7499e498ccff/readme.md), `e7aba6204dd949558c30db286d5a7499e498ccff` | README, gerekli fairplay alt modülünün özel olduğunu ve onsuz derlemenin boş uygulama kullanarak işlevsiz kaldığını belirtiyor. | Kamuya açık paket tek başına çalışır çekirdek değil. Erişim ve kullanım hakları sağlanmadan seçilemez. Boş modülle APK üretmek çözüm sayılmaz. |
| [xfirefly/Airplay-SDK](https://github.com/xfirefly/Airplay-SDK/blob/58780e1535ed44e7698e459b301385037d2f8521/README_EN.md), `58780e1535ed44e7698e459b301385037d2f8521` | Satıcı Android alıcısı, iPhone sistem yansıtması ve satılık protokol kaynak kodu sunuyor. | Bir satış beyanı, kaynak lisansı veya hak zinciri kanıtı değildir. Kaynak teslimi, yeniden dağıtım, çevrimdışı çalışma ve cihaz performansı doğrulanmalı. |
| [BiJie Android AirPlay SDK belgesi](https://www.bjcast.com/document-of-airplay-sdk-on-android/) | PIN, H.264/PCM geri çağrıları ve lisans doğrulama hata kodları belgelenmiş. | Teknik API mevcut olması SDK edinildiği veya ücretsiz dağıtım hakkı olduğu anlamına gelmez. Lisansın ağ gereksinimi ve donanıma bağlanma koşulları bilinmiyor. |

Ticari SDK yolu seçilecekse teklif/sözleşme incelemesi [hazır soru dosyasını](../../legal/REVIEW_REQUEST.md) karşılamalı. Özellikle ARM32/ARM64, 16 KB, yalnız yerel ağ, iPhone 14 Pro'nun gerçek iOS sürümü, PIN ve kaynak teslim koşulları yazılı olarak doğrulanmadan satın alma önerilmiyor. Üçüncü taraf uygulamanın APK lisansı, SDK'yı kendi uygulamamıza gömme lisansıyla aynı değildir.

## MFi sınırı

[Apple'ın MFi SSS sayfası](https://mfi.apple.com/faqs) program kapsamındaki teknolojiler arasında AirPlay **audio** sayıyor. Buradan Android TV için ekran yansıtma alıcısı SDK'sı veya otomatik dağıtım hakkı bulunduğu sonucu çıkarılamaz. MFi üyeliği satın almayı mevcut engelin doğrulanmış çözümü olarak sunmuyoruz; ilgili ürün türüne erişim ve sözleşme kapsamı ayrıca teyit edilmeli.

## İlerlemenin somut koşulu

Ya mevcut açık kaynak adayının kriptografik materyal kökenini ve kullanım haklarını çözen belgeler ya da şartnameyi karşılayan lisanslı çekirdek ve kaynak erişimi sağlanmalı. Ardından yetkilendirilmiş fiziksel TV'de iPhone bağlantı spike'ı yapılmalı. Model bilgisinin gelmesi tek başına çekirdek engelini, çekirdek edinilmesi de gerçek cihaz testini ortadan kaldırmaz.
