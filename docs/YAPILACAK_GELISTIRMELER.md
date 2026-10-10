# Yapılacak geliştirmeler ve sürüm sırası

## R30 — doğrudan video seçenekleri (test adayı)

Kullanıcının yeni talebiyle Kalite / Altyazı / Ses kontrolleri ekleniyor. Yalnız kaynağın sağladığı seçenekler kullanılır. Ayrıntı: `R30_DIRECT_VIDEO_TRACKS.md`. R28 kilitli paketi ve R29 dönüş paketi korunur; bu yeni aday kullanıcı kabulü olmadan kararlı ilan edilmez. Aşağıdaki önceki teslim notları tarihsel kayıttır.

## R28 — kilitli

15 Eylül 2026 tarihinde kullanıcı tarafından kabul edildi. Kilitli teslim: `artifacts/releases/AirRelay-R28-ARM-TV.apk`.
SHA-256: `36E8B300174E3C50FBCF7F2E804AC6862407C31A981D6535529E0889B10CC0BE`.

Bu dosya TV'ye yüklenen son paketin sabit kopyasıdır. İç Android sürüm etiketi tarihsel olarak `0.1.0-rc25.1-test` / `versionCode 60` kalır; R28 ürün sürüm adı ve geri dönüş noktasıdır. Bundan sonraki değişiklikler bu dosyayı değiştirmeyecek; R29 ve sonrası ayrı paketler olarak üretilecek.

En son TV adayı: AirRelay-R28-mode-status-ARM-TV.apk; gerçek aktarım bilgisi ve doğrudan oynatma açıklaması eklendi. R28_DIRECT_MODE_OBSERVATION.md incelemesi geçerlidir. Modlar arasında otomatik geçiş kabulü henüz tamamlanmadı. Diğer özellikler bekliyor.

Son ek talimat: İlk hata yardımı ve beş kez vurgu, kullanıcı tarafından R28 adayına ayrıca istendi ve uygulandı. Güncel aday `AirRelay-R28-help-ARM-TV.apk`; ayrıntı ve testler `R28_PLAYBACK_HELP.md`. Önceki aday silinmedi. Altıncı madde artık uygulanmış, gerçek TV/iPhone kabulü bekleyen düzeltmedir. Diğer özelliklere başlanmadı.

15 Eylül 2026: Son TV kurulumu R28 adayı olarak ayrıldı. Kullanıcı hatanın giderildiğini onaylamadan kararlı R28 sayılmayacak. Yeni özellik eklenmeyecek ve TV kurulumu değiştirilmeyecek.

Teslim: artifacts/rc25-direct-stall/AirRelay-R28-ADAY-ARM-TV.apk
İç sürüm: 0.1.0-rc25.1-test / versionCode 60. Test edilen dosya yeniden derlenmeden kopyalandı.
SHA-256: A1C05FDE8B80501F0EFE693BAD7615093B2DA896BD87C6A84FA55DC24FFCDBAD

Kabul: Başarısız doğrudan videodan ekran yansıtmaya dönüş ve telefon ana ekranının tekrar gelmesi kullanıcı tarafından doğrulanacak. Onay sonrası bu içerik R28 geri dönüş noktası olarak sabitlenecek. Sürüm etiketi değiştirilerek yeniden derlenirse teslim farkı açıkça belirtilecek.

## Yapılacak geliştirmeler

1. Yayını video olarak kaydetme. Taslak sessiz kayıt; sesli kayıt henüz doğrulanmış değil. Çalışan oynatma motoruna etkisi sınanacak.
2. 720p / 1080p kayıt kalitesi seçimi. Kayıt özelliğine bağlı; TV kaynak tüketimi ölçülecek.
3. Kaydı dışarı aktarma. Kayıt özelliğine bağlı; depolama ve izin akışı sınanacak.
4. Cihaz giriş kontrolü ve engelleme. Mevcut engelleme incelenecek, tekrar istek ve kabul davranışı doğrulanacak. Göndericinin ağa paket yollaması tamamen önlenebilir denmeyecek.
5. Akıllı video kalitesi: doğrudan oynatılan video zorlanınca kalite azaltma, toparlayınca artırma. Kararlı motoru bozuyorsa eklenmeyecek ve nedeni açıklanacak.
6. Erişim reddi hatasında Oynatma şekli düğmesinin en az beş kez vurgulanması. Her hata için yalnız bir kez; menüye girip çıkınca yeniden başlamayacak.

R28 kabul edildi. Kullanıcı hangi maddeyi seçerse ilk uygulama R29, sonraki R30 vb. olacak. Maddeler topluca bir sürüme konmayacak. Yeni sürüm kabul edilmeden diğerine geçilmeyecek.

## R29 — test bekliyor

Güncel düzeltme: `AirRelay-R29-rotation-all-angles-ARM-TV.apk`. Önceki R29 paketlerinde 180° dönüş eksikti. Ayrıntı: `R29_ROTATION_PIXEL_VALIDATION.md`. Emülatör ve gerçek Xiaomi'de dört açı ile 0'a dönüş toplam 30 piksel kontrolünden geçti. Kullanıcı iPhone kabulü bekleniyor; aşağıdaki ilk R29 kaydı tarihsel teslimdir.

Teslim: `artifacts/releases/AirRelay-R29-rotation-end-stream-ARM-TV.apk`.
SHA-256: `D7AA6F1DA2AA85A39BBA0573CAD88C6BC7D854275607775205653C82B80AC4DF`.

- Kontrol çubuğunda Döndür (0°, 90°, 180°, 270°) bulunur. Yatay dönüşte yüzey ölçüsü ters oranla hesaplanır; bu yalnız alıcı görünüm dönüşüdür, iPhone ekran yönünü değiştirmez.
- Yayını bitir, yerel görüntü/sesi kapatır; alıcının sunucusu ve AirPlay ilanı açık kalır. Yeni bağlantı için alıcıyı yeniden başlatmaz.
- Emülatör sentetik bağlantı testi alıcının/ilanın açık kaldığını doğruladı; 57 Python kontrolü geçti. Gerçek iPhone ile kullanıcı kabulü bekliyor.
