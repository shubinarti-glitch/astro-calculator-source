"""Isolated contract tests: never open the installed editorial package."""

import builtins
import json

import pytest

from backend import editorial_data


def scalar(value):
    return {"type": "scalar", "value": value}


@pytest.fixture(autouse=True)
def package_path(tmp_path, monkeypatch):
    editorial_data._package.cache_clear()
    path = tmp_path / "tables.json"
    monkeypatch.setattr(editorial_data, "_PACKAGE", path)
    try:
        yield path
    finally:
        editorial_data._package.cache_clear()


def write_package(path, node):
    path.write_text(
        json.dumps({"schema": 1, "tables": {"sample": node}}), encoding="utf-8"
    )


def test_preserves_tuple_and_integer_keys(package_path):
    write_package(package_path, {
        "type": "dict", "value": [
            [{"type": "tuple", "value": [scalar("Sun"), scalar(7)]},
             {"type": "tuple", "value": [scalar("Луна"), scalar(None)]}],
            [scalar(12), {"type": "list", "value": [scalar(True), scalar(1.5)]}],
        ],
    })

    result = editorial_data.table("sample")

    assert result == {("Sun", 7): ("Луна", None), 12: [True, 1.5]}
    assert type(next(key for key in result if key == 12)) is int
    assert type(result[("Sun", 7)]) is tuple
    assert type(result[12]) is list


def test_cached_package_returns_fresh_nested_tables(package_path):
    write_package(package_path, {
        "type": "dict", "value": [[scalar("items"), {
            "type": "tuple", "value": [{
                "type": "list", "value": [{
                    "type": "dict", "value": [[scalar(1), scalar("original")]],
                }],
            }],
        }]],
    })
    first = editorial_data.table("sample")
    first["items"][0][0][1] = "changed"
    first["items"][0].append("extra")
    first["extra"] = True
    # Even if the file changes, the parsed package is cached; decoded tables are not.
    write_package(package_path, scalar("replacement"))

    second = editorial_data.table("sample")
    assert second == {"items": ([{1: "original"}],)}
    assert second is not first
    assert second["items"][0] is not first["items"][0]
    assert editorial_data._package.cache_info().hits == 1
    editorial_data._package.cache_clear()
    assert editorial_data.table("sample") == "replacement"


@pytest.mark.parametrize("node", [
    None, [], {}, {"type": "scalar"},
    {"type": "scalar", "value": 1, "extra": True},
    {"type": "unknown", "value": 1},
    {"type": "scalar", "value": []},
    {"type": "scalar", "value": {}},
    {"type": "list", "value": "not a list"},
    {"type": "tuple", "value": {}},
    {"type": "dict", "value": {}},
    {"type": "list", "value": [None]},
    {"type": "tuple", "value": [scalar(1), {}]},
    {"type": "dict", "value": [None]},
    {"type": "dict", "value": [[scalar("key")]]},
    {"type": "dict", "value": [[scalar("key"), scalar(1), scalar(2)]]},
    {"type": "dict", "value": [[scalar("key"), {}]]},
])
def test_rejects_malformed_nodes(package_path, node):
    write_package(package_path, node)
    with pytest.raises(ValueError, match="Invalid editorial"):
        editorial_data.table("sample")


@pytest.mark.parametrize("key", [
    scalar("duplicate"), scalar(3),
    {"type": "tuple", "value": [scalar("Sun"), scalar(2)]},
])
def test_rejects_duplicate_dictionary_keys(package_path, key):
    write_package(package_path, {
        "type": "dict", "value": [[key, scalar(1)], [key, scalar(2)]],
    })
    with pytest.raises(ValueError, match="Duplicate editorial dictionary key"):
        editorial_data.table("sample")


def test_missing_package_has_actionable_error(package_path):
    assert not package_path.exists()
    with pytest.raises(RuntimeError, match="configure it before starting") as error:
        editorial_data.table("sample")
    assert isinstance(error.value.__cause__, FileNotFoundError)


def test_invalid_json_is_wrapped(package_path):
    package_path.write_text('{"schema":', encoding="utf-8")
    with pytest.raises(RuntimeError, match="missing or invalid") as error:
        editorial_data.table("sample")
    assert isinstance(error.value.__cause__, json.JSONDecodeError)


@pytest.mark.parametrize("raw", [
    {}, {"tables": {}}, {"schema": 2, "tables": {}},
    {"schema": "1", "tables": {}}, {"schema": 1},
    {"schema": 1, "tables": []}, {"schema": 1, "tables": None},
])
def test_rejects_invalid_schema(package_path, raw):
    package_path.write_text(json.dumps(raw), encoding="utf-8")
    with pytest.raises(ValueError, match="Unsupported editorial package schema"):
        editorial_data.table("sample")


def test_missing_table_names_required_key(package_path):
    write_package(package_path, scalar("present"))
    with pytest.raises(RuntimeError, match="Required editorial table is missing: absent") as error:
        editorial_data.table("absent")
    assert isinstance(error.value.__cause__, KeyError)
    assert editorial_data.table("sample") == "present"


def test_code_like_strings_remain_inert(package_path, tmp_path, monkeypatch):
    marker = tmp_path / "executed.txt"
    payload = f"__import__('pathlib').Path({str(marker)!r}).write_text('executed')"

    def forbidden_eval(*args, **kwargs):
        pytest.fail("Editorial data must never be evaluated as Python")

    write_package(package_path, {
        "type": "dict", "value": [[scalar(payload), scalar(payload)]],
    })
    with monkeypatch.context() as patch:
        patch.setattr(builtins, "eval", forbidden_eval)
        patch.setattr(builtins, "exec", forbidden_eval)
        result = editorial_data.table("sample")
    assert result == {payload: payload}
    assert not marker.exists()


def test_python_expression_is_not_a_package(package_path, tmp_path):
    marker = tmp_path / "executed.txt"
    package_path.write_text(
        f"__import__('pathlib').Path({str(marker)!r}).write_text('executed')",
        encoding="utf-8",
    )
    with pytest.raises(RuntimeError, match="missing or invalid"):
        editorial_data.table("sample")
    assert not marker.exists()
