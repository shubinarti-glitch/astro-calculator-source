# -*- coding: utf-8 -*-
"""Двуязычные (RU/EN) названия и символы для знаков, планет, домов и аспектов."""


def _l(d: dict, lang: str, default=""):
    """Локализованное значение: d['en'] при lang=='en', иначе d['ru']."""
    return d.get("en" if lang == "en" else "ru", default)


# Знаки зодиака: код Kerykeion -> названия/символ/стихия
SIGNS = {
    "Ari": {"ru": "Овен", "en": "Aries", "symbol": "♈", "element": "fire"},
    "Tau": {"ru": "Телец", "en": "Taurus", "symbol": "♉", "element": "earth"},
    "Gem": {"ru": "Близнецы", "en": "Gemini", "symbol": "♊", "element": "air"},
    "Can": {"ru": "Рак", "en": "Cancer", "symbol": "♋", "element": "water"},
    "Leo": {"ru": "Лев", "en": "Leo", "symbol": "♌", "element": "fire"},
    "Vir": {"ru": "Дева", "en": "Virgo", "symbol": "♍", "element": "earth"},
    "Lib": {"ru": "Весы", "en": "Libra", "symbol": "♎", "element": "air"},
    "Sco": {"ru": "Скорпион", "en": "Scorpio", "symbol": "♏", "element": "water"},
    "Sag": {"ru": "Стрелец", "en": "Sagittarius", "symbol": "♐", "element": "fire"},
    "Cap": {"ru": "Козерог", "en": "Capricorn", "symbol": "♑", "element": "earth"},
    "Aqu": {"ru": "Водолей", "en": "Aquarius", "symbol": "♒", "element": "air"},
    "Pis": {"ru": "Рыбы", "en": "Pisces", "symbol": "♓", "element": "water"},
}

ELEMENT_NAMES = {
    "fire": {"ru": "Огонь", "en": "Fire"},
    "earth": {"ru": "Земля", "en": "Earth"},
    "air": {"ru": "Воздух", "en": "Air"},
    "water": {"ru": "Вода", "en": "Water"},
}

# Планеты и точки: имя Kerykeion -> названия/символ
POINTS = {
    "Sun": {"ru": "Солнце", "en": "Sun", "symbol": "☉"},
    "Moon": {"ru": "Луна", "en": "Moon", "symbol": "☽"},
    "Mercury": {"ru": "Меркурий", "en": "Mercury", "symbol": "☿"},
    "Venus": {"ru": "Венера", "en": "Venus", "symbol": "♀"},
    "Mars": {"ru": "Марс", "en": "Mars", "symbol": "♂"},
    "Jupiter": {"ru": "Юпитер", "en": "Jupiter", "symbol": "♃"},
    "Saturn": {"ru": "Сатурн", "en": "Saturn", "symbol": "♄"},
    "Uranus": {"ru": "Уран", "en": "Uranus", "symbol": "♅"},
    "Neptune": {"ru": "Нептун", "en": "Neptune", "symbol": "♆"},
    "Pluto": {"ru": "Плутон", "en": "Pluto", "symbol": "♇"},
    "Mean_North_Lunar_Node": {"ru": "Сев. узел (Раху)", "en": "North Node (Rahu)", "symbol": "☊"},
    "True_North_Lunar_Node": {"ru": "Сев. узел (ист.)", "en": "North Node (true)", "symbol": "☊"},
    "Mean_South_Lunar_Node": {"ru": "Юж. узел (Кету)", "en": "South Node (Ketu)", "symbol": "☋"},
    "True_South_Lunar_Node": {"ru": "Юж. узел (ист.)", "en": "South Node (true)", "symbol": "☋"},
    "Chiron": {"ru": "Хирон", "en": "Chiron", "symbol": "⚷"},
    "Mean_Lilith": {"ru": "Лилит (Чёрная Луна)", "en": "Lilith (Black Moon)", "symbol": "⚸"},
    "True_Lilith": {"ru": "Лилит (ист.)", "en": "Lilith (true)", "symbol": "⚸"},
    "Ascendant": {"ru": "Асцендент", "en": "Ascendant", "symbol": "ASC"},
    "Medium_Coeli": {"ru": "Середина неба (MC)", "en": "Midheaven (MC)", "symbol": "MC"},
    "Descendant": {"ru": "Десцендент", "en": "Descendant", "symbol": "DSC"},
    "Imum_Coeli": {"ru": "Глубина неба (IC)", "en": "Imum Coeli (IC)", "symbol": "IC"},
    "Mercury_Retrograde": {"ru": "Меркурий", "en": "Mercury", "symbol": "☿"},
}

