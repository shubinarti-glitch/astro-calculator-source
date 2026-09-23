# -*- coding: utf-8 -*-
"""Отправка писем (подтверждение email, сброс пароля) через SMTP.

Настройки — data/smtp.json:
{
  "host": "smtp.yandex.ru", "port": 465, "ssl": true,
  "user": "noreply@astrosmap.ru", "password": "...",
  "from": "Астрокалькулятор <noreply@astrosmap.ru>"
}
Пока файла нет — сервис «не настроен»: письма не уходят, но регистрация работает.
"""
from __future__ import annotations

import html as html_mod
import json
import smtplib
import ssl
from email.message import EmailMessage
from pathlib import Path

CONFIG_PATH = Path(__file__).resolve().parent.parent / "data" / "smtp.json"


class NotConfiguredError(Exception):
    pass


def _config() -> dict:
    if not CONFIG_PATH.exists():
        raise NotConfiguredError("Почтовый сервис не настроен (data/smtp.json)")
    return json.loads(CONFIG_PATH.read_text(encoding="utf-8"))


def is_configured() -> bool:
    return CONFIG_PATH.exists()


def send(to: str, subject: str, body: str, html: str | None = None, reply_to: str | None = None) -> None:
    cfg = _config()
    msg = EmailMessage()
    msg["From"] = cfg.get("from") or cfg["user"]
    msg["To"] = to
    msg["Subject"] = subject
    if reply_to:
        msg["Reply-To"] = reply_to  # ответить пользователю — прямо из почты
    msg.set_content(body)  # текстовый вариант — фолбэк для клиентов без HTML
    if html:
        msg.add_alternative(html, subtype="html")
    port = int(cfg.get("port", 465))
    if cfg.get("ssl", True):
        with smtplib.SMTP_SSL(cfg["host"], port, context=ssl.create_default_context(), timeout=15) as s:
            s.login(cfg["user"], cfg["password"])
            s.send_message(msg)
    else:
        with smtplib.SMTP(cfg["host"], port, timeout=15) as s:
            s.starttls(context=ssl.create_default_context())
            s.login(cfg["user"], cfg["password"])
            s.send_message(msg)


def support_to() -> str:
    """Куда слать обращения из формы поддержки (data/smtp.json → support_to).
    По умолчанию — +support-алиас: тот же ящик, но адрес ≠ отправителя, поэтому
    почтовик кладёт письмо во «Входящие» (self-письма прячутся в «Отправленные»)."""
    try:
        return _config().get("support_to") or "astrosmap+support@yandex.ru"
    except NotConfiguredError:
        return "astrosmap+support@yandex.ru"


# ---------------------------------------------------------------------------
#  Тексты писем
# ---------------------------------------------------------------------------
def verify_letter(link: str, lang: str = "ru") -> tuple[str, str]:
    if lang == "en":
        return ("Confirm your email — Astrocalculator",
                f"Hello!\n\nTo confirm your email on astrosmap.ru, open this link:\n{link}\n\n"
                "The link is valid for 24 hours. If you didn't register, just ignore this message.")
    return ("Подтвердите почту — Астрокалькулятор",
            f"Здравствуйте!\n\nЧтобы подтвердить почту на astrosmap.ru, откройте ссылку:\n{link}\n\n"
            "Ссылка действует 24 часа. Если вы не регистрировались — просто удалите это письмо.")


def digest_letter(person: str, events: list[dict], unsub_link: str, lang: str = "ru") -> tuple[str, str, str]:
    """Еженедельный дайджест-открытка: person — имя основного человека,
    events — список {date, title, text}. Возвращает (subject, text, html)."""
    empty_note = ("На этой неделе крупных транзитов нет — спокойный фон, "
                  "хорошее время для рутины и накопленных дел.")
    if lang == "en":
        empty_note = ("There are no major transits this week: a calm background and "
                      "a good time for routine tasks and unfinished business.")
    if not events:
        body_events = empty_note
    else:
        body_events = "\n\n".join(f"• {e['date']} — {e['title']}\n  {e['text']}" for e in events)
    subject = f"Ваш астропрогноз на неделю — {person}"
    body = (f"Здравствуйте!\n\nКлючевые транзиты недели для натальной карты «{person}»:\n\n"
            f"{body_events}\n\n"
            "Подробный прогноз с точными датами — на astrosmap.ru, вкладка «Прогноз».\n\n"
            "———\n"
            f"Чтобы отписаться от еженедельных писем: {unsub_link}")
    if lang == "en":
        subject = f"Your weekly astrology forecast — {person}"
        body = (f"Hello!\n\nThe week's key transits for the natal chart of {person}:\n\n"
                f"{body_events}\n\n"
                "For the detailed forecast with exact dates, visit the Forecast tab at astrosmap.ru.\n\n"
                f"———\nTo unsubscribe from weekly emails: {unsub_link}")
    return subject, body, _digest_html(person, events, empty_note, unsub_link, lang)


