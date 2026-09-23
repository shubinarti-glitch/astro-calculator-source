# -*- coding: utf-8 -*-
"""SEO-страницы: авторские трактовки как отдельные HTML-страницы для поисковиков.

240 страниц (10 планет × 12 знаков + 10 планет × 12 домов) + каталог,
sitemap.xml и robots.txt. Тексты берутся через аксессоры interpretations
(учитывают правки из админки). RU URL сохранены; EN доступен через ?lang=en.
"""
from __future__ import annotations
from .editorial_data import text as _editorial_text
from html import escape

from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import HTMLResponse, PlainTextResponse, Response

from . import constants, interpretations

router = APIRouter()

_PLANET_SLUG = {
    "Sun": "solntse", "Moon": "luna", "Mercury": "merkuriy", "Venus": "venera",
    "Mars": "mars", "Jupiter": "yupiter", "Saturn": "saturn", "Uranus": "uran",
    "Neptune": "neptun", "Pluto": "pluton",
}
_SIGN_SLUG = {
    "Ari": "ovne", "Tau": "teltse", "Gem": "bliznetsah", "Can": "rake",
    "Leo": "lve", "Vir": "deve", "Lib": "vesah", "Sco": "skorpione",
    "Sag": "streltse", "Cap": "kozeroge", "Aqu": "vodolee", "Pis": "rybah",
}


def _planet_ru(planet: str) -> str:
    return constants.POINTS[planet]["ru"]


def _pages() -> dict[str, dict]:
    """slug -> {title, h1, text()} для всех 240 страниц. Тексты лениво (правки админки)."""
    pages = {}
    for planet, pslug in _PLANET_SLUG.items():
        for sign, sslug in _SIGN_SLUG.items():
            h1 = f'{_planet_ru(planet)} {constants.sign_in(sign)}'
            pages[f'{pslug}-v-{sslug}'] = {
                "h1": h1,
                "title": f"{h1}{_editorial_text('seo.4659faf409bc70a4b1ecb86261930ad93dfcba78ec9ead2a4794c02824ed9c3c')}",
                "get_text": (lambda p=planet, s=sign: interpretations.authored_sign(p, s, "ru")),
            }
        for house in range(1, 13):
            h1 = f"{_planet_ru(planet)}{_editorial_text('seo.71bff885aea101349a1d1df5f84d122c226c858876d333bedd3259a914588423')}{house}{_editorial_text('seo.4d022d168228fb38b176ade9f6b90a1e8639f83568a05133b57617b7bf707935')}"
            pages[f'{pslug}-v-{house}-dome'] = {
                "h1": h1,
                "title": f"{h1}{_editorial_text('seo.4659faf409bc70a4b1ecb86261930ad93dfcba78ec9ead2a4794c02824ed9c3c')}",
                "get_text": (lambda p=planet, h=house: interpretations.authored_house(p, h, "ru")),
            }
    return pages


PAGES = _pages()


def _english_pages():
    pages = {}
    for planet, pslug in _PLANET_SLUG.items():
        for sign, sslug in _SIGN_SLUG.items():
            pages[f'{pslug}-v-{sslug}'] = {
                "h1": f"{planet}{_editorial_text('seo.2eb9d0d30d4ceaac52e60cce021ce7d3c04cff836bb0bd8dec4a4f54fa6cc97a')}{constants.SIGNS[sign]['en']}",
                "get_text": lambda p=planet, s=sign: interpretations.authored_sign(p, s, "en"),
            }
        for house in range(1, 13):
            ordinal = {1: "1st", 2: "2nd", 3: "3rd"}.get(house, f'{house}th')
            pages[f'{pslug}-v-{house}-dome'] = {
                "h1": f"{planet}{_editorial_text('seo.2ac51b0d4b90df90db54fdeeb86e4b8598befc4db2e4064ddd32479510e6d202')}{ordinal}{_editorial_text('seo.41981f406d038c45c2655a9936bc1a781552af51a69bfd376e5260a22f84396b')}",
                "get_text": lambda p=planet, h=house: interpretations.authored_house(p, h, "en"),
            }
    return pages


EN_PAGES = _english_pages()


def _language_links(request):
    path = escape(request.url.path, quote=True)
    base = escape(str(request.base_url).rstrip("/"), quote=True)
    return (f'<link rel="alternate" hreflang="ru" href="{base}{path}"><link rel="alternate" hreflang="en" href="{base}{path}?lang=en"><link rel="alternate" hreflang="x-default" href="{base}{path}">')