# Аспекты: имя Kerykeion -> названия/символ/характер
ASPECTS = {
    "conjunction": {"ru": "Соединение", "en": "Conjunction", "symbol": "☌", "nature": "neutral"},
    "opposition": {"ru": "Оппозиция", "en": "Opposition", "symbol": "☍", "nature": "tense"},
    "trine": {"ru": "Трин", "en": "Trine", "symbol": "△", "nature": "harmonious"},
    "square": {"ru": "Квадрат", "en": "Square", "symbol": "□", "nature": "tense"},
    "sextile": {"ru": "Секстиль", "en": "Sextile", "symbol": "✱", "nature": "harmonious"},
    "quincunx": {"ru": "Квинконс", "en": "Quincunx", "symbol": "⚻", "nature": "tense"},
    "semisextile": {"ru": "Полусекстиль", "en": "Semisextile", "symbol": "⚺", "nature": "weak"},
    "semisquare": {"ru": "Полуквадрат", "en": "Semisquare", "symbol": "∠", "nature": "tense"},
    "sesquiquadrate": {"ru": "Полутораквадрат", "en": "Sesquiquadrate", "symbol": "⚼", "nature": "tense"},
    "sesquisquare": {"ru": "Полутораквадрат", "en": "Sesquiquadrate", "symbol": "⚼", "nature": "tense"},
    "quintile": {"ru": "Квинтиль", "en": "Quintile", "symbol": "Q", "nature": "creative"},
    "biquintile": {"ru": "Биквинтиль", "en": "Biquintile", "symbol": "bQ", "nature": "creative"},
}

NATURE_NAMES = {
    "neutral": {"ru": "нейтральный", "en": "neutral"},
    "tense": {"ru": "напряжённый", "en": "tense"},
    "harmonious": {"ru": "гармоничный", "en": "harmonious"},
    "weak": {"ru": "слабый", "en": "weak"},
    "creative": {"ru": "творческий", "en": "creative"},
}

# Дома: имя Kerykeion -> номер
HOUSE_NUM = {
    "First_House": 1, "Second_House": 2, "Third_House": 3, "Fourth_House": 4,
    "Fifth_House": 5, "Sixth_House": 6, "Seventh_House": 7, "Eighth_House": 8,
    "Ninth_House": 9, "Tenth_House": 10, "Eleventh_House": 11, "Twelfth_House": 12,
}

HOUSE_MEANINGS = {
    1: {"ru": "Личность, внешность, самовыражение", "en": "Self, appearance, self-expression"},
    2: {"ru": "Деньги, ресурсы, ценности", "en": "Money, resources, values"},
    3: {"ru": "Общение, обучение, ближнее окружение", "en": "Communication, learning, immediate surroundings"},
    4: {"ru": "Дом, семья, корни", "en": "Home, family, roots"},
    5: {"ru": "Творчество, дети, любовь", "en": "Creativity, children, romance"},
    6: {"ru": "Работа, обязанности, рутина", "en": "Work, duties, daily routine"},
    7: {"ru": "Партнёрство, брак, отношения", "en": "Partnership, marriage, relationships"},
    8: {"ru": "Кризисы, трансформация, чужие ресурсы", "en": "Crises, transformation, shared resources"},
    9: {"ru": "Мировоззрение, путешествия, высшее образование", "en": "Worldview, travel, higher education"},
    10: {"ru": "Карьера, статус, призвание", "en": "Career, status, vocation"},
    11: {"ru": "Друзья, цели, сообщества", "en": "Friends, goals, communities"},
    12: {"ru": "Подсознание, уединение, тайны", "en": "Subconscious, solitude, secrets"},
}

