# Çekirdek kökeni ve dağıtım inceleme dosyası

**Taslak; kimseye gönderilmedi.** Teknik inceleme sorularıdır, hukuk görüşü veya kullanım onayı değildir. Ürün: ücretsiz, reklamsız, yalnız iPhone → Android TV ekran yansıtma alıcısı.

İncelemeciye sağlanacak kaynaklar: [karar raporu](../docs/DEPENDENCY_AND_LICENSE_DECISION.md), [bulgular](../docs/research/NATIVE_AUDIT_FINDINGS.md), [185 dosyalık envanter](../docs/research/native-source-inventory.csv) ve [sabit commit kilidi](../docs/research/native-audit.lock.json).

1. **playfair kriptografik materyal kökeni:** Bu sabit commit'lerdeki algoritma, tablolar ve yanıt sabitleri nereden üretildi? Bağımsız kaynak ve hak zinciri belgeleri var mı? Firmware/özel binary'den alınmış materyal varsa yeniden dağıtım hakkı hangi belgeye dayanıyor? UxPlay ve RPiPlay'in açık belirsizlik beyanını hangi kanıt çözüyor?
2. **Birleşik eserin dağıtımı:** GPL ve alt bileşenlerin gerçek lisans kapsamı; Android/JNI bağlantısının karşılık gelen kaynak, NOTICE, yeniden derleme/kurulum ve Google Play/APK dağıtım yükümlülükleri nelerdir? Gerekli kaynak erişimi nasıl sağlanacak?
3. **Native yardımcı bağımlılıklar:** OpenSSL kaynağının hash/kökeni; libplist ve statik FFmpeg ALAC bileşenlerinin lisans kapsamı; transitif bağımlılık ve kaynak sağlama gereksinimleri doğrulanmalı.
4. **Protokol/marka:** Yalnız teknik protokol tanımı ve ürün açıklaması için hangi ifadeler kullanılabilir? Resmî Apple onayı varmış gibi davranılmayacak; tescilli teknik dokümana erişim ve ürün türü için gerekli sözleşme yolu doğrulanmalı.
5. **Alternatif çekirdek:** Bu rota şartnameyi karşılamıyorsa lisanslı veya kökeni belgelenmiş bir alıcı çekirdeği gerekir. İstenen yetenekler: iPhone sistem Ekran Yansıtma, PIN, H.264, gerekli AAC-ELD/AAC-LC/ALAC, tamamen yerel ağ, Android ARM32/ARM64, 16 KB uyumu, ücretsiz son kullanıcı uygulamasında dağıtım ve gerekli kaynak/belge erişimi. Ses alıcısı lisansı tek başına ekran yansıtma yeteneğini kanıtlamaz.

Onay, kaynak commit'leri ve dağıtım kapsamını açıkça belirtmeli; genel bir “GPL kullanabiliriz” yanıtı belirsiz kriptografik kökeni çözmez. DRM korumasını aşmak veya köken yasağını yok saymak alternatif çözüm değildir.
