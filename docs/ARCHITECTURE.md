# Android TV alıcısı mimari tasarımı

Tasarım durumu: uygulanmadı; seçilmiş protokol bağımlılığı yok. Hedef yalnız iPhone sistem AirPlay yansıtması → Android TV. Buradaki modüller oluşturulmuş uygulama kodu gibi değerlendirilmemelidir.

## Veri yolu

```mermaid
flowchart TB
  I[iPhone: Denetim Merkezi / Ekran Yansıtma] --> N[Aktif yerel ağ arabirimi]
  N --> D[receiver-discovery: mDNS ilanı]
  N --> C[receiver-native: incelemesi geçilmiş AirPlay çekirdeği]
  C --> S[receiver-session + receiver-security: PIN / tek oturum]
  C --> V[media-video: sınırlı H.264 kuyruğu / MediaCodec]
  V --> SV[Android TV SurfaceView]
  C --> A[media-audio: doğrulanmış ses decoder / AudioTrack]
  C --> T[media-sync: monotonic oturum saati / jitter / A-V drift]
  T --> V
  T --> A
  U[app-tv: Compose for TV / D-pad] --> UC[core-domain: use-case ve durum]
  UC --> R[receiver-service: izin / ağ / kaynak sahipliği]
  R --> D
  R --> C
```

mDNS alıcıyı listede gösterir; tek başına gerçek mirroring sağlamaz. Keşif ancak izin, ağ, dinleyici ve doğrulanmış protokol yetenekleri hazırsa ilan edilir. Sahte TXT kabiliyetleriyle iPhone'a çalışmayan özellik sunulmaz. Desteklenmeyen protokol adımları tahminle üretime yazılmaz.

## Sahiplik ve sınırlar

| Sorumlu | Sahip olduğu kaynak / kapanış |
|---|---|
| ReceiverService | Oturum scope'u, network callback, foreground bildirim; durdurmada idempotent kapanış |
| Discovery bileşeni | Yalnız aktif Ethernet/Wi-Fi ilanı; izin/ağ kaybında unregister ve multicast kilidini bırakma |
| Session bileşeni | Tek aktif gönderici, sınırlı istek/parser ve süre aşımı; ikinci gönderici otomatik devralamaz |
| Security bileşeni | SecureRandom PIN, hız sınırı ve Keystore kimliği; oturum anahtarı kapanışta temizlenir |
| Native adaptör | Socket/byte buffer sahipliği ve JNI callback kontratı; callback UI thread'ini bloklamaz |
| Video bileşeni | Donanım MediaCodec ve sınırlı frame kuyrukları; Surface kaybında kontrollü durdur/yeniden oluştur |
| Audio bileşeni | Codec, AudioTrack ve ses odağı; hatalı PCM güvenli susturulur |
| Sync bileşeni | Gönderici saatinden monotonic saate dönüşüm; sınırlı jitter buffer ve ölçülen drift |
| Diagnostics | Sınırlı yerel ring buffer ve sayaçlar; medya/PIN/anahtar içermez |

UI, native callback veya medya byte dizisini almaz. StateFlow/use-case üzerinden durumu gösterir. Domain Android framework sınıflarına bağımlı olmaz. Şartnamedeki `DiagnosticsRepository` örneğinde Android `Uri` bulunması bu ilkeyle çelişir; uygulamada URI platform adaptöründe tutulacak, domain'e platformdan bağımsız dışa aktarma hedefi verilecek.

## İzin ve durum akışı

Başlat → izin kontrolü → aktif yerel ağ kontrolü → protokol dinleyicisi → ilan → hazır → PIN → kimlik doğrulama → müzakere → canlı görüntü/ses. İzin reddi veya kaldırılması ilan/soket açmaya devam etmek için istisna değildir; kaynaklar bırakılır ve kullanıcıya izin durumu gösterilir.

Arka planda hazır bekleme varsayılan kapalıdır. Kullanıcı etkinleştirirse Android sürümünün foreground service kuralları uygulanır; platform kısıtlarını aşan boot/start hilesi yapılmaz. Kullanıcı alıcıyı kapatırsa otomatik olarak tekrar açılmaz.

PIN varsayılan açık; güvenilen cihaz ekleme yalnız başarıyla doğrulanmış bağlantı sonrası TV kullanıcısının açık seçimiyle. Aynı ağdaki herkes güvenilir sayılmaz. Açık Wi-Fi/LAN adresine bind etmek tek başına WAN yasağını kanıtlamaz; adres/arabirim politikası ve kontrollü dış ağ denemeleri ayrıca doğrulanır.

Başlangıçta donanım yetenekleri gerçek MediaCodecList sorgusundan alınır. H.264 → SurfaceView temel yoldur; ses AAC-ELD/AAC-LC ve gereken ALAC formatıyla AudioTrack'e aktarılır. Ses/video duvar saatiyle eşzamanlanmaz. Kuyruk boyutu, kurtarma sayısı ve bekleme süresi sınırsız olamaz. Kesin tampon değerleri gerçek spike ölçümüyle seçilecek; bu aşamada performans uydurulmadı.

Bu ilk uygulamada iPhone gönderici uygulaması, HLS/URL oynatma, kayıt, uzaktan kontrol, Cast, Miracast veya başka platform arayüzü geliştirilmeyecek.
