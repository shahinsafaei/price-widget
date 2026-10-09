#!/usr/bin/env python3
"""
Server-side data pipeline for the Nerkhino app (run by .github/workflows/fetch-rates.yml).

Devices never call brsapi.ir: this script is the ONLY client of the API, so
usage is constant no matter how many people install the app. It also keeps a
hard daily budget so the free plan's limit can't be exhausted:

  * gold/currency  – every run (critical, always has reserved budget)
  * bourse indices + all symbols – only during trading hours (Sat–Wed 09:00–13:15)
  * commodity, codal – once an hour
  * history backfill – leftover budget only, outside trading hours, each symbol once

Price history is BUILT here from data we already fetch (each day's
open/high/low/close is in AllSymbols), so charts cost zero extra API calls.

Outputs
  <main-dir>/latest.json             gold/currency/crypto (kept for old app versions)
  <data-dir>/latest.json             same, on the data branch
  <data-dir>/market.json             indices, commodity, light stock list (v2)
  <data-dir>/detail.json             per-stock order book + individual/institutional
  <data-dir>/codal.json              latest CODAL announcements
  <data-dir>/history/intraday.json   last 48h of snapshots for core items
  <data-dir>/history/daily_core.json daily OHLC for core items
  <data-dir>/history/stocks/NN.json  daily OHLC per stock, sharded (see shard())
  <data-dir>/state.json              budget counter + backfill progress
"""
import argparse
import datetime
import hashlib
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request
from zoneinfo import ZoneInfo

TEHRAN = ZoneInfo("Asia/Tehran")
HOSTS = ["https://Api.BrsApi.ir", "https://brsapi.ir/Api"]
UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
# Hard daily cap. The provider allows 1000/day; stopping at 600 means at least 400
# requests are ALWAYS left, whatever happens.
DAILY_BUDGET = int(os.environ.get("DAILY_BUDGET", "600"))
RESERVE = 150             # of those 600, kept for critical calls (gold/currency, codal)

# Minimum minutes between fetches of each kind. Runs may be triggered as often as
# every minute (external trigger); these keep usage predictable regardless:
#   gold 3' ≈ 240/day (09-21) · bourse 5' ≈ 100 · extra indices 15' ≈ 30 ·
#   commodity/codal 30' ≈ 50 · crypto 5' (free source, no quota)  → ≈ 420/day
TIERS = {"gold": 3, "bourse": 5, "idx_more": 15, "commodity": 30, "codal": 30, "crypto": 5}
BACKFILL_PER_RUN = 25
BACKFILL_DAILY_CAP = 300      # history backfill never uses more than this per day
HISTORY_DAYS = 500
INTRADAY_HOURS = 48
SHARDS = 32


# ----------------------------------------------------------------- helpers

def load(path, default):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return default


def save(path, obj):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(obj, f, ensure_ascii=False, separators=(",", ":"))
    os.replace(tmp, path)


def norm(s):
    """TSETMC uses Arabic ي/ك — normalize so the app's Persian search matches."""
    return str(s).replace("ي", "ی").replace("ك", "ک").strip() if s is not None else None


def num(v):
    if v is None or isinstance(v, bool):
        return None
    try:
        f = float(str(v).replace(",", ""))
        return None if f != f else f
    except ValueError:
        return None


def shard(l18):
    """Must match HistoryRepository.shardOf() in the app."""
    return "%02d" % (sum(ord(c) for c in l18) % SHARDS)


