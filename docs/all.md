# ULGENCS3 - CLOUDSTREAM 3 REPOSITORY PROJE DOKÜMANTASYONU

**Geliştirici (GitHub):** ulgenzade  
**Proje Adı:** ulgencs3  
**Repo Kısa Adı (Shortcode):** UlgenCS3  
**Dağıtım/Proxy:** Cloudflare Workers (`ulgencs3.workers.dev/tr`)

---

# 1. PLAN_OZET.md

## Proje Amacı ve Kapsamı
Bu proje; CloudStream 3 Android ve Android TV uygulamaları için Türkçe anime, dizi ve sinema platformlarını istemci tarafında ayrıştıran modüler bir eklenti havuzudur. Sistem, harici sunucu maliyeti olmadan GitHub altyapısı ve Cloudflare Workers üzerinden dağıtılır.

## Temel Mimari Prensipler
* **Tersine Mühendislik Tabanlı Geliştirme:** Mevcut çalışan açık kaynak repo'lar ve derlenmiş `.cs3` eklentileri (JADX yardımıyla) incelenerek sıfırdan kodlama maliyeti düşürülür.
* **Kademeli İçerik Havuzu:** İlk etapta 5 temel Türkçe anime sitesi ile başlanacak, ardından kaçak dizi ve film platformları sisteme eklenecektir.
* **İzolasyon:** Her sağlayıcı (`MainProvider`) bağımsız bir Gradle modülüdür; sitelerden birinin çökmesi havuzun kalanını etkilemez.
* **Ters Vekil (Reverse Proxy) ile Erişim:** Türkiye'deki `raw.githubusercontent.com` kısıtlamalarını aşmak ve TV'de kolay yazım sağlamak için Cloudflare Worker kullanılır.
* **Otomasyon (Faz 7):** Domain değişiklikleri ve site kırılmaları projenin en sonunda devreye alınacak sağlık taraması (health-check) ve dinamik konfigürasyon ile otomatikleştirilir.

---

# 2. IS_GIDIS_PLANI.md

## Yol Haritası (Roadmap)

| Faz | Aşama Başlığı | Süreç Tanımı | Temel Çıktı |
| :--- | :--- | :--- | :--- |
| Faz 1 | Altyapı ve Hazırlık | `recloudstream/TestPlugins` tabanının forku, `ulgenzade/ulgencs3` yapılandırması. | Çalışır GitHub derleme hattı. |
| Faz 2 | Tersine Mühendislik ve Analiz | Popüler açık kaynak eklentilerin ve `.cs3` paketlerinin JADX ile incelenmesi. | Çıkarıcı ve kazıyıcı şablon kodları. |
| Faz 3 | Anime Sağlayıcıları (İlk 5) | Belirlenen 5 Türkçe anime platformunun eklenti haline getirilmesi. | Kararlı çalışan anime modülleri. |
| Faz 4 | Dizi ve Film Sağlayıcıları | Popüler korsan yerli dizi/film sitelerinin eklenmesi. | Genişletilmiş medya havuzu. |
| Faz 5 | Dağıtım ve Worker Kurulumu | Cloudflare Worker reverse proxy kurulumu ve kısa kod yapılandırması. | `ulgencs3.workers.dev/tr` yayını. |
| Faz 6 | Manuel Stabilizasyon | Uygulama içi testler, hata loglarının incelenmesi ve null-safety kontrolleri. | Stabil v1.0 eklenti havuzu. |
| Faz 7 | Otomatik Güncelleme ve İzleme | `domains.json`, cron-job sağlık kontrolü ve otomatik sürüm artırma. | Kendi kendini onaran altyapı. |

---

# 3. YAPILACAKLAR.md

## 1. Hazırlık ve Altyapı
- [ ] `recloudstream/TestPlugins` reposunu `ulgenzade/ulgencs3` adıyla forkla (tüm dalları dahil et).
- [ ] GitHub ayarlarından Actions için "Read and write permissions" iznini aç.
- [ ] Kök dizindeki `repo.json` dosyasını `ulgenzade` ve `UlgenCS3` bilgilerine göre güncelle.
- [ ] Android Studio ve yerel ADB bağlantısını kur.

## 2. Tersine Mühendislik ve Çekirdek Yapı
- [ ] Çalışan açık kaynak repo'ları (`recloudstream`, topluluk eklentileri) klonla ve analiz et.
- [ ] İhtiyaç duyulan kapalı `.cs3` dosyalarını `.zip` yapıp JADX-GUI ile decompile et.
- [ ] Ortak `fixUrl`, bot koruma başlıkları ve hata yakalama bloklarını içeren temel yardımcı sınıfı oluştur.

## 3. Sağlayıcı Geliştirme (Anime & Dizi/Film)
- [ ] Anime Sitesi 1 (Örn: Animecix veya muadili) eklentisini yaz ve test et.
- [ ] Anime Sitesi 2 modülünü tamamla.
- [ ] Anime Sitesi 3 modülünü tamamla.
- [ ] Anime Sitesi 4 modülünü tamamla.
- [ ] Anime Sitesi 5 modülünü tamamla.
- [ ] Dizi/Film Sağlayıcı 1 (Örn: DiziPal varyantı) eklentisini yaz.
- [ ] Dizi/Film Sağlayıcı 2 (Örn: FilmModu varyantı) eklentisini yaz.
- [ ] Yaygın yerli video barındırıcılarını (Vidmoly vb.) test et, gerekirse özel extractor ekle.

## 4. Dağıtım ve Proxy Entegrasyonu
- [ ] Cloudflare üzerinde `ulgencs3` adıyla bir Worker oluştur.
- [ ] Reverse Proxy kodunu yükle ve `https://ulgencs3.workers.dev/tr` adresini test et.
- [ ] CloudStream içine depoyu ekleyip tüm eklentilerin listelendiğini doğrula.

## 5. Otomasyon ve Kendi Kendini Onarma (En Son Aşama)
- [ ] Depoya dinamik domain yönetimi için `domains.json` dosyasını ekle.
- [ ] Eklenti sınıflarındaki statik `mainUrl` mantığını `domains.json` üzerinden beslenecek şekilde refactor et.
- [ ] Sitelerin HTTP durum kodlarını ve yönlendirmelerini periyodik tarayan Python betiğini (`health_check.py`) yaz.
- [ ] GitHub Actions üzerinde her 12 saatte bir çalışacak cron iş akışını yapılandır.

---

# 4. NASIL_YAPILIR.md

## Eklenti Modülünün Yapılandırılması
`settings.gradle.kts` dosyasına yeni eklenti tanımlanır:
```kotlin
include(":OrnekAnimeProvider")