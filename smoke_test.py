# -*- coding: utf-8 -*-
"""WMS 闭环冒烟测试：订单汇聚 -> 波次生成 -> 拣货执行 -> 复核打包 -> 出库交接 -> 效率复盘

用法：先启动后端（java -jar target/wms-0.0.1-SNAPSHOT.jar --server.port=8080），再执行：
    python smoke_test.py
"""
import base64
import json
import urllib.parse
import urllib.request
import urllib.error

BASE = "http://127.0.0.1:8080/api"
PWD = base64.b64encode("123456".encode()).decode()

passed, failed = [], []


def call(method, path, token=None, body=None):
    # 对含中文的查询参数做 URL 编码（保留 / ? & = % 等结构字符）
    url = BASE + urllib.parse.quote(path, safe="/?&=%")
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return resp.status, json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"raw": raw}


def check(name, cond, detail=""):
    if cond:
        passed.append(name)
        print("  [OK]   %s %s" % (name, detail))
    else:
        failed.append(name)
        print("  [FAIL] %s %s" % (name, detail))


def login(username):
    st, res = call("POST", "/auth/login", body={"username": username, "password": PWD})
    assert st == 200 and res.get("code") == 0, "登录失败 %s: %s" % (username, res)
    return res["data"]["token"], res["data"]


print("=" * 62)
print("WMS 闭环冒烟测试")
print("=" * 62)

print("\n[0] 鉴权")
st, res = call("GET", "/order/list")
check("无 token 访问返回 401", st == 401 and res.get("code") == 401)

admin_t, admin_u = login("admin")
planner_t, planner_u = login("planner")
picker_t, picker_u = login("picker1")
checker_t, checker_u = login("checker1")
check("admin 登录", admin_u["role"] == "ADMIN", "role=%s" % admin_u["role"])
check("planner 登录", planner_u["role"] == "WAVE_PLANNER", "role=%s" % planner_u["role"])
check("picker1 登录", picker_u["role"] == "PICKER", "role=%s" % picker_u["role"])
check("checker1 登录", checker_u["role"] == "CHECKER", "role=%s" % checker_u["role"])

st, res = call("GET", "/auth/me", admin_t)
check("获取当前用户", st == 200 and res["data"]["username"] == "admin")
check("密码字段不回传", res["data"].get("password") is None)

st, res = call("POST", "/auth/login", body={"username": "admin", "password": base64.b64encode(b"wrong").decode()})
check("错误密码被拒", res.get("code") == 1, res.get("message"))

print("\n[1] 订单汇聚")
st, res = call("GET", "/order/list", planner_t, )
check("订单列表", st == 200 and res["data"]["total"] > 0, "total=%s" % res["data"]["total"])
st, res = call("GET", "/order/pending", planner_t)
pending = res["data"]
check("待分波订单", len(pending) > 0, "待分波=%d" % len(pending))

# 新建一张订单
st, res = call("POST", "/order/add", planner_t, {
    "platform": "天猫", "customerName": "冒烟测试客户", "customerPhone": "13900000000",
    "address": "测试地址 1 号", "priority": "普通",
    "items": [{"skuCode": "SKU10001", "qty": 2}, {"skuCode": "SKU10002", "qty": 1}],
})
check("新建订单", st == 200 and res["data"]["status"] == "待分波", res["data"]["orderNo"])
new_order_id = res["data"]["id"]

st, res = call("GET", "/order/detail/%d" % new_order_id, planner_t)
check("订单明细", len(res["data"]["items"]) == 2)

print("\n[2] 波次生成")
pick_order_ids = [o["id"] for o in pending[:3]] + [new_order_id]
st, res = call("POST", "/wave/create", planner_t, {
    "orderIds": pick_order_ids, "pickerId": picker_u and 3, "remark": "冒烟测试波次",
})
check("生成波次", st == 200 and res["data"]["status"] == "待拣货", res["data"].get("waveNo"))
wave = res["data"]
wave_id = wave["id"]

st, res = call("GET", "/order/list?status=已分波", planner_t)
check("订单已置为已分波", res["data"]["total"] >= len(pick_order_ids))

st, res = call("GET", "/wave/detail/%d" % wave_id, planner_t)
d = res["data"]
check("波次详情-拣货清单", len(d["items"]) > 0, "品项=%d" % len(d["items"]))
check("波次详情-关联订单", len(d["orders"]) == len(pick_order_ids))
check("波次-拣货员已指派", d["wave"]["pickerName"] is not None, d["wave"]["pickerName"])

