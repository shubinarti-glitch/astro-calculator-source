# -*- coding: utf-8 -*-
"""Платная подписка через ЮKassa (redirect-flow).

Схема: create_payment создаёт платёж в ЮKassa и возвращает confirmation_url;
после оплаты пользователь возвращается на сайт, фронт зовёт check_payments,
мы запрашиваем статус у ЮKassa и при succeeded продлеваем подписку.
Ключи — в data/yookassa.json (боевые/тестовые вносит владелец вручную).
"""
from __future__ import annotations

import json
import uuid
from pathlib import Path

import requests

from . import db

CONFIG_FILE = Path(__file__).resolve().parent.parent / "data" / "yookassa.json"
API = "https://api.yookassa.ru/v3/payments"

# план -> (цена в рублях, дней подписки)
# plus_* = подписка + разовая консультация астролога (+1500 ₽ к цене тарифа)
# consult = только консультация, без подписки (0 дней)
PLANS = {
    "month": (299, 30),
    "year": (1990, 365),
    "plus_month": (1799, 30),
    "plus_year": (3490, 365),
    "consult": (3500, 0),
    "report": (149, 0),  # разовый PDF-отчёт, без подписки (0 дней)
}

PLAN_TITLES = {
    "month": "Подписка «Премиум», месяц",
    "year": "Подписка «Премиум», год",
    "plus_month": "Подписка «Премиум+» (месяц + консультация)",
    "plus_year": "Подписка «Премиум+» (год + консультация)",
    "consult": "Консультация астролога",
    "report": "Разовый PDF-отчёт",
}


class NotConfiguredError(Exception):
    pass


def _auth() -> tuple[str, str]:
    if not CONFIG_FILE.exists():
        CONFIG_FILE.write_text(
            json.dumps({"shop_id": "", "secret_key": ""}, indent=2), encoding="utf-8"
        )
    cfg = json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
    if not cfg.get("shop_id") or not cfg.get("secret_key"):
        raise NotConfiguredError("Платёжная система не настроена (data/yookassa.json)")
    return cfg["shop_id"], cfg["secret_key"]


def create_payment(user_id: int, plan: str, return_url: str) -> str:
    """Создать платёж, вернуть URL страницы оплаты ЮKassa."""
    price, _days = PLANS[plan]
    resp = requests.post(
        API,
        auth=_auth(),
        headers={"Idempotence-Key": str(uuid.uuid4())},
        json={
            "amount": {"value": f"{price}.00", "currency": "RUB"},
            "capture": True,
            "confirmation": {"type": "redirect", "return_url": return_url},
            "description": f"{PLAN_TITLES[plan]}, пользователь #{user_id}",
            "metadata": {"user_id": user_id, "plan": plan},
        },
        timeout=15,
    )
    resp.raise_for_status()
    data = resp.json()
    db.add_payment(data["id"], user_id, plan)
    return data["confirmation"]["confirmation_url"]


def _apply_status(payment_id: str, user_id: int, plan: str, status: str) -> bool:
    """Идемпотентно применить статус ЮKassa к нашему платежу. Вызывать только для записей
    в статусе pending (иначе возможна двойная выдача подписки). Возвращает True при активации."""
    if status == "succeeded":
        db.set_payment_status(payment_id, "succeeded")
        days = PLANS[plan][1]
        if days:
            db.extend_subscription(user_id, plan, days)
        if plan.startswith("plus_") or plan == "consult":
            db.add_consultation(user_id)
        if plan == "report":
            db.add_report_credit(user_id)
        return True
    if status == "canceled":
        db.set_payment_status(payment_id, "canceled")
    return False


def reconcile_one(payment_id: str) -> bool:
    """Перепроверить один платёж в ЮKassa (авторитетно) и применить статус, если у нас ещё
    pending. Источник истины — API ЮKassa, не тело вебхука. Возвращает True при активации."""
    rec = db.get_pending_payment(payment_id)
    if not rec:
        return False  # неизвестный или уже обработанный платёж — ничего не делаем
    resp = requests.get(f"{API}/{payment_id}", auth=_auth(), timeout=15)
    if resp.status_code != 200:
        return False
    return _apply_status(payment_id, rec["user_id"], rec["plan"], resp.json().get("status", ""))


def reconcile_pending(older_than_min: int = 30) -> int:
    """Досверка всех висящих pending старше N минут (для админки). Возвращает число обновлённых."""
    n = 0
    for p in db.pending_payments_all(older_than_min):
        try:
            resp = requests.get(f"{API}/{p['payment_id']}", auth=_auth(), timeout=15)
            if resp.status_code != 200:
                continue
            status = resp.json().get("status", "")
            if status in ("succeeded", "canceled"):
                _apply_status(p["payment_id"], p["user_id"], p["plan"], status)
                n += 1
        except requests.RequestException:
            continue  # сеть/ЮKassa недоступны — пропускаем, не роняем вызывающего
    return n


def check_payments(user_id: int) -> dict:
    """Проверить pending-платежи пользователя; активировать подписку при успехе."""
    activated = False
    for p in db.pending_payments(user_id):
        resp = requests.get(f"{API}/{p['payment_id']}", auth=_auth(), timeout=15)
        if resp.status_code != 200:
            continue
        if _apply_status(p["payment_id"], user_id, p["plan"], resp.json().get("status", "")):
            activated = True
    return {"activated": activated, "premium": db.is_premium(user_id),
            "subscription": db.get_subscription(user_id)}