def _english_html(request, slug=None):
    page = EN_PAGES.get(slug) if slug else None
    heading = page["h1"] if page else _editorial_text('seo.34767fab9ccbce0753e7e4ca8943d79a5c5b2dcfb34b63c6291aede78ec0362e')
    title = f"{heading}{_editorial_text('seo.101fbc429400fe99accb98590cd203874968964311aeffabf6d6848f40cfb920')}" if page else f"{heading}{_editorial_text('seo.e4321baae1e27c0188004a0ab68d0d18ce510de9836dd930617fbb077843aceb')}"
    text = page["get_text"]() if page else _editorial_text('seo.c5c97e350e24bd11d3bebc5d08b41b89067eb545ce2df5e8a76c1db6ceb85359')
    description = text.split(".")[0][:160] + "."
    override = _SEO_OVERRIDES_EN.get(slug, {})
    title = override.get("title", title)
    description = override.get("description", description)
    quick_answer = ""
    if override.get("answer"):
        quick_answer = (f"""<section class="quick-answer" aria-label="Quick answer"><h2>In brief</h2><p>{escape(override['answer'])}</p></section>""")
    path = request.url.path
    canonical = escape(str(request.base_url).rstrip("/") + path + "?lang=en", quote=True)
    content = "".join(f'<p>{escape(p)}</p>' for p in text.split("\n") if p.strip())
    prefix = slug.split("-v-")[0] + "-v-" if slug else ""
    links = " ".join(f"""<a href="/opisanie/{s}?lang=en">{escape(p['h1'])}</a>"""
                     for s, p in EN_PAGES.items() if s != slug and s.startswith(prefix))
    related_heading = f"{_slug_planet(slug.split('-v-')[0])}{_editorial_text('seo.9ee9d551e2f253dafb8fd3f0be73012f9999256f502ca6c919b31bb083af2e07')}" if page else _editorial_text('seo.dd255a7f44a60be70f1d3ae2df89dcfec93b7260db48b19b63a9838c5e413a23')
    cluster = ""
    featured = ""
    for slugs, _ in _SEO_CLUSTERS:
        planet = _slug_planet(slugs[0].split("-v-")[0])
        label = f"{_editorial_text('seo.6e5ce6afa65bc328ed7ef2585ac6077dca716587a929ef60778a364b5680051c')}{planet}" if planet in ("Sun", "Moon") else planet
        cluster_links = "".join(
            f"""<a href="/opisanie/{s}?lang=en">{escape(EN_PAGES[s]['h1'])}</a> """
            for s in slugs if s != slug
        )
        if slug in slugs:
            cluster = f'<section class="cluster"><h2>Read more about {label}</h2>{cluster_links}</section>'
        if not page:
            featured += f'<section class="featured"><h2>Popular articles about {label}</h2>{cluster_links}</section>'
    return f'<!doctype html>\n<html lang="en"><head><meta charset="utf-8">\n<meta name="viewport" content="width=device-width,initial-scale=1">\n<title>{escape(title)}</title><meta name="description" content="{escape(description, quote=True)}">\n<meta property="og:title" content="{escape(title, quote=True)}">\n<meta property="og:description" content="{escape(description, quote=True)}">\n<meta property="og:locale" content="en_US"><meta property="og:url" content="{canonical}">\n<link rel="canonical" href="{canonical}">{_language_links(request)}\n<link rel="icon" href="/icon.svg" type="image/svg+xml"><style>{_STYLE}</style></head>\n<body><main><nav aria-label="Language"><a href="{escape(path)}" lang="ru">RU</a> · <a href="?lang=en" lang="en" aria-current="page">EN</a></nav>\n<h1>{escape(heading)}</h1>{quick_answer}{content}{featured}\n<a class="cta" href="/?lang=en">Calculate your natal chart for free</a>\n{cluster}\n<section class="rel"><h2>{related_heading}</h2>{links}</section>\n<footer><a href="/opisaniya?lang=en">All interpretations</a> · Calculations by Swiss Ephemeris. This service is for information and entertainment. 18+</footer>\n</main></body></html>'

