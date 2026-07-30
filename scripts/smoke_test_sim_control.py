"""One-off Playwright smoke test for the sim-control mini-app injectors. Not part of the app; run manually."""
import re
import sys
import time
from playwright.sync_api import sync_playwright

URL = "http://localhost:5174"
SHOT = "smoke_sim_control.png"

INJECTORS = ["Inject Fuel Theft", "Inject Overheat", "Inject Overload", "Drop Connectivity"]


def main():
    with sync_playwright() as p:
        browser = p.chromium.launch()
        page = browser.new_page(viewport={"width": 900, "height": 900})
        errors = []
        page.on("console", lambda m: errors.append(m.text) if m.type == "error" else None)
        page.on("pageerror", lambda e: errors.append(str(e)))

        page.goto(URL, wait_until="networkidle")
        card = page.locator(".device-card").first
        card.wait_for(state="visible", timeout=10_000)
        print(f"[OK] {page.locator('.device-card').count()} sim device card(s) loaded")

        gen_toggle = card.locator(".gen-toggle")
        before = gen_toggle.inner_text()
        gen_toggle.click()
        page.wait_for_timeout(500)
        after = gen_toggle.inner_text()
        assert before != after, "generator toggle did not change state"
        print(f"[OK] generator toggle: {before} -> {after}")
        gen_toggle.click()  # restore ON so fuel-theft/overload rules behave as documented
        page.wait_for_timeout(500)

        for label in INJECTORS:
            btn = card.locator(".injector-btn", has_text=label)
            btn.wait_for(state="visible", timeout=5_000)
            assert btn.is_enabled(), f"{label} button not enabled before click"
            btn.click()
            page.wait_for_timeout(500)
            classes = btn.get_attribute("class")
            text = btn.locator(".inj-label").inner_text()
            active = "injector-active" in classes and re.match(r"^\d+s$", text)
            print(f"[{'OK' if active else 'FAIL'}] {label} -> active={'injector-active' in classes}, label='{text}'")
            assert active, f"{label} did not enter active/counting-down state"

        page.wait_for_timeout(1500)
        # countdown should be ticking down, not stuck
        first_btn = card.locator(".injector-btn", has_text=INJECTORS[0])
        text_now = first_btn.locator(".inj-label").inner_text()
        print(f"[OK] countdown ticking: {INJECTORS[0]} now shows '{text_now}'")

        page.screenshot(path=SHOT, full_page=True)
        print(f"[OK] screenshot saved -> {SHOT}")

        browser.close()

        if errors:
            print(f"[WARN] {len(errors)} console error(s):")
            for e in errors:
                print(f"  - {e}")
        else:
            print("[OK] no console errors")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        print(f"[FAIL] {e}")
        sys.exit(1)
