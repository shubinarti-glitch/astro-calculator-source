# -*- coding: utf-8 -*-
"""Запуск сайта: python run.py  ->  http://127.0.0.1:8000

Порт можно переопределить переменной окружения PORT.
Если порт занят, автоматически выбирается следующий свободный.
Автоперезагрузку (для разработки) включает RELOAD=1.
"""
import os
import socket
import webbrowser
import threading

import uvicorn


def _free_port(preferred: int, host: str = "127.0.0.1") -> int:
    """Возвращает preferred, если свободен, иначе следующий свободный порт."""
    for port in range(preferred, preferred + 50):
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            if s.connect_ex((host, port)) != 0:  # порт не слушается — свободен
                return port
    return preferred


if __name__ == "__main__":
    host = "127.0.0.1"
    preferred = int(os.environ.get("PORT", "8000"))
    port = _free_port(preferred, host)
    reload = os.environ.get("RELOAD", "0") == "1"  # по умолчанию выкл. — один чистый процесс
    url = f"http://{host}:{port}"

    print("=" * 50)
    if port != preferred:
        print(f"  Порт {preferred} был занят — сайт на порту {port}.")
    print(f"  Сайт открыт по адресу:  {url}")
    print("  Остановить: Ctrl+C или закрыть это окно.")
    print("=" * 50)

    # Автоматически открыть браузер (только без режима reload).
    if not reload:
        threading.Timer(1.5, lambda: webbrowser.open(url)).start()

    uvicorn.run("backend.main:app", host=host, port=port, reload=reload)
