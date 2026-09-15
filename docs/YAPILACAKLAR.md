Yapılacaklar Listesi (Backlog & Checklist)
1. Hazırlık ve Konfigürasyon
[ ] recloudstream/TestPlugins deposunu çatalla (Fork) ("Include all branches" seçeneğini işaretle).

[ ] GitHub > Settings > Actions > General > Workflow permissions > "Read and write permissions" seçeneğini aktif et.

[ ] Kök dizindeki repo.json dosyasını kendi GitHub kullanıcı adına göre güncelle.

[ ] Android cihazda geliştirici seçeneklerini ve USB hata ayıklamayı aç.

2. Temel Altyapı Geliştirme
[ ] settings.gradle.kts dosyasına yeni eklenti modülünü tanımla (include(":OrnekProvider")).

[ ] Modül içerisindeki build.gradle.kts bağımlılıklarını yapılandır.

[ ] Eklenti ikonunu res/drawable/ altına ekle.

[ ] @CloudstreamPlugin anotasyonu ile Plugin kayıt sınıfını oluştur.

3. Sağlayıcı (Provider) Metotlarının Yazılması
[ ] mainUrl, name, supportedTypes ve lang = "tr" değişkenlerini tanımla.

[ ] getMainPage: Ana sayfa kategorilerini ve afişleri sayfalama mantığıyla çek.

[ ] search: Kullanıcı arama sorgusunu hedef platformda aratıp eşleştir.

[ ] load: Seçilen filmin detaylarını veya dizinin sezon/bölüm listesini ayrıştır.

[ ] loadLinks: Oynatıcı iframe bağlantılarını yakala ve loadExtractor fonksiyonuna ilet.

4. Dayanıklılık ve Yerel Test
[ ] Null gelebilecek tüm alanları ?. ve elvis (?:) operatörleriyle korumaya al.

[ ] CSS seçicilerini DOM değişikliklerine karşı esnek tut (mümkünse JSON verisini parse et).

[ ] ./gradlew <ModulAdi>:deployWithAdb ile yerel cihazda test et.

[ ] adb logcat -s Cloudstream komutuyla logları ve hata çıktılarını doğrula.

5. İlk Canlı Dağıtım
[ ] Kodları master dalına push et ve GitHub Actions derleme sürecini izle.

[ ] builds dalında plugins.json dosyasının oluştuğunu teyit et.

[ ] CloudStream uygulamasında Depo Ekle alanına ham repo.json bağlantısını girerek eklentileri indirip test et.

6. Otomatik Güncelleme ve İzleme Altyapısı (En Son Yapılacaklar)
[ ] Depoya dinamik domain kontrolü için domains.json dosyasını ekle.

[ ] Eklenti sınıflarındaki statik mainUrl alanlarını domains.json kaynağından veri çekecek şekilde refactor et.

[ ] Belirli periyotlarla (örneğin her 12 saatte bir) sitelerin ayakta olup olmadığını kontrol eden GitHub Actions health check workflow'u oluştur.

[ ] Sitelerin HTTP yönlendirmelerini (301/302) takip edip yeni domaini tespit ettiğinde domains.json dosyasını otomatik güncelleyen Python betiğini ekle.

[ ] Kritik kırılmalarda geliştiriciye anlık bilgi iletmesi için Discord veya Telegram Webhook entegrasyonunu bağla.