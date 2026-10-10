# AirRelay Receiver — Gizlilik Politikası

**Uygulama:** AirRelay Receiver (`com.airrelay.receiver`), Android TV
**Geliştirici:** Mahmut Enes Bozdoğan
**İletişim:** alquariz@yandex.com
**Yürürlük tarihi:** 11 Ekim 2026
**Belge sürümü:** 1.0 (uygulama sürümü 1.0.0 ve sonrası için geçerlidir)

---

## 1. Özet

- AirRelay Receiver **kişisel verilerinizi toplamaz, iletmez ve satmaz.** Hesap yok, oturum açma yok, geliştirici sunucusu yok.
- Uygulama tamamen **kendi yerel ağınızda** çalışır: Apple cihazınız (iPhone, iPad veya Mac) ile Android TV'niz arasında.
- **Reklam, analitik, izleme veya çökme raporlama SDK'sı yoktur.**
- Uygulamanın sakladığı her şey — ekran kayıtları, kaydedilen videolar, teknik günlükler — **cihazınızda** durur ve siz paylaşmadıkça oradan çıkmaz.
- Uygulama `RECORD_AUDIO` (mikrofonla ilişkili) iznini ister; ancak **mikrofonunuzu hiçbir zaman okumaz.** Bu izin, kayıt sırasında uygulamanın **kendi** sesini yakalayan Android API'sinin teknik ön koşuludur. Ayrıntı: bölüm 5.

---

## 2. Uygulama ne yapar

AirRelay Receiver, bir Android TV cihazını AirPlay alıcısına dönüştürür. Aynı yerel ağdaki bir Apple cihazından ekran yansıtmayı başlattığınızda TV o ekranı gösterir ve sesini çalar. İstek üzerine uygulama ayrıca:

- gelen görüntüyü ve sesi TV'de bir video dosyasına kaydedebilir,
- TV'ye yayınlanan bir videoyu kaydedebilir,
- sorunları teşhis etmek için teknik günlük tutabilir.

Bunların tamamı TV'de gerçekleşir. Hiçbir şey hiçbir yere yüklenmez.

---

## 3. Topladığımız veriler

**Hiçbiri.** Bu uygulamanın geliştiricisi verilerinizin hiçbirini almaz, saklamaz veya işlemez.

Özellikle:

| Soru | Yanıt |
|---|---|
| Hesap veya oturum açma gerekli mi? | Hayır. |
| Ad, e-posta veya cihaz kimliği topluyor musunuz? | Hayır. |
| Kullanım verisi, telemetri veya analitik bir yere gönderiliyor mu? | Hayır. |
| Reklam kimliği kullanılıyor mu? | Hayır. |
| Geliştiriciye veya üçüncü taraflara veri gönderiliyor mu? | Hayır, hiçbiri. |
| Uygulama internet olmadan çalışır mı? | Evet — yalnızca yerel ağ gerekir. |

Geliştiriciye hiçbir veri ulaşmadığı için, geliştirici tarafında erişilecek, düzeltilecek, dışa aktarılacak veya silinecek veri yoktur.

---

## 4. Cihazınızda saklananlar (ve nasıl silineceği)

| Saklanan şey | Nerede durur | Neden | Nasıl silinir |
|---|---|---|---|
| Başlattığınız ekran kayıtları | TV'de uygulamaya özel dizin (uygulama içinden Filmler kitaplığına aktarılabilir) | İstediğiniz kayıt özelliği | Uygulama içinden silin veya TV'den dosyaları kaldırın |
| TV'ye kaydetmeyi seçtiğiniz videolar | TV medya kitaplığı | İstediğiniz "videoyu kaydet" özelliği | TV medya kitaplığından silin |
| Teknik / destek günlükleri | TV'de uygulamaya özel dizin | Sorun giderme ve destek talepleri | Uygulamada "günlükleri temizle" veya uygulama verisini temizleyin / uygulamayı kaldırın |
| Uygulama ayarları (cihaz adı, PIN tercihi, görüntü tercihleri) | TV'de uygulamaya özel dizin | Tercihlerinizi hatırlamak | Uygulama verisini temizleyin veya uygulamayı kaldırın |

Buluta yedekleme kapalıdır (`android:allowBackup="false"`); bu veriler Google'ın bulut yedekleme hizmetine **kopyalanmaz**.

Günlükler, siz açıkça dışa aktarmadıkça veya göndermedikçe cihazda kalır. Destek amacıyla geliştiriciye günlük gönderirseniz, yalnızca talebinizi yanıtlamak için kullanılır ve sonrasında silinir.

---

## 5. İzinler ve neden gerekli oldukları