# Точечный SEO-слой для страниц, которые уже получают показы и находятся рядом
# с первой страницей выдачи. Авторские трактовки остаются нетронутыми: этот
# словарь управляет только сниппетом, кратким ответом и контекстными ссылками.
_SEO_OVERRIDES = {
    "uran-v-1-dome": {
        "title": _editorial_text('seo.9832a43e0eeed70a8970087b1c22c452642c5eb0d7cc8f4001c43e4cde390ba7'),
        "description": (
            _editorial_text('seo.bfe319505e31951648e0b00e56821531de76fbb153a179af86c81523899e81a6')
        ),
        "answer": (
            _editorial_text('seo.add21d9297546d05b041f4227830911b12aa1a12775b9f730cb91ec482bc0c71')
        ),
    },
    "uran-v-3-dome": {
        "title": _editorial_text('seo.e066c57cf809b7a0b732cb4e4c10df0d769e0a82cac813b062a616ea1ca1164d'),
        "description": (
            _editorial_text('seo.3231e9ee65da7a5229c7c00d9dd2c6e496e54524f71efe85096d08bbe3063fcc')
        ),
        "answer": (
            _editorial_text('seo.47725add3507cf957654b1b5453e497bc1d8d75e0d2e8be85c9fa7d085d829eb')
        ),
    },
    "uran-v-5-dome": {
        "title": _editorial_text('seo.3846b8aecb445df892b725f98ad1c6ea4b101f57434af4f0eccac5a5df407c02'),
        "description": (
            _editorial_text('seo.556228b123039e2d2b12a92023edbceb221302a789728862c9e9a766944b8ea1')
        ),
        "answer": (
            _editorial_text('seo.50ad2debf67daee87fb194cfb26c01abe90ba48c7acdd7b740c6e048dbc26d9b')
        ),
    },
    "uran-v-11-dome": {
        "title": _editorial_text('seo.b4854eb8e7cd6975c00fa36d55db7c98069e08631bc1a7ff7ae27a8100abe878'),
        "description": (
            _editorial_text('seo.a772410638af2fc442e552558e2cbc967527e90339aa08bfe6e91076b19a06b9')
        ),
        "answer": (
            _editorial_text('seo.4075eb49161ef798bbcad9fa3df91254c5f3348ed3491a888f82b3061993777a')
        ),
    },
    "uran-v-lve": {
        "title": _editorial_text('seo.27de682f519574bd71fb8eb8334433c310c82eeba8aa0843adfb61d3068605f9'),
        "description": (
            _editorial_text('seo.16c6088688261e3e5b6779d0aa081fd5f22ef5b371551fb99e4f0b53468bcf10')
        ),
        "answer": (
            _editorial_text('seo.133cd9cc0c341f7972c6a00f324cf607cd877cb6b4bda3115d011972c4e56212')
        ),
    },
    "luna-v-2-dome": {
        "title": _editorial_text('seo.a21ac4c4dcabe1c8979b157c5a8902e83f837895457e27bbfd1f75dc7e21cc6e'),
        "description": (
            _editorial_text('seo.d8b2a48a8b1e0491b6b59e6422ed4308411550c28088760daa5a137cc96c54d1')
        ),
        "answer": (
            _editorial_text('seo.49528f7d9fd94bf3cc38cf520354d9d6e1ed869b59ccf7a1dcda1c412a972b70')
        ),
    },
    "luna-v-4-dome": {
        "title": _editorial_text('seo.5a8f4df3ad20fc9b9b16e3d6a0bc3858306bad9f41b6ebf0c1ad804e8cdabe34'),
        "description": (
            _editorial_text('seo.02fcaecdbf26f09b2c8a4325b9444e6b58512cff3cff7f58ee573223c899f614')
        ),
        "answer": (
            _editorial_text('seo.179b79787b9783be6916d4fb6e1eec535217d3c2fca09e02c2d36cd5e7473019')
        ),
    },
    "luna-v-5-dome": {
        "title": _editorial_text('seo.d6de65dfa6037460f062db4b1fe656a37e61db272fbef58ff8a61ea8e529f661'),
        "description": (
            _editorial_text('seo.780efff4d54268662131356c413e94a170562d1d759cb39a45890599a4106d43')
        ),
        "answer": (
            _editorial_text('seo.cb4300fc79d09301aa5e63b333bf9c4b2e3e57ccd9d48dd6f2674da4fbf6da1f')
        ),
    },
    "luna-v-10-dome": {
        "title": _editorial_text('seo.5c013f35a4f707cf44a9cbed9adb0b379ea21b6a553e5713a1895fa2f281bf33'),
        "description": (
            _editorial_text('seo.0b792c2b823c4e2f89f205d358070f9023986081a5d93e0adfb96eed9e5d1938')
        ),
        "answer": (
            _editorial_text('seo.3b148dcf80b9e3a130201e24fdb40c4b519167c044389c5a9777d0aaeb0808f5')
        ),
    },
    "luna-v-11-dome": {
        "title": _editorial_text('seo.dc7bcbf42427485270f5f0af29b8530c802c6e59013bae0005c92fad9b9f8f4e'),
        "description": (
            _editorial_text('seo.54dc2966414a06be582f2562b349e929275a5d69f86916298742634feb455451')
        ),
        "answer": (
            _editorial_text('seo.c546204eaf92d9d4db7cc599da32d9209847b4f3d1f6f8377319925283011623')
        ),
    },
    "mars-v-5-dome": {
        "title": _editorial_text('seo.366b57fa92ec1324e46b4ef4ab5818f713e348762e8b19ea645fb6dec217ad52'),
        "description": (
            _editorial_text('seo.d6825d11fdcbde803c9b213a751c5b0acac9e2341fdbcbf75520779a4fc8fcdf')
        ),
        "answer": (
            _editorial_text('seo.87e90c85e935a6467aa44fec0911627d77ecb5e6093240d6416c44658603b86a')
        ),
    },
    "mars-v-6-dome": {
        "title": _editorial_text('seo.0e1a41934ab9c1144528b523367a474b7943fe39a6c526d7f09f6890f288c5cb'),
        "description": (
            _editorial_text('seo.9c8862a00a8028c2c8d5528ff86420c1435447575ded42d21b5ca14760be8738')
        ),
        "answer": (
            _editorial_text('seo.963316b8de6d1263433d550d905d23d3f362457fbcdb9b1d021daf4fa4c42fae')
        ),
    },
    "mars-v-10-dome": {
        "title": _editorial_text('seo.c10b552eb9417f7238551be4c40b1bfd3a40c9a9300cdfa5e673dae7b48b1a3d'),
        "description": (
            _editorial_text('seo.e74ea840acc2024a0c87ae810114f49b8cb10da32ae027e948617b2c3de1d0b8')
        ),
        "answer": (
            _editorial_text('seo.668eaabd7eed28f75883f652528535334e50ba819ce80c1acf9a4c8ba51cf05a')
        ),
    },
    "mars-v-11-dome": {
        "title": _editorial_text('seo.ec7f79db21050bcd46164df1dee27116bd2b078ecaf2706bfdf30ef00e4d30a5'),
        "description": (
            _editorial_text('seo.618d0a617ca47a16546d325f0dc5426097478072e1d94f7a0e3d6c8013f43aa4')
        ),
        "answer": (
            _editorial_text('seo.27b320b71f6f6c6781b9d3bd4a25a834968d27a8aa01ac7bfbf638b435175220')
        ),
    },
    "mars-v-lve": {
        "title": _editorial_text('seo.9de0ee4cce089e68e68763664f7cbda3284b0e98c6627cce38fe75e8a2858213'),
        "description": (
            _editorial_text('seo.cc80a9aa00f2cdfcab469c5cc84d8a1eaa4af3328bee299638732f82340b031c')
        ),
        "answer": (
            _editorial_text('seo.bc509f56243c55775b4045d1c96c17de6f83bff5977d3d510b68e2c67a0a0e58')
        ),
    },
    "solntse-v-3-dome": {
        "title": _editorial_text('seo.12ad0d58b0309bd00fb692bcb1c4aacc280204b4ef6093ff7117107e6822c85d'),
        "description": (
            _editorial_text('seo.b7b3d5dbf172be74c51a3eac42deca162d0fd6c4bf60618a2e6ba14480befee8')
        ),
        "answer": (
            _editorial_text('seo.6623feaaef7caf4c887b6cb7fd1a0b293b206a81d5dd3cca798cb1bb89cbfabe')
        ),
    },
    "solntse-v-7-dome": {
        "title": _editorial_text('seo.6ca3624d64249f07cdb97ce7f10cf6ddb66ba93407cc474ae91859eaf5dd748f'),
        "description": (
            _editorial_text('seo.97588ef947e15607d43b4541a93b9042a35732803e8fe79a7b4feb76ee2c4aa5')
        ),
        "answer": (
            _editorial_text('seo.21884c8a4cd488a7023954bae3297065d61fc6a4d7936323c2de5a697c80c20d')
        ),
    },
    "solntse-v-8-dome": {
        "title": _editorial_text('seo.9dbd00c9a9ce9b227a40dfee1f52418ca766ed66eac59e9ed56d9f31cb64e19f'),
        "description": (
            _editorial_text('seo.e898f1abddd35535c8ae89d84bcb2cdc56c333c199c4d11a886d79d0803c6af5')
        ),
        "answer": (
            _editorial_text('seo.511c88678c87b67408050d3e9d0c62bad3fe75cbb010c68fc4ced5bb17cc2d56')
        ),
    },
    "solntse-v-10-dome": {
        "title": _editorial_text('seo.ddb363b2da529db15863270844d917bb8cc975d4997f6f376c0516705e85316b'),
        "description": (
            _editorial_text('seo.f61eed7a0a2e53eff6d9dd9de5fd676350a79f23a422359e95c2259ed207dcda')
        ),
        "answer": (
            _editorial_text('seo.0a928bc52fa3e6e3a3f5508437beb19cc0750a5b770c2506c6b82f926c09dacd')
        ),
    },
    "solntse-v-11-dome": {
        "title": _editorial_text('seo.618568eb9a126f1ac7016884092f1392e46c014c94ce85145e9b17cec43242c9'),
        "description": (
            _editorial_text('seo.1b927b1049d1214114d6a1fc750328ea93b2933078dcc5eb0324ae91f23f173a')
        ),
        "answer": (
            _editorial_text('seo.f8e29dcf6307205877e17ffdfd66644641c59936cfbe6820e13ebf49249ed8ee')
        ),
    },
}

