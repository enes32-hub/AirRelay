# R29: 180 derece düzeltmesi

Önceki teslimde hata: `rotation % 180 != 0` hem en/boy değişimi hem video yüzeyi seçimi için kullanılmıştı. Bu nedenle 180 derecede SurfaceView'e geri dönülüyor, görüntü ters dönmüyordu. Önceki derleme ve durum testleri gerçek dönüşü kanıtlamıyordu.

Düzeltme: Yüzey seçimi `rotation % 360 != 0`; en/boy hesabı 90 ve 270 derecede ters çevrilmeye devam eder. 180 derecede TextureView korunur. Normal 0 derece SurfaceView kullanır.

## Gerçek ekran pikselleriyle doğrulama

`RotationSurfaceProbe` doğrudan üretimdeki FittedReceiverSurface bileşenini kullanır. Surface üzerine dört farklı renkli köşe çizer ve Android ekran görüntüsündeki piksel konumlarını denetler. Düğmenin metnine veya seçili durumuna bakarak başarılı saymaz.

Emülatör emulator-5584: 0 → 90 → 180 → 270 → 0 sırası üç kez; 15 piksel kontrolü geçti. Yüzey türü değiştirilip yeniden bağlandığında 0 dereceye dönüş de doğrulandı. Ekran görüntüleri emülatörde uygulamanın files/rotation-pixels klasöründe.

Bu test çizilmiş test görüntüsü kullanır; iPhone, şifreli AirPlay, decoder akıcılığı veya ses kabul testi değildir. TV/iPhone kabulü kullanıcı tarafından yapılmalıdır.

x86_64 APK: artifacts/releases/AirRelay-R29-rotation-all-angles-x86_64.apk
SHA-256: 23732B5B2790EFD0C9E3995D79D1FBD9315619F4B98048C47D5DC593E8A5E2A8

## Gerçek Xiaomi kutusu

192.168.31.131:5555 üzerinde aynı test geçti: 0/90/180/270/0 x3 = 15 piksel kontrolü. Emülatörle toplam 30 kontrol. Bu, gerçek TV cihazının görüntü birleştirme katmanındaki doğrulamadır; hâlâ çizilmiş test deseni kullanır, iPhone video/ses kabulü değildir.

ARM APK: artifacts/releases/AirRelay-R29-rotation-all-angles-ARM-TV.apk
SHA-256: 265615C7EFE4ABC6733A4D41BC20FE6BBC059FDDE61F0E2694ED3D97FE5E69C7
İmza ve ARM derleme başarılı. Test APK'sı TV'den kaldırıldı. Uygulama önceki temiz kurulum talimatıyla yeniden kuruldu ve açıldı; tercihler/eşleştirmeler sıfırlandı. Temiz kurulum TV uygulamasındaki test görüntülerini de kaldırdı; emülatör görüntüleri files/rotation-pixels konumunda kalır.
