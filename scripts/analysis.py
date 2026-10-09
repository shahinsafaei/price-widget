#!/usr/bin/env python3
"""
Daily market analysis for Nerkhino — runs right after fetch_rates.py.

Two layers, both free:
  1. A rule-based engine: every number (trend, RSI, moving averages, money
     flow, breadth, queues, leading sectors, coin bubble, a "market mood"
     gauge) is computed here in code and rendered as Persian sentences from
     templates. This ALWAYS produces a complete analysis.
  2. Optional AI polish through GitHub Models (free quota, uses the workflow's
     GITHUB_TOKEN — no extra key). At most a few times a day. The AI text is
     rejected if it contains any number that isn't in our facts, so it can't
     invent data; on any failure the template text is used.

The analysis is descriptive (what happened and why), never a buy/sell signal.

Output: <data-dir>/analysis.json
"""
import argparse
import datetime
import hashlib
import json
import os
import re
import urllib.request
from zoneinfo import ZoneInfo

TEHRAN = ZoneInfo("Asia/Tehran")
AI_SLOTS = (10, 13, 17, 20)   # Tehran hours at which the AI text may be refreshed
AI_ENDPOINTS = [
    ("https://models.github.ai/inference/chat/completions", "openai/gpt-4.1-mini"),
    ("https://models.inference.ai.azure.com/chat/completions", "gpt-4o-mini"),
]


def load(path, default):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return default


def save(path, obj):
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, separators=(",", ":"))
    os.replace(tmp, path)


def num(v):
    try:
        return float(str(v).replace(",", ""))
    except (TypeError, ValueError):
        return None


# ----------------------------------------------------------------- formatting

def fmt(v):
    if v is None:
        return "—"
    a = abs(v)
    if a >= 1000:
        return f"{v:,.0f}"
    if a >= 1:
        return f"{v:,.2f}".rstrip("0").rstrip(".")
    return f"{v:.4f}"


def pct(v):
    return f"{'+' if v >= 0 else ''}{v:.2f}٪"


def words(v):
    a = abs(v)
    if a >= 1e12:
        return f"{v / 1e12:.1f} هزار میلیارد"
    if a >= 1e9:
        return f"{v / 1e9:.1f} میلیارد"
    if a >= 1e6:
        return f"{v / 1e6:.1f} میلیون"
    return fmt(v)


def pick(options, seed):
    """Deterministic variety: same day → same phrasing, different days differ."""
    h = int(hashlib.md5(seed.encode()).hexdigest(), 16)
    return options[h % len(options)]


# ----------------------------------------------------------------- indicators

def closes(daily, key):
    return [r[4] for r in daily.get(key, []) if len(r) >= 5 and r[4] is not None]


def change_over(c, n):
    if len(c) <= n or not c[-1 - n]:
        return None
    return (c[-1] / c[-1 - n] - 1) * 100


def sma(c, n):
    return sum(c[-n:]) / n if len(c) >= n else None


def rsi(c, n=14):
    if len(c) <= n:
        return None
    gains = losses = 0.0
    for a, b in zip(c[-n - 1:-1], c[-n:]):
        d = b - a
        if d > 0:
            gains += d
        else:
            losses -= d
    if losses == 0:
        return 100.0
    rs = (gains / n) / (losses / n)
    return 100 - 100 / (1 + rs)


def trend_of(c):
    """'up' | 'down' | 'flat' | None from price vs 5/20-day averages."""
    s5, s20 = sma(c, 5), sma(c, 20)
    if not c or s5 is None:
        return None
    last = c[-1]
    if s20 is not None:
        if last > s5 > s20:
            return "up"
        if last < s5 < s20:
            return "down"
        return "flat"
    ch = change_over(c, min(4, len(c) - 1))
    if ch is None:
        return None
    return "up" if ch > 1 else "down" if ch < -1 else "flat"


TREND_FA = {"up": "صعودی", "down": "نزولی", "flat": "خنثی/نوسانی"}


def rsi_note(v):
    if v is None:
        return None
    if v >= 70:
        return "اشباع خرید"
    if v <= 30:
        return "اشباع فروش"
    return None


# ----------------------------------------------------------------- engine