# Faithful translations of the Russian editorial layer, separate from authored text.
# Keep keys/fields in sync with _SEO_OVERRIDES (enforced by regression tests).
_SEO_OVERRIDES_EN = {
    "uran-v-1-dome": {
        "title": _editorial_text('seo.147fa0aef5b74da6270325998b5ee9a87ae5accec4b5ede5b07027db21aef4df'),
        "description": _editorial_text('seo.add615ad3bd8ba3775e74e5c9b22a895ec8fdc09b47cb51390fecfcbe00fd2d2'),
        "answer": _editorial_text('seo.bd748bf1720ef677fbe4d8c043232a473f8d9d37db0105b8302756c4e9936e20'),
    },
    "uran-v-3-dome": {
        "title": _editorial_text('seo.8ebf64d4c5924ef58be92719b9be99de28e6bf4c6892f6de90b346ebfd7c1795'),
        "description": _editorial_text('seo.5d335c32365ba3aab7b5f438a6bb0f652dbe81cd577e7775bb60e55a1a015c27'),
        "answer": _editorial_text('seo.20e9320e8cfade1b977498e42beab9b821696302ae1fc2abe6e151db443541d8'),
    },
    "uran-v-5-dome": {
        "title": _editorial_text('seo.bd23d397e959a1615e625b6672f0db6934fa0d0dbbff2d82c2e76d6aed2a9bef'),
        "description": _editorial_text('seo.32ae336c3ab510f7ff276ef630d40be4f2267fcb5dfce941f4688eff46342018'),
        "answer": _editorial_text('seo.1c7dc45af8de6d67107319f737a50aec7dbefea38039eb3d5918d3da7edfb914'),
    },
    "uran-v-11-dome": {
        "title": _editorial_text('seo.2a5778a9397d41c01668b401724218e5bbc3f2712d95ae34dcf7caeb23b546d7'),
        "description": _editorial_text('seo.1578ce492fe11f4f058ca51972f113554b9a885796a7f48defd178503db340e9'),
        "answer": _editorial_text('seo.24a7a7db3f2c561b1df8da64d934cb938fcb510b5b6b76169da1d298143663a2'),
    },
    "uran-v-lve": {
        "title": _editorial_text('seo.47a89bc5a6883a9575a1e0c0c2322da64197e6a1819c5c430e0a62af1bcacab2'),
        "description": _editorial_text('seo.5013eba8c1c5589162d8e3ddbdecd9d2104a1ab0ab22615ba50efae41069c6ab'),
        "answer": _editorial_text('seo.12d84a85b946d277cee4927a41fcd9af31f27407bc8614d59dde942932661ee7'),
    },
    "luna-v-2-dome": {
        "title": _editorial_text('seo.863a44f15d7b1abfc26351060eab9642bda43f1dcec5464083397d7559394259'),
        "description": _editorial_text('seo.18f4848adf4239992edb5ccd4c1fcc52ebabf507c99ae0a601a7bd3f41dccf26'),
        "answer": _editorial_text('seo.84809f4e57fce310b22fa5890f298457ea309456a8a6ba9ab33b38114a13e1b5'),
    },
    "luna-v-4-dome": {
        "title": _editorial_text('seo.1c4047fbb312d9a03d7739af5b74ff36b2976a8becfce9d9a65455646ca41f41'),
        "description": _editorial_text('seo.f6a0a052a9c5e536b5252dd87021ca643606a2f5f26c48b3b89d44baa8457346'),
        "answer": _editorial_text('seo.8bd330fbfc7522badc028b94157281d8b93426b3d74c66b3f31db0621f79960f'),
    },
    "luna-v-5-dome": {
        "title": _editorial_text('seo.3e4e65e177e7c6bdca9ee5b67faed9fbea454a739d509488da1c405207341424'),
        "description": _editorial_text('seo.d8b1ded2488bf4fecbf439d24b0095bf993614215f18d35a701bb70ecb48ce17'),
        "answer": _editorial_text('seo.21f21ee3bdedabaadaa39d76e391afbd008be663e25e2069c4eb91459bccd7ed'),
    },
    "luna-v-10-dome": {
        "title": _editorial_text('seo.48a77a7493da3cd96696ba8af164c1473050604ccfe50db490d26df0ea1b72db'),
        "description": _editorial_text('seo.06d0244b7f7e6d75b1a9273e40ce9d46b3b9e31b84d3cf9aedf9a446295af09e'),
        "answer": _editorial_text('seo.fa9dce9538509fc8da3042483eb38c9ef0a74e9ad7a4c186b6e85fbfd92f5424'),
    },
    "luna-v-11-dome": {
        "title": _editorial_text('seo.400074c80def791cec4163f7b1ac26b164da3d19cb341de96361857fb89d2579'),
        "description": _editorial_text('seo.06e164131926de175a28fbcc3e2c41633173224c2f0aa449f8743370aa5d247e'),
        "answer": _editorial_text('seo.48b5467f58c8c90d9da6700024c264e04a411b74eb981a18b462409075aee690'),
    },
    "mars-v-5-dome": {
        "title": _editorial_text('seo.c48d0bb842b7ed6b1aca6b9b821d43b3d6e2db1a3ab71baa80b3d57b3c5abfbe'),
        "description": _editorial_text('seo.f07bfb79dccbf577bcd3a29935914e6869d784a2d8b55821693b7658984b3357'),
        "answer": _editorial_text('seo.61197cb6079e845e1457a6206bde47fb5f4caf9acfe25056304d04e394a53924'),
    },
    "mars-v-6-dome": {
        "title": _editorial_text('seo.af188983b8f17216b9b2f06380d1647bb56471181e79ef303254f9011bd9f328'),
        "description": _editorial_text('seo.b9c4c9011a040137242a0a6d8b02815d191ef866e0810b73fbf58fe7bf66e547'),
        "answer": _editorial_text('seo.196a5a26baa076f58eddfe1cbe6f7f5fb3ff2afc3904531169e0f80faf2baf7c'),
    },
    "mars-v-10-dome": {
        "title": _editorial_text('seo.1682f3fd89632aa5d860f19f0e2213dcb094621b22e5cb9b2b139625c4daea41'),
        "description": _editorial_text('seo.a47ff3df82e140317d9c02a3e4c5ac7ed98f62efe61751f6a47b91d23625df99'),
        "answer": _editorial_text('seo.7d4acf6eaa8368e3bc81f4ec6494c071e3a529773e0f006078a1f1aa57e40625'),
    },
    "mars-v-11-dome": {
        "title": _editorial_text('seo.46aa161f6ddcd16aadb0ebe4a64211b912a86bbc4425c6d918862c110714135a'),
        "description": _editorial_text('seo.a75caa25a9eae4ad6399923b699d0689547c110205b99b7ae1323fa418811242'),
        "answer": _editorial_text('seo.74726cbe7031dd82567326213c64d2cdc4f9a101e98371080b49d74b54f3f58b'),
    },
    "mars-v-lve": {
        "title": _editorial_text('seo.3a50f0f857cec43051bef17e3069d279a6bb21c4d80723f917dc377fcae84cea'),
        "description": _editorial_text('seo.561e4f4165eba67ba3ed2310b4e1d98c78dc6e56193dd187e13b9c437ed2d329'),
        "answer": _editorial_text('seo.c76217936120da2e2024369aacc644ecc32fac57c4583c229bfb7f0e510a4a6b'),
    },
    "solntse-v-3-dome": {
        "title": _editorial_text('seo.84a6864743ae83e20c292c5d15ffdffca5e45a84169a998ddc7720812329eeda'),
        "description": _editorial_text('seo.af7a0c411945e0873bebd062a439ddbedcdbb90d8cf9d9f112e23e148b62a7ae'),
        "answer": _editorial_text('seo.899b6b5b972b45473bdfdf01d66434f0bf381679d3ddde2ee6942e3b0395b3ba'),
    },
    "solntse-v-7-dome": {
        "title": _editorial_text('seo.4dd90c33392324934d9ae0794a1fdb57c2022673c43b6b2a88410966a8e95ca7'),
        "description": _editorial_text('seo.1b800c2501759454c832fad6c68a5424a4661902849629587bb756918b39118a'),
        "answer": _editorial_text('seo.feb6f3d957493c5698ee657b5c660cdb27d5fa246ec5deaa54e441e84494f132'),
    },
    "solntse-v-8-dome": {
        "title": _editorial_text('seo.fcf54790c401edb3d8ed0b3d0542c3a2bd3a4368a342e7ff59ee9eb986654a18'),
        "description": _editorial_text('seo.78f77c9f31ac58ca21ba1bf19a90d0099af275411adcdeb09eecc9c2946f2fa7'),
        "answer": _editorial_text('seo.e4421d5bbc0f9a1ebc2cb0755ddbe6b0695f158c1902154ffbf4010e2fb9797d'),
    },
    "solntse-v-10-dome": {
        "title": _editorial_text('seo.2a473d6dfff1baec4708f700e7834744d39cde74a522469343d0e70e1dd628a1'),
        "description": _editorial_text('seo.aac424ddaa5f4715901e77132be92f0b128c2efe36d70fb49994e0f291553496'),
        "answer": _editorial_text('seo.655f1372ad65fd9fb70f2e5d76bf0e7c89e8b6c26b831ab1b2c4472fdb71da65'),
    },
    "solntse-v-11-dome": {
        "title": _editorial_text('seo.24b705847f90b7cc9be66f171d9c9e30b19697288697c741686fd57ec4a3a982'),
        "description": _editorial_text('seo.93f50b2832e18a97b43cb8f22302bb3cae83c29dab862c086a4bb45a066e9146'),
        "answer": _editorial_text('seo.a5e3f815bdb49e22dc23e23878f309c7c7faf35601d4057acf4c915dade43efd'),
    },
}

