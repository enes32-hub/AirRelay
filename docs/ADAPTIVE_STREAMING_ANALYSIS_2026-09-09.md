# AynaTV: dinamik bitrate, FPS ve kalite politikası incelemesi

## Sonuç ve kapsam

Mevcut beş profil kullanıcının istediği dinamik uyarlama sistemi değildir.
Çözünürlük/FPS ilan sınırı ve eski çözülmüş kare toleransı seçer. Dengeli ile
Kalite arasındaki fark 180/250 ms toleranstır; daha yüksek çözünürlük veya
bitrate talebi yoktur. Bu nedenle kullanıcıya belirgin kalite farkı vaat etmek
doğru olmaz. Kararlılık ve Düşük gecikme de emülatörün 30 FPS sınırı nedeniyle
720p/30 ilan eder; toleransları 200/100 ms'dir.

Bu belge kaynak incelemesi ve uygulama önerisidir. Yeni APK yüklenmedi, çalışan
oturum değiştirilmedi, yeni performans deneyi yapılmadı. FPS ile kullanıcının
“FFS” ifadesinin saniyedeki kare sayısını kastettiği varsayıldı.

## Kaynakta doğrulanan mevcut davranış

| Bileşen | Mevcut durum | Sonuç |
|---|---|---|
| receiver/StreamProfile.kt | 5 sabit profil; emülatörde frameLimit en fazla 30 | Dinamik kapasite ölçümü yok |
| service/AirPlayService.kt | Profil başlangıçta uygulanıyor | Kaydetmek devam eden gönderici akışını yeniden müzakere etmiyor |
| native raop.c, raop_handlers.h | width/height/maxFPS/refreshRate, /info ekran yetenekleri | Başlangıç yetenek ilanı; etkin oturumda iPhone'u yeni FPS'ye zorladığı kanıtlanmış değil |
| renderer/VideoRenderer.kt | _updateStats, feedFrame girişinde çağrılıyor | FPS yüzey sunum sayacı değil; giriş callback sayacı |
| VideoRenderer._updateStats | Bir saniye aşılınca byte*8 ve kare sayısı, geçen süreye bölünmeden atanıyor | Yük veya duraklamada ölçüm penceresi uzarsa FPS/Mbps yanlış yorumlanabilir |
| VideoRenderer.drainOutput | Zamanlanmamış modda hazır çıkışlar arasından en yenisi sunuluyor | Gecikmeyi azaltabilir ama görülen kare sayısı düşebilir |
| DecoderResidence | Decoder giriş/çıkış arasında monoton süre | Telefon->ekran toplam gecikmesi ve ağ kuyruğu değil |
| AirPlayService.onVideoData | Yaklaşık 5 saniyede bir, ancak kare geldiğinde sağlık kaydı | Akış tamamen durunca periyodik gözlem de duruyor |
| AirPlayVideoPlayer | Varsayılan ExoPlayer.Builder; ses track override var | Uygulamaya özgü video ABR politikası/bant genişliği/kalite sınırı yok |

Media3 varsayılan olarak uygun çoklu kalite akışlarında adaptif seçim yapabilir.
Bu, AynaTV'ye özel dinamik bitrate sistemi yazılmış olduğu anlamına gelmez.
Hangi varyantların gerçekten adaptif ve seçilebilir olduğu çalışma anında
Tracks/format değişimleriyle doğrulanmalıdır. Tek varyantta ABR mümkün değildir.

## İki farklı aktarım yolu

### 1. Canlı iPhone ekran yansıtması

iPhone görüntüyü kodlar; alıcı şifrelenmiş/sıkıştırılmış akışı alıp çözer.
Android decoder ayarına bitrate yazmak iPhone encoder bitrate'ini değiştirmez.
KEY_FRAME_RATE da tek başına gerçek gönderici veya sunum hızının değişeceği
garantisi değildir. TCP'yi yavaş okuyarak telefonu zorlamak kuyruğu büyütebilir;
adaptif bitrate olarak kullanılmamalıdır.

Doğrudan yapılabilenler: gerçek çıkış hızını ölçmek, render zamanlamasını ayarlamak,
eski çözülmüş kareleri bırakmak, sınırlandırılmış kuyruk ve kontrollü decoder
toparlaması, ilk bağlantıda kapasiteye uygun tavan ilan etmek.

