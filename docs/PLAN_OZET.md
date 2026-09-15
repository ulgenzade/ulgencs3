Proje Yönetim ve Mimari Plan Özeti
1. Proje Amacı ve Kapsamı
Bu proje; CloudStream 3 Android/Android TV uygulaması için modüler, yüksek performanslı ve otomatik güncellenebilir bir Türkçe medya eklentisi havuzu (repository) oluşturmayı amaçlar. Sistem, hedef web sitelerindeki medya meta verilerini ve video kaynaklarını istemci tarafında ayrıştırarak CloudStream oynatıcısına aktarır.

2. Temel Mimari Prensipler
Modülerlik: Her hedef web sitesi bağımsız bir Gradle alt modülü olarak kodlanır. Bir sitede yaşanan aksaklık diğer eklentileri etkilemez.

Çıkarıcı (Extractor) Ayrımı: Medya kazıma (scraping) mantığı ile üçüncü taraf video barındırıcılarını (Vidmoly, Streamtape vb.) çözen kod blokları birbirinden izole edilir.

Dinamik Yükleme: Eklentiler DexClassLoader tabanlı dinamik bytecode enjeksiyonu ile ana uygulamaya çalışma zamanında dahil edilir.

Sıfır Çökme (Zero-Crash): Hedef sitelerdeki HTML/JSON şeması değişikliklerinde uygulamanın kapanmasını önleyen defansif kodlama standartları uygulanır.

3. Dağıtım ve Otomasyon Modeli
Proje, sunucu maliyeti gerektirmeyen GitHub Actions CI/CD ve GitHub Pages/Raw altyapısını dağıtık bir içerik dağıtım ağı (CDN) olarak kullanır.

Deponun istemciye bağlanması repo.json manifesti ve otomatik derlenen plugins.json indeksi üzerinden yürütülür.

Eklenti çalışma mantığı, Aliucord projesinden uyarlanan dinamik modül sistemine dayanır.

Tüm sağlayıcılar stabil hale geldikten sonra devreye girecek otomatik sağlık taraması (health check) ve dinamik domain çözümleme altyapısıyla bakım maliyeti en aza indirilir.