_URANUS_CLUSTER = (
    "uran-v-1-dome", "uran-v-3-dome", "uran-v-5-dome",
    "uran-v-11-dome", "uran-v-lve",
)

_MOON_CLUSTER = (
    "luna-v-2-dome", "luna-v-4-dome", "luna-v-5-dome",
    "luna-v-10-dome", "luna-v-11-dome",
)

_MARS_CLUSTER = (
    "mars-v-5-dome", "mars-v-6-dome", "mars-v-10-dome",
    "mars-v-11-dome", "mars-v-lve",
)

_SUN_CLUSTER = (
    "solntse-v-3-dome", "solntse-v-7-dome", "solntse-v-8-dome",
    "solntse-v-10-dome", "solntse-v-11-dome",
)

_SEO_CLUSTERS = (
    (_URANUS_CLUSTER, _editorial_text('seo.d29c62138e4b11c4abea6e969a3b8aa4b1949f4272c9aeec2d428c750bb1dede')),
    (_MOON_CLUSTER, _editorial_text('seo.dfe18bdca58d381c1abffb5a40af722b4af03dee3136bdb4045312793a00d4ac')),
    (_MARS_CLUSTER, _editorial_text('seo.5af55071f91e9e9d0c30061f77499775c692f57fbc07c4fd5d5c966ff9a1a21d')),
    (_SUN_CLUSTER, _editorial_text('seo.13c5411e6711fd7138c24e95cf9a2f9a88b64af293957bf0558a2662dc2ecbdd')),
)

