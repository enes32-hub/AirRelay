# Ortak bilgisayar yükü: WSL / topas

Kullanıcı başka projesindeki fragmanların da aynı dönemde kasmaya başladığını bildirdi. Uygulama değişiklikleri durdurulup yalnız sistem sayaçları okundu; hiçbir süreç durdurulmadı ve sistem ayarı değiştirilmedi.

## Ölçülenler

- CPU: Ryzen 7 9700X, 16 mantıksal işlemci. Toplam kullanım iki ayrı okumada %99 ve %98.
- Windows vmmemWSL süreci: PerfProc sayaçlarında %1234 ve %1378. Bu tek çekirdek ölçeğidir; yaklaşık 12.3–13.8 çekirdeklik yük, makinenin yaklaşık %77–86 kapasitesi.
- WSL Ubuntu-24.04 içinde PID 2163, `topas`: `ps` çıktısında %1343 CPU; yaklaşık 2 saat 23 dakikadır çalışıyor. Parent PID 347 `python`. ps yüzdesi süreç ömrü ortalamasıdır; Windows sayaçlarıyla beraber sürekli ağır yük için güçlü kanıt.
- NVIDIA RTX 5060 Ti: %5 GPU yükü, 32 °C, 2465/16311 MiB VRAM. Bu örnekte GPU doygunluğu görülmedi. GPU sıcaklığı CPU sıcaklığı değildir; CPU termal durumu ölçülmedi.
- RAM yaklaşık 31.2 GiB, boş yaklaşık 7.3–7.5 GiB. Bellek sayfalama/disk okuma sayaçlarında o anda baskı görülmedi. Commit %86; RAM darboğazının kesin yokluğu iddia edilmiyor.
- Güç planı Yüksek performans.
- AynaTV emülatörü PID 25096, diğer proje emülatörü PID 23660. İkisi de durdurulmadı.
- AVD dosyasında hw.gpu.enabled=no görülse de **çalışan** SurfaceFlinger renderer'ı NVIDIA OpenGL ES Translator. Yalnız config satırından yazılımsal renderer sonucu çıkarılmamalı; bu çalışmada config değiştirilmedi.

## Sonuç

İki farklı uygulamanın aynı dönemde yavaşlaması için belirgin ortak neden **WSL içindeki topas CPU yüküdür**. Bu, AynaTV'de bulunan geçiş/ses/zaman eşleme hatalarının yok olduğunu göstermez. Önce host yükü denetlenmeden uygulamanın decoder'ını veya kalite seviyesini değiştirmek doğru bir teşhis yöntemi değildir.

`ADAPTIVE_PROFILES_2026-09-09.md` içindeki 8.2 → 6.2 saniye ölçümü bu yükten etkilenmiş olabilir. Bunu kesin yazılım hızlanması veya eski hızlı sürümün geri geldiği kanıtı olarak kullanmayın. Yeni profil seçenekleri işlevsel olarak ayrı bir çalışma, performans kabulü ise beklemededir.

Önerilen sonraki adım: topas işinin ait olduğu çalışma üzerinden CPU paralelliğini azaltmak veya uygun bir kontrol noktasında ara vermek; ardından **APK ve çözünürlüğü değiştirmeden** aynı görüntü yükünü tekrarlamak. topas/WSL gelişigüzel kapatılmamalı; devam eden hesaplama kaybolabilir. Bu rapor sırasında işlem yapılmadı.

Donanım/sistem JSON: `artifacts/lag-assessment-20260909/host-system.json`. Süreç sayaçları ve WSL ps çıktısı bu görevdeki araç kayıtlarında yer alır.
