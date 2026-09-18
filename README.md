# Türkçe CloudStream 3 Eklenti Havuzu

Türkçe anime, dizi, film, belgesel ve çizgi dizi platformları için CloudStream 3 eklenti koleksiyonu. **28 aktif eklenti**, tümü `domains.json` ile dinamik domain yönetimine bağlı.

## 📦 Depoyu CloudStream'e Eklemek

### Yöntem 1: Tek Tıkla Otomatik Ekle (En Kolay)
Telefonunuzdan doğrudan aşağıdaki butona dokunun, CloudStream açılıp depoyu otomatik yükleyecektir:

👉 **[📲 Depoyu CloudStream'e Otomatik Ekle](https://recloudstream.github.io/http-protocol-redirector?r=cloudstreamrepo://raw.githubusercontent.com/ulgenzade/ulgencs3/builds/repo.json)**

---

### Yöntem 2: Kısa Kod (TV ve Kumanda İçin)
CloudStream uygulamasında **Eklentiler → + Depo Ekle** alanına sadece şu kısa kodu yazın:

```text
!UlgenCs3 / !ulgencs3
```
*(Not: Başındaki `!` işareti, Türkiye'de engelli olan cutt.ly yerine doğrudan açık olan py.md servisini kullanmasını sağlar).*

---

### Yöntem 3: Doğrudan URL ile Ekleme
Eğer tam URL yapıştırmak isterseniz:

* **CDN Bağlantısı (Türkiye ISS'leri için Önerilen - VPN Gerektirmez):**
  ```text
  https://fastly.jsdelivr.net/gh/ulgenzade/ulgencs3@builds/repo.json
  ```
* **GitHub Doğrudan:**
  ```text
  https://raw.githubusercontent.com/ulgenzade/ulgencs3/builds/repo.json
  ```


## 🎌 Anime Platformları (6 Eklenti)

| Eklenti | Site | Özellikler |
|---|---|---|
| **TurkAnimeProvider** | turkanime.tv | Tüm fansub/fandomlar, ArtPlayer M3U8, AES decrypt |
| **AnimeCixProvider** | animecix.tv | REST API, çoklu kalite (1080p–360p), bölüm başlıkları |
| **AnizmProvider** | anizm.net | Cloudflare bypass, çoklu gömülü oynatıcı |
| **AniziumProvider** | anizium.co | 4K anime keyfi, TR dublaj, VTT altyazı |
| **OpenAnimeProvider** | openani.me | Next.js API & DOM fallback, doğrudan HLS |
| **TrAnimeIzleProvider** | tranimeizle.io | Çoklu sunucu, Türkçe altyazı |

## 🎬 Dizi, Film, Belgesel & Çizgi Dizi (22 Eklenti)

| Eklenti | Site | Özellikler |
|---|---|---|
| **DiziPalProvider** | dizipal2132.com | Netflix/Exxen/BluTV/Gain, Imagestoo API |
| **HDFilmCehennemiProvider** | hdfilmcehennemi.nl | Devasa arşiv, IMDB 7+, deobfuscation |
| **FilmModuProvider** | filmmodu.one | Full HD, Türkçe altyazı ve dublaj |
| **DiziBoxProvider** | dizibox.live | Yabancı diziler, son bölümler |
| **DizillaProvider** | dizilla.now | Güncel diziler, Pichive oynatıcı |
| **SezonlukDiziProvider** | sezonlukdizi5.com | Kapsamlı dizi ve sezon arşivi |
| **DiziMomProvider** | dizimom.tv | Popüler diziler, hızlı oynatıcı |
| **FilmMakinesiProvider** | filmmakinesi.pw | 1080p ve 4K sinema filmleri |
| **FullHDFilmizleseneProvider** | fullhdfilmizlesene.pw | Geniş film arşivi, Türkçe dublaj |
| **InatBoxProvider** | inatbox.org | Canlı TV, dizi ve filmler |
| **RecTVProvider** | rectv.net | Canlı yayın, spor ve dizi kanalları |
| **SelcukFlixProvider** | selcukflix.com | Dizi, film ve canlı maç |
| **WebteIzleProvider** | webteizle.vip | Yüksek bitrateli film keyfi |
| **CizgiMaxProvider** | cizgimax.online | Nostaljik ve güncel çizgi diziler |
| **BelgeselXProvider** | belgeselx.com | Türkçe dublajlı bilim ve doğa belgeselleri |
| **DdiziProvider** | ddizi.pro | Yerli ve yabancı diziler |
| **DiziKoreaProvider** | dizikorea.com | Kore dizileri ve Asya dramaları |
| **JetFilmizleProvider** | jetfilmizle.mobi | Hızlı ve kesintisiz film izleme |
| **KultFilmlerProvider** | kultfilmler.com | Kült ve klasik sinema arşivi |
| **SetFilmIzleProvider** | setfilmizle.vip | Vizyon filmleri ve popüler diziler |
| **SinemaCXProvider** | sinemacx.com | HD kalitede sinema filmleri |
| **SinewixProvider** | sinewix.com | Zengin dizi ve film platformu |

## ⚙️ Dinamik Domain Yönetimi

Tüm eklentiler kök dizindeki [`domains.json`](domains.json) dosyasından domain adresini dinamik olarak okur. Site adresi değiştiğinde eklentileri yeniden derlemeye gerek kalmadan sadece `domains.json` güncellenir.

## 🛠️ Geliştirme

```bash
# Tüm eklentileri derle
./gradlew makePluginsJson

# Belirli bir eklentiyi ADB ile yükle
./gradlew AnimeCixProvider:deployWithAdb
```

## ⚖️ Yasal Uyarı

Bu repo video barındırmaz. Yalnızca genel ağa açık web sayfalarını istemci tarafında ayrıştıran bir araçtır. Tüm içerikler ilgili platformların sunucularından doğrudan sunulmaktadır.