_STYLE = '\nbody{margin:0;background:#0d0b1a;color:#e8e4f0;font:18px/1.7 Georgia,serif;}\nmain{max-width:720px;margin:0 auto;padding:40px 20px;}\nh1{color:#e8c66f;font-size:1.9em;line-height:1.3;}\nh2{color:#e8c66f;font-size:1.25em;line-height:1.4;}\na{color:#b79ce8;}\n.quick-answer{margin:22px 0 28px;padding:16px 18px;background:#17132a;border-left:3px solid #e8c66f;\nborder-radius:0 10px 10px 0;}\n.quick-answer h2{margin:0 0 6px;font-size:1.05em;}\n.quick-answer p{margin:0;}\n.cta{display:inline-block;margin-top:28px;padding:12px 22px;background:#e8c66f;color:#1a1430;\nborder-radius:8px;text-decoration:none;font-weight:bold;}\n.cluster{margin-top:32px;padding:18px;background:#17132a;border-radius:10px;}\n.cluster h2{margin:0 0 8px;}\n.cluster a{display:inline-block;margin:3px 14px 3px 0;}\n.rel{margin-top:36px;padding-top:16px;border-top:1px solid #2e2750;font-size:.85em;}\n.rel a{margin-right:12px;white-space:nowrap;line-height:2;}\n.featured{margin:24px 0;padding:18px;background:#17132a;border-radius:10px;}\n.featured h2{margin-top:0;}\nfooter{margin-top:36px;font-size:.8em;color:#8a83a8;}\n'


