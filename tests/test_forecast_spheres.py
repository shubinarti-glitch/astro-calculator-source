from backend import astrology as A
from backend import interpretations as I


def _event(*, sphere_key, moving="Neptune", target="Venus", orb=0.2,
           tone="negative", date="2026-09-15", text="Трактовка\n\nСовет: действие"):
    return {
        "date": date,
        "exact_datetime": f"{date}T12:00:00",
        "p1": moving,
        "p2": target,
        "aspect_ru": "Квадрат",
        "orb": orb,
        "tone": tone,
        "sphere_key": sphere_key,
        "text": text,
    }


def test_forecast_spheres_skip_empty_and_do_not_duplicate_events():
    love = _event(sphere_key="love")
    career = _event(sphere_key="career", moving="Jupiter", target="Sun",
                    tone="positive", date="2026-09-20")

    spheres = A._forecast_by_sphere([love, career], "ru")

    assert [sphere["key"] for sphere in spheres] == ["love", "career"]
    assert [sphere["count"] for sphere in spheres] == [1, 1]
    assert sum(len(sphere["highlights"]) for sphere in spheres) == 2
    assert all("В наступающем периоде эта сфера" not in sphere["text"] for sphere in spheres)


def test_forecast_sphere_uses_strongest_event_as_interpretation():
    jupiter = _event(sphere_key="career", moving="Jupiter", orb=0.01,
                     text="Юпитер\n\nСовет: расширяйте")
    pluto = _event(sphere_key="career", moving="Pluto", orb=1.5,
                   text="Плутон\n\nСовет: меняйте")

    sphere = A._forecast_by_sphere([jupiter, pluto], "ru")[0]

    assert sphere["text"] == pluto["text"]
    assert sphere["highlights"][0] is pluto


def test_transit_forecast_parts_have_authored_interpretation_and_advice_ru_en():
    for lang, label in (("ru", "Совет:"), ("en", "Advice:")):
        parts = I.transit_forecast_parts("Neptune", "square", "Venus", lang)
        assert parts
        assert len(parts["interpretation"]) > 80
        assert len(parts["advice"]) > 40
        rendered = f"{parts['interpretation']}\n\n{label} {parts['advice']}"
        assert label in rendered
        assert "В наступающем периоде эта сфера" not in rendered


def test_forecast_house_mapping_is_unique():
    assert set(A._FORECAST_HOUSE_SPHERE) == set(range(1, 13))
    assert all(isinstance(value, str) for value in A._FORECAST_HOUSE_SPHERE.values())