| İzin | Neden istenir | Yapmadığı şey |
|---|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` | AirPlay yayınını almak ve yerel ağdaki alıcıları bulmak | Geliştirici veya üçüncü taraf sunucularına bağlanmaz |
| `CHANGE_WIFI_MULTICAST_STATE` | Apple cihazınızın TV'yi bulabilmesi için multicast/mDNS keşfi | — |
| `RECORD_AUDIO` | Kayıt sırasında kullanılan Android ses-yakalama API'sinin **teknik ön koşulu**. Yakalama yalnızca **bu uygulamanın kendi ses çıkışıyla** sınırlıdır (kendi UID'siyle eşleşir) | **Mikrofonu asla okumaz.** Mikrofon sesi kaydedilmez, saklanmaz, iletilmez |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_MEDIA_PROJECTION` | Android'in şart koştuğu şekilde, uygulama arka plandayken alıcının güvenilir çalışmasını sağlamak | — |
| `POST_NOTIFICATIONS` | Arka plan çalışması için zorunlu olan "alıcı etkin / kayıt sürüyor" bildirimini göstermek | — |
| `WAKE_LOCK` | Yayın sırasında TV'nin uykuya geçmesini engellemek | — |
| `RECEIVE_BOOT_COMPLETED` | İsteğe bağlı: TV açıldığında alıcıyı otomatik başlatmak — **yalnızca bu ayarı açarsanız** (varsayılan olarak kapalıdır) | Açmadığınız hiçbir şeyi başlatmaz |
| `SYSTEM_ALERT_WINDOW` (diğer uygulamaların üstünde göster) | İsteğe bağlı: yayın başladığında alıcıyı öne getirmek | Reklam göstermek veya diğer uygulamaları izlemek için kullanılmaz |

Ekran kaydı ayrıca Android'in kendi MediaProjection onay penceresini gerektirir; her seferinde sizin onayınız gerekir.

---

## 6. Ağ etkinliği

- Uygulama **yalnızca yerel ağınızda (LAN)** haberleşir: AirPlay göndericilerini mDNS ile bulur ve yayını gönderen cihazdan AirPlay/RTSP portlarından (varsayılan 7000 ve 7100) alır.
- Uygulamada **sabitlenmiş hiçbir internet adresi yoktur** ve geliştiriciye veya üçüncü taraf servislere hiçbir bağlantı kurulmaz.
- Apple cihazınız ile TV arasındaki AirPlay/RTSP trafiği **şifreli değildir** — AirPlay yansıtma protokolü böyle çalışır. Yerel ağınızı gözlemleyebilen biri bu trafiği ilke olarak görebilir. Güvendiğiniz, parola korumalı bir Wi-Fi ağı kullanın; misafirler için ayrı bir ağ tercih edin.
- Android'in şifresiz trafik bayrağı protokol uyumluluğu için açıktır; bu, uygulamanın bu tür yerel trafiği alabilmesini sağlar. İnternete bağlanmak için kullanılmaz.

---

## 7. Kayıtlar ve telif hakkı

Kayıtlar ve kaydedilen videolar **sizin isteğinizle** oluşturulur ve cihazınızda saklanır. Ekranınızda görünen içeriği kaydetme veya saklama hakkına sahip olmak sizin sorumluluğunuzdadır (örneğin bir yayın servisinin korumalı videosu kaydedilemeyebilir ve başkalarının içeriğini kaydetmek onların iznini gerektirebilir). Uygulama içerik korumasını kaldırmaz veya atlatmaz.

---

## 8. Üçüncü taraf yazılımlar ve açık kaynak bileşenler

Uygulama açık kaynak bileşenler içerir; aralarında:

- **UxPlay** (GPL-3.0) ve **android-airplay-server** (GPL-3.0) — AirPlay alıcı uygulaması,
- **FFmpeg** (LGPL) — ses çözme,
- **libplist**, **OpenSSL** (Apache-2.0),
- **AndroidX, Jetpack Compose, Media3 (ExoPlayer), Oboe, Kotlin/kotlinx.coroutines** (Apache-2.0).

Lisans metinlerinin tamamı ve telif bildirimleri **uygulamanın içinde**, *Ayarlar → Hakkında → Bileşen lisansları* altında bulunur. Hiçbir üçüncü taraf reklam, analitik, atıf veya izleme SDK'sı yer almaz.

---

## 9. Çocukların gizliliği

Uygulama çocuklara yönelik değildir ve çocuklar dahil hiç kimseden kişisel veri toplamaz.

---

## 10. Haklarınız

GDPR (AB) ve CCPA/CPRA (Kaliforniya) gibi gizlilik yasaları, hakkınızda tutulan kişisel veriler üzerinde size haklar tanır. Geliştirici **hakkınızda hiçbir kişisel veri tutmaz**; dolayısıyla bizim tarafımızda açıklanacak, düzeltilecek veya silinecek bir şey yoktur. Uygulamanın oluşturduğu veriler kendi cihazınızda sizin denetiminizdedir ve bölüm 4 nasıl silineceğini anlatır. Sorularınız için aşağıdaki adresten bize ulaşabilirsiniz.

---

## 11. Bu politikadaki değişiklikler

Bu politika değişirse güncel sürüm, yeni yürürlük tarihi ve belge sürümüyle birlikte bu adreste yayınlanır. Önemli değişiklikler ayrıca uygulamanın sürüm notlarında belirtilir.

---

## 12. İletişim

**Mahmut Enes Bozdoğan**
E-posta: **alquariz@yandex.com**

Apple, AirPlay, iPhone, iPad ve Mac, Apple Inc. şirketinin ticari markalarıdır. Bu uygulama Apple Inc. ile bağlantılı, onun tarafından onaylanmış veya desteklenmiş değildir.