# Движение аспекта
MOVEMENT = {
    "Applying": {"ru": "сходящийся", "en": "applying"},
    "Separating": {"ru": "расходящийся", "en": "separating"},
    "Exact": {"ru": "точный", "en": "exact"},
    "Static": {"ru": "статичный", "en": "static"},
}

# Фазы Луны (ключи — английские названия Kerykeion)
MOON_PHASE = {
    "New Moon": {"ru": "Новолуние", "en": "New Moon"},
    "Waxing Crescent": {"ru": "Растущий серп", "en": "Waxing Crescent"},
    "First Quarter": {"ru": "Первая четверть", "en": "First Quarter"},
    "Waxing Gibbous": {"ru": "Растущая Луна", "en": "Waxing Gibbous"},
    "Full Moon": {"ru": "Полнолуние", "en": "Full Moon"},
    "Waning Gibbous": {"ru": "Убывающая Луна", "en": "Waning Gibbous"},
    "Last Quarter": {"ru": "Последняя четверть", "en": "Last Quarter"},
    "Waning Crescent": {"ru": "Убывающий серп", "en": "Waning Crescent"},
}

# Синастрия: описание индекса совместимости (метод Ч. Дисчеполо)
SCORE_DESC = {
    "Minimal": {"ru": "Минимальная", "en": "Minimal"},
    "Medium": {"ru": "Средняя", "en": "Medium"},
    "Important": {"ru": "Значимая", "en": "Important"},
    "Very Important": {"ru": "Очень значимая", "en": "Very important"},
    "Exceptional": {"ru": "Исключительная", "en": "Exceptional"},
    "Rare Exceptional": {"ru": "Редкая исключительная", "en": "Rare exceptional"},
}

# Синастрия: правила начисления очков
SYNASTRY_RULE = {
    "destiny_sign": {"ru": "Знаки судьбы (родство Солнц)", "en": "Destiny signs (Sun affinity)"},
    "sun_sun": {"ru": "Солнце — Солнце", "en": "Sun — Sun"},
    "sun_moon": {"ru": "Солнце — Луна", "en": "Sun — Moon"},
    "moon_moon": {"ru": "Луна — Луна", "en": "Moon — Moon"},
    "moon_sun": {"ru": "Луна — Солнце", "en": "Moon — Sun"},
    "sun_ascendant": {"ru": "Солнце — Асцендент", "en": "Sun — Ascendant"},
    "ascendant_sun": {"ru": "Асцендент — Солнце", "en": "Ascendant — Sun"},
    "moon_ascendant": {"ru": "Луна — Асцендент", "en": "Moon — Ascendant"},
    "ascendant_moon": {"ru": "Асцендент — Луна", "en": "Ascendant — Moon"},
    "venus_mars": {"ru": "Венера — Марс", "en": "Venus — Mars"},
    "mars_venus": {"ru": "Марс — Венера", "en": "Mars — Venus"},
    "ascendant_ascendant": {"ru": "Асцендент — Асцендент", "en": "Ascendant — Ascendant"},
    "sun_venus": {"ru": "Солнце — Венера", "en": "Sun — Venus"},
    "venus_sun": {"ru": "Венера — Солнце", "en": "Venus — Sun"},
}

