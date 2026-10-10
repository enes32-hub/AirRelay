# RC7 — İzleme sırasında görüntü boyutu

Yansıtma ve uzak video kontrollerine Ekrana sığdır, Doldur (kırp), Esnet düğmeleri eklendi. Seçili düğme işaretlenir. Kumanda etkinliği mevcut kontrol gizleme süresini yeniler; PIN/iletişim kutusu önceliği korunur. Yüzey anahtarı değişmez; yalnız boyutu güncellenir. Varsayılan sığdırmadır. Seçim Compose saved state içinde korunur; kalıcı bir alıcı/protokol ayarı değildir.

Sığdır, oranı koruyarak en az bir ekran kenarına ulaşır. Doldur, oranı koruyarak bütün alanı kaplar ve taşan bölümler merkezden kırpılır. Esnet, iki ekseni bağımsız doldurur. Akışın içine kodlanmış siyah bantları algılayan içerik kırpması eklenmedi. Gerçek görüntü çözünürlüğü veya göndericinin codec'i değiştirilmez.

İncelenen canlı görüntü 3840×1772 H.265 yansıtmaydı; ekran 1920×1080. Bu orandaki tam akışın sığdırılması 1920×886 alan ve üst/alt yaklaşık 97 piksel boşluk üretir. Kaynak içindeki bantlar buna eklenebilir. Önceki ekran artifacts/fit-check-20260910/before.png dosyasında korunur.

49 geometri kontrolü geçti: 1080p/4K ekran, dikey, 16:9, 4:3 ve incelenen geniş akış oranı; sığdırma sınırları, kırpma kapsamı, oran koruma, esnetme ve geçersiz oran varsayılanı. Fiziksel TV ve yeni sürümde gerçek telefonla üç modun görsel kabulü henüz yapılmadı.
