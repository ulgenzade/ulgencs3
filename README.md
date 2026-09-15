# UlgenCS3 — Türkçe Anime CloudStream 3 Eklenti Havuzu

Türkçe anime platformları için CloudStream 3 eklenti koleksiyonu.

## 📦 Depoyu CloudStream'e Eklemek

CloudStream → Depo Ekle → aşağıdaki URL'yi yapıştır:

```
https://raw.githubusercontent.com/ulgenzade/ulgencs3/master/repo.json
```

## 🎌 Desteklenen Platformlar

| Eklenti | Site | Özellik |
|---|---|---|
| TurkAnimeProvider | turkanime.tv | Çok embed, Türkçe altyazı/dublaj |
| AnimeCixProvider | animecix.tv | REST API, kalite seçimi |
| AnizmProvider | anizm.net | Türkçe altyazı |
| AniziumProvider | anizium.co | **4K desteği**, Türkçe dublaj |
| OpenAnimeProvider | openani.me | Next.js API tabanlı |
| TrAnimeIzleProvider | tranimeizle.io | Türkçe altyazı |

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
