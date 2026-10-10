# R32 kayıt kitaplığı

Ana menüye bağımsız Kayıtlar sekmesi eklendi. Yayın olmadan kayıtlar izlenebilir. Her kayıt başlangıç tarihi ve saati, süre, dosya boyutu ve çözünürlükle gösterilir. Bilgiler IO iş parçacığında okunur. Bozuk kayıt listede kalır ve silinebilir.

Yerel MP4 oynatıcı Media3 kullanır; oynat/duraklat, 10 saniye sarma, süre çubuğu ve kumanda kontrolü sunar. Ekrandan ayrıldığında decoder ve ses kaynakları serbest bırakılır. AirPlay oyuncusu ayrı kalır. Aktif yayın sırasında yerel kayıt oynatma devre dışıdır.

Silme onaylıdır ve sadece uygulamanın kayıt klasöründeki bitmiş MP4 dosyalarına uygulanır. Dışa aktarılan kopyalar bağımsızdır, kitaplıktaki dosyanın silinmesi onları silmez. Dışa aktarılan dosyalar TV'nin dosya yöneticisiyle yönetilebilir. Bir saat/2 GB sınırı kayıt oluşturma sınırıdır; kaydedilmiş dosyalar için süre sonu veya otomatik silme yoktur.

Kurulum güncelleme şeklindedir; mevcut kullanıcı kayıtları korunur. Kullanıcı kayıtlarını bilgisayara topluca yedekleme otomatik onay denetimi tarafından reddedildi, bu aktarım yapılmadı. Test için APK içindeki yerel video kullanıldı.

Doğrulama:
- ARM32/ARM64 APK ve Android test APK derlemesi başarılı.
- Xiaomi TV üzerinde bağımsız Kayıtlar sekmesi açılışı geçti.
- Test MP4 dosyasıyla gerçek Media3 hazırlama/oynatma, duraklatma ve sarma geçti; kapanışta player release edildi.
- Metadata okuma, uygulamaya ait test dosyasını silme, klasör dışına çıkan dosya adı reddi ve asıl test dosyasının korunması geçti.
- Bu test kişisel kayıtları kopyalamaz; APK test videosundan kendi geçici dosyalarını üretir ve temizler.
- APK imzası doğrulandı. R32 (67) kullanıcı verileri silinmeden kuruldu.
- Kullanıcının kendi kaydıyla kumanda kabulü bekliyor.

APK: artifacts/releases/AirRelay-R32-recording-library.apk
Test: artifacts/r32-library-test.txt


SHA-256: 950E6298F4AAE65AD1B7B527E913C392345FCD46788CCCC96A117C6A18EC7D77
