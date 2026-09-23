"""SEO router tests: deliberately do not import main or initialise the database."""
import re
from html import escape
from pathlib import Path
from xml.etree import ElementTree

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from backend import seo

app = FastAPI()
app.include_router(seo.router)
client = TestClient(app)


@pytest.mark.parametrize("slug", list(seo.EN_PAGES))
def test_all_english_placements(slug):
    response = client.get(f"/opisanie/{slug}?lang=en")
    assert response.status_code == 200
    html = response.text
    assert '<html lang="en">' in html
    assert not re.search(r"[А-Яа-яЁё]", html)
    assert escape(seo.EN_PAGES[slug]["get_text"]().split("\n")[0]) in html
    assert f'rel="canonical" href="http://testserver/opisanie/{slug}?lang=en"' in html
    assert 'hreflang="ru"' in html and 'hreflang="en"' in html
    assert 'href="/?lang=en"' in html


def test_russian_priority_content_preserved():
    html = client.get("/opisanie/uran-v-1-dome").text
    assert seo._SEO_OVERRIDES["uran-v-1-dome"]["answer"] in html
    assert '<html lang="ru">' in html
    assert 'href="?lang=en"' in html


def test_catalog_and_sitemap():
    html = client.get("/opisaniya?lang=en").text
    assert not re.search(r"[А-Яа-яЁё]", html)
    for slug in seo.EN_PAGES:
        assert f'href="/opisanie/{slug}?lang=en"' in html
    root = ElementTree.fromstring(client.get("/sitemap.xml").content)
    urls = [el.text for el in root.iter() if el.tag.endswith("}loc")]
    assert "http://testserver/opisanie/uran-v-1-dome?lang=en" in urls
    assert "http://testserver/opisanie/uran-v-1-dome" in urls


def test_invalid_language_and_missing_page():
    assert '<html lang="ru">' in client.get("/opisaniya?lang=fr").text
    assert client.get("/opisanie/missing?lang=en").json()["detail"] == "Page not found"


def test_english_content_is_escaped(monkeypatch):
    monkeypatch.setitem(seo.EN_PAGES["uran-v-1-dome"], "get_text", lambda: '<script>alert("x")</script>')
    html = client.get("/opisanie/uran-v-1-dome?lang=en").text
    assert '<script>' not in html
    assert '&lt;script&gt;' in html


@pytest.mark.parametrize("name,clauses", [("privacy", 10), ("terms", 8)])
def test_complete_legal_versions_retained(name, clauses):
    html = (Path(__file__).parents[1] / "frontend" / f"{name}.html").read_text(encoding="utf-8")
    ru, en = html.split('<main data-standalone-en hidden>')
    assert len(re.findall(r"<strong>\d+\.", ru)) == clauses
    assert len(re.findall(r"<strong>\d+\.", en)) == clauses
    for value in ("245010711934", "2026-09-01", "18+"):
        assert value in ru and value in en
    assert '/js/standalone-i18n.js' in html


def test_editorial_translation_inventory():
    # New Russian fields (including future FAQs) must not silently disappear in EN.
    assert seo._SEO_OVERRIDES_EN.keys() == seo._SEO_OVERRIDES.keys()
    for slug, original in seo._SEO_OVERRIDES.items():
        translated = seo._SEO_OVERRIDES_EN[slug]
        assert translated.keys() == original.keys(), slug
        for field, value in translated.items():
            assert isinstance(value, str) and value.strip(), (slug, field)
            assert not re.search(r"[А-Яа-яЁё]", value), (slug, field)
            assert value != original[field], (slug, field)


@pytest.mark.parametrize("slug", list(seo._SEO_OVERRIDES))
def test_all_editorial_sections_are_rendered(slug):
    en = client.get(f"/opisanie/{slug}?lang=en").text
    ru = client.get(f"/opisanie/{slug}").text
    translated = seo._SEO_OVERRIDES_EN[slug]
    assert f'<title>{escape(translated["title"])}</title>' in en
    for prefix in ('name="description"', 'property="og:description"'):
        assert f'{prefix} content="{escape(translated["description"])}"' in en
    assert f'property="og:title" content="{escape(translated["title"])}"' in en
    assert f'<h2>In brief</h2><p>{escape(translated["answer"])}</p>' in en
    assert 'aria-label="Quick answer"' in en
    assert en.index('class="quick-answer"') < en.index(escape(seo.EN_PAGES[slug]["get_text"]().split("\n")[0]))
    assert re.findall(r'<(?:section|div) class="([^"]+)"', en) == re.findall(r'<(?:section|div) class="([^"]+)"', ru)
    for slugs, _ in seo._SEO_CLUSTERS:
        if slug in slugs:
            cluster = re.search(r'<section class="cluster">(.*?)</section>', en).group(1)
            assert cluster.count('<a ') == len(slugs) - 1
            for related in slugs:
                if related != slug:
                    assert f'href="/opisanie/{related}?lang=en"' in cluster
    # Existing Russian editorial copy must remain intact.
    for value in seo._SEO_OVERRIDES[slug].values():
        assert value in ru


def test_catalog_featured_sections_match_russian():
    en = client.get('/opisaniya?lang=en').text
    ru = client.get('/opisaniya').text
    assert en.count('<section class="featured">') == ru.count('<section class="featured">') == 4
    for label in ('Uranus', 'the Moon', 'Mars', 'the Sun'):
        assert f'Popular articles about {label}' in en


def test_nonpriority_page_has_no_invented_editorial_answer():
    html = client.get('/opisanie/venera-v-ovne?lang=en').text
    assert '<section class="quick-answer"' not in html
    assert '<section class="cluster"' not in html


def test_editorial_translations_are_escaped(monkeypatch):
    unsafe = '<img src=x onerror="alert(1)">'
    monkeypatch.setitem(seo._SEO_OVERRIDES_EN, 'uran-v-1-dome', dict.fromkeys(('title', 'description', 'answer'), unsafe))
    html = client.get('/opisanie/uran-v-1-dome?lang=en').text
    assert unsafe not in html
    assert escape(unsafe) in html