class Builder:
    def __init__(self, seed):
        self.seed = seed
        self.sections = []
        self.facts = []          # plain lines given to the AI (the only allowed numbers)

    def fact(self, line):
        self.facts.append(line)

    def section(self, sid, title, icon):
        s = {"id": sid, "title": title, "icon": icon, "tone": "neutral", "bullets": [], "metrics": []}
        self.sections.append(s)
        return s


def bullet(section, text, tone="neutral"):
    section["bullets"].append({"text": text, "tone": tone})


def metric(section, label, value, tone="neutral"):
    section["metrics"].append({"label": label, "value": value, "tone": tone})


CONNECT_SAME = ["همچنین ", "علاوه بر این، ", "در همین حال ", "در کنار آن، "]
CONNECT_TURN = ["از سوی دیگر، ", "در مقابل، ", "با این حال، "]


def paragraph(bullets, seed):
    """Joins a section's sentences into one flowing paragraph with connectors,
    so it reads like an article instead of separate one-liners."""
    out, prev_tone, i_same, i_turn = [], None, 0, 0
    offset = int(hashlib.md5(seed.encode()).hexdigest(), 16)
    for i, b in enumerate(bullets):
        t = b["text"].strip()
        if not t.endswith((".", "؛", "!", "؟")):
            t += "."
        if i > 0:
            turned = prev_tone in ("positive", "negative") and b["tone"] in ("positive", "negative") and b["tone"] != prev_tone
            if turned:
                t = CONNECT_TURN[(i_turn + offset) % len(CONNECT_TURN)] + t
                i_turn += 1
            elif i % 2 == 0:
                t = CONNECT_SAME[(i_same + offset) % len(CONNECT_SAME)] + t
                i_same += 1
        if b["tone"] in ("positive", "negative"):
            prev_tone = b["tone"]
        out.append(t)
    return " ".join(out)


def tone_of(v, eps=0.05):
    if v is None:
        return "neutral"
    return "positive" if v > eps else "negative" if v < -eps else "neutral"


def asset_lines(b, sec, name, item, daily, key, unit):
    """Price, daily change, trend, RSI for one asset → bullets/metrics/facts."""
    if not item:
        return None
    p, ch = num(item.get("price")), num(item.get("change_percent"))
    c = closes(daily, key)
    tr, r = trend_of(c), rsi(c)
    w = change_over(c, 5)
    metric(sec, name, f"{fmt(p)} {unit}", tone_of(ch))
    parts = [f"{name} {fmt(p)} {unit}"]
    if ch is not None:
        parts.append(f"تغییر امروز {pct(ch)}")
    if w is not None:
        parts.append(f"در ۵ روز {pct(w)}")
    if tr:
        parts.append(f"روند {TREND_FA[tr]}")
    if r is not None:
        parts.append(f"RSI {r:.0f}" + (f" ({rsi_note(r)})" if rsi_note(r) else ""))
    b.fact(" · ".join(parts))
    return {"price": p, "chg": ch, "week": w, "trend": tr, "rsi": r}


