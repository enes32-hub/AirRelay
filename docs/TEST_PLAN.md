# Test planı ve Aşama 0 bağlantı denemesi

**Durum:** uygulama ve fiziksel kabul testleri çalıştırılmadı. [Test kataloğu](TEST_CATALOG.json) 3.1 P0 listesi, bölüm 19 ölçümleri ve E2E-001..015 senaryolarını içerir. [İzlenebilirlik](../REQUIREMENTS_TRACEABILITY.csv) başlangıç matrisidir; bütün ayrıntılı şartname maddelerinin kapsamı henüz tamamlanmış değildir.

## Aşama 0 fiziksel spike

Önkoşullar: incelemesi geçilmiş sabit çekirdek, doğrulanmış derleme bağımlılıkları, bir fiziksel Android TV ve bir iPhone, aynı kullanıcı kontrollü ağ, TV ADB yetkilendirmesi. Lisans/köken geçmeden aday APK kurulmaz. Test öncesi cihazda önemli bir aktif oturum olmadığı doğrulanır.

1. Model/OS/ABI ve ağ koşullarını kişisel kimlikleri kaydetmeden raporla.
2. İzole aday derlemesi ve SHA-256 üret; AynaTV yayını veya P0 tamamlandı olarak etiketleme.
3. TV'de alıcıyı aç; iPhone'a hiçbir uygulama kurmadan Denetim Merkezi → Ekran Yansıtma listesinden görünürlüğü doğrula.
4. PIN'i TV'den gir; doğru, yanlış ve süresi dolan PIN davranışını dene. PIN'i loga veya kanıt görseline alma.
5. Canlı ana ekran, hızlı kaydırma, korumasız test videosu/sesi ve 20 yön değişimi yap. MediaCodec donanım adını ve AudioTrack yolunu kaydet.
6. 30 dakika 1080p60 hedefini ölç; donanım yetersizse başarısız hedefi gizleme. Başarı, daha düşük kaliteye sessizce düşürülerek işaretlenmez.
7. Göndericiden ve TV kumandasından kapat; 20 bağlantı döngüsünde kaynak ölç. İnternet kapalı yerel ağda tekrar bağlan.
8. Sonucu [ölçüm yöntemi](PERFORMANCE_METHOD.md) ile raporla. Lisans ve spike sonuçlarıyla çekirdek kararı ver; sonra Aşama 1 ana ürün koduna geç.

Bu kısa spike, 2/8 saat soak veya tüm fiziksel matrisi geçmiş sayılmaz. Fiziksel ölçüm için gereken insan işlemleri otomasyon yapılmış gibi raporlanmaz.

## Kanıt kuralları

Geliştirme sırasında `NOT_RUN`, `BLOCKED`, `FAIL`, `PASS` açıkça ayrılır. Şartname 44'teki P0'da test edilmemiş satır olmaması **teslim kapısıdır**; şu anda test edilmemişleri PASS yapmak yasaktır. Hazırlık kontrolünün yeşil olması ürünün hazır olduğu anlamına gelmez.

`scripts/check_readiness.py` CSV kapsamını, kaynak hash/satırlarını, test ID'lerini ve kanıt bütünlüğünü kontrol eder. PASS satırında `Kanıt dosyası` alanı `artifacts/evidence/` içindeki gerçek JSON raporunu gösterir. Raporda `test_id`, `kind` (`physical`, `build`, `review`), `status`, `spec_sha256`, `observed_at`, `reviewer`, `build_sha256` ve `artifacts` dizisi bulunur. Her artifact gerçek dosyanın göreli `path` ve `sha256` değerlerini taşır. Fiziksel kanıt ayrıca `devices` altında `iphone_model`, `ios_build`, `android_tv_model`, `android_build` içerir. G0 gate PASS kayıtları da aynı şemayı kullanır; G0-SPIKE physical, diğer G0 kapıları review türündedir.

Araç metadatanın doğruluğunu, bütün ayrıntılı eşikleri veya hukuki uygunluğu kendiliğinden kanıtlayamaz. Test sahibi ölçümleri ve ham kanıtları incelemekten sorumludur. Kullanıcıya örnek PASS raporu gerçek kanıt gibi sunulmaz. `tests/` altındaki geçici sentetik kayıtlar yalnız denetim aracının regresyon testidir; ürüne kabul kanıtı değildir.

## Çalıştırma

```powershell
python scripts/run_checks.py
python scripts/check_readiness.py --output artifacts/readiness-report.json
python scripts/check_readiness.py --release --output artifacts/release-readiness.json
```

İlk komut hazırlık araçlarının testidir. İkinci komut açık eksikler varken kayıtların tutarlılığını denetler. Üçüncü komut mevcut durumda çıkış kodu **2** ile yayın hazırlığını reddetmelidir. Bu reddetme bir test hatası değil beklenen kapı davranışıdır. Python yolu için [ortam belgesi](BUILD_ENVIRONMENT.md).

`seed_traceability.py` yalnız ilk başlangıç dosyalarını üretir; mevcut sonuçları silmemek için ikinci çalıştırmada durur. Şartname değişince kaynak satırları ve test eşleştirmeleri elle incelenmeli, hash ancak bu incelemeden sonra güncellenmelidir.
