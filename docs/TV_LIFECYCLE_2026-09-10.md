# TV alıcısı: başlatma, görünürlük ve aynı ağda kullanım

Bu not yeni ürün düzenlemesindeki yaşam döngüsü değişikliklerini kapsar. Gerçek TV veya iPhone kabul testi yerine geçmez.

## Uygulanan değişiklikler

- TV açılışında başlatma yeni kurulumlarda kapalıdır; kurulum tamamlanmış ve kullanıcı bu tercihi açmışsa `BOOT_COMPLETED` alıcı servisini başlatır. Mevcut kayıtlı tercih korunur. Kullanıcının açıkça durdurduğu alıcı kendiliğinden yeniden başlatılmaz.
- Açılış servisi yalnız `connectedDevice` ön plan türünü kullanır; Android 15+ açılış kısıtına takılan `mediaPlayback` türünü açılışta istemez. Üreticinin hizmet başlatmayı reddetmesi broadcast'i çökertmez. TV'yi uyandırma veya Android'in zorla durdurma kuralını aşma iddiası yoktur.
- Ham TCP bağlantısı artık uygulamayı öne getirmez. PIN isteği, gerçek yansıtma kanalı, uzak video oynatma isteği veya ses kurulumu ilk kez geldiğinde otomatik görünürlük tercihi kontrol edilir. Aynı oturumdaki her video geçişi odağı tekrar çalmaz.
- Android 10+ arka plandan ekran açılması yalnız sistemin verdiği izinle denenir. İzin ayarı ancak kullanıcının düğmeye basmasıyla açılır. İzin yoksa alıcı arka planda kalır; kullanıcı bildirimden/uygulama listesinden açabilir. Bildirim izni reddedildiyse bildirimin görünürlüğü de sistem tarafından sınırlanır. Üreticiye göre otomatik ekran açılması kısıtlanabilir; izin verilmiş olması her TV'de başarı kanıtı değildir.
- Uygulamayı kapat eylemi oynatmayı/alıcıyı durdurur, keşif ilanlarını geri çeker, bekleyen başlamayı iptal eder ve görevi kaldırır. İşletim sistemi süreci zorla öldürülmez. Yeniden başlatma için kullanıcı alıcıyı yeniden başlatmalıdır.
- Varsayılan alıcı adı ürün adı, TV modeli ve kalıcı rastgele sekiz karakterlik ekten üretilir. Eski varsayılan adlar taşınır; farklı özel adlar korunur. MAC, AirPlay eşleştirme anahtarları ve uygulama kimliği değiştirilmez. Ad UTF-8 olarak 48 bayt içinde kalır. Rastgele ek çakışma olasılığını çok düşürür; matematiksel olarak küresel benzersizlik garantisi değildir. Android NSD yine yerel isim çakışmasını çözer.
- Wi-Fi/Ethernet kaybında keşif ilanları geri çekilir. Ağ geri geldiğinde veya aynı tür ağın kimliği/IP adresi değiştiğinde ilanlar kısa gecikmeyle yeniden oluşturulur. Çalışan native alıcı veya video oynatıcı bu nedenle yeniden başlatılmaz. Keşif geri çekme hataları artık sessizce yutulmaz.
- Başlatmadan önce kontrol TCP portu, TCP 7100 ve UDP 7011/6001/6000 kontrol edilir. Fiziksel TV'de dolu bir uç varsa native alıcı bütün portları işletim sistemine seçtirir: kontrol portu NSD ilanına, görüntü/ses/zamanlama portları mevcut SETUP yanıtlarına gerçek değerleriyle yazılır. Sabit portların boş olduğu durumda mevcut düzen korunur. Emülatörde köprü yalnız sabit portları iletebildiğinden çakışma hâlâ açık hata olarak gösterilir. Başka uygulama durdurulmaz ve o uygulamanın soketi/ayarları değiştirilmez. TCP denetimi eski bağlantının TIME_WAIT durumuna izin verir; aktif dinleyiciye izin vermez.

## Önemli sınırlar

Otomatik port desteğinin kaynak yolu doğrulandı: `netutils_init_socket` sıfır portla `bind` edip `getsockname` ile gerçek portu alır; `httpd_start` kontrol portunu döndürür, `nativeStart` bunu NSD ve HLS için kaydeder; `raop_ntp_start`, `raop_rtp_mirror_start`, `raop_rtp_start_audio` gerçek portlarını SETUP yanıtına iletir. **Fiziksel TV'de başka alıcı açıkken gerçek iPhone oturumu henüz denenmedi.** Güvenlik duvarının seçilen portları engellemesi veya başka uygulamanın başlangıç kontrolünden sonra aynı sabit portları alması ayrı hata olabilir. Emülatörde dinamik portlara geçilmez; kullanıcı aynı portu tutan diğer alıcıyı kapatmalıdır. Ürün ayarındaki kontrol portu kaydı otomatik mod yüzünden değiştirilmez.

Keşif ilanı geri çekilse bile iPhone/Android DNS önbelleği kısa süre eski ismi gösterebilir. Bu uzaktaki önbelleğin anında silindiği anlamına gelmez. Gerçek aynı-ağ keşif testi ayrı kabul maddesidir.

## Doğrulama

| Kontrol | Sonuç |
| --- | --- |
| Yeni adın kararlı olması, iki farklı kurulum eki, uzun Unicode adı, eski varsayılan ve özel adlar | Geçti; JVM mantık testleri |
| Native/emülatör port listesi sözleşmesi | Geçti; liste regresyon kontrolü |
| Gerçek host TCP ve UDP dinleyicilerinin çakışma olarak görülmesi, dinleyicilerin açık kalması | Geçti; gerçek yerel soketlerle test |
| Boş port ön kontrolü | Geçti |
| Fiziksel TV otomatik port seçimi ve emülatör sabit-port seçimi politikası | Geçti; 10 ek JVM kontrolü |
| Gerçek UxPlay `netutils.c` ile port 0 bağlama ve dönen portlar | Geçti; Windows host üzerinde aynı anda 2 TCP/3 UDP, gerçek `getsockname` karşılaştırması; TV/SETUP kabul testi değildir |
| Aynı transport üzerinde tekrarlanan SETUP-benzeri başlatmalar | Geçti; yeniden derlenen gerçek `libairplay` ile NTP/görüntü/ses soketleri açıldı, sıfır port girdisiyle 25 tekrarın tamamında ilk seçilen portlar döndü; iPhone HTTP SETUP kabul testi değildir |
| Toplam `ReceiverLifecycleChecks` | 25 kontrol geçti |
| Android derleme / kurulum / ekran görüntüsü | Ana ürün teslim raporunda ayrıca belirtilmeli |
| Gerçek TV açılışı, otomatik öne gelme, TV üreticisi izin ekranı | Çalıştırılmadı |
| iPhone keşfi, canlı bağlantı, kapatınca listeden silinme | Bu değişiklikler için çalıştırılmadı |
| TV üzerinde farklı alıcıyla gerçek port çakışması | Çalıştırılmadı; host soket testi bunun tamamı değildir |

Test kaynağı: `tests/integration/ReceiverLifecycleChecks.kt`.
