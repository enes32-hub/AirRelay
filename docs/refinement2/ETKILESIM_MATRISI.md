# Etkileşim teftiş matrisi — Revizyon 2

**Bu tablo Android test sonucu değildir.** “Host” satırları yalnız ilgili üretim karar sınıfının yerel JVM testini ifade eder. Aşağıdaki tüm satırlarda gerçek Android/TV yürütmesi yapılmamıştır. Kapsam dışında kalan cihaz kabulü için başarı işareti konmamıştır.

| Senaryo | Kodda hedeflenen davranış | Bu turdaki doğrulama | Android/TV |
|---|---|---|---|
| Ana ekranda hazır alıcı | Birincil eylem rehber; durdurma ikincil. | Karar fonksiyonu host testli | Yapılmadı |
| Ana ekranda aktif içerik | Birincil eylem içeriğe döner. | Karar fonksiyonu host testli | Yapılmadı |
| Ayarlar → Yardım → Ayarlar | Son odak ve kaydırma konumu korunur. | Kaynak; odak aday sırası host testli | Yapılmadı |
| İleri ayarlar → Back | Bağlantı/Görünüm sayfası değil, Ayarlar üst sayfası. | Sayfa hiyerarşisi host testli | Yapılmadı |
| Günlük → Back | Yardım üst sayfasına dönüş. | Sayfa hiyerarşisi host testli | Yapılmadı |
| Gizli kontrolde OK | Kontroller açılır; ilk düğme aynı basışta çalışmaz. | Karar fonksiyonu host testli | Yapılmadı |
| Gizli kontrolde OK basılı tutma | Repeat ve açılış key-up yutulur; yeni basış işler. | Karar fonksiyonu host testli | Yapılmadı |
| Açılış key-up kaybı | Yeni fiziksel OK basışı eski kilide takılmaz. | Karar fonksiyonu host testli | Yapılmadı |
| Ses aç/kıs tuşu | Arayüz bu tuşları yutmaz. | Karar fonksiyonu host testli | Yapılmadı |
| Kontrol basılı veya modal açık | Otomatik gizleme başlamaz. | Politika host testli; gerçek zamanlayıcı yürütülmedi | Yapılmadı |
| PIN gelirken ad düzenleme | PIN öncelik alır; iki bağımsız pencere kullanılmaz. | Kaynak incelemesi | Yapılmadı |
| Durdurma onayı | İlk odak güvenli iptal eyleminde. | Kaynak incelemesi | Yapılmadı |
| Geçersiz alıcı adı | Kaydet pasif; yanlış ad uygulanmaz. | Doğrulayıcı host testli; form kaynak incelemesi | Yapılmadı |
| Geçersiz gelişmiş sayı/çözünürlük | Kaydet pasif; IME Done hatayı saklamaz. | Doğrulayıcı host testli; form kaynak incelemesi | Yapılmadı |
| Aktif yayında ayar taslağı | Aktif ayar değişmez; otomatik restart yok. | Taslak ve controller host testli | Yapılmadı |
| Kalıcı yazma sırasında yeniden düzenleme | UI kilitte beklemez; işlem reddedilir ve kaydedildi sanılmaz. | Gerçek taslak sınıfı host testli; Android UI yürütülmedi | Yapılmadı |
| Başlatma iptali sırasında disk tamamlanması | Eski istek native alıcıyı yeniden başlatamaz. | Token sınıfı host testli; Android service kaynak incelemesi | Yapılmadı |
| Taslakları sil | Aktif ayarlar değişmez; taslak durumu temizlenir. | Taslak sınıfı host testli | Yapılmadı |
| Keşif kaydı sonuçsuz | 15 saniye sonra gözlem hatası; native bağlantı durdurulmaz. | Gerçek wrapper + sanal Handler host testli | Yapılmadı |
| Eski NSD callbacki | Yeni kaydın adı/durumu ve gözlem süresi etkilenmez. | Gerçek wrapper + Android doubles host testli | Yapılmadı |
| Keşif timeout sonrası geç başarı | Gerçek Android sonucu geldiğinde kayıt durumu güncellenir. | Gerçek wrapper + Android doubles host testli | Yapılmadı |
| Günlük okuma | Yeni loglar odaklı satırı değiştirmez. | Kaynak; gerçek Compose kaydırma test edilmedi | Yapılmadı |
| Tekrarlı dışa aktar/temizle | Aynı anda yeni işlem başlatılmaz. | Kaynak; dosya gizliliği önceki host testleri | Yapılmadı |
| Günlükten çıkma veya uygulamanın durması | Native debug taraması kapanır. | Kaynak yaşam döngüsü bağlantısı | Yapılmadı |
| Servis bağlantısı kesilmesi | Eski medya, başlık ve oynatım durumları temizlenir. | Kaynak; gerçek Binder kopması test edilmedi | Yapılmadı |
| Uzun ad/büyük yazı | Tam ad ve erişilebilir eylemler; dar sütun/kaydırma kullanımı. | Preview tanımları; render yapılmadı | Yapılmadı |
| Hızlı sekme tıklamaları | Eski/yeni sayfalar aynı anda odak ağacı oluşturmaz. | Kaynak; gerçek tuş temposu ölçülmedi | Yapılmadı |
| Canlı video sırasında sayfa değişimi | Mevcut yüzey sayfa giriş animasyonuna sarılmaz. | Kaynak/byte koruması; görüntü kabulü yapılmadı | Yapılmadı |
