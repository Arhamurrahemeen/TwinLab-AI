"""Self-check for simulator.py's generator_on publish gate. Run: python test_simulator.py"""
from simulator import _compute_values

SENSORS = ["fuel_level", "load_current", "temperature", "humidity"]


def ctrl(generator_on, fuel_theft=False):
    return {
        "generator_on": generator_on,
        "base_values": {"fuel_level": 70.0, "load_current": 18.0, "temperature": 35.0, "humidity": 55.0},
        "inject": {
            "fuel_theft": {"active": fuel_theft, "until_ts": 9_999_999_999_999 if fuel_theft else 0},
            "overheat": {"active": False, "until_ts": 0},
            "overload": {"active": False, "until_ts": 0},
            "offline": {"active": False, "until_ts": 0},
        },
    }


# generator off, no theft -> device stops publishing entirely
assert _compute_values("d1", SENSORS, ctrl(generator_on=False), t=0) is None

# generator on -> publishes as normal
assert _compute_values("d2", SENSORS, ctrl(generator_on=True), t=0) is not None

# generator off but mid fuel-theft injection -> keeps publishing so the theft rule can fire
assert _compute_values("d3", SENSORS, ctrl(generator_on=False, fuel_theft=True), t=0) is not None

print("[OK] simulator generator_on gate")
