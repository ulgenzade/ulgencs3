Otomatik Güncelleme ve Kendi Kendini Onarma Mimarisi
Bu doküman, yol haritasındaki Faz 7 kapsamında uygulanacak otomatik bakım, dinamik domain çözümü ve periyodik sağlık kontrollerini tanımlar.

1. Dinamik Domain Çözümleme (Dynamic Domain Resolver)
Eklentilerin içine sabit domain yazılmaz. Deponun master dalında tutulan bir domains.json dosyasından anlık domain çekilir:

domains.json Örneği:
{
  "filmmodu": "https://filmmodu12.com",
  "dizipal": "https://dizipal842.com"
}

Kotlin Tarafında Dinamik Okuma:

class FilmModuProvider : MainProvider() {
    override var name = "FilmModuTR"
    override var mainUrl = "https://filmmodu12.com" // Fallback url

    override suspend fun init() {
        try {
            val config = app.get("https://raw.githubusercontent.com/<KULLANICI>/<DEPO>/master/domains.json").text
            val dynamicUrl = JSONObject(config).optString("filmmodu")
            if (dynamicUrl.isNotEmpty()) {
                mainUrl = dynamicUrl
            }
        } catch (e: Exception) {
            // Ağ hatasında fallback URL devrede kalır
        }
    }
}

2. GitHub Actions Otomatik Sağlık Taraması (Health Check Cron)
Sitelerin çalışıp çalışmadığını her 12 saatte bir denetleyen .github/workflows/health-check.yml iş akışı:

name: Health Check & Auto-Resolve

on:
  schedule:
    - cron: '0 */12 * * *'
  workflow_dispatch:

jobs:
  check-providers:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Repository
        uses: actions/checkout@v4

      - name: Setup Python
        uses: actions/setup-python@v5
        with:
          python-version: '3.10'

      - name: Run Health Checker Script
        run: |
          pip install requests beautifulsoup4
          python scripts/health_check.py

      - name: Commit Updated Domains
        run: |
          git config --global user.name "github-actions[bot]"
          git config --global user.email "github-actions[bot]@users.noreply.github.com"
          git add domains.json
          git diff --quiet && git diff --staged --quiet || (git commit -m "chore: auto-update domain addresses [skip ci]" && git push)

          3. Domain Takip ve Güncelleme Betiği (scripts/health_check.py)
Yönlendirmeleri takip ederek yeni domaini bulan ve JSON dosyasını güncelleyen Python betiği:

import json
import requests

DOMAINS_FILE = "domains.json"

def get_redirected_url(url):
    try:
        response = requests.head(url, allow_redirects=True, timeout=10, headers={
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        })
        return response.url.rstrip('/')
    except Exception:
        return None

def main():
    with open(DOMAINS_FILE, "r", encoding="utf-8") as f:
        domains = json.load(f)

    updated = False
    for provider, current_url in domains.items():
        latest_url = get_redirected_url(current_url)
        if latest_url and latest_url != current_url:
            print(f"[!] {provider} domain güncellendi: {current_url} -> {latest_url}")
            domains[provider] = latest_url
            updated = True
        else:
            print(f"[OK] {provider} adresi stabil: {current_url}")

    if updated:
        with open(DOMAINS_FILE, "w", encoding="utf-8") as f:
            json.dump(domains, f, indent=2, ensure_ascii=False)

if __name__ == "__main__":
    main()