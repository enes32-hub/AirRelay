# Kullanıcı arayüzü RC2 — 10 Eylül 2026

Bu teslim marka seçimi ve gerçek cihaz kabulü öncesi test sürümüdür; mağaza sürümü değildir. Paket kimliği ve eşleştirme verileri korunur. Geçici görünen marka Velora Cast olarak kalır.

## Değişiklikler

- Dört tema: önceki Classic, mevcut Amber, Beyaz, Karanlık. Önceki palet kaynak yedeğinden geri alındı.
- Sistem temasını izle varsayılan açıktır: koyu sistem Karanlık, aydınlık sistem Classic seçer. Beyaz otomatik seçilmez. Elle tema seçimi sistem izlemeyi kapatır.
- Üretilen ad artık model/SDK metni içermez: Velora-Cast-5E4D81AF biçimi; kurulumun benzersiz eki korunur.
- PIN yalnız görünümde 1500 ms geciktirilir; güvenlik önceliği ve iptal işlemi hemen geçerlidir.
- Ana başlık ve yardım metinleri sadeleştirildi; cihaz dili iPhone, iPad ve Mac kullanımını kapsar.
- Destek günlüğü menüsü/paylaşımı ve ayrıntılı günlük seçenekleri kaldırıldı. Uygulamanın destek dosyalarına olay yazımı kapatıldı; eski destek dosyaları temizlenir. Android sistem Logcat kayıtlarının tamamen kapandığı iddia edilmez.
- Yayın metriklerini göster seçeneği eklendi, varsayılan kapalı. Yansıtmada giriş FPS/veri hızı; uzak videoda kaynağın bildirdiği FPS/nominal bitrate ve tampon gösterilir. Bunlar ekran FPS veya uçtan uca gecikme ölçümü değildir. Teknik bilgiler diske kaydedilmez.
- Gelişmiş seçeneklerde var olan uzak video bitrate tercihi korunur; göndericinin mirroring bitrate'ini zorlayan evrensel kontrol değildir.

## Doğrulama

Kanıt dizini: artifacts/user-polish-20260910.

| Kontrol | Sonuç |
|---|---|
| x86_64 debug ve ARM32/ARM64 debug derleme | GEÇTİ |
| TV APK imzası, API24+, Leanback, iki ARM ABI | GEÇTİ |
| Görünüm politika testleri | GEÇTİ: 18 |
| İsim/port yaşam döngüsü host testleri | GEÇTİ: 25 |
| Android test grupları | GEÇTİ: 22 |
| Dört tema gerçek UI/piksel kontrolü | GEÇTİ |
| PIN gecikmesi/iptali | GEÇTİ; son test gözlemi 1800 ms, politika 1500 ms |
| Gerçek decoder çözünürlük geçişleri | GEÇTİ: 15 geçiş ayağı |
| Yerel uzak video/ses seçimi/HLS | GEÇTİ: 10 video, 2 ses seçimi, 5 canlı liste isteği |
| Gerçek MediaCodec/EGL yerel AVC | GEÇTİ: 3 oturum; giriş düşümü 0 |
| Yerel native gönderici sarma ve EOS kontrol soketi | GEÇTİ |
| Fiziksel TV, gerçek iPhone, duyulan ses ve gerçek YouTube | ÇALIŞTIRILMADI |

İlk Android koşusunda ağ durumunun erişilebilirlik metni beklemesi başarısız oldu. Ekran görüntüsünde beklenen metin vardı; test erişilebilirlik düğümleri yenilenerek tekrar çalıştırıldı, bütün gruplar geçti. İlk başarısız günlük ve ekran silinmedi. Host derlemesinin ilk denemesi yanlış java/kotlin kaynak yolu nedeniyle çalışmadı; doğru kaynak yoluyla 25 kontrol geçti. Ek native sarma denemeleri önce alıcı kapalıyken yanıt alamadı; ekrandan başlatılıp hazır durumu doğrulandıktan sonra geçti. Hiçbiri geçmiş rapor değiştirilerek başarılı gösterilmedi.

Ana iş parçacığına yapılan 20 hedefli çağrının en uzunu 18.19 ms idi. Bu toplam takılma veya tüm native başlatma süresi ölçümü değildir.

Önceki RC1 bağımsız denetçi onayı bu yeni APK'ya aktarılmaz. Daha önce belgelenmiş gerçek gönderici/DVR sarma sınırı ve erken sarma isteği riskleri bu görünüm çalışmasında çözülmüş sayılmaz. Gerçek telefon kabul senaryoları PHONE_SEEK_ACCEPTANCE_2026-09-10.md dosyasındadır.
