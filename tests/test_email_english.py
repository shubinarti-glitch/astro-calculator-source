import re
from backend import emailer


def test_digest_english_empty_and_events():
    for events in ([], [{"date": "2026-09-07", "title": "Sun trine Moon", "text": "A supportive period."}]):
        subject, body, html = emailer.digest_letter("Alex", events, "https://example.com/unsubscribe?lang=en", "en")
        assert "Your weekly astrology forecast" in subject
        assert not re.search("[а-яА-ЯёЁ]", subject + body + html)
        assert 'lang="en"' in html
        assert "?lang=en" in html


def test_digest_preserves_russian_and_escapes_content():
    subject, body, html = emailer.digest_letter("<script>test</script>", [], "https://example.com/?a=1&b=2")
    assert "Ваш астропрогноз" in subject
    assert "<script>" not in html and "&lt;script&gt;" in html
    assert "a=1&amp;b=2" in html