def _digest_html(person: str, events: list[dict], empty_note: str, unsub_link: str, lang: str = "ru") -> str:
    """HTML-открытка дайджеста. Инлайн-стили — почтовые клиенты не читают <style>/классы."""
    esc = html_mod.escape
    en = lang == "en"
    heading = "Your weekly astrology forecast" if en else "Ваш астропрогноз на неделю"
    chart_label = "Natal chart" if en else "Натальная карта"
    open_label = "Open the detailed forecast" if en else "Открыть подробный прогноз"
    footer = "An email for Astrocalculator subscribers" if en else "Письмо для подписчиков Астрокалькулятора"
    unsubscribe = "Unsubscribe from weekly emails" if en else "Отписаться от еженедельных писем"
    site_url = "https://astrosmap.ru/?lang=en" if en else "https://astrosmap.ru"
    if events:
        rows = "".join(
            f'<tr><td style="padding:14px 18px;border-bottom:1px solid rgba(201,168,106,0.18);">'
            f'<span style="display:inline-block;background:rgba(201,168,106,0.18);color:#e6c98a;'
            f'font-size:12px;font-weight:600;padding:3px 9px;border-radius:20px;">{esc(e["date"])}</span>'
            f'<div style="color:#efe9ff;font-size:16px;font-weight:600;margin:8px 0 4px;">{esc(e["title"])}</div>'
            f'<div style="color:#b9b4d6;font-size:14px;line-height:1.5;">{esc(e["text"])}</div>'
            f'</td></tr>'
            for e in events
        )
        events_block = f'<table role="presentation" width="100%" cellpadding="0" cellspacing="0">{rows}</table>'
    else:
        events_block = (f'<p style="color:#b9b4d6;font-size:15px;line-height:1.6;'
                        f'padding:16px 18px;margin:0;">{esc(empty_note)}</p>')
    return f"""\
<!DOCTYPE html><html lang="{'en' if en else 'ru'}"><body style="margin:0;padding:0;background:#0a0a1a;">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#0a0a1a;padding:24px 12px;">
<tr><td align="center">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#14142b;border:1px solid rgba(139,123,216,0.28);border-radius:16px;overflow:hidden;">
    <tr><td style="background:linear-gradient(135deg,#2a2350,#3a2f1a);padding:30px 24px;text-align:center;">
      <div style="font-size:30px;letter-spacing:6px;color:#c9a86a;">☉&nbsp;☽&nbsp;✦</div>
      <div style="color:#c9a86a;font-family:Georgia,serif;font-size:24px;font-weight:700;margin-top:10px;">{heading}</div>
      <div style="color:#b9b4d6;font-size:14px;margin-top:6px;">{chart_label} · {esc(person)}</div>
    </td></tr>
    <tr><td style="padding:6px 6px 0;">{events_block}</td></tr>
    <tr><td style="padding:22px 24px;text-align:center;">
      <a href="{site_url}" style="display:inline-block;background:#8b7bd8;color:#fff;text-decoration:none;font-size:15px;font-weight:600;padding:12px 26px;border-radius:10px;">{open_label}</a>
    </td></tr>
    <tr><td style="padding:0 24px 24px;text-align:center;">
      <div style="color:#6f6b90;font-size:12px;line-height:1.6;">{footer} · Project Artemisa.<br>
      <a href="{esc(unsub_link)}" style="color:#8b8ab0;">{unsubscribe}</a></div>
    </td></tr>
  </table>
</td></tr>
</table>
</body></html>"""


def support_letter(name: str, email: str, message: str, user_id: int | None) -> tuple[str, str]:
    """Письмо оператору с обращением из формы поддержки на сайте."""
    who = name.strip() if name else "аноним"
    contact = email.strip() if email else "почта не указана"
    acc = f"аккаунт #{user_id}" if user_id else "без входа"
    subject = f"Поддержка astrosmap.ru — {who}"
    body = (f"Новое сообщение из формы поддержки на сайте.\n\n"
            f"От: {who} ({contact}, {acc})\n\n"
            f"Сообщение:\n{message}\n\n"
            "———\n"
            "Ответить можно прямо на это письмо (Reply-To — почта пользователя, если он её указал).")
    return subject, body


def reset_letter(link: str, lang: str = "ru") -> tuple[str, str]:
    if lang == "en":
        return ("Password reset — Astrocalculator",
                f"Hello!\n\nTo set a new password on astrosmap.ru, open this link:\n{link}\n\n"
                "The link is valid for 1 hour and works once. If you didn't request a reset, ignore this message.")
    return ("Сброс пароля — Астрокалькулятор",
            f"Здравствуйте!\n\nЧтобы задать новый пароль на astrosmap.ru, откройте ссылку:\n{link}\n\n"
            "Ссылка действует 1 час и срабатывает один раз. Если вы не запрашивали сброс — удалите это письмо.")
