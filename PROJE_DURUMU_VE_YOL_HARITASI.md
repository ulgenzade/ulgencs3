# Cloudstream Türkçe Sağlayıcılar Projesi — Durum Raporu ve Devam Planı

> **Tarih:** 16 Eylül 2026  
> **Depo:** [ulgenzade/ulgencs3](https://github.com/ulgenzade/ulgencs3)  
> **Varsayılan Dal:** `master`  
> **Dağıtım Dalı:** `builds`  

---

## 1. Yönetici Özeti

Bu proje; popüler açık kaynaklı Android medya oynatıcısı **Cloudstream 3** için Türkiye'nin en popüler anime platformlarını destekleyen, Cloudflare/bot korumalarını aşabilen, 4K ve çoklu kaynak desteği sunan, domain engellemelerine karşı uzaktan otomatik güncellenebilen kurumsal kalitede bir eklenti (provider) reposudur.

---

## 2. Neler Yapıldı? (Tamamlananlar)

### A. 6 Adet Türkçe Anime Sağlayıcısı (Sıfırdan Yazıldı)
Tüm sağlayıcılar `MainAPI` sınıfından türetildi, yaşam döngüsü sınıfları (`Plugin`) oluşturuldu:

1. **TürkAnime TV (`TurkAnimeProvider`)**:
   - **Hedef:** `https://www.turkanime.tv`
   - **Mimari:** Jsoup DOM kazıma + arka plan AJAX endpoint'leri (`/ajax/sezonlukanime`, `/ajax/yenieklenenanime` vb.).
   - **Video Desteği:** Sayfadaki tüm iframe'ler, `data-video` ve regex tabanlı doğrudan video linkleri (`.m3u8`, `.mp4`).

2. **AnimeciX (`AnimeCixProvider`)**:
   - **Hedef:** `https://animecix.tv`
   - **Mimari:** Angular tabanlı REST API (`/api/v1/titles`, `/api/v1/search`).
   - **Özellik:** XSRF-TOKEN cookie yönetimi, 4K / 1080p / 720p akıllı kalite etiketleme, dizi/sezon/bölüm hiyerarşisi.

3. **Anizm (`AnizmProvider`)**:
   - **Hedef:** `https://anizm.net`
   - **Mimari:** PHP HTML kazıma, Cloudflare koruma başlıkları (`Accept-Language`, `User-Agent`), filtreli sayfalandırma desteği.

4. **Anizium (`AniziumProvider`)**:
   - **Hedef:** `https://anizium.co`
   - **Özellik:** **Kesin 4K (2160p UHD)** video akış yakalayıcı, hibrit API + DOM yapısı.

5. **OpenAnime (`OpenAnimeProvider`)**:
   - **Hedef:** `https://openani.me`
   - **Mimari:** Next.js SPA yapısı. HTML kazıma yerine sayfa içindeki `__NEXT_DATA__` JSON verisinden ve Next buildId rotalarından veri çekme (çok daha hızlı ve kırılmaz).

6. **TrAnimeİzle (`TrAnimeIzleProvider`)**:
   - **Hedef:** `https://www.tranimeizle.io`
   - **Mimari:** Bot kontrol bypass başlıkları, oturum çerezi yönetimi, DOM + regex video çıkarıcı.

---

### B. Sıfır Kesinti & Dinamik Domain Güncelleme Mimarisi
- Kök dizinde `domains.json` oluşturuldu.
- Türkiye'de anime sitelerinin domainleri sık sık BTK engeline takıldığı için, tüm sağlayıcıların `init()` fonksiyonuna **uzaktan domain çekme mekanizması** eklendi.
- Bir site adres değiştirdiğinde Android eklentisini güncellemeye gerek kalmadan sadece `domains.json` dosyasını düzenlemek yeterlidir.

---

### C. Gradle ve Derleme Altyapısı (Standartlaştırma)
- Resmi [recloudstream/TestPlugins](https://github.com/recloudstream/TestPlugins) mimarisi baz alındı:
  - **Kök `build.gradle.kts`:**
    - Android Gradle Plugin: `8.7.3`
    - Kotlin Gradle Plugin: `2.1.0`
    - Cloudstream Gradle Plugin: `-SNAPSHOT`
    - Merkezi `subprojects` konfigürasyonuyla tüm alt projelere `android`, `cloudstream`, `NiceHttp`, `Jsoup`, `Jackson` bağımlılıkları bağlandı.
  - **Modül `build.gradle.kts`:**
    - Her modül gereksiz boilerplate'ten arındırıldı, sadeleştirildi.
  - **AndroidManifest.xml:**
    - AGP 8+ çakışmalarını önlemek için manifest dosyaları yalınlaştırıldı, namespace'ler Gradle üzerinden dinamik verildi.
  - **Kotlin Kod Düzeltmeleri:**
    - Kotlin derleyicisinin hata verdiği `catch (_: Exception)` yapıları standart `catch (e: Exception)` ile düzeltildi.
    - URL aramalarında ihtiyaç duyulan `encodeUrl()` extension yardımcı fonksiyonu eklendi.

---

### D. GitHub & CI/CD Dağıtım Hattı
- GitHub reposu [ulgenzade/ulgencs3](https://github.com/ulgenzade/ulgencs3) bağlandı ve tüm kodlar push edildi.
- Derlenmiş eklentilerin depolanacağı bağımsız **`builds` dalı** (orphan branch) GitHub'da açıldı.
- `.github/workflows/build.yml` oluşturuldu:
  - Java 17 Temurin kurulumu
  - Gradle kurulumu ve cache yönetimi
  - `./gradlew make makePluginsJson` çalıştırma
  - Üretilen tüm `.cs3` dex eklenti dosyaları ile `plugins.json` manifestosunu otomatik `builds` branchine pushlama.

---

## 3. Tam Olarak Kaldığımız Yer (Current State)

1. **En Son Yapılan Hamle:**
   - GitHub Actions logunda `Minimum supported Gradle version is 8.9. Current version is 8.7` uyarısı tespit edildi (AGP 8.7.3 için Gradle 8.9+ zorunludur).
   - `gradle/wrapper/gradle-wrapper.properties` dosyası **Gradle 8.9**'a yükseltildi ve commit edilip GitHub `master` dalına pushlandı (`commit: 9cbf0c9`).
   - Şu anda GitHub Actions üzerinde yeni build döngüsü tetiklenmiş durumdadır.

---

## 4. Bir Sonraki Oturumda Yapılacaklar (Yol Haritası)

1. **1. Adım: Derleme Başarısının Doğrulanması**
   - GitHub Actions (`gh run list`) üzerinden Gradle 8.9 ile çalışan derlemenin sonucunu kontrol etmek.
   - `builds` dalında `.cs3` dosyalarının ve `plugins.json` dosyasının oluştuğunu teyit etmek.

2. **2. Adım: Cloudstream Reposunu Test Etme**
   - Cloudstream 3 uygulamasında `Eklentiler -> Depolar -> Depo Ekle` kısmına şu URL girilerek test edilecek:
     ```text
     https://raw.githubusercontent.com/ulgenzade/ulgencs3/builds/plugins.json
     ```
   - 6 eklentinin de listede göründüğü ve hatasız yüklendiği doğrulanacak.

3. **3. Adım: Oynatıcı & Video Link Çıkarıcı Doğrulaması**
   - Her eklenti için bir anime aratılıp video kaynakları ve 4K oynatma yetenekleri test edilecek.

4. **4. Adım: Kullanıcının İstediği Diğer Sitelerin Eklenmesi**
   - İlk 6 ana hedef tamamlandıktan sonra, diğer topluluk repolarındaki popüler Türkçe dizi/anime/film kaynakları da sırayla yeni modüller olarak projeye eklenecek.

---

*İyi uykular! Kalktığında `devam et` demen yeterli, kaldığımız noktadan testleri bitirip depoyu yayına hazır hale getireceğiz.*