def build(data_dir, now):
    gold = load(f"{data_dir}/latest.json", {})
    market = load(f"{data_dir}/market.json", {})
    daily = load(f"{data_dir}/history/daily_core.json", {})
    codal = load(f"{data_dir}/codal.json", {}).get("items", [])
    seed = now.strftime("%Y-%m-%d")
    b = Builder(seed)

    by_sym = {}
    for k in ("gold", "currency", "cryptocurrency"):
        for it in gold.get(k, []) or []:
            if isinstance(it, dict) and it.get("symbol"):
                by_sym[it["symbol"]] = it
    for it in market.get("indices", []) or []:
        by_sym[it.get("symbol")] = it

    # ---------- currency & gold ----------
    sec = b.section("fx", "ارز و طلا", "💵")
    usd = asset_lines(b, sec, "دلار", by_sym.get("USD"), daily, "USD", "تومان")
    usdt = asset_lines(b, sec, "تتر", by_sym.get("USDT_IRT"), daily, "USDT_IRT", "تومان")
    g18 = asset_lines(b, sec, "طلای ۱۸ عیار", by_sym.get("IR_GOLD_18K"), daily, "IR_GOLD_18K", "تومان")
    oz = asset_lines(b, sec, "انس جهانی", by_sym.get("XAUUSD"), daily, "XAUUSD", "دلار")
    emami = asset_lines(b, sec, "سکه امامی", by_sym.get("IR_COIN_EMAMI"), daily, "IR_COIN_EMAMI", "تومان")

    if usd and usd["chg"] is not None:
        d = usd["chg"]
        if abs(d) < 0.15:
            bullet(sec, pick(["دلار امروز تقریباً بدون تغییر بود و بازار ارز آرام است.",
                              "نرخ دلار امروز در محدوده‌ی دیروز ثابت ماند."], seed + "usd"))
        else:
            verb = "بالا رفت" if d > 0 else "پایین آمد"
            bullet(sec, f"دلار امروز {pct(d)} {verb} و به {fmt(usd['price'])} تومان رسید.")
        if usd["trend"] in ("up", "down"):
            bullet(sec, f"روند کوتاه‌مدت دلار {TREND_FA[usd['trend']]} است" +
                   (f" و در ۵ روز اخیر {pct(usd['week'])} تغییر کرده." if usd["week"] is not None else "."))
        if rsi_note(usd["rsi"]):
            bullet(sec, f"شاخص RSI دلار روی {usd['rsi']:.0f} است ({rsi_note(usd['rsi'])}) — احتمال نوسان برگشتی بیشتر می‌شود.")
    if usd and g18 and usd["chg"] is not None and g18["chg"] is not None:
        same = (usd["chg"] >= 0) == (g18["chg"] >= 0)
        if same:
            bullet(sec, f"طلا ({pct(g18['chg'])}) و دلار ({pct(usd['chg'])}) امروز هم‌جهت حرکت کردند.")
        else:
            bullet(sec, f"طلا ({pct(g18['chg'])}) و دلار ({pct(usd['chg'])}) امروز خلاف جهت هم حرکت کردند؛ "
                        "در این حالت معمولاً اثر انس جهانی پررنگ‌تر است.")
    if oz and oz["chg"] is not None and abs(oz["chg"]) >= 0.5:
        bullet(sec, f"انس جهانی طلا {pct(oz['chg'])} تغییر کرد که روی قیمت طلای داخلی هم اثر دارد.", tone_of(oz["chg"]))
    if emami and g18 and emami["price"] and g18["price"]:
        intrinsic = 8.133 * 0.9 / 0.75 * g18["price"]
        bubble = (emami["price"] / intrinsic - 1) * 100
        metric(sec, "حباب سکه امامی", pct(bubble), "negative" if bubble > 10 else "neutral")
        b.fact(f"حباب سکه امامی {pct(bubble)}")
        level = "بالا" if bubble > 15 else "متوسط" if bubble > 5 else "کم"
        bullet(sec, f"حباب سکه امامی حدود {pct(bubble)} است (سطح {level}).",
               "negative" if bubble > 15 else "neutral")
    # A rising dollar is good for some users and bad for others — no colored judgment here.
    sec["tone"] = "neutral"

    # ---------- bourse ----------
    stocks = [s for s in market.get("stocks", []) or [] if isinstance(s, dict)]
    idx = by_sym.get("IDX_MAIN")
    mood = None
    if idx or stocks:
        sec = b.section("bourse", "بورس و فرابورس", "🏛")
        ic = num(idx.get("change_percent")) if idx else None
        if idx:
            asset_lines(b, sec, "شاخص کل", idx, daily, "IDX_MAIN", "واحد")
            ew = by_sym.get("IDX_EQUAL")
            ewc = num(ew.get("change_percent")) if ew else None
            if ic is not None:
                bullet(sec, f"شاخص کل امروز با {pct(ic)} به {fmt(num(idx.get('price')))} واحد رسید.", tone_of(ic))
            if ic is not None and ewc is not None and (ic >= 0) != (ewc >= 0):
                bullet(sec, f"شاخص هم‌وزن ({pct(ewc)}) خلاف شاخص کل حرکت کرد؛ یعنی "
                       + ("رشد بیشتر از سهم‌های بزرگ بوده و اکثر نمادها همراهی نکردند." if ic > ewc else "نمادهای کوچک‌تر بهتر از بزرگ‌ها عمل کردند."))
        traded = [s for s in stocks if (s.get("tno") or 0) > 0 and s.get("plp") is not None]
        breadth = None
        if traded:
            up = sum(1 for s in traded if s["plp"] > 0)
            breadth = up / len(traded) * 100
            metric(sec, "نمادهای مثبت", f"{breadth:.0f}٪", tone_of(breadth - 50, 5))
            b.fact(f"از {len(traded)} نماد معامله‌شده {up} نماد مثبت بودند ({breadth:.0f}٪)")
            bullet(sec, f"{breadth:.0f}٪ نمادها ({up} از {len(traded)}) مثبت بودند"
                   + ("؛ فضای بازار مثبت است." if breadth >= 60 else "؛ فضای بازار منفی است." if breadth <= 40 else "؛ بازار دوقطبی است."),
                   tone_of(breadth - 50, 5))
        flow = sum(s.get("ii") or 0 for s in stocks)
        total_value = sum(s.get("tval") or 0 for s in stocks)
        if flow:
            toman = flow / 10
            metric(sec, "پول حقیقی", f"{'+' if flow > 0 else '−'}{words(abs(toman))} تومان", tone_of(flow))
            b.fact(f"{'ورود' if flow > 0 else 'خروج'} پول حقیقی {words(abs(toman))} تومان")
            bullet(sec, (f"{words(abs(toman))} تومان پول حقیقی وارد بازار شد؛ نشانه‌ی تمایل خریداران خرد."
                         if flow > 0 else f"{words(abs(toman))} تومان پول حقیقی از بازار خارج شد؛ خریداران خرد محتاط‌اند."), tone_of(flow))
        bq = sum(1 for s in stocks if s.get("tmax") and s.get("pl") and s["pl"] >= s["tmax"])
        sq = sum(1 for s in stocks if s.get("tmin") and s.get("pl") and s["pl"] <= s["tmin"])
        if bq or sq:
            metric(sec, "صف خرید / فروش", f"{bq} / {sq}", tone_of(bq - sq, 0))
            b.fact(f"{bq} نماد در صف خرید و {sq} نماد در صف فروش")
            bullet(sec, f"{bq} نماد در صف خرید و {sq} نماد در صف فروش بسته شدند.", tone_of(bq - sq, 0))
        # sectors weighted by traded value
        sectors = {}
        for s in traded:
            cs = s.get("cs")
            if cs and s.get("tval"):
                v = sectors.setdefault(cs, [0.0, 0.0])
                v[0] += s["plp"] * s["tval"]
                v[1] += s["tval"]
        ranked = sorted(((k, v[0] / v[1], v[1]) for k, v in sectors.items() if v[1] > 0), key=lambda x: -x[1])
        ranked = [r for r in ranked if r[2] >= 0.01 * (total_value or 1)]
        if len(ranked) >= 4:
            best = "، ".join(f"{k} ({pct(c)})" for k, c, _ in ranked[:3])
            worst = "، ".join(f"{k} ({pct(c)})" for k, c, _ in ranked[-2:])
            b.fact(f"گروه‌های پیشرو: {best}؛ عقب‌مانده: {worst}")
            bullet(sec, f"گروه‌های پیشرو: {best}.", "positive")
            bullet(sec, f"ضعیف‌ترین گروه‌ها: {worst}.", "negative")
        top = sorted(traded, key=lambda s: -(s.get("tval") or 0))[:3]
        if top:
            t = "، ".join(f"{s['l18']} ({pct(s['plp'])})" for s in top)
            b.fact(f"پرمعامله‌ترین نمادها: {t}")
            bullet(sec, f"پرمعامله‌ترین نمادها: {t}.")

        # market mood gauge (bourse): -100 … +100
        score, parts = 0.0, 0
        if ic is not None:
            score += max(-40, min(40, ic * 20)); parts += 1
        if breadth is not None:
            score += max(-30, min(30, (breadth - 50) * 0.9)); parts += 1
        if flow and total_value:
            score += max(-20, min(20, flow / total_value * 400)); parts += 1
        if bq or sq:
            score += (bq - sq) / (bq + sq) * 10; parts += 1
        if parts:
            score = max(-100, min(100, score))
            label, emoji = next((l, e) for lim, l, e in [
                (-60, "ترس شدید", "😱"), (-20, "ترس", "😟"), (20, "خنثی", "😐"), (60, "خوش‌بینی", "🙂"), (101, "هیجان", "🤩")
            ] if score < lim)
            mood = {"score": round(score), "label": label, "emoji": emoji}
            b.fact(f"دماسنج بورس: {label}")
        sec["tone"] = tone_of(ic or (breadth - 50 if breadth is not None else 0))

    # ---------- crypto ----------
    btc = by_sym.get("BTC")
    if btc:
        sec = b.section("crypto", "ارز دیجیتال", "₿")
        bt = asset_lines(b, sec, "بیت‌کوین", btc, daily, "BTC", "دلار")
        et = asset_lines(b, sec, "اتریوم", by_sym.get("ETH"), daily, "ETH", "دلار")
        if bt and bt["chg"] is not None:
            bullet(sec, f"بیت‌کوین در ۲۴ ساعت {pct(bt['chg'])} تغییر کرد"
                   + (f" و روند کوتاه‌مدتش {TREND_FA[bt['trend']]} است." if bt["trend"] else "."), tone_of(bt["chg"]))
        if bt and et and bt["chg"] is not None and et["chg"] is not None and abs(et["chg"] - bt["chg"]) > 2:
            bullet(sec, f"اتریوم ({pct(et['chg'])}) " + ("قوی‌تر" if et["chg"] > bt["chg"] else "ضعیف‌تر") + " از بیت‌کوین عمل کرد.")
        sec["tone"] = tone_of(bt["chg"] if bt else None)

    # ---------- codal ----------
    today_items = [c for c in codal if c.get("title")][:40]
    if today_items:
        sec = b.section("codal", "رویدادهای کدال", "📰")
        keywords = ("افزایش سرمایه", "مجمع", "صورت‌های مالی", "گزارش فعالیت ماهانه", "سود")
        important = [c for c in today_items if any(k in c["title"] for k in keywords)][:4] or today_items[:3]
        for c in important:
            bullet(sec, f"{c.get('l18') or ''}: {c['title']}")

    # ---------- headline (template) ----------
    head = []
    if mood:
        head.append(f"بورس امروز در فاز «{mood['label']}» {mood['emoji']}")
    if usd and usd["chg"] is not None:
        head.append(f"دلار {pct(usd['chg'])}")
    if g18 and g18["chg"] is not None:
        head.append(f"طلا {pct(g18['chg'])}")
    headline = "؛ ".join(head) or "تحلیل بازار"
    for s in b.sections:
        s["paragraph"] = paragraph(s["bullets"], seed + s["id"])
    intro = []
    if mood:
        intro.append(f"فضای بورس امروز «{mood['label']}» ارزیابی می‌شود")
    if usd and usd["chg"] is not None:
        intro.append(f"دلار {pct(usd['chg'])} تغییر کرد")
    if g18 and g18["chg"] is not None:
        intro.append(f"طلای ۱۸ عیار {pct(g18['chg'])} جابه‌جا شد")
    if len(intro) > 1:
        intro_text = "، ".join(intro[:-1]) + " و " + intro[-1] + "."
    else:
        intro_text = intro[0] + "." if intro else ""
    # The fallback narrative reads as a short article: intro + one paragraph per market.
    template_text = "\n\n".join(
        ([intro_text] if intro_text else []) +
        [s["paragraph"] for s in b.sections if s["bullets"] and s["id"] != "codal"]
    )
    return {
        "headline": headline,
        "mood": mood,
        "sections": b.sections,
        "facts": b.facts,
        "template_text": template_text,
    }


