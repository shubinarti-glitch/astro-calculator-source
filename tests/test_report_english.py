import re
import pytest
from backend import astrology

NATAL = dict(name="Alex", year=1990, month=5, day=17, hour=12, minute=30,
             lat=55.75, lng=37.61, city="Moscow", tz_str="Europe/Moscow", lang="en")


def assert_english(value, path="report"):
    if isinstance(value, dict):
        for key, item in value.items():
            assert_english(item, f"{path}.{key}")
    elif isinstance(value, list):
        for index, item in enumerate(value):
            assert_english(item, f"{path}[{index}]")
    elif isinstance(value, str):
        assert not re.search("[а-яА-ЯёЁ]", value), (path, value[:200])


@pytest.mark.parametrize("kind", ["natal", "progression", "solar", "lunar", "transit", "synastry", "forecast", "calendar"])
def test_english_calculation_output(kind):
    if kind == "natal":
        report = astrology.natal_report(dict(NATAL), with_svg=False)
    elif kind == "progression":
        report = astrology.progression_report(dict(NATAL), dict(year=2026, month=9, day=8, hour=12, minute=0), with_svg=False)
    elif kind == "transit":
        report = astrology.transit_report(dict(NATAL), dict(year=2026, month=9, day=8, hour=12, minute=0), with_svg=False)
    elif kind == "synastry":
        report = astrology.synastry_report(dict(NATAL), dict(NATAL, name="Sam", year=1992), with_svg=False)
    elif kind in ("forecast", "calendar"):
        method = astrology.forecast_report if kind == "forecast" else astrology.transit_calendar_report
        report = method(dict(NATAL), dict(year=2026, month=9, day=8), dict(year=2026, month=9, day=10))
    else:
        report = astrology.return_report(dict(NATAL), year=2026, month=9 if kind == "lunar" else None,
                                         return_type="Lunar" if kind == "lunar" else "Solar", with_svg=False)
    assert_english(report)
