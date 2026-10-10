# Cihaz engelleme planı

## Mevcut durum
Ayarlar içindeki cihaz erişim penceresi daha önce eşleşen cihazları ad, kısa kimlik ve engel durumu ile gösteriyor. Engelleme doğrulanmış eşleştirme açık anahtarına yazılıyor; IP veya görünen cihaz adına bağlı değil. Kimlik doğrulamasında engelli anahtar reddediliyor. Mevcut aktif oturumu anında düşürdüğü henüz doğrulanmış değil.

## Önerilen kullanıcı akışı
1. Ayarlar > Bağlantı ve güvenlik > Cihazlar. İzin verilenler ve Engellenenler bölümleri. Ad, kısa kimlik, son bağlantı tarihi ve varsa Şu an bağlı durumu.
2. Cihaz > Engelle. Onay penceresi; etkin cihazsa mevcut yayının da sonlandırılacağını açıkça söyler. Engel önce kalıcı kaydedilir, sonra yalnız o kimliğe ait oturum kesilir.
3. Engellenenler > Engeli kaldır. Kalıcı tercih; alıcı yeniden başladığında da korunur. Kullanıcının silme/unutma eylemi kara listeyi yanlışlıkla temizlemez.
4. Tekrarlanan reddedilmiş istekler PIN veya bildirim göstermez; oturum başlatma sınırı ve süreli yavaşlatma uygulanır. Yerel ağdaki paket gönderimini engellediği iddia edilmez.
5. Bilinmeyen cihazlar için PIN doğrulaması korunur. İsteğe bağlı Yalnız izin verilen cihazlar modu sonraki adım olarak değerlendirilir.

## Kimlik sınırı
Telefon kendi eşleştirme anahtarını sıfırlarsa yeni kimlikle görünebilir. İsim veya IP ile kesin engel vaat edilmez; bilinmeyen cihazın PIN/izin kontrolü bu yüzden gereklidir.

## Testler
- Engelle/yeniden başlat/yeniden bağlan; engel kalıcı.
- IP veya cihaz adı değişse de aynı anahtar reddedilir.
- Engeli kaldırınca bağlantı mümkün.
- Aktif kimlik engellendiğinde yalnız onun yayını biter, alıcı açık kalır.
- Bilinmeyen yeni kimlik PIN kontrolünü atlayamaz.
- Tekrarlanan girişimler mevcut yayını veya arayüzü bozmaz.

Bu belge planlamadır; R33 kapsamında bu ek arayüz ve aktif oturum sonlandırma özelliği uygulanmış sayılmaz.