# Системы домов
HOUSE_SYSTEMS = {
    "P": {"ru": "Плацидус", "en": "Placidus"},
    "K": {"ru": "Кох", "en": "Koch"},
    "R": {"ru": "Региомонтан", "en": "Regiomontanus"},
    "C": {"ru": "Кампано", "en": "Campanus"},
    "E": {"ru": "Равнодомная (от Asc)", "en": "Equal (from Asc)"},
    "W": {"ru": "Цельнознаковая (Whole Sign)", "en": "Whole Sign"},
    "B": {"ru": "Алькабитиус", "en": "Alcabitius"},
    "M": {"ru": "Морин", "en": "Morinus"},
}


# --------------------------------------------------------------------------- #
#  Аксессоры с языком
# --------------------------------------------------------------------------- #
def sign_name(code, lang="ru"):
    return _l(SIGNS.get(code, {}), lang, code)


# Названия знаков в предложном падеже (для конструкций «в Раке», «во Льве»).
SIGN_LOC_RU = {
    "Ari": "Овне", "Tau": "Тельце", "Gem": "Близнецах", "Can": "Раке",
    "Leo": "Льве", "Vir": "Деве", "Lib": "Весах", "Sco": "Скорпионе",
    "Sag": "Стрельце", "Cap": "Козероге", "Aqu": "Водолее", "Pis": "Рыбах",
}


def sign_prep(code, lang="ru"):
    """Знак в предложном падеже (RU); для EN — обычное имя."""
    if lang == "en":
        return sign_name(code, "en")
    return SIGN_LOC_RU.get(code, sign_name(code, "ru"))


def sign_in(code, lang="ru"):
    """Полная форма «в Раке» / «во Льве» / «in Cancer» — предлог + знак в нужном падеже."""
    if lang == "en":
        return f"in {sign_name(code, 'en')}"
    prep = "во" if code == "Leo" else "в"
    return f"{prep} {sign_prep(code, 'ru')}"


def sign_symbol(code):
    return SIGNS.get(code, {}).get("symbol", "")


def sign_element(code, lang="ru"):
    el = SIGNS.get(code, {}).get("element", "")
    return _l(ELEMENT_NAMES.get(el, {}), lang, el)


def point_name(name, lang="ru"):
    return _l(POINTS.get(name, {}), lang, name)


def point_symbol(name):
    return POINTS.get(name, {}).get("symbol", "")


def aspect_name(kind, lang="ru"):
    return _l(ASPECTS.get(kind, {}), lang, kind)


def aspect_symbol(kind):
    return ASPECTS.get(kind, {}).get("symbol", "")


def aspect_nature(kind, lang="ru"):
    nat = ASPECTS.get(kind, {}).get("nature", "")
    return _l(NATURE_NAMES.get(nat, {}), lang, nat)


def house_meaning(num, lang="ru"):
    return _l(HOUSE_MEANINGS.get(num, {}), lang, "")


def movement_name(name, lang="ru"):
    return _l(MOVEMENT.get(name, {}), lang, name)


def moon_phase_name(name, lang="ru"):
    return _l(MOON_PHASE.get(name, {}), lang, name)


def score_desc(desc, lang="ru"):
    return _l(SCORE_DESC.get(desc, {}), lang, desc)


def synastry_rule(rule, lang="ru"):
    return _l(SYNASTRY_RULE.get(rule, {}), lang, rule)


def house_system_name(ident, lang="ru"):
    return _l(HOUSE_SYSTEMS.get(ident, {}), lang, ident)


# Порядок планет для таблицы
PLANET_ORDER = [
    "sun", "moon", "mercury", "venus", "mars",
    "jupiter", "saturn", "uranus", "neptune", "pluto",
    "mean_north_lunar_node", "mean_south_lunar_node",
    "chiron", "mean_lilith",
]

# Угловые точки (оси)
ANGLE_ORDER = ["ascendant", "medium_coeli", "descendant", "imum_coeli"]

HOUSE_ORDER = [
    "first_house", "second_house", "third_house", "fourth_house",
    "fifth_house", "sixth_house", "seventh_house", "eighth_house",
    "ninth_house", "tenth_house", "eleventh_house", "twelfth_house",
]
