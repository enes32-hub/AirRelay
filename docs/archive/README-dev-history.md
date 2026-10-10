# AirRelay

Android TV için AirPlay ekran, video ve ses alıcısı. iPhone, iPad ve Mac ile aynı yerel ağda kullanılır.

## Güncel kaynak durumu — 6 Ekim 2026

R39 yayın öncesi inceleme sürümü: `0.1.0-rc39-test`, versionCode `74`.
Görünen marka **AirRelay**. Mevcut kurulumların güncellenmesi için `com.aynatv.receiver` paket kimliği; JNI ve upstream uyumluluğu için `io.github.jqssun.airplay` ad alanı korunur. Eski isimleri tanıyan geçiş kodları ve üçüncü taraf atıfları bilinçli olarak saklanır.

Son değişiklikler: eski varsayılan alıcı adı/önizlemeler temizlendi, indirme dosyası markası ve seçilen dile bağlı bildirim metinleri düzeltildi; mikrofon donanımı isteğe bağlı tanımlandı; kayıt sesi başlatma hatasında kaynak sahipliği düzeltildi.

Bu kaynak **üretim onayı değildir**. Test APK'sı Android Debug imzalıdır. Üretim imzalı AAB, release cihaz testleri, lisans/mağaza beyanları ve R38 keşif toparlamasının uzun süreli iPhone doğrulaması ayrı kabul işleridir. GPL ve diğer üçüncü taraf lisansları geçerlidir; `legal/` belgelerini koruyun.

Derleme: JDK 17, Android SDK 36, NDK 29.0.14206865, MSYS2 ve Python. `local.properties` içine kendi SDK yolunuzu yazın. Ardından:

```powershell
.\scripts\build_receiver.ps1 -Abis 'arm64-v8a,armeabi-v7a' -BuildRoot C:\AirRelayBuild
```

Aşağıdaki bölüm tarihsel ilk entegrasyon kaydıdır. Eski marka, durum ve test sayıları güncel sürümün durumunu göstermez.

---
> **7 Eylül 2026 — İlk dört aşama kaynak güncellemesi:** Güncel teslim kapsamı, uygulanan değişiklikler ve gerçek doğrulama sınırları için [BASLANGIC_ILK_4_ASAMA.md](BASLANGIC_ILK_4_ASAMA.md) dosyasından başlayın. Aşağıdaki eski emülatör/APK kayıtları önceki sürüme aittir; bu yeni arayüzün derlendiği veya cihazda denendiği anlamına gelmez. Bu teslimde Android/ABI/paketleme (5. aşama), fiziksel kabul ve ticari yayın çalışması yapılmamıştır.

# AynaTV Receiver

> AirPlay çekirdeği entegrasyon adayı seçildi: ayrıntı ve doğrulama durumu için [CORE_SELECTION.md](docs/CORE_SELECTION.md) dosyasına bakın.

**Hedef: iPhone'un sistem Ekran Yansıtma menüsünden, aynı yerel ağdaki Android TV'ye AirPlay ile canlı ekran ve ses aktarmak.** Uygulama yalnız Android TV'ye kurulacak; iPhone'a yardımcı uygulama gerekmeyecek.

**Etkin hedef: şartnamedeki tüm P0 kabul kapılarını geçen nihai Android TV uygulaması ve teslim paketi.** Araştırma ve hazırlık yalnız ara adımlardır; nihai hedef henüz tamamlanmadı.

**Mevcut durum: emülatör alıcı entegrasyonu geçti.** Açık kaynak Android AirPlay alıcı çekirdeği ana `app` modülüne alındı. x86_64 Android TV emülatöründe alıcı servisi, AirPlay/RAOP mDNS ilanı ve D-pad ile başlat/durdur davranışı gözlendi. Uygulamanın yüklenen paket kimliği `com.aynatv.receiver`, görünen adı AynaTV Receiver'dır.

Bu sonuç iPhone görüntü ve ses yansıtmasının kanıtı değildir. iPhone 14 Pro + Xiaomi TV Box 4K fiziksel kabulü, kullanıcı tercihiyle nihai aşamaya bırakılmıştır. GPL-3.0 kaynak, değişiklik, üçüncü taraf bildirimleri ve kriptografik köken denetimi dağıtım öncesi açık kapılardır.

## Hazırlananlar

