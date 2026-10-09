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
import sys
import time
import urllib.parse
import urllib.request
from zoneinfo import ZoneInfo

TEHRAN = ZoneInfo("Asia/Tehran")
HOSTS = ["https://Api.BrsApi.ir", "https://brsapi.ir/Api"]
UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36"
DAILY_BUDGET = int(os.environ.get("DAILY_BUDGET", "900"))
RESERVE = 80              # always kept free for the critical gold/currency call
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
    hourly = now.minute < 15

    dd = args.data_dir
    state = load(f"{dd}/state.json", {})
    if state.get("day") != today:
        state.update(day=today, calls=0)
    state.setdefault("backfilled", [])
    # Symbols whose history request failed today — not retried until tomorrow.
    # (Bug fix: they used to be retried every run, burning ~60 calls each time.)
    if state.get("failed_day") != today:
        state["failed_day"], state["failed"], state["bf_calls"] = today, [], 0
    api = Api(key, state)
    print(f"Tehran {now:%a %H:%M}  bourse_window={bourse_window} manual={manual} budget used={state['calls']}/{DAILY_BUDGET}")

    # 1) gold / currency / crypto — critical
    gold = api.get("Market/Gold_Currency.php", critical=True, validate=lambda d: isinstance(d, dict) and all(
        isinstance(d.get(k), list) for k in ("gold", "currency", "cryptocurrency")))
    gold_ok = gold is not None
    if gold_ok:
        save(f"{args.main_dir}/latest.json", gold)
        save(f"{dd}/latest.json", gold)
    else:
        print("::error::Gold_Currency failed — keeping old latest.json")

    # 2) market (indices, stocks, commodity)
    prev = load(f"{dd}/market.json", {})
    market = {"v": 2, "updated": epoch,
              "indices": prev.get("indices", []), "stocks": prev.get("stocks", []),
              "commodity": prev.get("commodity", [])}
    raw_stocks = None
    if bourse_window or manual or not prev.get("stocks"):
        im = api.get("Tsetmc/Index.php", {"type": 1})
        ifa = api.get("Tsetmc/Index.php", {"type": 2})
        isel = api.get("Tsetmc/Index.php", {"type": 3})
        idx = index_items(im, ifa, isel, epoch)
        if idx:
            market["indices"] = idx
        raw_stocks = api.get("Tsetmc/AllSymbols.php", {"type": 1}, validate=lambda d: isinstance(d, list) and d)
        if raw_stocks:
            light, detail = stock_rows(raw_stocks)
            if light:
                market["stocks"] = light
                save(f"{dd}/detail.json", {"updated": epoch, "s": detail})
    if hourly or manual or not prev.get("commodity"):
        com = commodity_items(api.get("Market/Commodity.php"), epoch)
        if com:
            market["commodity"] = com
    save(f"{dd}/market.json", market)

    # 3) codal — hourly, latest announcements for all symbols in one call
    if hourly or manual or not os.path.exists(f"{dd}/codal.json"):
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
    if gold_ok:
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
    return 0 if gold_ok else 1


if __name__ == "__main__":
    sys.exit(main())