print("\n[3] 拣货执行")
st, res = call("POST", "/pick/start", picker_t, {"waveId": wave_id})
check("开始拣货", res.get("code") == 0)

st, res = call("GET", "/pick/detail/%d" % wave_id, picker_t)
items = res["data"]["items"]
submit_items = []
for i, it in enumerate(items):
    # 第一个品项制造缺货，其余足量拣齐
    picked = it["requiredQty"] - 1 if i == 0 else it["requiredQty"]
    submit_items.append({
        "skuCode": it["skuCode"], "pickedQty": picked,
        "shortageQty": it["requiredQty"] - picked,
        "reason": "库存不足" if picked < it["requiredQty"] else "",
    })
st, res = call("POST", "/pick/submit", picker_t, {"waveId": wave_id, "items": submit_items})
check("提交拣货", res.get("code") == 0)

st, res = call("POST", "/pick/finish", picker_t, {"waveId": wave_id})
check("完成拣货", res.get("code") == 0)

st, res = call("GET", "/shortage/list", planner_t)
check("缺货记录已生成", res["data"]["total"] >= 1, "缺货=%s" % res["data"]["total"])

print("\n[4] 复核打包")
st, res = call("GET", "/review/detail/%d" % wave_id, checker_t)
orders = res["data"]["orders"]
check("待复核波次详情", len(orders) == len(pick_order_ids))
reviews = []
for idx, o in enumerate(orders):
    if idx == 0:
        reviews.append({
            "orderId": o["order"]["id"], "result": "异常", "packCount": 1,
            "remark": "冒烟测试：登记一条差错",
            "errors": [{"skuCode": o["items"][0]["skuCode"], "errorType": "破损", "qty": 1, "description": "包装破损"}],
        })
    else:
        reviews.append({"orderId": o["order"]["id"], "result": "通过", "packCount": 1, "remark": "", "errors": []})
st, res = call("POST", "/review/submit", checker_t, {"waveId": wave_id, "reviews": reviews})
check("提交复核", res.get("code") == 0)

st, res = call("GET", "/error/list", checker_t)
check("差错记录已生成", res["data"]["total"] >= 1, "差错=%s" % res["data"]["total"])

print("\n[5] 出库交接")
st, res = call("GET", "/wave/outboundable", checker_t)
check("待出库波次包含本次波次", any(w["id"] == wave_id for w in res["data"]))
st, res = call("POST", "/outbound/submit", checker_t, {
    "waveId": wave_id, "carrier": "顺丰速运", "handoverTo": "冒烟测试交接人", "remark": "冒烟测试",
})
check("提交出库交接", res.get("code") == 0)

st, res = call("GET", "/wave/detail/%d" % wave_id, planner_t)
check("波次状态=已出库", res["data"]["wave"]["status"] == "已出库", res["data"]["wave"]["status"])
check("波次已记录出库时间", res["data"]["wave"]["outboundTime"] is not None)
check("出库记录已生成", len(res["data"]["outbound"]) == 1)

print("\n[6] 效率复盘 / 差错统计")
st, res = call("GET", "/stats/dashboard", admin_t)
dash = res["data"]
check("工作台看板", st == 200 and "waveTotal" in dash,
      "波次=%s 订单=%s 差错率=%s%%" % (dash["waveTotal"], dash["orderTotal"], dash["errorRate"]))

st, res = call("GET", "/stats/efficiency", admin_t)
check("拣货员效率", len(res["data"]["pickers"]) >= 1, "拣货员=%d" % len(res["data"]["pickers"]))
check("复核员效率", len(res["data"]["checkers"]) >= 1, "复核员=%d" % len(res["data"]["checkers"]))

st, res = call("GET", "/stats/errors", admin_t)
check("差错统计", "byType" in res["data"] and "errorRate" in res["data"],
      "差错率=%s%% 类型数=%d" % (res["data"]["errorRate"], len(res["data"]["byType"])))

print("\n[7] 基础数据")
st, res = call("GET", "/sku/list", planner_t)
check("SKU 列表", res["data"]["total"] > 0, "SKU=%s" % res["data"]["total"])
st, res = call("GET", "/inventory/list", planner_t)
check("库存列表", res["data"]["total"] > 0, "库存行=%s" % res["data"]["total"])
st, res = call("GET", "/user/list", admin_t)
check("用户列表", res["data"]["total"] >= 6, "用户=%s" % res["data"]["total"])

print("\n" + "=" * 62)
print("通过 %d 项，失败 %d 项" % (len(passed), len(failed)))
if failed:
    print("失败项：" + ", ".join(failed))
print("=" * 62)
