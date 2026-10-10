# Gerçek cihaz ölçüm yöntemi

Bu belge test planıdır; ölçüm sonucu değildir. Etkin zincir yalnız iPhone → Android TV'dir.

## Deney kaydı

Her koşuda alıcı build/çekirdek commit ve APK SHA-256, iPhone model/iOS build, Android TV model/OS build/ABI, decoder adı, ses formatı, ağ türü ve test süresi kaydedilir. Gerçek IP, MAC, PIN, özel anahtar, SSID veya kişisel içerik rapora girmez. Kullanıcının kişisel ekranı yerine cihaz üzerindeki korumasız test içeriği kullanılır; alıcıda ekran/ses kaydı özelliği eklenmez.

Referans ağ: aynı alt ağ, TV Ethernet veya güçlü 5/6 GHz Wi-Fi, iPhone güçlü 5/6 GHz, yaklaşık -60 dBm veya daha iyi RSSI, en az 100 Mbps yerel throughput, kayıp <%0,5, jitter p95 <10 ms ve AP isolation kapalı. İnternet kesilse de yerel ağ çalışmalı. Ağ ölçümü için kullanıcıya ait kontrollü laboratuvar kullanılır; dış servise hız testi gönderilmez.

## Keşif ve başlangıç

Keşif ve bağlantı dağılımları için her cihaz/OS/ağ/profil birleşiminde en az 30 bağımsız bağlanma denemesi planlanır. İlk kurulum ve güvenilen cihaz yeniden bağlantıları karıştırılmaz. Başarısız denemeler raporda tutulur, yalnız başarılı olanlar seçilmez.

Monotonic olay zamanları: uygulama açılışı, ilan hazır, iPhone listesinde görünür, kullanıcı seçimi, PIN ekranı, PIN kabulü, oturum kurulumu, ilk video ve ilk ses. İlk kare/ses sürelerinin başlangıcı PIN kabulüdür; PIN süresiz beklemeleri ayrı raporlanır. Bu başlangıç tanımı bir ölçüm kararıdır, eşikleri değiştirmez. Şartname 19.2: ilan ≤5 s p95, listede görünme ≤8 s p95, PIN ekranı ≤3 s p95, PIN sonrası oturum ≤4 s p95, ilk kare p50 ≤2 s / p95 ≤4 s, ilk ses p95 ≤4 s.

## Gecikme, kare ve ses

1. iPhone ve TV aynı yüksek hızlı kameranın karesinde görünür; zaman kodu/clap deseniyle uçtan uca fark ölçülür. Kamera kare süresi, belirsizlik ve örnek sayısı raporlanır. Telefon/TV duvar saatlerinin farkı ölçüm yerine kullanılmaz.
2. Sıralı örneklerde p95 için nearest-rank `ceil(0.95*n)` kullanılır; p50 medyanıdır. Profiller ve cihazlar ayrı değerlendirilir. Düşük gecikme p50 ≤150 ms, p95 ≤250 ms; otomatik p95 ≤350 ms; kararlı p95 ≤500 ms.
3. 1080p60 testinde 30 dakika: ortalama ≥59 fps, drop <%0,5; 1080p30 için ≥29,5 fps ve drop <%0,25. Pay/payda (beklenen/alınan/çözülen/gösterilen/atılan kareler) ayrı belirtilir; decoder callback sayısı tek başına ekranda gösterilen kare değildir.
4. >500 ms donma 0, 250–500 ms donma saatte en çok 1, bozuk kare ve yanlış oran 0. Saatlik donma ölçütü için en az 60 dakika ayrı gözlem gerekir; 30 dakikalık kayıt kendiliğinden bir saat sayılmaz.
5. A/V farkının mutlak değeri p95 ≤50 ms, hiçbir örnek >100 ms; 2 saat sonunda drift ≤50 ms. Negatif ve pozitif kaymaları birbirini götüren ortalamaya indirgeme. 30 dakikada underrun hedef 0, en fazla 1 kısa olay; bozuk PCM 0.
6. Yön değişiminden doğru kareye p95 ≤1,5 s. 500 ms kesinti, decoder stall ve Surface sonrası hareketli görüntü ≤2 s; roaming ≤8 s, kontrollü hizmet yeniden başlatma ≤5 s.

## Dayanıklılık ve kaynaklar

Önce 20 adet 2 dakikalık bağlan/ayrıl; ardından 2 saat ve Kademe A/B'de ayrı 8 saat koşuları. Kararlı başlangıçta ve aynı toparlanma aralığı sonrasında bellek, fd, thread, codec, AudioTrack ve kilit sayıları alınır. 2 saat sonunda bellek büyümesi <%10, crash/ANR/kilitlenme/sızıntı 0. 8 saatte sürekli bellek artışı ve yeniden bağlanmayı engelleyen bozulma 0. Sayısal bellek ölçümü GC ve allocator etkileri açıklanarak yorumlanır; tek dalgalanma trend sayılmaz.

30 dakika 1080p60 sonrası termal durum kaydedilir; sürekli severe seviyesi kabul edilmez. Ağ bozunma matrisi ana şartname bölüm 32'ye göre ayrı kontrollü koşularda uygulanır. Eşikleri karşılamayan sonuçlar FAIL olarak saklanır.
