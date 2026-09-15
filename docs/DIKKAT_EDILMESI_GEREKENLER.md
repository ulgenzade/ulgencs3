Dikkat Edilmesi Gereken Kritik Noktalar ve Best Practices
Eklenti ve depo geliştirirken sistemin çökmemesi ve uzun ömürlü olması için aşağıdaki teknik kurallara dikkat edilmelidir.

1. Alan Adı (Domain) Değişiklikleri ve Dinamik Yönlendirme
Sorun: Türkiye'deki içerik siteleri düzenli olarak idari tedbir kararlarıyla engellenir ve adres uzantıları değişir. Sabit kodlanmış (hardcoded) domainler eklentinin kısa sürede çalışamaz hale gelmesine neden olur.

Çözüm: Eklenti açılışında sabit bir ana yönlendirici domainine HEAD veya GET isteği atılarak yönlendirmeler (HTTP 301/302) otomatik takip edilmeli veya uzaktan çekilen dinamik bir yapılandırma dosyası (domains.json) okunmalıdır.

2. Bot Korumaları, User-Agent ve Referer Yönetimi
Cloudflare Turnstile, DDoS-Guard gibi mekanizmalara takılmamak için OkHttp tabanlı app.get ve app.post fonksiyonlarında tarayıcı başlıkları kullanılmalıdır:

val doc = app.get(
    url = targetUrl,
    headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer" to mainUrl
    )
).document

Bazı video barındırıcıları ham medya parçalarını (.ts veya .m3u8) verirken katı Referer kontrolü uygular. ExtractorLink modelinde referer alanının doğru sunucuya işaret ettiğinden emin olunmalıdır.

3. Defansif Kodlama ve Null-Safety
Web siteleri HTML yapılarını veya CSS sınıflarını sık sık değiştirir.

Kod içerisinde asla doğrudan zorunlu unwrap (!!) operatörü kullanılmamalıdır.

Bulunamayan başlık, afiş veya video bağlantılarında uygulama çökmek yerine işlemi güvenli bir şekilde null döndürerek atlamalıdır (mapNotNull ve selectFirst()?.text() tercih edilmelidir).

4. HTML Kazıma Yerine Dahili API Kullanımı
Bir sayfa incelenirken öncelikle Ağ Trafiği (Network Tab) denetlenmelidir.

Birçok modern platform verileri doğrudan dahili bir REST API veya Next.js yapılandırması (<script id="__NEXT_DATA__">) üzerinden çeker.

Sayfa DOM'unu kazımak yerine doğrudan bu JSON uç noktalarından veri almak, eklentiyi onlarca kat daha hızlı ve sayfa tasarımı değişimlerine karşı dayanıklı kılar.

5. GitHub Actions İzinleri ve Builds Dalı
En sık yapılan hata, GitHub Actions'ın derlenen dosyaları depoya yazma yetkisinin olmamasıdır.

Settings > Actions > General > Workflow permissions sekmesinde Read and write permissions seçeneğinin açık olduğu mutlaka kontrol edilmelidir.

6. Ağ Kısıtlamaları ve İSS Engelleri
Türkiye'deki bazı servis sağlayıcılar GitHub Raw (raw.githubusercontent.com) bağlantılarına erişim kısıtlaması uygulayabilmektedir.

Eklentiler veya depolar CloudStream içine yüklenemediğinde kullanıcılara DoH (DNS over HTTPS) veya VPN kullanmaları yönünde bilgilendirme yapılmalıdır.

7. Hukuki Sınırlar ve Telif Ayrımı
CloudStream depoları ve eklentileri asla video barındırmaz.

Eklenti yalnızca standart bir internet tarayıcısının yaptığı gibi genel ağa açık web sayfalarını ayrıştıran bir istemci arabirimidir.

Depolara telif hakkı içeren hiçbir medya dosyası yüklenmemeli, sorumluluk reddi beyanları (DMCA disclaimer) proje ana dizininde açıkça belirtilmelidir.