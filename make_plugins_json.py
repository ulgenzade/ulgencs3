import os
import sys
import json
import zipfile

def generate_plugins_json(builds_dir, repo_slug="ulgenzade/ulgencs3"):
    plugins = []
    
    metadata = {
        "TurkAnimeProvider": {
            "name": "Türk Anime TV",
            "description": "Türkiye'nin en büyük anime arşivi.",
            "tvTypes": ["Anime", "AnimeMovie", "OVA"],
            "iconUrl": "https://www.turkanime.tv/imajlar/favicon.ico"
        },
        "AnimeCixProvider": {
            "name": "AnimeCiX",
            "description": "Geniş anime ve çizgi dizi platformu.",
            "tvTypes": ["Anime", "AnimeMovie", "OVA"],
            "iconUrl": "https://animecix.tv/favicon.ico"
        },
        "AnizmProvider": {
            "name": "Anizm",
            "description": "Popüler güncel anime arşivi.",
            "tvTypes": ["Anime", "AnimeMovie", "OVA"],
            "iconUrl": "https://anizm.net/favicon.ico"
        },
        "AniziumProvider": {
            "name": "Anizium",
            "description": "4K anime keyfi, çoklu altyazı ve TR dublaj.",
            "tvTypes": ["Anime", "AnimeMovie", "OVA"],
            "iconUrl": "https://anizium.co/assets/images/favicon.png"
        },
        "OpenAnimeProvider": {
            "name": "OpenAnime",
            "description": "Modern ve hızlı anime izleme.",
            "tvTypes": ["Anime", "AnimeMovie", "OVA"],
            "iconUrl": "https://openani.me/favicon.ico"
        },
        "TrAnimeIzleProvider": {
            "name": "TrAnimeİzle",
            "description": "Geniş Türkçe altyazılı anime koleksiyonu.",
            "tvTypes": ["Anime", "AnimeMovie", "OVA"],
            "iconUrl": "https://www.tranimeizle.io/favicon.ico"
        },
        "FilmModuProvider": {
            "name": "FilmModu",
            "description": "Full HD Türkçe dublaj ve altyazılı filmler.",
            "tvTypes": ["Movie"],
            "iconUrl": "https://www.filmmodu.one/favicon.ico"
        },
        "HDFilmCehennemiProvider": {
            "name": "HDFilmCehennemi",
            "description": "En popüler yabancı dizi ve sinema arşivi.",
            "tvTypes": ["Movie", "TvSeries"],
            "iconUrl": "https://www.hdfilmcehennemi.nl/favicon.ico"
        },
        "DiziPalProvider": {
            "name": "DiziPal",
            "description": "Dijital platform dizileri ve filmleri.",
            "tvTypes": ["TvSeries", "Movie"],
            "iconUrl": "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAV&fallback_opts=TYPE,SIZE,URL&url=https://dizipal2132.com&size=128"
        },
        "DiziBoxProvider": {
            "name": "DiziBox",
            "description": "Yabancı diziler, dublaj ve altyazı seçenekleri.",
            "tvTypes": ["TvSeries"],
            "iconUrl": "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAV&fallback_opts=TYPE,SIZE,URL&url=https://www.dizibox.live&size=128"
        },
        "DizillaProvider": {
            "name": "Dizilla",
            "description": "Popüler yabancı ve yerli diziler.",
            "tvTypes": ["TvSeries"],
            "iconUrl": "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAV&fallback_opts=TYPE,SIZE,URL&url=https://dizilla.now&size=128"
        },
        "SezonlukDiziProvider": {
            "name": "SezonlukDizi",
            "description": "Eksiksiz sezonluk yabancı dizi arşivi.",
            "tvTypes": ["TvSeries"],
            "iconUrl": "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAV&fallback_opts=TYPE,SIZE,URL&url=https://sezonlukdizi5.com&size=128"
        },
        "DiziMomProvider": {
            "name": "DiziMom",
            "description": "Güncel yabancı diziler ve animasyonlar.",
            "tvTypes": ["TvSeries"],
            "iconUrl": "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAV&fallback_opts=TYPE,SIZE,URL&url=https://dizimom.tv&size=128"
        },
        "FilmMakinesiProvider": {
            "name": "FilmMakinesi",
            "description": "1080p ve 4K yerli ve yabancı filmler.",
            "tvTypes": ["Movie", "TvSeries"],
            "iconUrl": "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAV&fallback_opts=TYPE,SIZE,URL&url=https://filmmakinesi.pw&size=128"
        },
        "FullHDFilmizleseneProvider": {
            "name": "FullHDFilmizlesene",
            "description": "Geniş Full HD sinema arşivi.",
            "tvTypes": ["Movie"],
            "iconUrl": "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAV&fallback_opts=TYPE,SIZE,URL&url=https://www.fullhdfilmizlesene.pw&size=128"
        },
        "InatBoxProvider": {
            "name": "InatBox",
            "description": "Canlı TV, spor ve sinema yayınları.",
            "tvTypes": ["Live", "Movie", "TvSeries"],
            "iconUrl": "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAV&fallback_opts=TYPE,SIZE,URL&url=https://inatbox.org&size=128"
        },
        "RecTVProvider": {
            "name": "RecTV",
            "description": "Canlı TV kanalları, dizi ve film akışları.",
            "tvTypes": ["Live", "TvSeries", "Movie"],
            "iconUrl": "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAV&fallback_opts=TYPE,SIZE,URL&url=https://rectv.net&size=128"
        },
        "SelcukFlixProvider": {
            "name": "SelcukFlix",
            "description": "Dizi, film ve özel canlı yayınlar.",
            "tvTypes": ["Live", "TvSeries", "Movie"],
            "iconUrl": "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAV&fallback_opts=TYPE,SIZE,URL&url=https://selcukflix.com&size=128"
        },
        "WebteIzleProvider": {
            "name": "WebteIzle",
            "description": "Yüksek kaliteli film ve diziler.",
            "tvTypes": ["Movie", "TvSeries"],
            "iconUrl": "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAV&fallback_opts=TYPE,SIZE,URL&url=https://webteizle.vip&size=128"
        },
        "CizgiMaxProvider": {
            "name": "CizgiMax",
            "description": "Nostaljik ve güncel çizgi filmler.",
            "tvTypes": ["Cartoon", "Anime"],
            "iconUrl": "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAV&fallback_opts=TYPE,SIZE,URL&url=https://cizgimax.online&size=128"
        },
        "BelgeselXProvider": {
            "name": "BelgeselX",
            "description": "Türkçe dublajlı doğa, bilim ve tarih belgeselleri.",
            "tvTypes": ["Documentary"],
            "iconUrl": "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAV&fallback_opts=TYPE,SIZE,URL&url=https://belgeselx.com&size=128"
        },
        "DdiziProvider": {
            "name": "Ddizi",
            "description": "Klasik ve güncel yerli/yabancı diziler.",
            "tvTypes": ["TvSeries"],
            "iconUrl": "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAV&fallback_opts=TYPE,SIZE,URL&url=https://www.ddizi.pro&size=128"
        },
        "DiziKoreaProvider": {
            "name": "DiziKorea",
            "description": "Asya dizileri ve K-Drama arşivi.",
            "tvTypes": ["TvSeries"],
            "iconUrl": "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAV&fallback_opts=TYPE,SIZE,URL&url=https://dizikorea.com&size=128"
        },
        "JetFilmizleProvider": {
            "name": "JetFilmizle",
            "description": "Hızlı ve kesintisiz film izleme.",
            "tvTypes": ["Movie"],
            "iconUrl": "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAV&fallback_opts=TYPE,SIZE,URL&url=https://jetfilmizle.mobi&size=128"
        },
        "KultFilmlerProvider": {
            "name": "Kült Filmler",
            "description": "Kült ve klasik sinema filmleri.",
            "tvTypes": ["Movie"],
            "iconUrl": "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAV&fallback_opts=TYPE,SIZE,URL&url=https://kultfilmler.com&size=128"
        },
        "SetFilmIzleProvider": {
            "name": "SetFilmİzle",
            "description": "Güncel vizyon filmleri ve yabancı diziler.",
            "tvTypes": ["Movie", "TvSeries"],
            "iconUrl": "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAV&fallback_opts=TYPE,SIZE,URL&url=https://setfilmizle.vip&size=128"
        },
        "SinemaCXProvider": {
            "name": "SinemaCX",
            "description": "Yüksek çözünürlüklü sinema filmleri.",
            "tvTypes": ["Movie"],
            "iconUrl": "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAV&fallback_opts=TYPE,SIZE,URL&url=https://sinemacx.com&size=128"
        },
        "SinewixProvider": {
            "name": "SineWix",
            "description": "Film, dizi ve belgesel platformu.",
            "tvTypes": ["Movie", "TvSeries"],
            "iconUrl": "https://t1.gstatic.com/faviconV2?client=SOCIAL&type=FAV&fallback_opts=TYPE,SIZE,URL&url=https://sinewix.com&size=128"
        }
    }

    if not os.path.exists(builds_dir):
        print(f"Error: builds directory '{builds_dir}' not found.")
        sys.exit(1)

    for filename in sorted(os.listdir(builds_dir)):
        if filename.endswith(".cs3"):
            internal_name = filename[:-4]
            filepath = os.path.join(builds_dir, filename)
            filesize = os.path.getsize(filepath)

            meta = metadata.get(internal_name, {})
            name = meta.get("name", internal_name)
            description = meta.get("description", "Türkçe içerik sağlayıcısı.")
            tv_types = meta.get("tvTypes", ["TvSeries", "Movie"])
            icon_url = meta.get("iconUrl", "https://raw.githubusercontent.com/ulgenzade/ulgencs3/master/icon.png")

            # cs3 zip dosyasından hash ve manifest oku
            class_name = f"com.ulgencs3.{internal_name.lower().replace('provider','')}.{internal_name}"
            version = 1
            try:
                with zipfile.ZipFile(filepath, 'r') as zip_ref:
                    manifest_data = None
                    for name_in_zip in zip_ref.namelist():
                        if name_in_zip.endswith("manifest.json"):
                            manifest_data = json.loads(zip_ref.read(name_in_zip).decode("utf-8"))
                            break
                    if manifest_data:
                        class_name = manifest_data.get("pluginClassName", class_name)
                        version = manifest_data.get("version", version)
            except Exception as e:
                pass

            url = f"https://raw.githubusercontent.com/{repo_slug}/builds/{filename}"

            plugin_entry = {
                "name": name,
                "internalName": internal_name,
                "pluginClassName": class_name,
                "version": version,
                "url": url,
                "iconUrl": icon_url,
                "description": description,
                "tvTypes": tv_types,
                "language": "tr",
                "fileSize": filesize,
                "status": 1
            }
            plugins.append(plugin_entry)

    if not plugins:
        # Fallback: Eğer yerel dizinde .cs3 dosyaları henüz yoksa metadata'dan 28 eklentinin tamamını oluştur
        for internal_name, meta in sorted(metadata.items()):
            name = meta.get("name", internal_name)
            description = meta.get("description", "Türkçe içerik sağlayıcısı.")
            tv_types = meta.get("tvTypes", ["TvSeries", "Movie"])
            icon_url = meta.get("iconUrl", "https://raw.githubusercontent.com/ulgenzade/ulgencs3/master/icon.png")
            pkg_name = internal_name.lower().replace('provider', '')
            class_name = f"com.ulgencs3.{pkg_name}.{internal_name}"
            url = f"https://raw.githubusercontent.com/{repo_slug}/builds/{internal_name}.cs3"

            plugin_entry = {
                "name": name,
                "internalName": internal_name,
                "pluginClassName": class_name,
                "version": 1,
                "url": url,
                "iconUrl": icon_url,
                "description": description,
                "tvTypes": tv_types,
                "language": "tr",
                "fileSize": 150000,
                "status": 1
            }
            plugins.append(plugin_entry)

    out_path = os.path.join(builds_dir, "plugins.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(plugins, f, ensure_ascii=False, indent=2)

    print(f"Successfully generated {out_path} with {len(plugins)} plugins.")

    # repo.json dosyasını da builds dizinine kopyala / oluştur
    repo_dest = os.path.join(builds_dir, "repo.json")
    repo_src = os.path.join(os.path.dirname(os.path.abspath(__file__)), "repo.json")
    if os.path.exists(repo_src):
        import shutil
        shutil.copy2(repo_src, repo_dest)
        print(f"Copied repo.json to {repo_dest}")
    else:
        repo_data = {
            "name": "UlgenCS3",
            "description": "Türkçe Anime Eklentileri — Anime, Dizi ve Film",
            "manifestVersion": 1,
            "pluginLists": [
                f"https://raw.githubusercontent.com/{repo_slug}/builds/plugins.json"
            ]
        }
        with open(repo_dest, "w", encoding="utf-8") as f:
            json.dump(repo_data, f, ensure_ascii=False, indent=2)
        print(f"Generated repo.json at {repo_dest}")

if __name__ == "__main__":
    b_dir = sys.argv[1] if len(sys.argv) > 1 else "."
    r_slug = sys.argv[2] if len(sys.argv) > 2 else "ulgenzade/ulgencs3"
    generate_plugins_json(b_dir, r_slug)