def j2g(jy, jm, jd):
    """Jalali → Gregorian (standard jdf algorithm)."""
    jy += 1595
    days = -355668 + 365 * jy + (jy // 33) * 8 + ((jy % 33) + 3) // 4 + jd
    days += (jm - 1) * 31 if jm < 7 else (jm - 7) * 30 + 186
    gy = 400 * (days // 146097)
    days %= 146097
    if days > 36524:
        days -= 1
        gy += 100 * (days // 36524)
        days %= 36524
        if days >= 365:
            days += 1
    gy += 4 * (days // 1461)
    days %= 1461
    if days > 365:
        gy += (days - 1) // 365
        days = (days - 1) % 365
    gd = days + 1
    leap = (gy % 4 == 0 and gy % 100 != 0) or gy % 400 == 0
    months = [31, 29 if leap else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
    gm = 0
    while gm < 12 and gd > months[gm]:
        gd -= months[gm]
        gm += 1
    return gy, gm + 1, gd


def g2j(gy, gm, gd):
    """Gregorian → Jalali (standard jdf algorithm)."""
    g_d_m = [0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334]
    gy2 = gy + 1 if gm > 2 else gy
    days = 355666 + 365 * gy + (gy2 + 3) // 4 - (gy2 + 99) // 100 + (gy2 + 399) // 400 + gd + g_d_m[gm - 1]
    jy = -1595 + 33 * (days // 12053)
    days %= 12053
    jy += 4 * (days // 1461)
    days %= 1461
    if days > 365:
        jy += (days - 1) // 365
        days = (days - 1) % 365
    if days < 186:
        return jy, 1 + days // 31, 1 + days % 31
    return jy, 7 + (days - 186) // 30, 1 + (days - 186) % 30


# ----------------------------------------------------------------- crypto (free, 24/7)

# brsapi symbol → CoinGecko id. CoinGecko's free public endpoint needs no key and
# returns every coin in ONE request, so crypto stays fresh around the clock
# without touching the paid-provider budget.
COINGECKO_IDS = {
    "BTC": "bitcoin", "ETH": "ethereum", "USDT": "tether", "XRP": "ripple", "BNB": "binancecoin",
    "SOL": "solana", "USDC": "usd-coin", "TRX": "tron", "DOGE": "dogecoin", "ADA": "cardano",
    "LINK": "chainlink", "XLM": "stellar", "AVAX": "avalanche-2", "SHIB": "shiba-inu", "LTC": "litecoin",
    "DOT": "polkadot", "UNI": "uniswap", "ATOM": "cosmos", "FIL": "filecoin",
}


TOP_CRYPTO = 100
# Persian display names for well-known coins (others keep their English name).
CRYPTO_FA = {
    "BTC": "بیت‌کوین", "ETH": "اتریوم", "BNB": "بی‌ان‌بی", "XRP": "ریپل", "SOL": "سولانا", "TRX": "ترون",
    "DOGE": "دوج‌کوین", "ADA": "کاردانو", "LINK": "چین‌لینک", "XLM": "استلار", "AVAX": "آوالانچ",
    "SHIB": "شیبا اینو", "LTC": "لایت‌کوین", "DOT": "پولکادات", "UNI": "یونی‌سواپ", "ATOM": "کازماس",
    "FIL": "فایل‌کوین", "BCH": "بیت‌کوین کش", "XMR": "مونرو", "ZEC": "زی‌کش", "NEAR": "نییر",
    "SUI": "سویی", "HBAR": "هدرا", "TON": "تون‌کوین", "GRAM": "تون‌کوین", "PEPE": "پپه", "ETC": "اتریوم کلاسیک",
    "ARB": "آربیتروم", "APT": "آپتوس", "ALGO": "الگوراند", "AAVE": "آوه", "ICP": "اینترنت کامپیوتر",
    "KAS": "کسپا", "RENDER": "رندر", "VET": "وی‌چین", "INJ": "اینجکتیو", "CAKE": "پنکیک‌سواپ",
    "POL": "پالیگان", "WLD": "ورلدکوین", "TAO": "بیتنسور", "PI": "پای نتورک", "HYPE": "هایپرلیکوئید",
    "XAUT": "تتر گلد (طلا)", "PAXG": "پکس گلد (طلا)", "USDT": "تتر", "USDC": "یواس‌دی کوین",
    "CRO": "کرونوس", "OKB": "اوکی‌بی", "LEO": "لئو", "QNT": "کوانت", "ONDO": "اوندو", "ENA": "اتنا",
}
# Stablecoins and tokenized funds/credit add noise to a price list — USDT/USDC stay.
CRYPTO_SKIP_IDS = {
    "usds", "ethena-usde", "dai", "usd1-wlfi", "global-dollar", "paypal-usd", "ripple-usd", "hashnote-usyc",
    "ondo-us-dollar-yield", "blackrock-usd-institutional-digital-liquidity-fund", "falcon-finance",
    "spiko-amundi-overnight-swap-fund-eur", "usdd", "united-stables", "bfusd", "usdgo", "open-usd",
    "superstate-short-duration-us-government-securities-fund-ustb", "stable-2", "gho", "figure-heloc",
    "blockchain-capital", "first-digital-usd", "true-usd", "frax", "susds", "syrupusdc",
}


LOGO_DIR = None          # set in main(): <data-dir>/logos — persisted on the data branch
LOGO_BUDGET = 30         # max new logo downloads per run (first run fills ~100 in 4 runs)


def ensure_logo(sym, image_url):
    """Downloads a coin's official logo once (CoinGecko 'small', ~50px, ~2.5 KB) and
    returns its path relative to the data branch, or None. Served from the data
    branch so the app never has to reach the logo CDN directly."""
    global LOGO_BUDGET
    if not LOGO_DIR or not image_url or not re.fullmatch(r"[A-Z0-9]{1,15}", sym):
        return None
    rel = f"logos/{sym}.png"
    path = os.path.join(LOGO_DIR, f"{sym}.png")
    if os.path.exists(path) and os.path.getsize(path) > 100:
        return rel
    if LOGO_BUDGET <= 0:
        return None
    LOGO_BUDGET -= 1
    url = image_url.replace("/large/", "/small/")
    try:
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        with urllib.request.urlopen(req, timeout=20) as r:
            data = r.read(200_000)
        if not data.startswith(bytes([0x89]) + b"PNG"):
            return None          # only PNGs (the app decodes them as bitmaps)
        os.makedirs(LOGO_DIR, exist_ok=True)
        with open(path, "wb") as f:
            f.write(data)
        return rel
    except Exception as e:
        print(f"  logo {sym}: {type(e).__name__}")
        return None


def _is_stable(c):
    """Generic stablecoin filter for coins not in the skip list."""
    sym = (c.get("symbol") or "").upper()
    p = c.get("current_price") or 0
    return sym not in ("USDT", "USDC") and ("USD" in sym or "EUR" in sym) and 0.9 <= p <= 1.1


def refresh_crypto_top(gold, now):
    """Replaces the crypto list with the top coins by market cap (one free CoinGecko
    request): price in USD + 24h change. Persian names where known."""
    url = ("https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc"
           "&per_page=150&page=1&price_change_percentage=24h")
    try:
        req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
        with urllib.request.urlopen(req, timeout=30) as r:
            coins = json.loads(r.read().decode("utf-8"))
    except Exception as e:
        print(f"  crypto top list (CoinGecko): {type(e).__name__}")
        return False
    if not isinstance(coins, list) or not coins:
        return False
    jy, jm, jd = g2j(now.year, now.month, now.day)
    out, seen = [], set()
    for c in coins:
        sym = (c.get("symbol") or "").upper()
        p = c.get("current_price")
        if not sym or p is None or c.get("id") in CRYPTO_SKIP_IDS or _is_stable(c) or sym in seen:
            continue
        seen.add(sym)
        pct = c.get("price_change_percentage_24h")
        out.append({
            "symbol": sym,
            "name": CRYPTO_FA.get(sym) or c.get("name") or sym,
            "name_en": c.get("name"),
            "price": ("%.10f" % p).rstrip("0").rstrip("."),
            "change_percent": round(pct, 2) if pct is not None else None,
            "change_value": c.get("price_change_24h"),
            "unit": "دلار",
            "date": "%d/%02d/%02d" % (jy, jm, jd),
            "time": now.strftime("%H:%M"),
            "time_unix": int(now.timestamp()),
            "x_mcap": c.get("market_cap"),
            "x_volume": c.get("total_volume"),
            "x_high": c.get("high_24h"),
            "x_low": c.get("low_24h"),
            "x_logo": ensure_logo(sym, c.get("image")),
        })
        if len(out) >= TOP_CRYPTO:
            break
    if len(out) < 20:
        return False
    gold["cryptocurrency"] = out
    print(f"  crypto: top {len(out)} coins from CoinGecko")
    return True


def refresh_crypto(gold, now):
    """Top-100 list first; if that request fails, at least refresh the coins we already have."""
    if refresh_crypto_top(gold, now):
        return True
    return refresh_crypto_quotes(gold, now)


def refresh_crypto_quotes(gold, now):
    """Overwrites crypto prices in the gold/currency payload with fresh CoinGecko
    quotes (USD + 24h change). Returns True if anything was updated."""
    items = {x["symbol"]: x for x in gold.get("cryptocurrency", []) or [] if isinstance(x, dict) and x.get("symbol") in COINGECKO_IDS}
    if not items:
        return False
    ids = ",".join(COINGECKO_IDS[s] for s in items)
    url = ("https://api.coingecko.com/api/v3/simple/price?vs_currencies=usd"
           f"&include_24hr_change=true&include_last_updated_at=true&ids={ids}")
    try:
        req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
        with urllib.request.urlopen(req, timeout=30) as r:
            quotes = json.loads(r.read().decode("utf-8"))
    except Exception as e:
        print(f"  crypto (CoinGecko): {type(e).__name__}")
        return False
    jy, jm, jd = g2j(now.year, now.month, now.day)
    updated = 0
    for sym, it in items.items():
        q = quotes.get(COINGECKO_IDS[sym]) or {}
        p = q.get("usd")
        if p is None:
            continue
        pct = q.get("usd_24h_change")
        it["price"] = ("%.10f" % p).rstrip("0").rstrip(".")
        it["change_percent"] = round(pct, 2) if pct is not None else None
        it["change_value"] = round(p - p / (1 + pct / 100), 10) if pct not in (None, -100) else None
        it["time_unix"] = int(q.get("last_updated_at") or now.timestamp())
        it["date"] = "%d/%02d/%02d" % (jy, jm, jd)
        it["time"] = now.strftime("%H:%M")
        updated += 1
    print(f"  crypto: {updated} coins updated from CoinGecko")
    return updated > 0


def day_key(v):
    """Any date the API returns (1403/05/01, 2024-07-22, 20240722 …) → 'YYYY-MM-DD' Gregorian."""
    if v is None:
        return None
    s = str(v).strip().translate(str.maketrans("۰۱۲۳۴۵۶۷۸۹", "0123456789"))
    s = s.split(" ")[0].replace("-", "/")
    try:
        if "/" in s:
            y, m, d = (int(x) for x in s.split("/")[:3])
        elif len(s) == 8 and s.isdigit():
            y, m, d = int(s[:4]), int(s[4:6]), int(s[6:])
        else:
            return None
        if y < 1700:
            y, m, d = j2g(y, m, d)
        return "%04d-%02d-%02d" % (y, m, d)
    except Exception:
        return None


# ----------------------------------------------------------------- API with budget

class Api:
    def __init__(self, key, state):
        self.key = key
        self.state = state
        self.last_error = None   # last failure reason, surfaced in state.json for debugging

    def remaining(self, critical):
        limit = DAILY_BUDGET if critical else DAILY_BUDGET - RESERVE
        return limit - self.state["calls"]

    def get(self, path, params=None, critical=False, validate=None, single_host=False):
        params = dict(params or {})
        params["key"] = self.key
        for host in HOSTS[:1] if single_host else HOSTS:
            if self.remaining(critical) <= 0:
                print(f"  budget exhausted, skipping {path}")
                return None
            url = f"{host}/{path}?{urllib.parse.urlencode(params)}"
            self.state["calls"] += 1
            try:
                req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
                with urllib.request.urlopen(req, timeout=60) as r:
                    data = json.loads(r.read().decode("utf-8"))
            except Exception as e:
                body = ""
                if hasattr(e, "read"):
                    try:
                        body = e.read().decode("utf-8", "replace")[:200]
                    except Exception:
                        pass
                self.last_error = f"{host}: {type(e).__name__} {getattr(e, 'code', '')} {body}".strip()
                print(f"  {path} via {self.last_error}")
                continue
            if isinstance(data, dict) and (data.get("successful") is False or "code_http" in data):
                self.last_error = f"{host}: API error {data.get('message_error') or data.get('status')}"
                print(f"  {path} via {self.last_error}")
                continue
            if validate and not validate(data):
                print(f"  {path} via {host}: unexpected shape")
                continue
            return data
        return None


# ----------------------------------------------------------------- builders

def index_items(main, fara, selected, updated):
    out = []

    def first(o):
        if isinstance(o, list):
            o = next((x for x in o if isinstance(x, dict)), None)
        return o if isinstance(o, dict) else None

    def item(symbol, name, value, change, pct, o):
        value = num(value)
        if not value or value <= 0:
            return None
        change = num(change)
        if pct is None and change is not None and value - change:
            pct = change / (value - change) * 100
        return {
            "symbol": symbol, "name": name, "price": value, "change_value": change,
            "change_percent": round(pct, 3) if pct is not None else None, "unit": "واحد",
            "date": o.get("date"), "time": o.get("time"), "time_unix": updated,
            "x_low": num(o.get("min")), "x_high": num(o.get("max")),
            "x_volume": num(o.get("tvol")), "x_value": num(o.get("tval")),
            "x_count": num(o.get("tno")), "x_mcap": num(o.get("mv")),
        }

    m = first(main)
    if m:
        out.append(item("IDX_MAIN", "شاخص کل بورس", m.get("index"), m.get("index_change"), None, m))
        out.append(item("IDX_EQUAL", "شاخص هم‌وزن", m.get("index_equalWeight"), m.get("index_equalWeight_change"), None, m))
    f = first(fara)
    if f:
        out.append(item("IDX_FARA", "شاخص کل فرابورس", f.get("index"), f.get("index_change"), None, f))
    for o in selected if isinstance(selected, list) else []:
        if isinstance(o, dict) and o.get("name"):
            name = norm(o["name"])
            sym = "IDX_S_" + hashlib.md5(name.encode()).hexdigest()[:8]
            out.append(item(sym, name, o.get("index"), o.get("index_change"), num(o.get("index_change_percent")), o))
    return [x for x in out if x]


def commodity_items(raw, updated):
    rows = []

    def walk(e, depth):
        if depth > 3:
            return
        if isinstance(e, list):
            for c in e:
                if isinstance(c, dict) and "price" in c and any(k in c for k in ("name", "name_en", "symbol", "title")):
                    rows.append(c)
                else:
                    walk(c, depth + 1)
        elif isinstance(e, dict):
            for v in e.values():
                walk(v, depth + 1)

    walk(raw, 0)
    out, seen = [], set()
    for o in rows:
        price = num(o.get("price"))
        name = o.get("name") or o.get("title") or o.get("name_en") or o.get("symbol")
        if price is None or not name:
            continue
        sym = "CMD_" + str(o.get("symbol") or o.get("name_en") or name).upper().replace(" ", "_")
        if sym in seen:
            continue
        seen.add(sym)
        out.append({
            "symbol": sym, "name": name, "name_en": o.get("name_en"), "price": price,
            "change_value": num(o.get("change_value")), "change_percent": num(o.get("change_percent")),
            "unit": o.get("unit") or "دلار", "date": o.get("date"), "time": o.get("time"),
            "time_unix": int(num(o.get("time_unix")) or updated),
        })
    return out


def stock_rows(raw):
    """Light list for the app + detail map (order book, individual/institutional)."""
    light, detail = [], {}
    for o in raw if isinstance(raw, list) else []:
        if not isinstance(o, dict) or not o.get("l18"):
            continue
        l18 = norm(o["l18"])
        pc, pl = num(o.get("pc")), num(o.get("pl"))
        if not (pl or pc):
            continue
        bi, si = num(o.get("Buy_I_Volume")) or 0, num(o.get("Sell_I_Volume")) or 0
        light.append({
            "l18": l18, "l30": norm(o.get("l30")), "cs": norm(o.get("cs")),
            "pl": pl, "plp": num(o.get("plp")), "plc": num(o.get("plc")),
            "pc": pc, "pcp": num(o.get("pcp")), "pf": num(o.get("pf")), "py": num(o.get("py")),
            "pmin": num(o.get("pmin")), "pmax": num(o.get("pmax")),
            "tmin": num(o.get("tmin")), "tmax": num(o.get("tmax")),
            "tvol": num(o.get("tvol")), "tval": num(o.get("tval")), "tno": num(o.get("tno")),
            "pe": num(o.get("pe")), "eps": num(o.get("eps")), "mv": num(o.get("mv")),
            # net individual ("smart money") inflow in Rial
            "ii": round((bi - si) * (pc or pl or 0)),
            "time": o.get("time"),
        })
        book = []
        for i in range(1, 6):
            row = [num(o.get(f"{k}{i}")) for k in ("zd", "qd", "pd", "po", "qo", "zo")]
            if any(row):
                book.append(row)
        detail[l18] = {
            "hh": [num(o.get(k)) for k in (
                "Buy_CountI", "Buy_CountN", "Sell_CountI", "Sell_CountN",
                "Buy_I_Volume", "Buy_N_Volume", "Sell_I_Volume", "Sell_N_Volume")],
            "ob": book,
        }
    return light, detail


def codal_rows(raw):
    """Announcement.php may return a bare list or an object with paging fields
    (count_announcement, count_page …) and the list under some key — find the
    first list of dicts that have a "title", wherever it is."""
    def find(e, depth=0):
        if depth > 3:
            return None
        if isinstance(e, list):
            if any(isinstance(x, dict) and x.get("title") for x in e):
                return e
            for x in e:
                r = find(x, depth + 1)
                if r:
                    return r
        elif isinstance(e, dict):
            for v in e.values():
                r = find(v, depth + 1)
                if r:
                    return r
        return None

    rows = find(raw) or []
    out = []
    for o in rows:
        if isinstance(o, dict) and o.get("title"):
            out.append({
                "l18": norm(o.get("l18")), "l30": norm(o.get("l30")), "title": norm(o.get("title")),
                "date": o.get("date_send"), "time": o.get("time_send"),
                "link": o.get("link"), "pdf": o.get("link_pdf"),
            })
    return out


# ----------------------------------------------------------------- history

def merge_candle(series, day, o, h, l, c, v=None, overwrite=True):
    """series: list of [day,o,h,l,c,(v)] sorted by day."""
    if not day or c is None:
        return
    o = o or c
    h = max(x for x in (h, o, c) if x is not None)
    l = min(x for x in (l, o, c) if x is not None)
    row = [day, o, h, l, c] + ([v] if v is not None else [])
    for i in range(len(series) - 1, -1, -1):
        if series[i][0] == day:
            if overwrite:
                series[i] = row
            return
        if series[i][0] < day:
            series.insert(i + 1, row)
            return
    series.insert(0, row)


def update_core_history(data_dir, items, now):
    intraday = load(f"{data_dir}/history/intraday.json", {})
    daily = load(f"{data_dir}/history/daily_core.json", {})
    epoch = int(now.timestamp())
    cutoff = epoch - INTRADAY_HOURS * 3600
    today = now.strftime("%Y-%m-%d")
    for it in items:
        key, p = it.get("symbol"), num(it.get("price"))
        if not key or p is None:
            continue
        pts = [x for x in intraday.get(key, []) if x[0] >= cutoff]
        if not pts or pts[-1][1] != p:
            pts.append([epoch, p])
        intraday[key] = pts
        series = daily.setdefault(key, [])
        last = series[-1] if series and series[-1][0] == today else None
        if last:
            merge_candle(series, today, last[1], max(last[2], p), min(last[3], p), p)
        else:
            merge_candle(series, today, p, p, p, p)
        daily[key] = series[-HISTORY_DAYS:]
    save(f"{data_dir}/history/intraday.json", intraday)
    save(f"{data_dir}/history/daily_core.json", daily)


class Shards:
    def __init__(self, data_dir):
        self.dir = f"{data_dir}/history/stocks"
        self.cache, self.dirty = {}, set()

    def series(self, l18):
        s = shard(l18)
        if s not in self.cache:
            self.cache[s] = load(f"{self.dir}/{s}.json", {})
        self.dirty.add(s)
        return self.cache[s].setdefault(l18, [])

    def flush(self):
        for s in self.dirty:
            data = {k: v[-HISTORY_DAYS:] for k, v in self.cache[s].items()}
            save(f"{self.dir}/{s}.json", data)


# ----------------------------------------------------------------- main

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--main-dir", required=True)
    ap.add_argument("--data-dir", required=True)
    args = ap.parse_args()

    key = os.environ.get("BRSAPI_KEY")
    if not key:
        print("::error::BRSAPI_KEY is empty")
        return 1
    manual = os.environ.get("MANUAL") == "1"
    now = datetime.datetime.now(TEHRAN)
    today = now.strftime("%Y-%m-%d")
    epoch = int(now.timestamp())
    mins = now.hour * 60 + now.minute
    bourse_day = now.weekday() in (5, 6, 0, 1, 2)          # Sat–Wed
    bourse_window = bourse_day and 8 * 60 + 55 <= mins <= 13 * 60 + 15

    dd = args.data_dir
    global LOGO_DIR
    LOGO_DIR = os.path.join(dd, "logos")
    state = load(f"{dd}/state.json", {})
    if state.get("day") != today:
        state.update(day=today, calls=0)
    state.setdefault("backfilled", [])
    # Symbols whose history request failed today — not retried until tomorrow.
    # (Bug fix: they used to be retried every run, burning ~60 calls each time.)
    if state.get("failed_day") != today:
        state["failed_day"], state["failed"], state["bf_calls"] = today, [], 0
    last = state.setdefault("last", {})

    def due(tier):
        return manual or epoch - last.get(tier, 0) >= TIERS[tier] * 60 - 20

    def mark(tier):
        last[tier] = epoch

    api = Api(key, state)
    print(f"Tehran {now:%a %H:%M}  bourse_window={bourse_window} manual={manual} budget used={state['calls']}/{DAILY_BUDGET}")

    # Gold/currency/bourse only during Tehran market hours; at night the job runs
    # just for crypto (24/7, free source) and makes no paid-provider calls at all.
    market_hours = manual or 9 * 60 <= mins < 21 * 60

    # 1) gold / currency / crypto — critical
    gold = None
    gold_due = market_hours and due("gold")
    if gold_due:
        mark("gold")
        gold = api.get("Market/Gold_Currency.php", critical=True, validate=lambda d: isinstance(d, dict) and all(
            isinstance(d.get(k), list) for k in ("gold", "currency", "cryptocurrency")))
    gold_ok = gold is not None
    if not gold_ok:
        if gold_due:
            print("::warning::Gold_Currency failed/over budget — keeping previous gold/currency prices")
        gold = load(f"{dd}/latest.json", None) or load(f"{args.main_dir}/latest.json", None)
    crypto_ok = False
    if gold and due("crypto"):
        mark("crypto")
        crypto_ok = refresh_crypto(gold, now)
    if gold and (gold_ok or crypto_ok):
        save(f"{args.main_dir}/latest.json", gold)
        save(f"{dd}/latest.json", gold)

    if not market_hours:
        if gold:
            update_core_history(dd, [x for x in gold.get("cryptocurrency", []) if isinstance(x, dict)], now)
        save(f"{dd}/state.json", state)
        print(f"night run — crypto only ({'ok' if crypto_ok else 'failed'}); API calls today: {state['calls']}")
        return 0

    # 2) market (indices, stocks, commodity)
    prev = load(f"{dd}/market.json", {})
    market = {"v": 2, "updated": epoch,
              "indices": prev.get("indices", []), "stocks": prev.get("stocks", []),
              "commodity": prev.get("commodity", [])}
    raw_stocks = None
    # A failed fetch waits for the next interval — never "retry every run because the file is missing".
    if due("bourse") and (bourse_window or manual or not prev.get("stocks")):
        mark("bourse")
        im = api.get("Tsetmc/Index.php", {"type": 1})
        more = due("idx_more") or not prev.get("indices")
        if more:
            mark("idx_more")
        ifa = api.get("Tsetmc/Index.php", {"type": 2}) if more else None
        isel = api.get("Tsetmc/Index.php", {"type": 3}) if more else None
        idx = index_items(im, ifa, isel, epoch)
        if idx and not more:
            # keep the previous Farabourse/selected indices between their 15-minute refreshes
            fresh = {x["symbol"] for x in idx}
            idx += [x for x in prev.get("indices", []) if x.get("symbol") not in fresh and x.get("symbol") != "IDX_EQUAL"]
        if idx:
            market["indices"] = idx
        raw_stocks = api.get("Tsetmc/AllSymbols.php", {"type": 1}, validate=lambda d: isinstance(d, list) and d)
        if raw_stocks:
            light, detail = stock_rows(raw_stocks)
            if light:
                market["stocks"] = light
                save(f"{dd}/detail.json", {"updated": epoch, "s": detail})
    if due("commodity"):
        mark("commodity")
        com = commodity_items(api.get("Market/Commodity.php"), epoch)
        if com:
            market["commodity"] = com
    save(f"{dd}/market.json", market)

    # 3) codal — hourly, latest announcements for all symbols in one call
    if due("codal"):
        mark("codal")
        # Uses the reserved budget: ~12 calls/day, and it must not starve when backfill runs.
        raw_codal = api.get("Codal/Announcement.php", {"page": 1}, critical=True)
        rows = codal_rows(raw_codal)
        # Record the response shape when nothing parses, so a format change is visible.
        state["codal_debug"] = None if rows else (
            f"no response: {api.last_error}" if raw_codal is None else
            f"{type(raw_codal).__name__}: " + (", ".join(list(raw_codal.keys())[:12]) if isinstance(raw_codal, dict)
                                              else json.dumps(raw_codal, ensure_ascii=False)[:300])
        )
        if rows:
            old = load(f"{dd}/codal.json", {}).get("items", [])
            seen, merged = set(), []
            for r in rows + old:
                k = (r.get("l18"), r.get("title"), r.get("date"))
                if k not in seen:
                    seen.add(k)
                    merged.append(r)
            save(f"{dd}/codal.json", {"updated": epoch, "items": merged[:300]})

    # 4) history built from what we already have — zero extra calls
    core = []
    if gold:
        for k in ("gold", "currency", "cryptocurrency"):
            core += [x for x in gold.get(k, []) if isinstance(x, dict)]
    core += market["indices"] + market["commodity"]
    update_core_history(dd, core, now)

    shards = Shards(dd)
    if raw_stocks and bourse_day:
        for s in market["stocks"]:
            if (s.get("tno") or 0) > 0:
                merge_candle(shards.series(s["l18"]), today, s.get("pf"), s.get("pmax"), s.get("pmin"),
                             s.get("pc") or s.get("pl"), s.get("tvol"))

    # 5) backfill old stock history with LEFTOVER budget, outside trading hours
    if not bourse_window and market["stocks"]:
        done = set(state["backfilled"]) | set(state["failed"])
        # most-traded symbols first: those are the ones people open
        todo = [s["l18"] for s in sorted(market["stocks"], key=lambda s: -(s.get("tval") or 0)) if s["l18"] not in done]
        started = time.time()
        for l18 in todo[:BACKFILL_PER_RUN]:
            if api.remaining(False) <= 0 or state["bf_calls"] >= BACKFILL_DAILY_CAP or time.time() - started > 240:
                break
            before = state["calls"]
            rows = api.get("Tsetmc/History.php", {"type": 0, "l18": l18}, single_host=True)
            state["bf_calls"] += state["calls"] - before
            if rows is None:
                state["failed"].append(l18)
                continue
            if isinstance(rows, dict):
                rows = next((v for v in rows.values() if isinstance(v, list)), [])
            series = shards.series(l18)
            for r in rows if isinstance(rows, list) else []:
                if isinstance(r, dict):
                    merge_candle(series, day_key(r.get("date")), num(r.get("pf")), num(r.get("pmax")),
                                 num(r.get("pmin")), num(r.get("pc")) or num(r.get("pl")), num(r.get("tvol")),
                                 overwrite=False)
            state["backfilled"].append(l18)
            time.sleep(0.4)
        print(f"  backfilled {len(state['backfilled'])}/{len(market['stocks'])} symbols")
    shards.flush()

    save(f"{dd}/state.json", state)
    print(f"done — API calls today: {state['calls']}/{DAILY_BUDGET}")
    # Never fail the job just because a provider call failed or the budget is used
    # up — the data branch still gets the fresh crypto/analysis and old prices stay.
    return 0


if __name__ == "__main__":
    sys.exit(main())