def _page_html(slug: str, page: dict, request: Request) -> str:
    text = page["get_text"]()
    paragraphs = "".join(f'<p>{p}</p>' for p in text.split("\n") if p.strip())
    override = _SEO_OVERRIDES.get(slug, {})
    title = override.get("title", page["title"])
    # Для приоритетных страниц — ручной сниппет; для остальных сохраняем шаблон.
    descr = override.get("description", text.split(".")[0][:160] + ".")
    quick_answer = ""
    if override.get("answer"):
        quick_answer = (
            f"""<section class="quick-answer" aria-label="Краткий ответ"><h2>Кратко</h2><p>{override['answer']}</p></section>"""
        )
    base = str(request.base_url).rstrip("/")
    pslug = slug.split("-v-")[0]
    cluster = ""
    for slugs, heading in _SEO_CLUSTERS:
        if slug in slugs:
            links = "".join(
                f"""<a href="/opisanie/{s}">{PAGES[s]['h1']}</a>"""
                for s in slugs if s != slug
            )
            cluster = f'<section class="cluster"><h2>{heading}</h2>{links}</section>'
            break
    related = "".join(
        f"""<a href="/opisanie/{s}">{p['h1']}</a> """
        for s, p in PAGES.items() if s.startswith(pslug + "-v-") and s != slug
    )
    return f'''<!doctype html>\n<html lang="ru"><head><meta charset="utf-8">\n<meta name="viewport" content="width=device-width,initial-scale=1">\n<title>{title}</title>\n<meta name="description" content="{descr}">\n<link rel="canonical" href="{base}/opisanie/{slug}">\n<link rel="icon" href="/icon.svg" type="image/svg+xml">\n<meta property="og:title" content="{title}">\n<meta property="og:description" content="{descr}">\n<style>{_STYLE}</style></head>\n<body><main>\n<h1>{page['h1']}</h1>\n{quick_answer}\n{paragraphs}\n<a class="cta" href="/">Рассчитать свою натальную карту бесплатно</a>\n{cluster}\n<div class="rel"><b>{_planet_ru(_slug_planet(pslug))} в других положениях:</b><br>{related}</div>\n<footer><a href="/opisaniya">Все описания</a> · Расчёты — Swiss Ephemeris. Сервис носит информационно-развлекательный характер. 18+</footer>\n</main></body></html>'''