Araştırılması gereken: mevcut AirPlay sürümünde canlı gönderici FPS/bitrate veya
çözünürlük talebi/geri bildirimi. Kaynak taramasında doğrulanmış bir API bulunmadı.
“AirPlay'de imkânsız” sonucu çıkarılmıyor; uygulanabilirliği henüz kanıtlanmadı.
Başlangıç /info değerlerini değiştirmek bu araştırmanın yerine geçmez. Gizli
yeniden bağlantıyla kalite değiştirilmemeli; açık bir kullanıcı seçeneği gerekirse
“sonraki bağlantıda uygulanır” diye belirtilmeli.

Sunum FPS'sini düşürmek ağ bitrate'ini düşürmez. Çözülmüş kare atlamak decoder'ın
tüm iş yükünü ortadan kaldırmaz. H.264/H.265 referans karelerini gelişigüzel atmak
bozulma/donma yaratabilir; yükün kaynağı decoder ise sadece render politikası yetmez.

### 2. YouTube/Fotoğraflar/Safari ayrı uzak video oynatması

Oynatıcı HLS/DASH gibi çoklu varyantlı bir kaynağı gerçekten alıyorsa Media3 ile
kalite/bitrate tavanı, uygun codec/çözünürlük ve buffer politikası uygulanabilir.
Yalnız kaynağın sunduğu varyantlar seçilebilir; tek MP4'ün bitrate'i kendiliğinden
düşürülemez. Decoder çıkış FPS'sini keyfi 60 yapmak 24/30 FPS videoya yeni kare üretmez.
24/30 FPS içerik için hedef düzgün pacing ve ses senkronudur.

FCUP yolunda playlist erişimi telefon üzerinden, segment indirmesi farklı yoldan
gerçekleşebilir. Loopback playlist aktarım hızını internet bant genişliği sanmak
yanlıştır. BandwidthMeter örnekleri medya segmentleri için ayrılmalı; yerel proxy
ve önbellek kaynaklı örnekler politika girdisi olarak denetlenmelidir.

Kalite değişikliği ses dili seçimini sıfırlamamalı. Video variant değişimi ile
ses track override ayrı tutulmalı. VOD ve canlı/DVR buffer hedefleri ayrılmalı;
VOD'un daha büyük buffer'ı telefon ekranına taşınmamalı.

## Önerilen kullanıcı arayüzü

Beş benzer preset yerine tek adaptif motor, üç anlaşılır öncelik ve gelişmiş sınırlar:

| Öncelik | Amaç | İzin verilen ödün |
|---|---|---|
| Akıcılık | Etkileşim gecikmesini ve kare düzensizliğini azaltmak | Desteklenen aktarımda çözünürlük/bitrate düşebilir |
| Görüntü kalitesi | Kapasite elverdiğinde daha yüksek ayrıntı | Ayrı videoda daha fazla buffer; canlı ekranda sınırsız gecikme değil |
| Dengeli (varsayılan) | Sürekli profil değiştirmeden ikisini dengelemek | Ölçüme bağlı, sınırlı uyarlama |

“Otomatik uyarlama” anahtarı ve gelişmiş ayarlar:
- Ekran yansıtma çözünürlük tavanı: Otomatik / 720p / 1080p.
- FPS tavanı: Otomatik / 30 / deneysel 60. Başlangıç ilanı ve yerel sunum
  hedefi ayrı etiketlenmeli; mevcut emülatör 30 sınırı gizlenmemeli.
- Uzak video kalitesi: Otomatik / azami çözünürlük / gelişmiş azami bitrate.
- Ekran yansıtma bitrate'i: mevcut durumda “Gönderici yönetiyor”; etkisiz slider yok.
- Canlı ekran gecikme tercihi ve ayrı video buffer tercihi ayrı tutulmalı.
- Tanı görünümü: aktarım türü, gelen/çözülen/sunulan FPS, ölçülen Mbps, seçilen
  codec/çözünürlük, buffer, son uyarlama nedeni. Ölçülen değer ile hedef karışmamalı.

“Kararlılık” ayrı bir 720p preset olmak yerine bütün modların zorunlu güvenlik
özelliği olmalı. Özel ayarlar silinmemeli; eski tercihler geçişte korunmalı.

## Adaptif denetim tasarımı

Oturum ve medya nesline bağlı, arayüz iş parçacığından bağımsız bir örnekleyici:
monoton saatte 1 saniyelik ölçüm, 5 saniyelik değerlendirme penceresi önerilir.
Bunlar başlangıç tasarım değerleridir; cihazda kalibre edilmelidir.

