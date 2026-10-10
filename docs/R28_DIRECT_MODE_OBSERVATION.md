# R28: Doğrudan oynat seçiliyken ekran aktarımı

15 Eylül 2026 kullanıcı bildirimi: YouTube videosunda da ekran yansıtma devam etti.

TV incelemesi: advertise_video=true. NsdManager 11:23:55 ilanında features=0x5A7FFEE6,0x400; 11:27:47 ilanında features=0x5A7FFEF7,0x400. Video/HLS bitleri kapalıdan açığa geçmiş. Son 1200 satırlık gözlemde doğrudan hazırlık/oynatma başlangıcı yok, ekran decoder olayları mevcut. Uygulamanın medya oturumu aktif değil. Bu sınırlı kayıt, iPhone uygulamasının ekran görüntüsü veya eksiksiz ağ yakalaması değildir.

Doğrudan tercih alıcının video kabulünü açar; gönderenin mevcut ekran aktarımını zorla medya URL aktarımına çeviremez. iPhone yetenek önbelleği ihtimali var, kesin neden olarak doğrulanmadı. cancelledMedia durumu incelendi: geçerli yeni /play işleyicisi önce onVideoPrepare çağırarak bunu temizliyor; rastgele sıfırlanmadı.

Yalnız arayüz düzenlemesi: kontrol panelinde kullanılan gerçek aktarım gösterilir. Doğrudan oynat açıklaması videonun AirPlay menüsünden TV seçmeye yönlendirir. Motor, ağ ve ses değişmedi. Bu düzenleme otomatik aktarım geçişinin çözüldüğü anlamına gelmez. Gerçek iPhone ile video içinden AirPlay hedefi seçilmesi hâlâ doğrulanmalı.

ARM derleme 1 dakika 11 saniyede geçti, APK imzası doğrulandı. TV temiz kurulumu ve activity başlatma başarılı. Teslim: artifacts/rc25-direct-stall/AirRelay-R28-mode-status-ARM-TV.apk. SHA-256: 36E8B300174E3C50FBCF7F2E804AC6862407C31A981D6535529E0889B10CC0BE. Önceki adaylar korundu; R28 kararlı kabulü verilmedi.
