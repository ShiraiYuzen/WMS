-- ============================================================
-- 仓库拣货与波次管理系统 建表 + 种子数据脚本
-- 数据库：wms（jdbc:mysql://127.0.0.1:3306/wms, root/123456）
-- 说明：应用启动时由 DatabaseInitializer 自动执行（幂等），也可手工执行本文件
-- 业务闭环：订单汇聚 -> 波次生成 -> 拣货执行 -> 复核打包 -> 出库交接 -> 效率复盘
-- ============================================================

-- ---------- 用户（角色：ADMIN 管理员 / WAVE_PLANNER 波次员 / PICKER 拣货员 / CHECKER 复核员） ----------
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(128) NOT NULL COMMENT '明文密码（演示；前端 Base64 编码传输，后端解码比对）',
    nickname VARCHAR(64),
    role VARCHAR(32) NOT NULL DEFAULT 'PICKER',
    status VARCHAR(16) DEFAULT '启用',
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 商品 SKU ----------
CREATE TABLE IF NOT EXISTS skus (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    spec VARCHAR(128),
    category VARCHAR(64),
    location VARCHAR(32) COMMENT '默认拣货库位',
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 库存（按 SKU + 库位） ----------
CREATE TABLE IF NOT EXISTS inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku_id BIGINT NOT NULL,
    sku_code VARCHAR(64),
    sku_name VARCHAR(128),
    location VARCHAR(32) NOT NULL,
    total_qty INT DEFAULT 0,
    locked_qty INT DEFAULT 0 COMMENT '波次锁定',
    available_qty INT DEFAULT 0 COMMENT '可用库存',
    update_time DATETIME,
    UNIQUE KEY uk_sku_loc (sku_id, location)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 订单 ----------
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE,
    platform VARCHAR(32) COMMENT '天猫/京东/抖音/拼多多',
    customer_name VARCHAR(64),
    customer_phone VARCHAR(32),
    address VARCHAR(255),
    item_count INT DEFAULT 0,
    total_qty INT DEFAULT 0,
    status VARCHAR(24) DEFAULT '待分波' COMMENT '待分波/已分波/拣货中/已拣货/已复核/已出库/已取消',
    wave_id BIGINT,
    wave_no VARCHAR(64),
    priority VARCHAR(16) DEFAULT '普通',
    create_time DATETIME,
    wave_time DATETIME,
    finish_time DATETIME,
    KEY idx_status (status),
    KEY idx_wave (wave_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 订单明细 ----------
CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    sku_id BIGINT,
    sku_code VARCHAR(64),
    sku_name VARCHAR(128),
    location VARCHAR(32),
    qty INT DEFAULT 0,
    picked_qty INT DEFAULT 0,
    shortage_qty INT DEFAULT 0,
    status VARCHAR(24) DEFAULT '待拣',
    KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 波次 ----------
CREATE TABLE IF NOT EXISTS waves (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    wave_no VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(24) DEFAULT '待拣货' COMMENT '待拣货/拣货中/待复核/待出库/已出库/已取消',
    order_count INT DEFAULT 0,
    item_count INT DEFAULT 0,
    total_qty INT DEFAULT 0,
    picker_id BIGINT,
    picker_name VARCHAR(64),
    checker_id BIGINT,
    checker_name VARCHAR(64),
    creator_id BIGINT,
    creator_name VARCHAR(64),
    strategy VARCHAR(32) DEFAULT '按库位聚合',
    remark VARCHAR(255),
    create_time DATETIME,
    start_time DATETIME,
    pick_finish_time DATETIME,
    review_finish_time DATETIME,
    outbound_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 波次拣货明细 ----------
CREATE TABLE IF NOT EXISTS wave_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    wave_id BIGINT NOT NULL,
    sku_id BIGINT,
    sku_code VARCHAR(64),
    sku_name VARCHAR(128),
    location VARCHAR(32),
    required_qty INT DEFAULT 0,
    picked_qty INT DEFAULT 0,
    shortage_qty INT DEFAULT 0,
    status VARCHAR(24) DEFAULT '待拣',
    KEY idx_wave (wave_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 缺货记录 ----------
CREATE TABLE IF NOT EXISTS shortages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    wave_id BIGINT,
    wave_no VARCHAR(64),
    sku_id BIGINT,
    sku_code VARCHAR(64),
    sku_name VARCHAR(128),
    location VARCHAR(32),
    required_qty INT DEFAULT 0,
    picked_qty INT DEFAULT 0,
    short_qty INT DEFAULT 0,
    reason VARCHAR(64) COMMENT '库存不足/库位缺货/商品破损/其他',
    reporter_id BIGINT,
    reporter_name VARCHAR(64),
    status VARCHAR(16) DEFAULT '待处理' COMMENT '待处理/已补货/已核减',
    handle_remark VARCHAR(255),
    create_time DATETIME,
    handle_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 复核记录 ----------
CREATE TABLE IF NOT EXISTS review_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    wave_id BIGINT,
    wave_no VARCHAR(64),
    order_id BIGINT,
    order_no VARCHAR(64),
    checker_id BIGINT,
    checker_name VARCHAR(64),
    result VARCHAR(16) COMMENT '通过/异常',
    pack_count INT DEFAULT 0,
    error_count INT DEFAULT 0,
    remark VARCHAR(255),
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 差错记录 ----------
CREATE TABLE IF NOT EXISTS error_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    wave_id BIGINT,
    wave_no VARCHAR(64),
    order_id BIGINT,
    order_no VARCHAR(64),
    sku_id BIGINT,
    sku_code VARCHAR(64),
    sku_name VARCHAR(128),
    error_type VARCHAR(32) COMMENT '错拣/漏拣/多拣/破损/其他',
    qty INT DEFAULT 1,
    checker_id BIGINT,
    checker_name VARCHAR(64),
    description VARCHAR(255),
    create_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------- 出库交接 ----------
CREATE TABLE IF NOT EXISTS outbounds (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    wave_id BIGINT,
    wave_no VARCHAR(64),
    order_count INT DEFAULT 0,
    total_qty INT DEFAULT 0,
    carrier VARCHAR(64) COMMENT '承运商',
    handover_to VARCHAR(64) COMMENT '交接人',
    operator_id BIGINT,
    operator_name VARCHAR(64),
    status VARCHAR(16) DEFAULT '已交接',
    remark VARCHAR(255),
    create_time DATETIME,
    handover_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 种子数据
-- ============================================================

INSERT IGNORE INTO users(id, username, password, nickname, role, status, create_time) VALUES
(1, 'admin',   '123456', '系统管理员', 'ADMIN',         '启用', '2026-01-01 09:00:00'),
(2, 'planner', '123456', '李波（波次员）', 'WAVE_PLANNER', '启用', '2026-01-01 09:00:00'),
(3, 'picker1', '123456', '张拣（拣货员）', 'PICKER',        '启用', '2026-01-01 09:00:00'),
(4, 'picker2', '123456', '王拣（拣货员）', 'PICKER',        '启用', '2026-01-01 09:00:00'),
(5, 'checker1','123456', '赵核（复核员）', 'CHECKER',       '启用', '2026-01-01 09:00:00'),
(6, 'checker2','123456', '孙核（复核员）', 'CHECKER',       '启用', '2026-01-01 09:00:00');

INSERT IGNORE INTO skus(id, sku_code, name, spec, category, location, create_time) VALUES
(1,  'SKU10001', '无线蓝牙耳机', '白色/标准版', '数码配件', 'A-01-01', '2026-01-02 09:00:00'),
(2,  'SKU10002', '手机保护壳',   '透明/通用',   '数码配件', 'A-01-02', '2026-01-02 09:00:00'),
(3,  'SKU10003', '快充数据线',   '1米/Type-C',  '数码配件', 'A-01-03', '2026-01-02 09:00:00'),
(4,  'SKU10004', '便携充电宝',   '10000mAh',    '数码配件', 'A-02-01', '2026-01-02 09:00:00'),
(5,  'SKU10005', '智能手环',     '黑色/标准版', '智能穿戴', 'A-02-02', '2026-01-02 09:00:00'),
(6,  'SKU10006', '蓝牙音箱',     '迷你/红色',   '数码配件', 'A-02-03', '2026-01-02 09:00:00'),
(7,  'SKU10007', '机械键盘',     '87键/青轴',   '电脑外设', 'B-01-01', '2026-01-02 09:00:00'),
(8,  'SKU10008', '无线鼠标',     '静音/灰色',   '电脑外设', 'B-01-02', '2026-01-02 09:00:00'),
(9,  'SKU10009', '显示器支架',   '单屏/铝合金', '电脑外设', 'B-01-03', '2026-01-02 09:00:00'),
(10, 'SKU10010', '保温杯',       '500ml/不锈钢','家居百货', 'C-01-01', '2026-01-02 09:00:00'),
(11, 'SKU10011', '收纳盒',       '中号/透明',   '家居百货', 'C-01-02', '2026-01-02 09:00:00'),
(12, 'SKU10012', '洗衣液',       '2kg/薰衣草',  '家清个护', 'C-01-03', '2026-01-02 09:00:00'),
(13, 'SKU10013', '抽纸',         '3层/24包',    '家清个护', 'C-02-01', '2026-01-02 09:00:00'),
(14, 'SKU10014', '运动水壶',     '750ml/蓝',    '户外运动', 'D-01-01', '2026-01-02 09:00:00'),
(15, 'SKU10015', '瑜伽垫',       '10mm/紫',     '户外运动', 'D-01-02', '2026-01-02 09:00:00'),
(16, 'SKU10016', '折叠雨伞',     '三折/黑色',   '户外运动', 'D-01-03', '2026-01-02 09:00:00');

INSERT IGNORE INTO inventory(id, sku_id, sku_code, sku_name, location, total_qty, locked_qty, available_qty, update_time) VALUES
(1,  1,  'SKU10001', '无线蓝牙耳机', 'A-01-01', 200, 0, 200, NOW()),
(2,  2,  'SKU10002', '手机保护壳',   'A-01-02', 180, 0, 180, NOW()),
(3,  3,  'SKU10003', '快充数据线',   'A-01-03', 300, 0, 300, NOW()),
(4,  4,  'SKU10004', '便携充电宝',   'A-02-01', 120, 0, 120, NOW()),
(5,  5,  'SKU10005', '智能手环',     'A-02-02', 150, 0, 150, NOW()),
(6,  6,  'SKU10006', '蓝牙音箱',     'A-02-03', 90,  0, 90,  NOW()),
(7,  7,  'SKU10007', '机械键盘',     'B-01-01', 80,  0, 80,  NOW()),
(8,  8,  'SKU10008', '无线鼠标',     'B-01-02', 160, 0, 160, NOW()),
(9,  9,  'SKU10009', '显示器支架',   'B-01-03', 8,   0, 8,   NOW()),
(10, 10, 'SKU10010', '保温杯',       'C-01-01', 220, 0, 220, NOW()),
(11, 11, 'SKU10011', '收纳盒',       'C-01-02', 140, 0, 140, NOW()),
(12, 12, 'SKU10012', '洗衣液',       'C-01-03', 100, 0, 100, NOW()),
(13, 13, 'SKU10013', '抽纸',         'C-02-01', 260, 0, 260, NOW()),
(14, 14, 'SKU10014', '运动水壶',     'D-01-01', 75,  0, 75,  NOW()),
(15, 15, 'SKU10015', '瑜伽垫',       'D-01-02', 45,  0, 45,  NOW()),
(16, 16, 'SKU10016', '折叠雨伞',     'D-01-03', 130, 0, 130, NOW());

-- 波次（1-3 已完成出库；4 待拣货；5 拣货中；6 待复核；7 待出库）
INSERT IGNORE INTO waves(id, wave_no, status, order_count, item_count, total_qty, picker_id, picker_name, checker_id, checker_name, creator_id, creator_name, strategy, remark, create_time, start_time, pick_finish_time, review_finish_time, outbound_time) VALUES
(1, 'W20261010001', '已出库', 2, 2, 4, 3, '张拣（拣货员）', 5, '赵核（复核员）', 2, '李波（波次员）', '按库位聚合', '正常波次', '2026-10-10 09:00:00', '2026-10-10 09:05:00', '2026-10-10 09:32:00', '2026-10-10 09:45:00', '2026-10-10 10:10:00'),
(2, 'W20261011002', '已出库', 2, 2, 4, 4, '王拣（拣货员）', 6, '孙核（复核员）', 2, '李波（波次员）', '按库位聚合', '含缺货1项', '2026-10-11 09:10:00', '2026-10-11 09:15:00', '2026-10-11 09:50:00', '2026-10-11 10:05:00', '2026-10-11 10:30:00'),
(3, 'W20261012003', '已出库', 2, 2, 3, 3, '张拣（拣货员）', 5, '赵核（复核员）', 2, '李波（波次员）', '按库位聚合', '正常波次', '2026-10-12 14:00:00', '2026-10-12 14:06:00', '2026-10-12 14:28:00', '2026-10-12 14:40:00', '2026-10-12 15:05:00'),
(4, 'W20261015004', '待拣货', 2, 2, 4, 3, '张拣（拣货员）', NULL, NULL, 2, '李波（波次员）', '按库位聚合', NULL, '2026-10-15 08:40:00', NULL, NULL, NULL, NULL),
(5, 'W20261015005', '拣货中', 2, 2, 3, 4, '王拣（拣货员）', NULL, NULL, 2, '李波（波次员）', '按库位聚合', '拣货中', '2026-10-15 08:45:00', '2026-10-15 08:50:00', NULL, NULL, NULL),
(6, 'W20261015006', '待复核', 2, 2, 4, 3, '张拣（拣货员）', NULL, NULL, 2, '李波（波次员）', '按库位聚合', '拣货完成待复核', '2026-10-15 09:00:00', '2026-10-15 09:05:00', '2026-10-15 09:40:00', NULL, NULL),
(7, 'W20261015007', '待出库', 2, 2, 4, 4, '王拣（拣货员）', 5, '赵核（复核员）', 2, '李波（波次员）', '按库位聚合', '复核完成待出库', '2026-10-15 10:00:00', '2026-10-15 10:05:00', '2026-10-15 10:35:00', '2026-10-15 10:50:00', NULL);

-- 订单（1-10 待分波；11-12 已分波；13-14 拣货中；15-16 已拣货；17-18 已复核；19-24 已出库；25-26 已取消）
INSERT IGNORE INTO orders(id, order_no, platform, customer_name, customer_phone, address, item_count, total_qty, status, wave_id, wave_no, priority, create_time, wave_time, finish_time) VALUES
(1,  'SO20261015001', '天猫',   '陈晓', '13800001001', '上海市浦东新区张江路100号', 2, 3, '待分波', NULL, NULL, '加急', '2026-10-15 07:30:00', NULL, NULL),
(2,  'SO20261015002', '京东',   '林芳', '13800001002', '北京市朝阳区建国路88号',   1, 1, '待分波', NULL, NULL, '普通', '2026-10-15 07:35:00', NULL, NULL),
(3,  'SO20261015003', '抖音',   '黄强', '13800001003', '广州市天河区体育西路5号',  2, 3, '待分波', NULL, NULL, '普通', '2026-10-15 07:40:00', NULL, NULL),
(4,  'SO20261015004', '拼多多', '周敏', '13800001004', '成都市武侯区天府大道1号',  1, 3, '待分波', NULL, NULL, '普通', '2026-10-15 07:45:00', NULL, NULL),
(5,  'SO20261015005', '天猫',   '吴磊', '13800001005', '杭州市西湖区文三路20号',   2, 3, '待分波', NULL, NULL, '加急', '2026-10-15 07:50:00', NULL, NULL),
(6,  'SO20261015006', '京东',   '郑爽', '13800001006', '南京市鼓楼区中山路30号',   1, 1, '待分波', NULL, NULL, '普通', '2026-10-15 07:55:00', NULL, NULL),
(7,  'SO20261015007', '抖音',   '孙丽', '13800001007', '武汉市洪山区珞喻路10号',   1, 1, '待分波', NULL, NULL, '普通', '2026-10-15 08:00:00', NULL, NULL),
(8,  'SO20261015008', '拼多多', '马超', '13800001008', '西安市雁塔区科技路5号',    2, 3, '待分波', NULL, NULL, '普通', '2026-10-15 08:05:00', NULL, NULL),
(9,  'SO20261015009', '天猫',   '朱婷', '13800001009', '苏州市工业园区星湖街8号',  1, 1, '待分波', NULL, NULL, '普通', '2026-10-15 08:10:00', NULL, NULL),
(10, 'SO20261015010', '京东',   '胡军', '13800001010', '重庆市渝中区解放碑1号',    2, 3, '待分波', NULL, NULL, '普通', '2026-10-15 08:15:00', NULL, NULL),
(11, 'SO20261015011', '天猫',   '高原', '13800001011', '天津市和平区南京路50号',   2, 2, '已分波', 4, 'W20261015004', '普通', '2026-10-15 08:20:00', '2026-10-15 08:40:00', NULL),
(12, 'SO20261015012', '京东',   '何洁', '13800001012', '青岛市市南区香港路3号',    1, 2, '已分波', 4, 'W20261015004', '普通', '2026-10-15 08:22:00', '2026-10-15 08:40:00', NULL),
(13, 'SO20261015013', '抖音',   '罗宾', '13800001013', '厦门市思明区厦禾路9号',    2, 2, '拣货中', 5, 'W20261015005', '普通', '2026-10-15 08:25:00', '2026-10-15 08:45:00', NULL),
(14, 'SO20261015014', '拼多多', '谢娜', '13800001014', '长沙市岳麓区麓山南路2号',  1, 1, '拣货中', 5, 'W20261015005', '普通', '2026-10-15 08:26:00', '2026-10-15 08:45:00', NULL),
(15, 'SO20261015015', '天猫',   '邓超', '13800001015', '郑州市金水区花园路12号',   1, 2, '已拣货', 6, 'W20261015006', '普通', '2026-10-15 08:28:00', '2026-10-15 09:00:00', NULL),
(16, 'SO20261015016', '京东',   '范冰', '13800001016', '合肥市蜀山区长江西路6号',  2, 2, '已拣货', 6, 'W20261015006', '普通', '2026-10-15 08:30:00', '2026-10-15 09:00:00', NULL),
(17, 'SO20261015017', '抖音',   '杨洋', '13800001017', '济南市历下区泉城路7号',    2, 2, '已复核', 7, 'W20261015007', '普通', '2026-10-15 09:50:00', '2026-10-15 10:00:00', NULL),
(18, 'SO20261015018', '拼多多', '刘涛', '13800001018', '沈阳市和平区太原街4号',    1, 2, '已复核', 7, 'W20261015007', '普通', '2026-10-15 09:52:00', '2026-10-15 10:00:00', NULL),
(19, 'SO20261010019', '天猫',   '李娜', '13800001019', '上海市徐汇区漕溪路11号',   2, 3, '已出库', 1, 'W20261010001', '普通', '2026-10-10 08:30:00', '2026-10-10 09:00:00', '2026-10-10 10:10:00'),
(20, 'SO20261010020', '京东',   '王强', '13800001020', '北京市海淀区中关村1号',    1, 1, '已出库', 1, 'W20261010001', '普通', '2026-10-10 08:32:00', '2026-10-10 09:00:00', '2026-10-10 10:10:00'),
(21, 'SO20261011021', '抖音',   '张伟', '13800001021', '广州市白云区机场路2号',    1, 2, '已出库', 2, 'W20261011002', '普通', '2026-10-11 08:30:00', '2026-10-11 09:10:00', '2026-10-11 10:30:00'),
(22, 'SO20261011022', '拼多多', '刘洋', '13800001022', '成都市锦江区春熙路3号',    2, 2, '已出库', 2, 'W20261011002', '普通', '2026-10-11 08:35:00', '2026-10-11 09:10:00', '2026-10-11 10:30:00'),
(23, 'SO20261012023', '天猫',   '陈静', '13800001023', '杭州市滨江区江南大道4号',  1, 1, '已出库', 3, 'W20261012003', '普通', '2026-10-12 13:30:00', '2026-10-12 14:00:00', '2026-10-12 15:05:00'),
(24, 'SO20261012024', '京东',   '赵磊', '13800001024', '南京市玄武区珠江路5号',    2, 2, '已出库', 3, 'W20261012003', '普通', '2026-10-12 13:35:00', '2026-10-12 14:00:00', '2026-10-12 15:05:00'),
(25, 'SO20261014025', '抖音',   '钱进', '13800001025', '武汉市江汉区解放大道6号',  1, 1, '已取消', NULL, NULL, '普通', '2026-10-14 16:00:00', NULL, NULL),
(26, 'SO20261014026', '拼多多', '孙武', '13800001026', '西安市碑林区南大街7号',    1, 1, '已取消', NULL, NULL, '普通', '2026-10-14 16:10:00', NULL, NULL);

-- 订单明细
INSERT IGNORE INTO order_items(id, order_id, sku_id, sku_code, sku_name, location, qty, picked_qty, shortage_qty, status) VALUES
(1,  1,  1,  'SKU10001', '无线蓝牙耳机', 'A-01-01', 2, 0, 0, '待拣'),
(2,  1,  7,  'SKU10007', '机械键盘',     'B-01-01', 1, 0, 0, '待拣'),
(3,  2,  2,  'SKU10002', '手机保护壳',   'A-01-02', 1, 0, 0, '待拣'),
(4,  3,  5,  'SKU10005', '智能手环',     'A-02-02', 1, 0, 0, '待拣'),
(5,  3,  14, 'SKU10014', '运动水壶',     'D-01-01', 2, 0, 0, '待拣'),
(6,  4,  10, 'SKU10010', '保温杯',       'C-01-01', 3, 0, 0, '待拣'),
(7,  5,  3,  'SKU10003', '快充数据线',   'A-01-03', 2, 0, 0, '待拣'),
(8,  5,  8,  'SKU10008', '无线鼠标',     'B-01-02', 1, 0, 0, '待拣'),
(9,  6,  13, 'SKU10013', '抽纸',         'C-02-01', 1, 0, 0, '待拣'),
(10, 7,  4,  'SKU10004', '便携充电宝',   'A-02-01', 1, 0, 0, '待拣'),
(11, 8,  11, 'SKU10011', '收纳盒',       'C-01-02', 2, 0, 0, '待拣'),
(12, 8,  12, 'SKU10012', '洗衣液',       'C-01-03', 1, 0, 0, '待拣'),
(13, 9,  15, 'SKU10015', '瑜伽垫',       'D-01-02', 1, 0, 0, '待拣'),
(14, 10, 6,  'SKU10006', '蓝牙音箱',     'A-02-03', 1, 0, 0, '待拣'),
(15, 10, 16, 'SKU10016', '折叠雨伞',     'D-01-03', 2, 0, 0, '待拣'),
(16, 11, 1,  'SKU10001', '无线蓝牙耳机', 'A-01-01', 1, 0, 0, '待拣'),
(17, 11, 3,  'SKU10003', '快充数据线',   'A-01-03', 1, 0, 0, '待拣'),
(18, 12, 1,  'SKU10001', '无线蓝牙耳机', 'A-01-01', 2, 0, 0, '待拣'),
(19, 13, 7,  'SKU10007', '机械键盘',     'B-01-01', 1, 0, 0, '待拣'),
(20, 13, 8,  'SKU10008', '无线鼠标',     'B-01-02', 1, 0, 0, '待拣'),
(21, 14, 7,  'SKU10007', '机械键盘',     'B-01-01', 1, 0, 0, '待拣'),
(22, 15, 10, 'SKU10010', '保温杯',       'C-01-01', 2, 2, 0, '已拣'),
(23, 16, 10, 'SKU10010', '保温杯',       'C-01-01', 1, 1, 0, '已拣'),
(24, 16, 11, 'SKU10011', '收纳盒',       'C-01-02', 1, 1, 0, '已拣'),
(25, 17, 5,  'SKU10005', '智能手环',     'A-02-02', 1, 1, 0, '已拣'),
(26, 17, 6,  'SKU10006', '蓝牙音箱',     'A-02-03', 1, 1, 0, '已拣'),
(27, 18, 5,  'SKU10005', '智能手环',     'A-02-02', 2, 2, 0, '已拣'),
(28, 19, 2,  'SKU10002', '手机保护壳',   'A-01-02', 2, 2, 0, '已拣'),
(29, 19, 3,  'SKU10003', '快充数据线',   'A-01-03', 1, 1, 0, '已拣'),
(30, 20, 2,  'SKU10002', '手机保护壳',   'A-01-02', 1, 1, 0, '已拣'),
(31, 21, 13, 'SKU10013', '抽纸',         'C-02-01', 2, 2, 0, '已拣'),
(32, 22, 13, 'SKU10013', '抽纸',         'C-02-01', 1, 1, 0, '已拣'),
(33, 22, 14, 'SKU10014', '运动水壶',     'D-01-01', 1, 0, 1, '缺货'),
(34, 23, 9,  'SKU10009', '显示器支架',   'B-01-03', 1, 1, 0, '已拣'),
(35, 24, 9,  'SKU10009', '显示器支架',   'B-01-03', 1, 1, 0, '已拣'),
(36, 24, 15, 'SKU10015', '瑜伽垫',       'D-01-02', 1, 1, 0, '已拣'),
(37, 25, 4,  'SKU10004', '便携充电宝',   'A-02-01', 1, 0, 0, '待拣'),
(38, 26, 16, 'SKU10016', '折叠雨伞',     'D-01-03', 1, 0, 0, '待拣');

-- 波次拣货明细
INSERT IGNORE INTO wave_items(id, wave_id, sku_id, sku_code, sku_name, location, required_qty, picked_qty, shortage_qty, status) VALUES
(1,  1, 2,  'SKU10002', '手机保护壳', 'A-01-02', 3, 3, 0, '已拣'),
(2,  1, 3,  'SKU10003', '快充数据线', 'A-01-03', 1, 1, 0, '已拣'),
(3,  2, 13, 'SKU10013', '抽纸',       'C-02-01', 3, 3, 0, '已拣'),
(4,  2, 14, 'SKU10014', '运动水壶',   'D-01-01', 1, 0, 1, '缺货'),
(5,  3, 9,  'SKU10009', '显示器支架', 'B-01-03', 2, 2, 0, '已拣'),
(6,  3, 15, 'SKU10015', '瑜伽垫',     'D-01-02', 1, 1, 0, '已拣'),
(7,  4, 1,  'SKU10001', '无线蓝牙耳机','A-01-01', 3, 0, 0, '待拣'),
(8,  4, 3,  'SKU10003', '快充数据线', 'A-01-03', 1, 0, 0, '待拣'),
(9,  5, 7,  'SKU10007', '机械键盘',   'B-01-01', 2, 1, 0, '待拣'),
(10, 5, 8,  'SKU10008', '无线鼠标',   'B-01-02', 1, 0, 1, '缺货'),
(11, 6, 10, 'SKU10010', '保温杯',     'C-01-01', 3, 3, 0, '已拣'),
(12, 6, 11, 'SKU10011', '收纳盒',     'C-01-02', 1, 1, 0, '已拣'),
(13, 7, 5,  'SKU10005', '智能手环',   'A-02-02', 3, 3, 0, '已拣'),
(14, 7, 6,  'SKU10006', '蓝牙音箱',   'A-02-03', 1, 1, 0, '已拣');

-- 缺货记录
INSERT IGNORE INTO shortages(id, wave_id, wave_no, sku_id, sku_code, sku_name, location, required_qty, picked_qty, short_qty, reason, reporter_id, reporter_name, status, handle_remark, create_time, handle_time) VALUES
(1, 1, 'W20261010001', 3,  'SKU10003', '快充数据线', 'A-01-03', 1, 0, 1, '商品破损', 3, '张拣（拣货员）', '已补货', '已从备用库位补货', '2026-10-10 09:20:00', '2026-10-10 09:30:00'),
(2, 2, 'W20261011002', 14, 'SKU10014', '运动水壶',   'D-01-01', 1, 0, 1, '库存不足', 4, '王拣（拣货员）', '已核减', '库存不足，已核减订单', '2026-10-11 09:30:00', '2026-10-11 09:55:00'),
(3, 5, 'W20261015005', 8,  'SKU10008', '无线鼠标',   'B-01-02', 1, 0, 1, '库位缺货', 4, '王拣（拣货员）', '待处理', NULL, '2026-10-15 09:10:00', NULL);

-- 复核记录（1-3 已出库波次 + 7 待出库波次）
INSERT IGNORE INTO review_records(id, wave_id, wave_no, order_id, order_no, checker_id, checker_name, result, pack_count, error_count, remark, create_time) VALUES
(1, 1, 'W20261010001', 19, 'SO20261010019', 5, '赵核（复核员）', '通过', 1, 0, '包装完好', '2026-10-10 09:40:00'),
(2, 1, 'W20261010001', 20, 'SO20261010020', 5, '赵核（复核员）', '通过', 1, 0, '包装完好', '2026-10-10 09:44:00'),
(3, 2, 'W20261011002', 21, 'SO20261011021', 6, '孙核（复核员）', '通过', 1, 0, '包装完好', '2026-10-11 09:58:00'),
(4, 2, 'W20261011002', 22, 'SO20261011022', 6, '孙核（复核员）', '异常', 1, 1, '运动水壶漏拣，已补拣', '2026-10-11 10:03:00'),
(5, 3, 'W20261012003', 23, 'SO20261012023', 5, '赵核（复核员）', '通过', 1, 0, '包装完好', '2026-10-12 14:36:00'),
(6, 3, 'W20261012003', 24, 'SO20261012024', 5, '赵核（复核员）', '异常', 1, 1, '瑜伽垫多拣1件，已退回', '2026-10-12 14:39:00'),
(7, 7, 'W20261015007', 17, 'SO20261015017', 5, '赵核（复核员）', '通过', 1, 0, '包装完好', '2026-10-15 10:45:00'),
(8, 7, 'W20261015007', 18, 'SO20261015018', 5, '赵核（复核员）', '通过', 1, 0, '包装完好', '2026-10-15 10:49:00');

-- 差错记录
INSERT IGNORE INTO error_records(id, wave_id, wave_no, order_id, order_no, sku_id, sku_code, sku_name, error_type, qty, checker_id, checker_name, description, create_time) VALUES
(1, 2, 'W20261011002', 22, 'SO20261011022', 14, 'SKU10014', '运动水壶', '漏拣', 1, 6, '孙核（复核员）', '运动水壶未放入包裹，已补拣', '2026-10-11 10:03:00'),
(2, 3, 'W20261012003', 24, 'SO20261012024', 15, 'SKU10015', '瑜伽垫',   '多拣', 1, 5, '赵核（复核员）', '瑜伽垫多放1件，已退回', '2026-10-12 14:39:00');

-- 出库交接记录
INSERT IGNORE INTO outbounds(id, wave_id, wave_no, order_count, total_qty, carrier, handover_to, operator_id, operator_name, status, remark, create_time, handover_time) VALUES
(1, 1, 'W20261010001', 2, 4, '顺丰速运', '陈师傅', 5, '赵核（复核员）', '已交接', '交接正常', '2026-10-10 10:10:00', '2026-10-10 10:10:00'),
(2, 2, 'W20261011002', 2, 3, '京东物流', '刘师傅', 6, '孙核（复核员）', '已交接', '含缺货核减', '2026-10-11 10:30:00', '2026-10-11 10:30:00'),
(3, 3, 'W20261012003', 2, 3, '中通快递', '周师傅', 5, '赵核（复核员）', '已交接', '交接正常', '2026-10-12 15:05:00', '2026-10-12 15:05:00');