- [Etkin kapsam](docs/SCOPE.md): kullanıcının yalnız Android TV / iPhone kararı; ana şartnameye de işlendi.
- [Çekirdek ve lisans kararı](docs/DEPENDENCY_AND_LICENSE_DECISION.md): üç aday, sabit commit'ler, açık köken riskleri. Hiçbiri üretim için onaylanmadı.
- [Mimari tasarım](docs/ARCHITECTURE.md): Android TV alıcı hizmeti, AirPlay sınırı, MediaCodec/SurfaceView ve AudioTrack.
- [Emülatör doğrulaması](docs/EMULATOR_TESTS.md): debug APK kurulumu, ilk açılış, D-pad ile foreground servis başlatma testi. Bu bir AirPlay veya fiziksel TV kabulü değildir.
- [Native kaynak incelemesi](docs/research/NATIVE_AUDIT_FINDINGS.md): yedi sabit depodan 185 dosyanın envanteri; çekirdeğin kendi hukuk belirsizliği beyanı ve ek bağımlılık sorunları.
- [Ek çekirdek yolları](docs/research/ALTERNATIVE_CORES.md): ek açık depolar ve ticari SDK belgeleri; henüz kullanım hakları ve gerçek cihaz sonucu doğrulanmış seçenek yok.
- [Cihaz çifti](docs/DEVICE_COMPATIBILITY.md): iPhone 14 Pro + Xiaomi TV Box 4K; fiziksel kabul henüz yok.
- [Gereksinim matrisi](REQUIREMENTS_TRACEABILITY.csv): 33 üst seviye P0 ve 69 performans/ağ kabul kaydı, tümü `NOT_RUN`.
- [Test planı](docs/TEST_PLAN.md): 58 test tanımı; fiziksel spike, ölçüm ve kanıt kuralları.
- [Hazırlık araçlarının test raporu](artifacts/preparation-tests.json): 44 birim test geçti. Bu sonuç yansıtma kabulü değildir.

## Android derleme

Windows üzerindeki FFmpeg/OpenSSL araçları Unicode çalışma yolunda güvenilir olmadığı için derleme, kaynakları geçici bir ASCII yola kopyalayan betikle yapılır. Bu iPhone veya TV'ye bağlanmaz:

```powershell
.\scripts\build_receiver.ps1 -PythonExe 'C:\Python\python.exe'
```

Varsayılan hedef Android TV emülatörü için `x86_64`'tür. ARM dağıtım denemesi için:

```powershell
.\scripts\build_receiver.ps1 -Abis 'arm64-v8a,armeabi-v7a' -PythonExe 'C:\Python\python.exe'
```

## Hazırlık denetimleri

Python 3.11+ ve standart kütüphaneleri yeterlidir. pip bağımlılığı yok.

```powershell
python scripts/run_checks.py
python scripts/environment_doctor.py
python scripts/check_readiness.py --output artifacts/readiness-report.json
python scripts/check_readiness.py --release --output artifacts/release-readiness.json
```

Bu makinedeki çalışan Python yolu [ortam belgesinde](docs/BUILD_ENVIRONMENT.md). `--release` şu an **2** koduyla reddetmelidir; hazırlık tutarlılığı başarılı olsa da hiçbir P0 işlevi geçmiş değildir.

Araştırma hashlerini yenilemek için `python scripts/inspect_candidates.py` kullanılır. Bu komut yalnız açık GitHub kaynaklarına araştırma amaçlı gider; gelecekteki Android TV uygulamasının ağ davranışı değildir. Aday kodunu çalıştırmaz ve kaynak/kripto verisini diske kaydetmez. Araştırma çıktısının zamanı değişir; eski karar kanıtı güncellenmeden önce Git ile korunmalıdır.

## Sıradaki gerçek geliştirme kapısı

Ana şartname 5 ve 42 gereği önce protokol adayının dosya/lisans/kripto köken incelemesi ve gerçek iPhone → Android TV bağlantı spike'ı geçmeli. Lisans dosyasının varlığı, README'deki destek iddiası veya yalnız derleme yeterli değil. [Açık riskler ve işler](docs/NEXT_STEPS.md) bu sırayı korur.

Başka platform uygulaması, Cast/Miracast/DLNA, bulut, reklam, telemetri veya DRM aşma eklenmeyecek. Ana gereksinim belgesi [şartnamedir](Android_TV_iPhone_Ekran_Yansitma_Alicisi_AI_Ajan_Ana_Sartnamesi.md); [kapsam düzeltmesi](docs/SCOPE.md) eski geniş platform ifadelerinden önceliklidir.


Geliştirme SBOM'u: legal/SBOM.spdx.json. Dağıtım öncesi lisans kapısı hâlâ açıktır.