# ----------------------------------------------------------------- AI polish

SYSTEM = (
    "تو یک روزنامه‌نگار اقتصادی فارسی‌زبان هستی. فقط با استفاده از «داده‌ها»یی که کاربر می‌دهد، "
    "یک تحلیل کوتاه و روان از بازار امروز بنویس.\n"
    "قوانین سخت:\n"
    "۱. هیچ عددی جز اعداد موجود در داده‌ها ننویس؛ اعداد را دقیقاً با همان رقم‌ها (لاتین) بنویس.\n"
    "۲. پیش‌بینی قیمت و توصیه‌ی خرید یا فروش ممنوع است.\n"
    "۳. چیزی از خودت (اخبار، دلیل سیاسی، رویداد) اضافه نکن؛ فقط ارتباط بین همین داده‌ها را توضیح بده.\n"
    "۴. قالب: خط اول یک تیتر کوتاه (حداکثر ۱۲ کلمه)، سپس ۲ یا ۳ پاراگراف کوتاه. بدون Markdown و بدون فهرست."
)

NUM_RE = re.compile(r"\d[\d,]*(?:\.\d+)?")


def numbers_in(text):
    text = text.translate(str.maketrans("۰۱۲۳۴۵۶۷۸۹٫٬", "0123456789.,"))
    out = []
    for m in NUM_RE.findall(text):
        try:
            out.append(float(m.replace(",", "")))
        except ValueError:
            pass
    return out


