# R30 test adayı: doğrudan video seçenekleri

Kapsam: yalnız AirPlay doğrudan video oynatımı. Kontrol panelinin ikinci sırasında Kalite, Altyazı ve Ses bulunur. Ekran yansıtmasında bu seçenekler gösterilmez.

- Kalite: akışta bulunan ve decoder tarafından desteklenen gerçek çözünürlükler; Otomatik mevcut profil sınırlarını kullanan adaptif seçime döner. Elle seçim yalnız ilgili videoda geçerlidir.
- Ses: akışın ses parçaları, kaynak etiketi/dili/kanal bilgisiyle seçilir. Yerel seçim eski gönderici seçimi tarafından hemen geri alınmaz; göndericinin daha sonra açıkça gönderdiği yeni seçim uygulanabilir.
- Altyazı: kaynakta bulunan desteklenen altyazılar ve Kapalı. Media3 tarafından çözülen metin/bitmap altyazılar SubtitleView ile çizilir.
- Kaynak seçenek sunmuyorsa hayali çözünürlük veya dil eklenmez. Platformun kendi uygulamasındaki bütün seçenekleri AirPlay ile göndermesi garanti değildir.
- Seçimler medya kimliği, oynatıcı nesli ve gerçek parça grubuyla doğrulanır. Video değişince menü kapanır. Durdurulan videonun altyazısı ve parça kataloğu temizlenir.
- Kumanda odağı mevcut seçime gelir. Açık menü sırasında alttaki kontroller otomatik gizlenmez. Geri menüyü kapatır.

Bu değişiklik kimlik doğrulama, native AirPlay protokolü, ekran sesi motoru veya mirroring decoder zamanlamasını değiştirmez. R28 ve R29 arşivleri korunur. İç Android sürümü: 0.1.0-rc30-test / 61.

## Doğrulama

İlk emülatör Media3 testi geçti: gerçek 720p/1080p çıkış, tr/en ses seçimi, çözülen altyazı ve ekranda altyazı pikselleri, Kapalı ve Otomatik, eski/geçersiz seçimlerin reddi. Python host testleri: 57 geçti.

Son x86_64 ve ARM derlemeleri başarılı. Son paket üzerinde emülatör testleri:

| Kontrol | Sonuç |
| --- | --- |
| Gerçek Media3 çıkışında 720p ve 1080p seçimi | Geçti |
| Otomatik kaliteye dönüş | Geçti |
| tr/en ses parçalarının seçimi | Geçti; fiziksel hoparlör duyumu doğrulanmadı |
| HLS WebVTT çözümü, SubtitleView ekran pikselleri, kapatınca temizlenme | Geçti |
| Geçersiz/eski medya seçiminin reddi, durdurunca kataloğun temizlenmesi | Geçti |
| Üç menüde mevcut seçime odak, OK ve Geri | Geçti |
| 10 ardışık video, iki ses seçimi, canlı HLS | Geçti; 4 canlı playlist erişimi |
| Döndürme 0/90/180/270/0 x3 | Geçti; 15 gerçek yüzey piksel kontrolü |
| Python host testleri | 57 geçti |
| Uzun süreli bellek testi | Çalıştırılmadı; regresyonun canlı bölümü yalnız 12 saniye |
| Gerçek iPhone / YouTube ses-altyazı-kalite kabulü | Çalıştırılmadı |

Emülatör görüntüleri uygulamanın `files/track-options/` dizininde: menu-1.png, menu-2.png, menu-3.png, subtitles-on.png, subtitles-off.png. Görsel piksel testi otomatik yapıldı; tasarımın insan değerlendirmesi yerine geçmez.

APK dosyaları:

- `artifacts/releases/AirRelay-R30-track-options-ARM-TV.apk` — SHA-256 `55D7D5639B1FC89B6EBCFD4A0EF2A17364A8B24DED626C3727F0CA7CCD944D2B`
- `artifacts/releases/AirRelay-R30-track-options-x86_64.apk` — SHA-256 `76DD01B1D6A143E1E3A0188832787360D6F9754B8EB785B1A93D8DFEB1FBBF2A`

ARM APK imzası doğrulandı. İçindeki 12 native .so girdisinin SHA-256 değerleri R29 rotation-all-angles ARM paketiyle birebir aynı. Media3 sürümü değiştirilmedi; aynı sürümün altyazı görünümü bağımlılığı eklendi.

TV 192.168.31.131:5555 üzerinde kullanıcının temiz kurulum talimatıyla kuruldu ve açıldı. Ayarlar, izin seçimleri ve eşleştirmeler sıfırlandı. Android paket sorgusu versionCode 61 / 0.1.0-rc30-test doğruladı. Emülatör de güncellendi. TV'de gerçek video kabul testi henüz yapılmadı; sürüm test adayıdır. Bu belge eski sürümlerin kabul kanıtlarını değiştirmez.
