import os
import sys
import json
import zipfile

def generate_plugins_json(builds_dir, repo_slug="ulgenzade/ulgencs3"):
    plugins = []
    
    metadata = {
        "TurkAnimeProvider": {
            "name": "Türk Anime TV",
            "description": "Türkiye'nin en büyük anime arşivi. Tüm fandom/çeviri grupları (AniSekai, Benihime, PuzzleSubs vb.) ve tüm medya oynatıcıları (Sibnet, OK.ru, Mail.ru, Doodstream, VOE, Vudea, M3U8 vb.) desteklenir.",
            "tvTypes": ["Anime", "AnimeMovie", "OVA"],
            "iconUrl": "https://www.turkanime.tv/imajlar/favicon.ico"
        },
        "AnimeCixProvider": {
            "name": "AnimeCiX",
            "description": "AnimeciX resmi REST API ve TauVideo oynatıcı desteği ile yüksek hızlı anime akışı.",
            "tvTypes": ["Anime", "AnimeMovie", "OVA"],
            "iconUrl": "https://animecix.tv/favicon.ico"
        },
        "AnizmProvider": {
            "name": "Anizm",
            "description": "Anizm anime arşivi. Cloudflare Turnstile koruması aşımı ve çoklu gömülü oynatıcı desteği.",
            "tvTypes": ["Anime", "AnimeMovie", "OVA"],
            "iconUrl": "https://anizm.net/favicon.ico"
        },
        "AniziumProvider": {
            "name": "Anizium",
            "description": "Anizium anime platformu. 4K Ultra HD (2160p) video ve zengin alternatif oynatıcı desteği.",
            "tvTypes": ["Anime", "AnimeMovie", "OVA"],
            "iconUrl": "https://anizium.co/favicon.ico"
        },
        "OpenAnimeProvider": {
            "name": "OpenAnime",
            "description": "OpenAnime Next.js modern anime platformu. Doğrudan HLS m3u8 akışları ve alternatif oynatıcılar.",
            "tvTypes": ["Anime", "AnimeMovie", "OVA"],
            "iconUrl": "https://openani.me/favicon.ico"
        },
        "TrAnimeIzleProvider": {
            "name": "TrAnimeİzle",
            "description": "TrAnimeİzle arşivi. Çoklu alternatif sunucu ve harici oynatıcı desteği.",
            "tvTypes": ["Anime", "AnimeMovie", "OVA"],
            "iconUrl": "https://www.tranimeizle.io/favicon.ico"
        },
        "FilmModuProvider": {
            "name": "FilmModu",
            "description": "Türkçe Dublaj ve Altyazılı Full HD Film platformu. Doğrudan yüksek hızlı M3U8 video akışı ve Türkçe altyazı desteği.",
            "tvTypes": ["Movie"],
            "iconUrl": "https://www.filmmodu.one/favicon.ico"
        },
        "HDFilmCehennemiProvider": {
            "name": "HDFilmCehennemi",
            "description": "Türkiye'nin en büyük film ve yabancı dizi arşivi. IMDB 7+ filmler, popüler diziler ve alternatif sunucular.",
            "tvTypes": ["Movie", "TvSeries"],
            "iconUrl": "https://www.hdfilmcehennemi.nl/favicon.ico"
        },
        "DiziPalProvider": {
            "name": "DiziPal",
            "description": "DiziPal güncel yerli/yabancı dizi ve film arşivi. Netflix, Exxen, BluTV, Disney+, Prime Video platform içerikleri.",
            "tvTypes": ["TvSeries", "Movie"],
            "iconUrl": "https://dizipal2132.com/favicon.ico"
        },
        "DiziBoxProvider": {
            "name": "DiziBox",
            "description": "Türkiye'nin en popüler yabancı dizi arşivi. Güncel bölümler, Türkçe altyazı ve dublaj seçenekleri.",
            "tvTypes": ["TvSeries"],
            "iconUrl": "https://www.google.com/s2/favicons?domain=dizibox.live&sz=128"
        },
        "DizillaProvider": {
            "name": "Dizilla",
            "description": "Yabancı ve yerli popüler diziler, yüksek hızlı alternatif oynatıcılar ve kesintisiz akış.",
            "tvTypes": ["TvSeries"],
            "iconUrl": "https://www.google.com/s2/favicons?domain=dizilla.club&sz=128"
        },
        "SezonlukDiziProvider": {
            "name": "SezonlukDizi",
            "description": "Tüm sezon ve bölümlerin eksiksiz yer aldığı zengin yabancı dizi arşivi.",
            "tvTypes": ["TvSeries"],
            "iconUrl": "https://www.google.com/s2/favicons?domain=sezonlukdizi5.com&sz=128"
        },
        "DiziMomProvider": {
            "name": "DiziMom",
            "description": "Yabancı diziler, animasyonlar ve çizgi diziler.",
            "tvTypes": ["TvSeries"],
            "iconUrl": "https://www.google.com/s2/favicons?domain=dizimom.tv&sz=128"
        },
        "FilmMakinesiProvider": {
            "name": "FilmMakinesi",
            "description": "1080p Türkçe dublaj ve altyazılı film ve yabancı dizi platformu.",
            "tvTypes": ["Movie", "TvSeries"],
            "iconUrl": "https://www.google.com/s2/favicons?domain=filmmakinesi.pw&sz=128"
        },
        "FullHDFilmizleseneProvider": {
            "name": "FullHDFilmizlesene",
            "description": "Türkiye'nin en büyük ve en köklü Full HD film arşivi.",
            "tvTypes": ["Movie"],
            "iconUrl": "https://www.google.com/s2/favicons?domain=fullhdfilmizlesene.pw&sz=128"
        },
        "InatBoxProvider": {
            "name": "InatBox",
            "description": "Canlı televizyon kanalları, spor, belgesel ve sinema yayınları.",
            "tvTypes": ["Live", "TvSeries", "Movie"],
            "iconUrl": "https://www.google.com/s2/favicons?domain=inatbox.cfd&sz=128"
        },
        "RecTVProvider": {
            "name": "RecTV",
            "description": "Canlı TV kanalları, canlı spor müsabakaları, dizi ve sinema filmleri.",
            "tvTypes": ["Live", "TvSeries", "Movie"],
            "iconUrl": "https://www.google.com/s2/favicons?domain=rectv.me&sz=128"
        },
        "SelcukFlixProvider": {
            "name": "SelcukFlix",
            "description": "Film, dizi ve canlı spor yayın platformu.",
            "tvTypes": ["Live", "TvSeries", "Movie"],
            "iconUrl": "https://www.google.com/s2/favicons?domain=selcuksportshd.com&sz=128"
        },
        "WebteIzleProvider": {
            "name": "WebteIzle",
            "description": "Yüksek kaliteli film arşivi, Türkçe dublaj ve altyazı desteği.",
            "tvTypes": ["Movie"],
            "iconUrl": "https://www.google.com/s2/favicons?domain=webteizle.vip&sz=128"
        },
        "CizgiMaxProvider": {
            "name": "CizgiMax",
            "description": "Nostaljik ve güncel çizgi filmler ve animasyon dizileri.",
            "tvTypes": ["TvSeries", "Anime"],
            "iconUrl": "https://www.google.com/s2/favicons?domain=cizgimax.online&sz=128"
        },
        "BelgeselXProvider": {
            "name": "BelgeselX",
            "description": "Türkçe dublajlı ve altyazılı zengin belgesel arşivi.",
            "tvTypes": ["Documentary", "TvSeries"],
            "iconUrl": "https://www.google.com/s2/favicons?domain=belgeselx.com&sz=128"
        },
        "DdiziProvider": {
            "name": "Ddizi",
            "description": "Klasik ve güncel yerli Türk dizileri arşivi.",
            "tvTypes": ["TvSeries"],
            "iconUrl": "https://www.google.com/s2/favicons?domain=ddizi.pro&sz=128"
        },
        "DiziKoreaProvider": {
            "name": "DiziKorea",
            "description": "Kore dizileri (K-Drama) ve Asya yapımları.",
            "tvTypes": ["AsianDrama", "TvSeries"],
            "iconUrl": "https://www.google.com/s2/favicons?domain=dizikorea.com&sz=128"
        },
        "JetFilmizleProvider": {
            "name": "JetFilmizle",
            "description": "Güncel sinema filmleri, Türkçe dublaj ve altyazı desteği.",
            "tvTypes": ["Movie"],
            "iconUrl": "https://www.google.com/s2/favicons?domain=jetfilmizle.mobi&sz=128"
        },
        "KultFilmlerProvider": {
            "name": "Kült Filmler",
            "description": "IMDb yüksek puanlı kült filmler ve sinema başyapıtları.",
            "tvTypes": ["Movie"],
            "iconUrl": "https://www.google.com/s2/favicons?domain=kultfilmler.com&sz=128"
        },
        "SetFilmIzleProvider": {
            "name": "SetFilmİzle",
            "description": "Yüksek hızlı film ve yabancı dizi izleme platformu.",
            "tvTypes": ["Movie", "TvSeries"],
            "iconUrl": "https://www.google.com/s2/favicons?domain=setfilmizle.vip&sz=128"
        },
        "SinemaCXProvider": {
            "name": "SinemaCX",
            "description": "Sinema filmleri ve popüler yabancı yapımlar.",
            "tvTypes": ["Movie"],
            "iconUrl": "https://www.google.com/s2/favicons?domain=sinemacx.com&sz=128"
        },
        "SinewixProvider": {
            "name": "SineWix",
            "description": "Full HD film ve dizi izleme arşivi.",
            "tvTypes": ["Movie", "TvSeries"],
            "iconUrl": "https://www.google.com/s2/favicons?domain=sinewix.com&sz=128"
        }
    }

    for fname in sorted(os.listdir(builds_dir)):
        if not fname.endswith(".cs3"):
            continue

        fpath = os.path.join(builds_dir, fname)
        internal_name = fname[:-4]
        file_size = os.path.getsize(fpath)

        manifest = {}
        try:
            with zipfile.ZipFile(fpath, 'r') as z:
                if 'manifest.json' in z.namelist():
                    manifest = json.loads(z.read('manifest.json').decode('utf-8'))
        except Exception as e:
            print(f"Warning: Could not read manifest from {fname}: {e}")

        meta = metadata.get(internal_name, {})
        entry = {
            "name": meta.get("name", manifest.get("name", internal_name)),
            "internalName": internal_name,
            "pluginClassName": manifest.get("pluginClassName", f"com.ulgencs3.{internal_name.lower()}.{internal_name}"),
            "version": manifest.get("version", 1),
            "url": f"https://raw.githubusercontent.com/{repo_slug}/builds/{fname}",
            "apiVersion": 1,
            "fileSize": file_size,
            "status": 1,
            "language": "tr",
            "tvTypes": meta.get("tvTypes", ["Anime"]),
            "authors": ["ulgenzade"],
            "description": meta.get("description", f"{internal_name} Cloudstream eklentisi."),
            "iconUrl": meta.get("iconUrl", "")
        }
        plugins.append(entry)

    out_path = os.path.join(builds_dir, "plugins.json")
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(plugins, f, indent=2, ensure_ascii=False)
    
    print(f"Successfully generated {out_path} with {len(plugins)} plugins.")

if __name__ == "__main__":
    b_dir = sys.argv[1] if len(sys.argv) > 1 else "builds"
    r_slug = sys.argv[2] if len(sys.argv) > 2 else os.getenv("GITHUB_REPOSITORY", "ulgenzade/ulgencs3")
    generate_plugins_json(b_dir, r_slug)
