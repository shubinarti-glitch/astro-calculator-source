# -*- coding: utf-8 -*-
"""Еженедельный дайджест транзитов подписчикам (премиум, подтверждённая почта).

Запуск (из корня проекта): python scripts/weekly_digest.py
На проде вызывается systemd-таймером раз в неделю (см. deploy/astro-digest.*).
Идёт последовательно, между письмами пауза — не упереться в лимит Gmail.
"""
from __future__ import annotations

import sys
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from backend import astrology, db, emailer  # noqa: E402

BASE_URL = "https://astrosmap.ru"
SEND_PAUSE_SEC = 2  # ponytail: наивная пауза; при росте базы — очередь/пакетная отправка


def week_events(natal_params: dict) -> list[dict]:
    """Ключевые транзиты на 7 дней вперёд: [{date, title, text}]."""
    today = datetime.now(timezone.utc).date()
    end = today + timedelta(days=7)
    rep = astrology.forecast_report(
        natal_params=natal_params,
        start={"year": today.year, "month": today.month, "day": today.day},
        end={"year": end.year, "month": end.month, "day": end.day},
    )
    # За короткое окно медленные планеты дают много «стоячих» аспектов в орбе —
    # для письма берём только 6 самых точных, иначе получается стена текста.
    events = sorted(rep.get("events", []), key=lambda e: e.get("orb", 99))[:6]
    events.sort(key=lambda e: e["date"])  # в письме — по дате
    return [{
        "date": e["date"],
        "title": f"{e['p1_ru']} {e['aspect_ru']} {e['p2_ru']}",
        "text": e["text"],
    } for e in events]


def main() -> int:
    if not emailer.is_configured():
        print("SMTP не настроен (data/smtp.json) — рассылка пропущена")
        return 0
    sent = failed = skipped = 0
    for sub in db.weekly_subscribers():
        if not db.is_premium(sub["id"]):
            skipped += 1  # дайджест — премиум-функция
            continue
        primary = db.digest_profile(sub["id"])  # primary, а если не выбран — последняя карта
        if not primary:
            skipped += 1
            continue
        params = dict(primary["data"])
        lang = "en" if params.get("lang") == "en" else "ru"
        params["lang"] = lang
        try:
            events = week_events(params)
            unsub = f"{BASE_URL}/api/unsubscribe?token={sub['unsub_token']}&lang={lang}"
            subject, body, html = emailer.digest_letter(primary["label"], events, unsub, lang)
            emailer.send(sub["email"], subject, body, html)
            sent += 1
            time.sleep(SEND_PAUSE_SEC)
        except Exception as exc:  # одно упавшее письмо не должно ронять всю рассылку
            failed += 1
            print(f"Ошибка для user {sub['id']}: {exc}")
    print(f"Дайджест: отправлено {sent}, ошибок {failed}, пропущено {skipped}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
