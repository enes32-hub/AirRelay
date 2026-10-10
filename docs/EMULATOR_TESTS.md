# Emülatör doğrulama kaydı

1 Eylül 2026. Günlük geliştirme yalnız Android TV emülatöründe yapılır; bu kayıt fiziksel Xiaomi TV kabulünün yerine geçmez.

| Kontrol | Sonuç | Kanıt |
|---|---|---|
| Temiz kaynak native derleme | Geçti | UxPlay, FFmpeg, OpenSSL, libplist ve JNI ile x86_64, ARM64 ve ARM32 `libairplay_native.so` temiz ASCII çalışma yolunda CMake/Ninja ile üretildi. |
| Debug APK paketleme | Geçti | PIN güvenlik güncellemesi: `artifacts/aynatv-receiver-x86_64-pin-default-debug.apk` SHA-256 `DE9E3BA3…C312BA6F`; ARM: `artifacts/aynatv-receiver-arm-pin-default-debug.apk` SHA-256 `C6D205BF…8B58706E`. |
| Paket kimliği ve TV başlatıcısı | Geçti | `com.aynatv.receiver`, `AynaTV Receiver`, Leanback başlatıcısı ve x86_64 native kitaplıkları APK içinde doğrulandı. |
| Uygulama açılışı | Geçti | `io.github.jqssun.airplay.MainActivity` AynaTV paketi altında emülatörde odağa geldi. |
| D-pad / OK ile başlatma | Geçti | `KEYCODE_DPAD_CENTER` ardından ön plan `AirPlayService`, `isForeground=true`, `startRequested=true` görüldü. |
| AirPlay ve RAOP keşfi | Geçti | Native alıcı 7000 portunda başladı; Android NSD `_airplay._tcp` ve `_raop._tcp` kayıtlarını `AynaTV Receiver` adıyla ilan etti. |
| ARM32/ARM64 ve 16 KB hizalama | Geçti | ARM APK, armeabi-v7a ve arm64-v8a için tüm native kitaplıkları içeriyor. zipalign -c -P 16 -v 4 → Verification successful. |
| D-pad ile durdurma ve yeniden başlatma | Geçti | Aynı orta tuş durdurmada `startRequested=false`, yeniden başlatmada `lastStartId=2` ve iki mDNS kaydı yeniden görüldü. UI kanıtları: `docs/emulator-clean-source-stopped-window.xml`, `docs/emulator-clean-source-restarted-window.xml`. |
| iPhone-emülatör ağ köprüsü | Hazır, fiziksel iPhone denemesi bekliyor | Test köprüsü 192.168.31.32 üzerinde TCP 7000/7001/7100 ve UDP 6000/6001/7011 dinliyor; TCP 7000 yerel bağlantısı ve altı geçici Emulator Console yönlendirmesi doğrulandı. Windows'ta UDP 5353 birden çok sistem mDNS kullanıcısı tarafından paylaşıldığından köprünün kendi duyurusunu ayrı yerel dinleyicide görmek güvenilir bir kanıt değildir. iPhone keşfi henüz yapılmadı. Windows güvenlik duvarı kuralı için açık onay bekleniyor. |
| Güvenli PIN varsayılanı | Geçti | Temiz uygulama verisiyle AirPlay ve RAOP mDNS ilanlarında pw=true görüldü. Rastgele PIN üretimi başarısız olursa alıcı sabit PIN kullanmadan başlatmayı reddeder. |

Bu kanıt iPhone bağlantısını veya akışın görüntü/ses/senkron kalitesini ölçmez. Gerçek Wi-Fi keşfi, iPhone 14 Pro ekran yansıtması, Xiaomi donanım codec davranışı, ARM paketleri ve 16 KB uyumu sonraki kapılardır.

Windows çalışma dizini Türkçe karakter içerdiği için derleme betiği kaynakları ASCII geçici çalışma yoluna alır. Bu yalnız Windows derleme uyumluluğudur.




