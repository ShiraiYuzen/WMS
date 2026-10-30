#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
WMS v4 演示数据一键生成器。

作用：把演示数据从「库内最后一张订单」续写到现在，随时运行都能得到一份
「业务时间 = 当前时间」且内部完全自洽的数据。

核心保证（运行结束前会逐条校验，任何一条不通过就整批回滚）：
  1. 单号日期 = 业务发生日：SO/WV/PK/RV/HO/SH 前缀后的 8 位日期，
     与该单据自己的 pay_time / create_time 完全一致；
  2. 时间不倒挂：波次创建晚于其所有订单的下单时间，
     拣货完成晚于开始、复核晚于拣货、交接晚于复核；
  3. 不产生未来时间：所有已发生的节点都 ≤ 运行时刻；
  4. 状态咬合：波次 HANDOVERED 的订单必为 OUTBOUND，只有拣完的波次才开复核单；
  5. 汇总自洽：wave.order_count = 关联订单数，pick_task.should_qty = Σ 拣货明细。

用法（在项目根目录）：
  ./tools/gen-demo.sh                 # 一键：续写到现在（数据已是最新则什么都不做）
  ./tools/gen-demo.sh --days 3        # 补最近 3 天
  ./tools/gen-demo.sh --dry-run       # 只打印 SQL，不写库
  ./tools/gen-demo.sh --no-verify     # 跳过校验（不推荐）

