# UlgenCS3 — Türkçe Anime CloudStream 3 Eklenti Havuzu

Türkçe anime platformları için CloudStream 3 eklenti koleksiyonu.

## 📦 Depoyu CloudStream'e Eklemek

CloudStream → Depo Ekle → aşağıdaki URL'yi yapıştır:

```
https://raw.githubusercontent.com/ulgenzade/ulgencs3/master/repo.json
```

## 🎌 Desteklenen Platformlar

| Eklenti | Tür | Site | Özellik |
|---|---|---|---|
| **TurkAnimeProvider** | Anime | turkanime.tv | Tüm Fansub/fandomlar, ArtPlayer M3U8, AES decrypt |
| **AnimeCixProvider** | Anime | animecix.tv | REST API, TauVideo çoklu kalite (1080p-360p) |
| **AnizmProvider** | Anime | anizm.net | Cloudflare Turnstile bypass, çoklu gömülü oynatıcı |
| **AniziumProvider** | Anime | anizium.co | **4K Ultra HD desteği**, alternatif oynatıcılar |
| **OpenAnimeProvider** | Anime | openani.me | Next.js API & DOM fallback, doğrudan HLS akışları |
| **TrAnimeIzleProvider** | Anime | tranimeizle.io | Çoklu alternatif sunucular ve altyazı desteği |
| **FilmModuProvider** | Film | filmmodu.one | Yüksek hızlı doğrudan M3U8 akışları, Türkçe altyazı |
| **HDFilmCehennemiProvider** | Film/Dizi | hdfilmcehennemi.nl | Devasa arşiv, IMDB 7+, deobfuscation çözücü |
| **DiziPalProvider** | Dizi/Film | dizipal2132.com | Netflix/Exxen/BluTV/Gain platformları, Imagestoo API |

## 🛠️ Geliştirme

```bash
# Tüm eklentileri derle
./gradlew makePluginsJson

# Belirli bir eklentiyi ADB ile yükle
./gradlew TurkAnimeProvider:deployWithAdb
```

## ⚙️ Otomatik Güncelleme

GitHub Actions her 12 saatte bir `domains.json` dosyasını kontrol eder ve
domain değişikliklerini otomatik olarak günceller.

## ⚖️ Yasal Uyarı

Bu repo video barındırmaz. Yalnızca genel ağa açık web sayfalarını
istemci tarafında ayrıştıran bir araçtır. Tüm içerikler ilgili
platformların sunucularından doğrudan sunulmaktadır.
