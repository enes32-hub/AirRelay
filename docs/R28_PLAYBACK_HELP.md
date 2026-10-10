# R28 adayı: oynatma hatasında ilk yardım

Kullanıcı 15 Eylül 2026 testinde yönlendirmenin görünmediğini bildirdi. R28 henüz kararlı kabul edilmedi. İstenen küçük arayüz düzeltmesi aynı aday üzerinde uygulanıyor; diğer özellikler bekliyor.

- İlk doğrudan oynatma hatasında açıklama ve Anladım düğmesi.
- Anladım veya Geri ile kapatılan yardım uygulama tercihlerinde hatırlanır; sonraki hatalarda yeniden açılmaz. Uygulama verilerini temizlemek bu tercihi sıfırlar.
- Her yeni oynatma hatası için Oynatma şekli düğmesinde beş yavaş vurgu. Azaltılmış hareket açıksa yanıp sönmek yerine sabit vurgu.
- Menü/diyalog dönüşü aynı hatanın animasyonunu yeniden başlatmaz. Kullanıcının kontrolü açması animasyonu kesebilir; gezinme kilitlenmez.
- İlk kareyle hata yazısı temizlense bile hata kimliği korunur; yönlendirme kaybolmaz. Kod 5003 (elle ekran moduna dönüş) yeni oynatma hatası sayılmaz.
- PIN ve ilk kurulum pencereleri önceliklidir.
- Oynatma, codec, ses, ağ ve kimlik doğrulama yollarına yeni davranış eklenmedi. Servise yalnız arayüz hata kimliği eklendi.

## Doğrulama

- 57 Python kontrolü geçti.
- x86_64 uygulama ve Android test derlemesi geçti.
- Android playback-help testi geçti: ilk yardım, ilk kareyle hata temizlense de yönlendirme, iki ayrı hatada beşer vurgu, seçim penceresinden geri dönünce sıfır tekrar.
- İlk sayım denemeleri başarısızdı: test Android Compose düğmesinin seçili durumunu yanlış okuyordu. Arayüz ağacı incelendi; düğmenin kardeş semantics öğesi ve stateDescription içindeki Selected/Seçili bilgisi okunarak düzeltildi. Ürün animasyonu test geçsin diye azaltılmadı.
- Android return-error regresyonu geçti. Her iki Android kontrolü sentetik hata/callback kullanır; gerçek iPhone video kabulü değildir.
- ARM32/ARM64 derleme ve APK imzası geçti. Önceki R28 adayının tüm native kütüphaneleriyle byte eşitliği doğrulandı.

Teslim: artifacts/rc25-direct-stall/AirRelay-R28-help-ARM-TV.apk
SHA-256: 17A440102F310FE8498F2A9FDA0AF9EEB5DC0E6C2ECBDD4ED0FEB7E3016E7CC5
İç sürüm etiketi 0.1.0-rc25.1-test / 60. R28 kararlı onayı hâlâ kullanıcıda. Önceki aday APK aynı dosya adıyla korunuyor.