def grounded(ai_text, facts_text):
    """Every number in the AI text must match a number in our facts (incl. scaled forms)."""
    allowed = set()
    for f in numbers_in(facts_text):
        for scale in (1, 1e3, 1e6, 1e9, 1e12):
            allowed.add(f / scale)
    for v in numbers_in(ai_text):
        if v <= 12:          # small counts / list numbers / "۵ روز"
            continue
        if not any(abs(v - a) <= max(0.051, 0.02 * abs(a)) for a in allowed):
            print(f"  AI text rejected: number {v} not in facts")
            return False
    return True


AI_STATUS = []   # why the AI text wasn't used — written to analysis.json for debugging


def ai_polish(facts, template_text, token):
    facts_text = "\n".join(facts)
    user = f"داده‌ها:\n{facts_text}\n\nپیش‌نویس الگویی (برای کمک):\n{template_text}"
    for url, model in AI_ENDPOINTS:
        body = json.dumps({
            "model": model,
            "temperature": 0.4,
            "max_tokens": 700,
            "messages": [{"role": "system", "content": SYSTEM}, {"role": "user", "content": user}],
        }).encode()
        req = urllib.request.Request(url, data=body, headers={
            "Authorization": f"Bearer {token}", "Content-Type": "application/json", "Accept": "application/json",
        })
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                data = json.loads(r.read().decode("utf-8"))
            text = data["choices"][0]["message"]["content"].strip()
        except Exception as e:
            detail = ""
            if hasattr(e, "read"):
                try:
                    detail = e.read().decode("utf-8")[:200]
                except Exception:
                    pass
            msg = f"{model}: {type(e).__name__} {getattr(e, 'code', '')} {detail}".strip()
            print("  AI " + msg)
            AI_STATUS.append(msg)
            continue
        text = text.replace("**", "").replace("#", "").strip()
        if len(text) < 80 or not grounded(text, facts_text):
            AI_STATUS.append(f"{model}: rejected (too short or a number not in the data)")
            continue
        lines = [l.strip() for l in text.splitlines() if l.strip()]
        return {"headline": lines[0][:120], "text": "\n\n".join(lines[1:]), "model": model}
    return None


