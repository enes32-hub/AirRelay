# RC8 — Tek görüntü modu düğmesi

İzleme çubuğunda görüntü modu tek düğmedir ve uzak videoda Oynat/Duraklat'tan hemen sonra gelir. Döngü: Ekrana sığdır, Doldur (kırp), Esnet, Orijinal, Ekrana sığdır. Yansıtmada oynat/duraklat yoktur; aynı tek mod düğmesi görünür. Sabit odak kimliği ve aynı Compose düğmesi korunur.

Orijinal: kaynak piksel boyutunu fiziksel ekran piksellerine 1:1 uygular. Android dp yoğunluğuyla yanlışlıkla büyütülmez. Küçük kaynak büyütülmez; ekrandan büyük kaynak ortalanır ve taşan kısmı kırpılır. Kaynak boyutu henüz bilinmiyorsa geçici olarak sığdırılır. Kaynak boyutu yansıtmada videoResolution, uzak videoda gerçek oynatıcı videoWidth/videoHeight değerlerinden alınır; metrik seçeneğinin açık olması gerekmez.

56 geometri/döngü kontrolü geçti. Önceki modların 1080p/4K ve dikey/yatay davranışları, büyük/küçük orijinal kaynak ve dört basışta başlangıca dönüş sınandı. Gerçek telefonla yeni düğmenin görsel kabulü kullanıcı testi olarak kalır. Bu değişiklik aktarım çözünürlüğünü/FPS'yi değiştirmez.