也可以直接调 python：python3 tools/gen-demo-data.py --days 3
数据库连接从 backend/src/main/resources/application.properties 读取，
可用环境变量覆盖：WMS_DB_HOST / WMS_DB_PORT / WMS_DB_NAME / WMS_DB_USER / WMS_DB_PASS。
"""

import argparse
import datetime as dt
import os
import random
import re
import subprocess
import sys

REMARK = "DEMO-GEN"
SEED = 20261029
WORK_START = dt.time(9, 0)
WORK_END = dt.time(23, 30)

PLACES = [
    ("广东省", "广州市", "天河区"), ("广东省", "深圳市", "南山区"), ("浙江省", "杭州市", "余杭区"),
    ("浙江省", "宁波市", "鄞州区"), ("江苏省", "南京市", "江宁区"), ("江苏省", "苏州市", "工业园区"),
    ("上海市", "上海市", "浦东新区"), ("北京市", "北京市", "朝阳区"), ("四川省", "成都市", "武侯区"),
    ("湖北省", "武汉市", "洪山区"), ("山东省", "青岛市", "市南区"), ("福建省", "厦门市", "思明区"),
    ("湖南省", "长沙市", "岳麓区"), ("河南省", "郑州市", "金水区"), ("陕西省", "西安市", "雁塔区"),
    ("天津市", "天津市", "滨海新区"),
]
SURNAMES = "张王李赵刘陈杨黄周吴徐孙马朱胡郭何高林罗"
GIVEN = ["伟", "芳", "娜", "洋", "晨", "帆", "敏", "磊", "静", "杰", "丽", "超", "婷", "军", "丹",
         "涛", "琳", "鹏", "雪", "强", "倩", "浩", "鑫", "宇", "萌"]
ROADS = ["演示路", "云栖路", "科创大道", "物流园路", "振兴街", "望江路", "兴业路", "仓储一路"]
PLATFORMS = ["天猫", "京东", "拼多多", "抖音商城", "美团优选"]
REASONS = ["STOCK_ZERO", "STOCK_DIFF", "LOCATION_WRONG", "DAMAGED", "EXPIRED", "LOCKED", "UNKNOWN"]
HANDLE_TYPES = ["SPLIT", "CANCEL_ITEM", "SUBSTITUTE", "WAIT_REPLENISH", "CANCEL_ORDER"]
DIFF_TYPES = ["SHORT", "OVER", "WRONG_SKU", "DAMAGED", "WRONG_ORDER"]
PACK_MATERIALS = ["纸箱", "气泡袋", "保温箱"]
STRATEGIES = ["BY_AREA", "BY_CARRIER", "BY_TIMELINESS", "BY_ORDER_COUNT"]
RECEIVERS = ["陈师傅", "李师傅", "王师傅", "刘师傅", "赵师傅", "周师傅"]
PLATE_PREFIX = ["粤B", "粤A", "浙A", "苏E", "沪A", "京A"]


# ============================ 数据库连接 ============================

def mysql_bin():
    env = os.environ.get("MYSQL_BIN")
    if env:
        return env
    for p in ("/usr/local/mysql/bin/mysql", "/opt/homebrew/bin/mysql", "/usr/bin/mysql"):
        if os.path.exists(p):
            return p
    return "mysql"


def load_config():
    """优先读 application.properties，环境变量可覆盖，保证与后端用的是同一个库。"""
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    props = os.path.join(root, "backend", "src", "main", "resources", "application.properties")
    cfg = {"host": "127.0.0.1", "port": "3306", "name": "wms_v4", "user": "root", "pass": "123456"}

    if os.path.exists(props):
        with open(props, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line.startswith("spring.datasource.url="):
                    m = re.search(r"//([^:/]+):(\d+)/([^?]+)", line)
                    if m:
                        cfg["host"], cfg["port"], cfg["name"] = m.group(1), m.group(2), m.group(3)
                elif line.startswith("spring.datasource.username="):
                    cfg["user"] = line.split("=", 1)[1].strip()
                elif line.startswith("spring.datasource.password="):
                    cfg["pass"] = line.split("=", 1)[1].strip()

    cfg["host"] = os.environ.get("WMS_DB_HOST", cfg["host"])
    cfg["port"] = os.environ.get("WMS_DB_PORT", cfg["port"])
    cfg["name"] = os.environ.get("WMS_DB_NAME", cfg["name"])
    cfg["user"] = os.environ.get("WMS_DB_USER", cfg["user"])
    cfg["pass"] = os.environ.get("WMS_DB_PASS", cfg["pass"])
    return cfg


class Db:
    def __init__(self, cfg):
        self.cfg = cfg
        self.bin = mysql_bin()

    def _args(self):
        return [self.bin, "-h" + self.cfg["host"], "-P" + self.cfg["port"],
                "-u" + self.cfg["user"], "-p" + self.cfg["pass"],
                "--default-character-set=utf8mb4", "-D", self.cfg["name"]]

    def query(self, sql):
        p = subprocess.run(self._args() + ["-N", "-B", "-e", sql], capture_output=True, text=True)
        if p.returncode != 0:
            raise RuntimeError(p.stderr.strip())
        return [ln.split("\t") for ln in p.stdout.strip().split("\n") if ln]

    def scalar(self, sql, default=0):
        rows = self.query(sql)
        v = rows[0][0] if rows and rows[0] else ""
        return default if v in ("", "NULL", None) else v

    def run_script(self, path):
        with open(path, "rb") as f:
            p = subprocess.run(self._args(), stdin=f, capture_output=True, text=True)
        return p


# ============================ 工具函数 ============================

def esc(v):
    if v is None:
        return "NULL"
    if isinstance(v, (int, float)):
        return str(v)
    return "'" + str(v).replace("\\", "\\\\").replace("'", "''") + "'"


def ts(d):
    return d.strftime("%Y-%m-%d %H:%M:%S")


def day_key(d):
    return d.strftime("%Y%m%d")


def parse_when(s):
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d %H:%M", "%Y-%m-%d"):
        try:
            return dt.datetime.strptime(s, fmt)
        except ValueError:
            continue
    raise SystemExit("无法解析时间：%s（支持 2026-10-29 或 '2026-10-29 15:30'）" % s)


# ============================ 生成器 ============================

class Gen:
    def __init__(self, db, start_dt, end_dt):
        self.db = db
        self.start = start_dt
        self.end = end_dt
        self.now = end_dt
        self.rnd = random.Random(SEED + int(end_dt.timestamp()) // 60)
        self.sql = []
        self.counts = {}

        self.skus = db.query("SELECT sku_code,sku_name,spec,unit,COALESCE(default_location_code,''),"
                             "COALESCE(weight,0.5),category FROM sku WHERE status=1")
        self.locs = db.query("SELECT location_code,zone,COALESCE(path_seq,999) FROM location WHERE status='ENABLED'")
        self.loc_path = {r[0]: int(r[2]) for r in self.locs}
        self.carriers = db.query("SELECT carrier_code,carrier_name FROM carrier WHERE status=1")
        self.pickers = db.query("SELECT employee_no,employee_name FROM employee WHERE role='PICKER' AND status=1")
        self.reviewers = db.query("SELECT employee_no,employee_name FROM employee WHERE role='REVIEWER' AND status=1")
        self.operators = db.query("SELECT employee_no,employee_name FROM employee WHERE role='WAVE_OPERATOR' AND status=1")
        if not (self.skus and self.locs and self.carriers and self.pickers and self.reviewers and self.operators):
            raise SystemExit("主数据不全（sku/location/carrier/employee），请先初始化数据库")

        self.order_seq = int(db.scalar("SELECT COALESCE(MAX(CAST(SUBSTRING(order_no,11) AS UNSIGNED)),0) FROM `order`"))
        self.wave_seq = int(db.scalar("SELECT COALESCE(MAX(CAST(SUBSTRING(wave_no,11) AS UNSIGNED)),0) FROM wave"))
        self.review_seq = int(db.scalar("SELECT COALESCE(MAX(CAST(SUBSTRING(review_no,11) AS UNSIGNED)),0) FROM review"))
        self.handover_seq = int(db.scalar("SELECT COALESCE(MAX(CAST(SUBSTRING(handover_no,11) AS UNSIGNED)),0) FROM handover"))
        self.shortage_seq = int(db.scalar("SELECT COALESCE(MAX(CAST(SUBSTRING(shortage_no,11) AS UNSIGNED)),0) FROM shortage"))
        # 当天已有多少波次：续写时波次名要接着编号，不能从 1 重来
        self.day_waves = {r[0]: int(r[1]) for r in
                          db.query("SELECT LEFT(create_time,10), COUNT(*) FROM wave GROUP BY 1")}

    def add(self, table, stmt):
        self.sql.append(stmt)
        self.counts[table] = self.counts.get(table, 0) + 1

    # ---------- 随机素材 ----------
    def name(self):
        return self.rnd.choice(SURNAMES) + self.rnd.choice(GIVEN)

    def phone(self):
        return "1" + self.rnd.choice("3578") + "".join(self.rnd.choice("0123456789") for _ in range(9))

    def addr(self):
        prov, city, _ = self.rnd.choice(PLACES)
        return prov, city, "%s%s%s %d 号" % (prov, city, self.rnd.choice(ROADS), self.rnd.randint(1, 300))

    def volume(self, w_start, w_end):
        """按窗口时长折算单量：整营业日工作日 45~60、周末 18~28。"""
        hours = (w_end - w_start).total_seconds() / 3600
        if hours <= 0:
            return 0
        base = self.rnd.randint(18, 28) if w_start.weekday() >= 5 else self.rnd.randint(45, 60)
        full = (dt.datetime.combine(w_start.date(), WORK_END) -
                dt.datetime.combine(w_start.date(), WORK_START)).total_seconds() / 3600
        return max(0, int(round(base * min(1.0, hours / full))))

    # ---------- 订单 ----------
    def gen_orders(self, w_start, w_end, count):
        orders = []
        span = max(60, int((w_end - w_start).total_seconds()))
        for _ in range(count):
            pay = w_start + dt.timedelta(seconds=self.rnd.randint(0, span))
            prov, city, address = self.addr()
            carrier = self.rnd.choice(self.carriers)
            n_items = self.rnd.choices([1, 2, 3, 4, 5], weights=[42, 28, 16, 9, 5])[0]
            items, total_qty, amount = [], 0, 0.0
            for ln, s in enumerate(self.rnd.sample(self.skus, n_items), 1):
                qty = self.rnd.randint(1, 6)
                price = round(self.rnd.uniform(8, 180), 2)
                amt = round(price * qty, 2)
                items.append({"line": ln, "sku": s[0], "name": s[1], "spec": s[2], "unit": s[3],
                              "qty": qty, "price": price, "amount": amt,
                              "loc": s[4] or self.rnd.choice(self.locs)[0],
                              "weight": float(s[5] or 0.5)})
                total_qty += qty
                amount += amt
            self.order_seq += 1
            orders.append({
                "no": "SO%s%06d" % (day_key(pay), self.order_seq),   # 单号日期 = 下单日
                "platform": self.rnd.choice(PLATFORMS),
                "carrier": carrier[0], "carrier_name": carrier[1],
                "receiver": self.name(), "phone": self.phone(), "address": address,
                "province": prov, "city": city,
                "priority": "URGENT" if self.rnd.random() < 0.15 else "NORMAL",
                "pay": pay,
                "expected": pay + dt.timedelta(hours=self.rnd.choice([20, 24, 30, 36])),
                "items": items, "sku_count": n_items, "total_qty": total_qty,
                "amount": round(amount, 2),
                "remark": self.rnd.choice(["", "", "", "尽快发货", "需要发票", "放快递柜"]),
                "status": "CREATED", "wave": None,
            })
        orders.sort(key=lambda o: o["pay"])
        return orders

    def plan_waves(self, orders):
        """按时间顺序切成 4~9 单一批；不足 3 单的尾巴不排波次，留在订单池。"""
        waves, i = [], 0
        while i < len(orders):
            chunk = orders[i:i + self.rnd.randint(4, 9)]
            if len(chunk) < 3:
                if waves:
                    waves[-1]["orders"].extend(chunk)
                    i += len(chunk)
                break
            waves.append({"orders": chunk})
            i += len(chunk)
        return waves, orders[i:]

    def stage_of(self, create):
        elapsed = (self.now - create).total_seconds()
        if elapsed > 5 * 3600:
            return "CLOSED" if self.rnd.random() < 0.08 else "HANDOVERED"
        if elapsed > 3 * 3600:
            return "REVIEWED" if self.rnd.random() < 0.55 else "REVIEWING"
        if elapsed > 2 * 3600:
            return "PICKED"
        if elapsed > 1 * 3600:
            return "PICKING"
        if elapsed > 0.5 * 3600:
            return "ASSIGNED"
        return "CREATED"

    # ---------- 波次 ----------
    def build_wave(self, d, w, forced_stage=None):
        rnd = self.rnd
        orders = w["orders"]
        max_pay = max(o["pay"] for o in orders)
        create = max_pay + dt.timedelta(minutes=rnd.randint(6, 45))
        # 波次不能建在未来，也不能建在订单下单之前；两头夹住还放不下就这单不排波次
        create = min(create, self.now - dt.timedelta(minutes=2))
        if create <= max_pay + dt.timedelta(minutes=1):
            return False

        stage = forced_stage or self.stage_of(create)
        picker = rnd.choice(self.pickers)
        reviewer = rnd.choice(self.reviewers)
        creator = rnd.choice(self.operators)

        self.day_waves[d.isoformat()] = self.day_waves.get(d.isoformat(), 0) + 1
        wave_no = "WV%s%03d" % (day_key(create), self.wave_seq + 1)   # 单号日期 = 创建日
        self.wave_seq += 1

        assign = create + dt.timedelta(minutes=rnd.randint(1, 4))
        start = assign + dt.timedelta(minutes=rnd.randint(4, 18))

        merged = {}
        for o in orders:
            for it in o["items"]:
                m = merged.setdefault((it["loc"], it["sku"]),
                                      {"loc": it["loc"], "sku": it["sku"], "name": it["name"],
                                       "spec": it["spec"], "unit": it["unit"], "qty": 0,
                                       "orders": [], "weight": it["weight"]})
                m["qty"] += it["qty"]
                m["orders"].append(o["no"])
        items = sorted(merged.values(), key=lambda m: self.loc_path.get(m["loc"], 999))
        should_qty = sum(m["qty"] for m in items)
        locs = {m["loc"] for m in items}
        path_text = " → ".join(m["loc"] for m in items)

        dur = max(300, int(should_qty * rnd.uniform(8, 14) + len(locs) * rnd.uniform(20, 40)))
        pick_finish = start + dt.timedelta(seconds=dur)
        review_finish = pick_finish + dt.timedelta(minutes=rnd.randint(12, 38))
        handover_time = review_finish + dt.timedelta(minutes=rnd.randint(8, 28))

        # 已发生的节点一律压到现在之前，保证不出现「未来时间」
        lim = self.now - dt.timedelta(minutes=1)
        if stage in ("PICKED", "REVIEWING", "REVIEWED", "HANDOVERED", "CLOSED"):
            if pick_finish > lim:
                pick_finish = lim
                start = min(start, pick_finish - dt.timedelta(minutes=5))
                dur = max(60, int((pick_finish - start).total_seconds()))
            if stage in ("REVIEWED", "HANDOVERED", "CLOSED"):
                review_finish = min(review_finish, lim)
            if stage in ("HANDOVERED", "CLOSED"):
                handover_time = min(handover_time, lim)
        elif stage == "PICKING":
            start = min(start, lim)
        elif stage == "ASSIGNED":
            assign = min(assign, lim)

        done_pick = stage in ("PICKED", "REVIEWING", "REVIEWED", "HANDOVERED", "CLOSED")
        done_review = stage in ("REVIEWED", "HANDOVERED", "CLOSED")

        short_items = []
        if done_pick:
            for m in items:
                if rnd.random() < 0.02 and m["qty"] > 1:
                    short_items.append((m, rnd.randint(1, min(3, m["qty"] - 1))))
        short_map = {m["loc"] + m["sku"]: s for m, s in short_items}
        shortage_qty = sum(s for _, s in short_items)
        actual_qty = should_qty - shortage_qty

        self.add("wave", "INSERT INTO wave (wave_no,wave_name,strategy,status,order_count,sku_count,total_qty,"
                        "location_count,province_scope,creator_no,creator_name,picker_no,picker_name,"
                        "reviewer_no,reviewer_name,create_time,assign_time,pick_start_time,pick_finish_time,"
                        "review_finish_time,handover_time,pick_duration_sec,total_duration_sec,has_shortage,remark) VALUES ("
                 + ",".join([
                     esc(wave_no), esc("%s 波次 %d" % (d.isoformat(), self.day_waves[d.isoformat()])),
                     esc(rnd.choice(STRATEGIES)), esc(stage), str(len(orders)),
                     str(len({i["sku"] for o in orders for i in o["items"]})), str(should_qty), str(len(locs)),
                     esc(max({o["province"] for o in orders},
                             key=lambda p: sum(1 for o in orders if o["province"] == p))),
                     esc(creator[0]), esc(creator[1]),
                     esc(picker[0] if stage != "CREATED" else None),
                     esc(picker[1] if stage != "CREATED" else None),
                     esc(reviewer[0] if stage in ("REVIEWING", "REVIEWED", "HANDOVERED", "CLOSED") else None),
                     esc(reviewer[1] if stage in ("REVIEWING", "REVIEWED", "HANDOVERED", "CLOSED") else None),
                     esc(ts(create)), esc(ts(assign) if stage != "CREATED" else None),
                     esc(ts(start) if done_pick or stage == "PICKING" else None),
                     esc(ts(pick_finish) if done_pick else None),
                     esc(ts(review_finish) if done_review else None),
                     esc(ts(handover_time) if stage in ("HANDOVERED", "CLOSED") else None),
                     str(dur) if done_pick else "NULL",
                     str(int((handover_time - create).total_seconds())) if stage in ("HANDOVERED", "CLOSED") else "NULL",
                     "1" if shortage_qty else "0", esc(REMARK)]) + ");")

        for seq, o in enumerate(orders, 1):
            self.add("wave_order", "INSERT INTO wave_order (wave_no,order_no,seq_no) VALUES (%s,%s,%d);"
                     % (esc(wave_no), esc(o["no"]), seq))
            o["wave"] = wave_no
            if stage in ("HANDOVERED", "CLOSED"):
                o["status"] = "OUTBOUND"
            elif stage == "REVIEWED":
                o["status"] = "REVIEWED"
            elif stage == "REVIEWING":
                o["status"] = "PICKED"
            elif stage == "PICKED":
                o["status"] = "PICKED"
            else:
                o["status"] = "WAVED"

        task_no = "PK" + wave_no[2:]
        if stage == "CREATED":
            task_status, task_picker = "WAITING", (None, None)
        elif stage == "ASSIGNED":
            task_status, task_picker = "WAITING", picker
        elif stage == "PICKING":
            task_status, task_picker = "PICKING", picker
        else:
            task_status, task_picker = ("EXCEPTION" if shortage_qty else "FINISHED"), picker

        finished_count = len(items) if done_pick else (max(1, len(items) // 2) if stage == "PICKING" else 0)
        picked_now = actual_qty if done_pick else sum(m["qty"] for m in items[:finished_count])
        self.add("pick_task", "INSERT INTO pick_task (task_no,wave_no,picker_no,picker_name,status,item_count,"
                              "finished_count,location_count,should_qty,actual_qty,shortage_qty,order_count,"
                              "start_time,finish_time,duration_sec,path_text,remark,create_time) VALUES ("
                 + ",".join([
                     esc(task_no), esc(wave_no), esc(task_picker[0]), esc(task_picker[1]), esc(task_status),
                     str(len(items)), str(finished_count), str(len(locs)), str(should_qty),
                     str(picked_now), str(shortage_qty if done_pick else 0), str(len(orders)),
                     esc(ts(start) if stage not in ("CREATED", "ASSIGNED") else None),
                     esc(ts(pick_finish) if done_pick else None),
                     str(dur) if done_pick else "NULL",
                     esc(path_text), esc(REMARK), esc(ts(create))]) + ");")

        for seq, m in enumerate(items, 1):
            s_qty = short_map.get(m["loc"] + m["sku"], 0)
            if done_pick:
                a_qty, st = m["qty"] - s_qty, ("SHORTAGE" if s_qty else "PICKED")
            elif stage == "PICKING" and seq <= finished_count:
                a_qty, st = m["qty"], "PICKED"
            else:
                a_qty, st = 0, "PENDING"
            self.add("pick_item", "INSERT INTO pick_item (task_no,wave_no,seq_no,location_code,sku_code,sku_name,"
                                  "spec,unit,should_qty,actual_qty,shortage_qty,status,related_order_nos,"
                                  "scan_time,remark) VALUES ("
                     + ",".join([
                         esc(task_no), esc(wave_no), str(seq), esc(m["loc"]), esc(m["sku"]), esc(m["name"]),
                         esc(m["spec"]), esc(m["unit"]), str(m["qty"]), str(a_qty), str(s_qty), esc(st),
                         esc(",".join(m["orders"])),
                         esc(ts(pick_finish - dt.timedelta(seconds=rnd.randint(0, max(1, dur // 2)))) if a_qty else None),
                         esc(REMARK)]) + ");")

        diff_orders, review_rows = 0, []
        for o in (orders if done_pick else []):
            r_start = pick_finish + dt.timedelta(minutes=rnd.randint(2, 10))
            r_dur = max(120, int(o["total_qty"] * rnd.uniform(45, 90)))
            r_finish = r_start + dt.timedelta(seconds=r_dur)
            # 复核可能跨零点：单号日期取复核自己的开始日，而不是波次日
            self.review_seq += 1
            review_no = "RV%s%03d" % (day_key(r_start), self.review_seq)
            finished_now = done_review or (stage == "REVIEWING" and rnd.random() < 0.5)
            if finished_now:
                if rnd.random() < 0.11:
                    diff_orders += 1
                    dtype = rnd.choice(DIFF_TYPES)
                    dqty = rnd.randint(1, 2)
                    a_qty = o["total_qty"] - dqty if dtype in ("SHORT", "DAMAGED", "WRONG_ORDER") else o["total_qty"] + dqty
                    status = "DIFF"
                else:
                    dtype, dqty, a_qty, status = "NONE", 0, o["total_qty"], "PASS"
                finish, dur_val = ts(r_finish), r_dur
            else:
                dtype, dqty, a_qty, status, finish, dur_val = "NONE", 0, 0, "WAITING", None, None

            pkgs = max(1, round(o["total_qty"] / rnd.uniform(2.2, 3.2)))
            weight = round(sum(i["weight"] * i["qty"] for i in o["items"]), 2)
            self.add("review", "INSERT INTO review (review_no,wave_no,order_no,reviewer_no,reviewer_name,status,"
                               "should_sku_count,should_qty,actual_qty,diff_type,diff_qty,diff_detail,pack_material,"
                               "package_count,weight,start_time,finish_time,duration_sec,remark,create_time) VALUES ("
                     + ",".join([
                         esc(review_no), esc(wave_no), esc(o["no"]), esc(reviewer[0]), esc(reviewer[1]),
                         esc(status), str(o["sku_count"]), str(o["total_qty"]), str(a_qty),
                         esc(dtype), str(dqty),
                         esc("%s：应有 %d 件，实收 %d 件" % (dtype, o["total_qty"], a_qty) if status == "DIFF" else None),
                         esc(rnd.choice(PACK_MATERIALS)), str(pkgs), str(weight),
                         esc(ts(r_start) if status != "WAITING" else None), esc(finish),
                         str(dur_val) if dur_val else "NULL", esc(REMARK), esc(ts(r_start))]) + ");")
            review_rows.append({"pkgs": pkgs, "weight": weight, "status": status})

            if status == "DIFF":
                line = rnd.choice(o["items"])
                self.add("review_diff",
                         "INSERT INTO review_diff (review_no,wave_no,order_no,sku_code,sku_name,diff_type,"
                         "should_qty,actual_qty,diff_qty,responsible_stage,responsible_no,remark,create_time) VALUES ("
                         + ",".join([
                             esc(review_no), esc(wave_no), esc(o["no"]), esc(line["sku"]), esc(line["name"]),
                             esc(dtype), str(line["qty"]),
                             str(line["qty"] - dqty if dtype in ("SHORT", "DAMAGED") else line["qty"] + dqty),
                             str(-dqty if dtype in ("SHORT", "DAMAGED", "WRONG_ORDER") else dqty),
                             esc(rnd.choice(["PICK", "PICK", "PICK", "WAVE", "DATA"])),
                             esc(picker[0]), esc(REMARK), esc(ts(r_finish))]) + ");")

        if stage in ("HANDOVERED", "CLOSED", "REVIEWED"):
            carrier = max({o["carrier"] for o in orders},
                          key=lambda c: sum(1 for o in orders if o["carrier"] == c))
            cname = next(c[1] for c in self.carriers if c[0] == carrier)
            confirmed = stage in ("HANDOVERED", "CLOSED")
            h_create = review_finish if confirmed else review_finish - dt.timedelta(minutes=rnd.randint(5, 20))
            self.handover_seq += 1
            handover_no = "HO%s%03d" % (day_key(h_create), self.handover_seq)   # 单号日期 = 交接日
            self.add("handover",
                     "INSERT INTO handover (handover_no,wave_nos,carrier_code,carrier_name,order_count,"
                     "package_count,total_weight,status,handover_user_no,handover_user_name,receive_name,"
                     "vehicle_no,create_time,handover_time,remark) VALUES ("
                     + ",".join([
                         esc(handover_no), esc(wave_no), esc(carrier), esc(cname), str(len(orders)),
                         str(sum(r["pkgs"] for r in review_rows)),
                         str(round(sum(r["weight"] for r in review_rows), 2)),
                         esc("CONFIRMED" if confirmed else "CREATED"),
                         esc(reviewer[0]), esc(reviewer[1]),
                         esc(rnd.choice(RECEIVERS) if confirmed else None),
                         esc("%s%05d" % (rnd.choice(PLATE_PREFIX), rnd.randint(10000, 99999)) if confirmed else None),
                         esc(ts(h_create)), esc(ts(handover_time) if confirmed else None), esc(REMARK)]) + ");")

        for m, s_qty in short_items:
            report = pick_finish - dt.timedelta(minutes=rnd.randint(1, 8))
            self.shortage_seq += 1
            s_no = "SH%s%03d" % (day_key(report), self.shortage_seq)
            handled = (self.now - report).total_seconds() > 36 * 3600
            status = "PENDING" if not handled else ("CLOSED" if rnd.random() < 0.6 else "HANDLED")
            h_time = report + dt.timedelta(minutes=rnd.randint(20, 180))
            reason = rnd.choice(REASONS)
            self.add("shortage",
                     "INSERT INTO shortage (shortage_no,wave_no,task_no,order_no,location_code,sku_code,sku_name,"
                     "should_qty,shortage_qty,reason,reporter_no,reporter_name,report_time,status,handle_type,"
                     "handler_no,handler_name,handle_time,handle_remark,substitute_sku_code,create_time) VALUES ("
                     + ",".join([
                         esc(s_no), esc(wave_no), esc(task_no), esc(m["orders"][0]), esc(m["loc"]), esc(m["sku"]),
                         esc(m["name"]), str(m["qty"]), str(s_qty), esc(reason),
                         esc(picker[0]), esc(picker[1]), esc(ts(report)), esc(status),
                         esc(rnd.choice(HANDLE_TYPES) if handled else None),
                         esc(creator[0] if handled else None), esc(creator[1] if handled else None),
                         esc(ts(h_time) if handled else None),
                         esc("已与采购确认，次日补货" if handled else None), esc(None), esc(ts(report))]) + ");")
            self.add("operation_log",
                     self.log_sql("SHORTAGE", s_no, "PICK_SHORTAGE", "上报缺货", picker, "PICKER",
                                  "%s 缺 %d 件，原因=%s" % (m["name"], s_qty, reason), report))

        self.add("operation_log", self.log_sql("WAVE", wave_no, "WAVE_CREATE", "生成波次", creator, "WAVE_OPERATOR",
                                               "订单 %d 单，%d 个 SKU，共 %d 件"
                                               % (len(orders), len({i["sku"] for o in orders for i in o["items"]}),
                                                  should_qty), create))
        if stage != "CREATED":
            self.add("operation_log", self.log_sql("WAVE", wave_no, "WAVE_ASSIGN", "分配拣货员", creator,
                                                   "WAVE_OPERATOR", "拣货员=" + picker[1], assign))
            self.add("operation_log", self.log_sql("PICK", task_no, "PICK_START", "开始拣货", picker, "PICKER",
                                                   "路径 " + path_text, start))
        if done_pick:
            self.add("operation_log", self.log_sql("PICK", task_no, "PICK_FINISH", "拣货完成", picker, "PICKER",
                                                   "实拣 %d 件，耗时 %d 秒" % (actual_qty, dur), pick_finish))
        if done_review:
            self.add("operation_log", self.log_sql("REVIEW", wave_no, "REVIEW_FINISH", "复核完成", reviewer,
                                                   "REVIEWER", "复核 %d 单，差异 %d 单" % (len(orders), diff_orders),
                                                   review_finish))
        if stage in ("HANDOVERED", "CLOSED"):
            self.add("operation_log", self.log_sql("HANDOVER", handover_no, "HANDOVER_CONFIRM", "交接完成",
                                                   reviewer, "REVIEWER",
                                                   "交接 %d 单 / %d 件包裹"
                                                   % (len(orders), sum(r["pkgs"] for r in review_rows)),
                                                   handover_time))
        return True

    @staticmethod
    def log_sql(biz_type, biz_no, action, desc, who, role, detail, t):
        return ("INSERT INTO operation_log (biz_type,biz_no,action,action_desc,operator_no,operator_name,"
                "operator_role,detail,operate_time,create_time) VALUES ("
                + ",".join([esc(biz_type), esc(biz_no), esc(action), esc(desc), esc(who[0]), esc(who[1]),
                            esc(role), esc(detail), esc(ts(t)), esc(ts(t))]) + ");")

    # ---------- 主流程 ----------
    def run(self):
        d = self.start.date()
        while d <= self.end.date():
            w_start = max(dt.datetime.combine(d, WORK_START), self.start)
            w_end = min(dt.datetime.combine(d, WORK_END), self.end)
            if w_end <= w_start:
                d += dt.timedelta(days=1)
                continue

            orders = self.gen_orders(w_start, w_end, self.volume(w_start, w_end))
            waves, left = self.plan_waves(orders)

            forced = {}
            full_day = (w_end - w_start).total_seconds() >= 6 * 3600
            if d == self.end.date() and full_day and len(waves) >= 4:
                # 今天最后 4 波铺成「复核中→拣货中→已分配→待分配」，各角色登录都有活干
                for w, st in zip(waves[-4:], ["REVIEWING", "PICKING", "ASSIGNED", "CREATED"]):
                    forced[id(w)] = st

            for w in waves:
                if not self.build_wave(d, w, forced.get(id(w))):
                    left.extend(w["orders"])

            for o in left:
                if o["wave"] is None:
                    o["status"] = "POOLED" if self.rnd.random() < 0.6 else "CREATED"

            for o in orders:
                self.add("order", "INSERT INTO `order` (order_no,platform,carrier_code,carrier_name,receiver_name,"
                                  "receiver_phone,receiver_address,province,city,priority,pay_time,"
                                  "expected_delivery_time,status,wave_no,sku_count,total_qty,amount,buyer_remark,"
                                  "create_time) VALUES ("
                         + ",".join([
                             esc(o["no"]), esc(o["platform"]), esc(o["carrier"]), esc(o["carrier_name"]),
                             esc(o["receiver"]), esc(o["phone"]), esc(o["address"]), esc(o["province"]),
                             esc(o["city"]), esc(o["priority"]), esc(ts(o["pay"])), esc(ts(o["expected"])),
                             esc(o["status"]), esc(o["wave"]), str(o["sku_count"]), str(o["total_qty"]),
                             str(o["amount"]), esc(o["remark"]), esc(ts(o["pay"]))]) + ");")
                for it in o["items"]:
                    self.add("order_item",
                             "INSERT INTO order_item (order_no,line_no,sku_code,sku_name,spec,unit,qty,picked_qty,"
                             "location_code,price,amount,remark) VALUES ("
                             + ",".join([
                                 esc(o["no"]), str(it["line"]), esc(it["sku"]), esc(it["name"]), esc(it["spec"]),
                                 esc(it["unit"]), str(it["qty"]),
                                 str(it["qty"] if o["status"] in ("PICKED", "REVIEWED", "OUTBOUND") else 0),
                                 esc(it["loc"]), str(it["price"]), str(it["amount"]), esc(REMARK)]) + ");")
            d += dt.timedelta(days=1)
        return self.sql


# ============================ 写入前自检 ============================

VERIFY_CHECKS = [
    ("波次存在晚于当前时间的节点",
     "SELECT COUNT(*) FROM wave WHERE create_time>NOW() OR assign_time>NOW() OR pick_start_time>NOW() "
     "OR pick_finish_time>NOW() OR review_finish_time>NOW() OR handover_time>NOW()"),
    ("订单存在晚于当前时间的节点",
     "SELECT COUNT(*) FROM `order` WHERE pay_time>NOW() OR create_time>NOW()"),
    ("拣货/复核/交接/缺货/流水存在未来时间",
     "SELECT (SELECT COUNT(*) FROM pick_task WHERE create_time>NOW() OR finish_time>NOW())"
     "+(SELECT COUNT(*) FROM pick_item WHERE scan_time>NOW())"
     "+(SELECT COUNT(*) FROM review WHERE create_time>NOW() OR finish_time>NOW())"
     "+(SELECT COUNT(*) FROM handover WHERE create_time>NOW() OR handover_time>NOW())"
     "+(SELECT COUNT(*) FROM shortage WHERE report_time>NOW())"
     "+(SELECT COUNT(*) FROM operation_log WHERE operate_time>NOW())"),
    ("单号日期与业务时间不一致",
     "SELECT (SELECT COUNT(*) FROM wave WHERE CONCAT('WV',REPLACE(LEFT(create_time,10),'-',''))<>LEFT(wave_no,10))"
     "+(SELECT COUNT(*) FROM pick_task WHERE CONCAT('PK',REPLACE(LEFT(create_time,10),'-',''))<>LEFT(task_no,10))"
     "+(SELECT COUNT(*) FROM `order` WHERE CONCAT('SO',REPLACE(LEFT(pay_time,10),'-',''))<>LEFT(order_no,10))"
     "+(SELECT COUNT(*) FROM review WHERE CONCAT('RV',REPLACE(LEFT(create_time,10),'-',''))<>LEFT(review_no,10))"
     "+(SELECT COUNT(*) FROM handover WHERE CONCAT('HO',REPLACE(LEFT(create_time,10),'-',''))<>LEFT(handover_no,10))"
     "+(SELECT COUNT(*) FROM shortage WHERE CONCAT('SH',REPLACE(LEFT(report_time,10),'-',''))<>LEFT(shortage_no,10))"),
    ("存在订单晚于所属波次的创建时间",
     "SELECT COUNT(*) FROM `order` o JOIN wave w ON w.wave_no=o.wave_no WHERE o.pay_time>w.create_time"),
    ("波次节点顺序倒挂",
     "SELECT COUNT(*) FROM wave WHERE (pick_finish_time IS NOT NULL AND pick_finish_time<pick_start_time)"
     " OR (review_finish_time IS NOT NULL AND pick_finish_time IS NOT NULL AND review_finish_time<pick_finish_time)"
     " OR (handover_time IS NOT NULL AND review_finish_time IS NOT NULL AND handover_time<review_finish_time)"),
    ("波次订单数与关联表不符",
     "SELECT COUNT(*) FROM wave w WHERE w.order_count<>(SELECT COUNT(*) FROM wave_order wo WHERE wo.wave_no=w.wave_no)"),
    ("拣货任务件数与明细合计不符",
     "SELECT COUNT(*) FROM pick_task t WHERE t.should_qty<>"
     "(SELECT COALESCE(SUM(should_qty),0) FROM pick_item i WHERE i.task_no=t.task_no)"),
    ("订单状态与波次状态冲突",
     "SELECT COUNT(*) FROM `order` o JOIN wave w ON w.wave_no=o.wave_no WHERE "
     "(w.status IN ('HANDOVERED','CLOSED') AND o.status<>'OUTBOUND') OR (w.status='REVIEWED' AND o.status<>'REVIEWED') "
     "OR (w.status IN ('CREATED','ASSIGNED','PICKING') AND o.status<>'WAVED') OR (w.status='PICKED' AND o.status<>'PICKED')"),
    ("主键重复",
     "SELECT (SELECT COUNT(*)-COUNT(DISTINCT wave_no) FROM wave)"
     "+(SELECT COUNT(*)-COUNT(DISTINCT task_no) FROM pick_task)"
     "+(SELECT COUNT(*)-COUNT(DISTINCT order_no) FROM `order`)"
     "+(SELECT COUNT(*)-COUNT(DISTINCT review_no) FROM review)"
     "+(SELECT COUNT(*)-COUNT(DISTINCT handover_no) FROM handover)"),
    ("存在孤儿记录（明细/关系指向不存在的单据）",
     "SELECT (SELECT COUNT(*) FROM order_item i LEFT JOIN `order` o ON o.order_no=i.order_no WHERE o.order_no IS NULL)"
     "+(SELECT COUNT(*) FROM wave_order wo LEFT JOIN `order` o ON o.order_no=wo.order_no WHERE o.order_no IS NULL)"
     "+(SELECT COUNT(*) FROM review r LEFT JOIN `order` o ON o.order_no=r.order_no WHERE o.order_no IS NULL)"),
]


def verify_block():
    """生成一个存储过程：任意一项校验不通过就 SIGNAL，让整批 INSERT 随事务回滚。"""
    body = ["DROP PROCEDURE IF EXISTS wms_gen_verify;",
            "DELIMITER $$", "CREATE PROCEDURE wms_gen_verify()", "BEGIN", "  DECLARE bad BIGINT DEFAULT 0;"]
    for msg, sql in VERIFY_CHECKS:
        # VERIFY_CHECKS 里存的已经是完整的 SELECT 语句，这里只补 INTO 变量
        body.append("  %s INTO bad;" % sql.replace("\n", " "))
        body.append("  IF bad > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '数据自检未通过：%s'; END IF;" % msg)
    body += ["END$$", "DELIMITER ;"]
    return "\n".join(body)


# ============================ 入口 ============================

def main():
    ap = argparse.ArgumentParser(description="WMS 演示数据一键生成")
    ap.add_argument("--days", type=int, help="补最近 N 天（默认从库内最后一张订单续写到现在）")
    ap.add_argument("--start", help="起始时间，如 2026-10-29 或 '2026-10-29 15:30'")
    ap.add_argument("--end", help="结束时间，默认当前时刻")
    ap.add_argument("--dry-run", action="store_true", help="只打印 SQL，不写库")
    ap.add_argument("--no-verify", action="store_true", help="跳过写入前自检")
    ap.add_argument("--out", default="/tmp/wms_gen.sql", help="SQL 输出路径（默认 /tmp/wms_gen.sql）")
    args = ap.parse_args()

    now = dt.datetime.now().replace(second=0, microsecond=0)
    db = Db(load_config())

    end = parse_when(args.end) if args.end else now
    if end > now:
        end = now

    if args.start:
        start = parse_when(args.start)
    else:
        last = db.scalar("SELECT MAX(pay_time) FROM `order`", "")
        last_dt = parse_when(last) if last else None
        if last_dt is None:
            resume = dt.datetime.combine(end.date(), WORK_START)
        elif last_dt.date() == end.date():
            # 同一天：从最后一单之后接着写
            resume = last_dt + dt.timedelta(minutes=5)
        else:
            # 跨天：从次日营业开始补
            resume = dt.datetime.combine(last_dt.date() + dt.timedelta(days=1), WORK_START)

        if args.days:
            # 补 N 天，但绝不回头覆盖已有数据：取「N 天前」与「库内最新」中较晚的一个
            start = max(end - dt.timedelta(days=args.days), resume)
        else:
            start = resume

    if start >= end - dt.timedelta(minutes=5):
        print("数据已是最新（最后一单 %s），无需生成。" % db.scalar("SELECT MAX(pay_time) FROM `order`", "-"))
        return 0

    print("生成区间：%s  →  %s" % (ts(start), ts(end)))
    g = Gen(db, start, end)
    stmts = g.run()

    if not stmts:
        print("该区间内没有可生成的营业时段（营业时间 09:00–23:30），未写入。")
        return 0

    with open(args.out, "w", encoding="utf-8") as f:
        f.write("SET NAMES utf8mb4;\n")
        if not args.no_verify:
            f.write(verify_block() + "\n")
        f.write("SET autocommit=0;\n")
        for s in stmts:
            f.write(s + "\n")
        if not args.no_verify:
            f.write("CALL wms_gen_verify();\n")
        f.write("COMMIT;\n")
        if not args.no_verify:
            f.write("DROP PROCEDURE wms_gen_verify;\n")

    if args.dry_run:
        print("--dry-run：SQL 已写入 %s，共 %d 条语句，未执行。" % (args.out, len(stmts)))
        for k, v in sorted(g.counts.items(), key=lambda x: -x[1]):
            print("   %-14s +%d" % (k, v))
        return 0

    p = db.run_script(args.out)
    if p.returncode != 0:
        err = (p.stderr or "").strip()
        # 自检失败会让脚本中断在 DROP 之前，这里补一次清理，保持库内无残留
        if not args.no_verify:
            try:
                db.query("DROP PROCEDURE IF EXISTS wms_gen_verify")
            except Exception:
                pass
        print("\n写入失败，事务已回滚：", file=sys.stderr)
        print(err, file=sys.stderr)
        return 1

    print("写入完成，自检%s。" % ("已通过" if not args.no_verify else "已跳过"))
    for k, v in sorted(g.counts.items(), key=lambda x: -x[1]):
        print("   %-14s +%d" % (k, v))

    total = db.scalar("SELECT COUNT(*) FROM `order`")
    print("库内现有订单 %s 单，最新一单 %s。" % (total, db.scalar("SELECT MAX(pay_time) FROM `order`", "-")))
    return 0


if __name__ == "__main__":
    sys.exit(main())