# ----------------------------------------------------------------- main

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data-dir", required=True)
    args = ap.parse_args()
    dd = args.data_dir
    now = datetime.datetime.now(TEHRAN)
    today = now.strftime("%Y-%m-%d")

    result = build(dd, now)
    if not result["sections"]:
        print("no data for analysis yet")
        return 0

    prev = load(f"{dd}/analysis.json", {})
    ai = prev.get("ai") if (prev.get("ai") or {}).get("day") == today else None

    # Refresh the AI text at most once per slot (≈4×/day — well within the free quota).
    slot = max((h for h in AI_SLOTS if now.hour >= h), default=None)
    token = os.environ.get("GITHUB_TOKEN")
    force = os.environ.get("MANUAL") == "1"
    if token and slot is not None and (force or not ai or ai.get("slot") != slot):
        polished = ai_polish(result["facts"], result["template_text"], token)
        if polished:
            ai = {**polished, "day": today, "slot": slot, "at": int(now.timestamp())}
            print(f"  AI analysis updated ({polished['model']})")
        else:
            print("  AI unavailable — using template text")

    out = {
        "updated": int(now.timestamp()),
        "headline": result["headline"],
        "mood": result["mood"],
        "sections": result["sections"],
        "summary": result["template_text"],
        "ai": ai,
        "ai_status": AI_STATUS or ([] if token else ["no GITHUB_TOKEN"]),
        "disclaimer": "این تحلیل خودکار و صرفاً آموزشی است و توصیه‌ی خرید یا فروش نیست.",
    }
    save(f"{dd}/analysis.json", out)
    print(f"analysis: {len(result['sections'])} sections, mood={result['mood']}, ai={'yes' if ai else 'no'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