def _slug_planet(pslug: str) -> str:
    return next(p for p, s in _PLANET_SLUG.items() if s == pslug)


@router.get("/opisanie/{slug}", response_class=HTMLResponse)
def seo_page(slug: str, request: Request):
    if request.query_params.get("lang") == "en":
        page = EN_PAGES.get(slug)
        if not page or not page["get_text"]():
            raise HTTPException(status_code=404, detail=_editorial_text('seo.a469ab4ca4e55bf547566e9ebfa1b809c933207e9d558156bc0c4252b17533fe'))
        return _english_html(request, slug)
    page = PAGES.get(slug)
    if not page or not page["get_text"]():
        raise HTTPException(status_code=404, detail=_editorial_text('seo.af9d33066ad69a3ec7523920934ec440f8b61871f8219b584721bcfa2a275d5b'))
    return _page_html(slug, page, request).replace("</head>", _language_links(request) + "</head>").replace(
        "<body><main>", '<body><main><nav aria-label="Язык"><a href="?lang=ru" lang="ru">RU</a> · <a href="?lang=en" lang="en">EN</a></nav>')


@router.get("/opisaniya", response_class=HTMLResponse)
def seo_index(request: Request):
    if request.query_params.get("lang") == "en":
        return _english_html(request)
    links = "".join(f"""<a href="/opisanie/{s}">{p['h1']}</a> """ for s, p in PAGES.items())
    featured = "".join(
        f"""<a href="/opisanie/{s}">{PAGES[s]['h1']}</a> """ for s in _URANUS_CLUSTER
    )
    moon_featured = "".join(
        f"""<a href="/opisanie/{s}">{PAGES[s]['h1']}</a> """ for s in _MOON_CLUSTER
    )
    mars_featured = "".join(
        f"""<a href="/opisanie/{s}">{PAGES[s]['h1']}</a> """ for s in _MARS_CLUSTER
    )
    sun_featured = "".join(
        f"""<a href="/opisanie/{s}">{PAGES[s]['h1']}</a> """ for s in _SUN_CLUSTER
    )
    return f'''<!doctype html>\n<html lang="ru"><head><meta charset="utf-8">\n<meta name="viewport" content="width=device-width,initial-scale=1">\n<title>Планеты в знаках и домах — все описания | Астрокалькулятор</title>\n<meta name="description" content="Авторские описания всех положений планет в знаках зодиака и домах натальной карты.">\n<link rel="icon" href="/icon.svg" type="image/svg+xml">\n<link rel="canonical" href="{escape(str(request.base_url).rstrip('/'))}{_editorial_text('seo.4f909ad5773e8327afb61515956d7baefde879835f0093c84917b9526eaae413')}{_language_links(request)}<style>{_STYLE}</style></head>\n<body><main><nav aria-label="Язык"><a href="?lang=ru" lang="ru">RU</a> · <a href="?lang=en" lang="en">EN</a></nav><h1>Планеты в знаках и домах</h1>\n<section class="featured"><h2>Популярные материалы об Уране</h2>{featured}</section>\n<section class="featured"><h2>Популярные материалы о Луне</h2>{moon_featured}</section>\n<section class="featured"><h2>Популярные материалы о Марсе</h2>{mars_featured}</section>\n<section class="featured"><h2>Популярные материалы о Солнце</h2>{sun_featured}</section>\n<div class="rel">{links}</div>\n<a class="cta" href="/">Рассчитать свою натальную карту бесплатно</a>\n</main></body></html>'''


@router.get("/sitemap.xml")
def sitemap(request: Request):
    base = str(request.base_url).rstrip("/")
    urls = [base + "/", base + "/opisaniya"] + [f'{base}/opisanie/{s}' for s in PAGES]
    urls += [base + "/opisaniya?lang=en"] + [f'{base}/opisanie/{s}?lang=en' for s in EN_PAGES]
    body = "".join(f'<url><loc>{u}</loc></url>' for u in urls)
    xml = f'<?xml version="1.0" encoding="UTF-8"?><urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">{body}</urlset>'
    return Response(content=xml, media_type="application/xml")


@router.get("/robots.txt", response_class=PlainTextResponse)
def robots(request: Request):
    base = str(request.base_url).rstrip("/")
    return f"{_editorial_text('seo.f5da3f5c2ebd63c54a1e011735f19201a5add124ff9f3babc7314bc100750e6e')}{base}{_editorial_text('seo.df60d71a62cfd15dd6e7d9b137b34cd0503093dfe32becafdc8acf241011b34f')}"