Giriş, decoder çıkışı ve yüzeye sunum ayrı sayaçlar olmalı. Mbps hesabı
bytes*8/elapsedSeconds; FPS de gerçek pencere süresine bölünmeli. Sayaçlar sadece
veri geldiğinde değil periyodik güncellenmeli. Surface sunumu ile gerçekten
görüntülenen kare ayrımı testte frame-rendered/EGL gözlemiyle doğrulanmalı.

Karar girdileri: p50/p95 decoder residence, feed kilit/bekleme süresi, çıkış
ilerlemesi, input kuyruğu reddi, dropped/coalesced artışları, frame pacing,
audio underrun/xrun artışları, uzak video rebuffer süresi ve segment throughput.
Sayaçların ömür boyu toplamı yerine pencere içi artışı kullanılmalı.

Örnek durumlar: STARTUP -> STEADY -> PRESSURE -> RECOVERY. Başlangıç, duraklatma,
video geçişi ve sabit telefon ekranı ayrı durum olmalı. Düşük kaynak FPS'si tek
başına yük sinyali değildir. NTP eşlemesi geçersizken uçtan uca gecikme üretilmemeli.

Örnek politika: iki kötü pencerede tek kademe düşüş; 20–30 saniye sağlıklı
çalışmada tek kademe artış; değişiklikler arasında bekleme. Eşikler öneridir,
başarılı test sonucu değildir. Ciddi takılmada kontrollü recovery ayrı tutulmalı.
Her kararda etkilenen kontrol, neden ve önce/sonra değer kaydedilmeli.

Hangi kontrol uygulanır:
- Ağ baskısı + çoklu video varyantı: ABR bitrate/çözünürlük seçimi.
- Decoder baskısı: desteklenen daha hafif video varyantı; canlı ekranda güvenli
  sunum/kuyruk yönetimi ve gerekiyorsa sonraki oturum tavanı önerisi.
- Render baskısı: uygun pacing ve eski çözülmüş çıkış yönetimi.
- Ses açlığı: ses zamanlamasını koru; rastgele FPS/codec değişimiyle gizleme.
- Host CPU baskısı: emülatör ölçümünü cihaz performansı diye raporlama.

## Uygulama sırası ve kabul

1. Önce doğru sayaçlar ve başlangıç baz ölçümü. Şu anda ölçülen FPS'nin yanlış
   anlamıyla algoritma kurulmamalı. Mevcut 30 FPS sınırının nedeni tekrar ölçülmeli.
2. Gerçek kapasite sınırı olan bir adaptif politika modeli; sentetik zaman/sayaç
   testleriyle kararlılık, histerezis, nesil değişimi ve duraklatma doğrulanmalı.
3. HLS çoklu varyant + Media3 ABR politikası; sabit/azalan/artırılan segment hızında
   gerçek seçilen formatı izle. Ses dili, live/DVR ve yeniden bağlanmayı koru.
4. Ekran yansıtma için yerel pacing/kuyruk kontrolü. Telefon bitrate/FPS talebi
   araştırmasını ayrı kapı tut; destek kanıtlanmazsa menüde varmış gibi gösterme.
5. UI sadeleştirmesi ve gerçekten etkili gelişmiş ayarlar.
6. Emülatör 720p/30, 1080p/30, 720p/60 kontrollü karşılaştırma; aynı host yükünde,
   aynı klipte en az üç tekrar. 60 FPS ancak sunulan FPS, p95 gecikme ve ses
   senkronu birlikte iyileşiyorsa etkinleştirilmeli; yalnız ilanı 60 yapmak yetmez.

Test matrisi: sabit ekran/scroll/oyun, video->ekran dönüşü, art arda 10 medya,
tek ve çoklu HLS varyantı, düşük ağ, decoder/host baskısı, kısa kopma, gerçek
bağlantı sonu, en az 10 dakika süreklilik. Yanlış eski callback, ses kaybı,
oturum kapatma ve sürekli kalite değişimi olmamalı. Uçtan uca gecikme için telefon
ve alıcıyı birlikte gösteren sayaçlı görüntü gerekir; decoder residence bunun
yerine geçmez. Xiaomi/ARM testi bu aşamada zorunlu tutulmamalı.

En doğru yakın hedef: **ölçülebilir sunum + gerçek uzak-video ABR + dürüst kontrol
sınırları**. Doğrulanmamış iPhone encoder kontrolünü bu teslimin tamamlanmış
özelliği olarak göstermemek gerekir.
