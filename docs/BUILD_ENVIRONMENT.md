# Geliştirme ortamı

31 Ağustos 2026 incelemesi. [Makine denetimi](../artifacts/environment-report.json) ile resmî sürüm doğrulaması farklı kanıtlardır; kurulu klasör, çalışan toolchain kanıtı sayılmaz.

| Bileşen | Araştırma tabanı | Kaynak / durum |
|---|---|---|
| Android SDK | min 26 / compile 37 / target 37 | API 37.0 revision 2 ve android.jar bulundu |
| AGP | 9.3.2 | [Resmî notlar](https://developer.android.com/build/releases/agp-9-3-0-release-notes); JDK 17 lint düzeltmesi içeriyor |
| Gradle | 9.5.0 | Aynı AGP uyumluluk tablosu; projede wrapper henüz yok |
| JDK | 17 | Microsoft JDK 17.0.20.1 çalıştırıldı |
| Kotlin | 2.4.10 | [Kotlin sürümleri](https://kotlinlang.org/docs/releases.html) |
| Compose for TV | tv-material 1.1.0 | [Kararlı sürüm tablosu](https://developer.android.com/jetpack/androidx/releases/tv) |
| Media3 | 1.11.0 | [Resmî notlar](https://developer.android.com/jetpack/androidx/releases/media3); ham mirroring yolunun yerine ExoPlayer kullanılmayacak |
| NDK | r29 / 29.0.14206865 | [Kararlı sürüm](https://developer.android.com/ndk/downloads); SDK Manager ile kurulum tamamlandı ve iki ARM ABI için gerçek derleme yapıldı |
| CMake / Ninja | 3.22.1 / SDK ile gelen | Özgün `toolchain-probe` C hedefinde iki ARM ABI için çalıştırıldı; alıcı çekirdeği derlenmedi |
| Build Tools | 37.0.0 | SDK içinde klasör bulundu; AAB/APK doğrulaması yapılmadı |
| Android Studio | Quail 3 / 2026.1.3 Patch 1 | [Resmî sürüm](https://developer.android.com/studio/releases); IDE kurulumu bu denetimde doğrulanmadı |
| Python | 3.12.13 | Bu aşamanın yalnız standart kütüphaneli araştırma/QA araçları için |

[libs.versions.toml](../libs.versions.toml) etkin bağımlılık grafiği değil, sabit araştırma tabanıdır. Lisans incelemesi yapılmadan uygulamaya kütüphane eklenmedi. AGP'nin yerleşik Kotlin desteğiyle seçilen Kotlin/Compose eklentisi uyumu Aşama 1'de resmî yönerge ve gerçek derlemeyle doğrulanacak.

Android 17 hedefinde [ACCESS_LOCAL_NETWORK](https://developer.android.com/privacy-and-security/local-network-permission) izni verilmeden ağ iletişimi başlatılmayacak; API kontrolü ve izin iptali ayrı test edilecek. [16 KB sayfa uyumu](https://developer.android.com/guide/practices/page-sizes) NDK sürümüyle kendiliğinden onaylanmaz: ELF/ZIP hizalaması ve gerçek 16 KB çalışma testi gereklidir.

## Yeniden denetleme

```powershell
python scripts/environment_doctor.py --sdk "$env:LOCALAPPDATA\Android\Sdk"
```

Bu makinede `python` Windows Store takma adına gidiyor. Çalışan yerel yorumlayıcı:

```powershell
& 'C:\Users\ASUS\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' scripts/environment_doctor.py
```

Araç kurulum yapmaz, SDK lisansı kabul etmez, cihazlara bağlanmaz veya APK yüklemez. ADB listesindeki kimlikleri rapora yazmaz. ADB'ye bağlı cihaz olmaması evde cihaz olmadığı anlamına gelmez; fiziksel erişim henüz doğrulanmamıştır.

NDK r29 kurulumu tamamlandı. Aşama 1 öncesinde Gradle wrapper resmi dağıtım hash'iyle sabitlenecek, version catalog gerçek bağımlılıklara bağlanacak, dependency locking/verification metadata üretilecek. Bunlar henüz yapılmış gibi raporlanmadı.

## Native araç zinciri denemesi

```powershell
python scripts/probe_toolchain.py --sdk "$env:LOCALAPPDATA\Android\Sdk"
```

[Gerçek komut sonuçları ve hashler](../artifacts/toolchain-probe.json): CMake/Ninja + NDK r29 ile `armeabi-v7a` ve `arm64-v8a` için özgün küçük C kitaplığı derlendi. Her iki ELF'te PT_LOAD hizalaması 16384; ZIP içindeki `.so` veri başlangıçları 16384 ve 32768. Sonuç ayrıca NDK `llvm-readelf` ve SDK `zipalign -c -P 16` ile çapraz kontrol edildi.

Deneme çıktısı `.research/toolchain-probe/` altındadır ve Git dışında tutulur. `layout-16kb.zip` kurulan bir APK değildir; manifest ve uygulama içermez. `verify_native.py` ELF/ZIP biçimini denetler; APK imzası, manifest, dinamik bağımlılıkların tamlığı, decoder işlevi veya gerçek 16 KB cihaz üzerinde çalışma kanıtı sağlamaz. Native runtime kabulü ve AynaTV release ikililerinin denetimi açık kalır.
