-- WMS v4 表结构（幂等：IF NOT EXISTS）。数据见同目录 seed.sql
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 表结构。数据在 seed.sql（数据库快照导出）

CREATE TABLE IF NOT EXISTS carrier (id BIGINT AUTO_INCREMENT PRIMARY KEY, carrier_code VARCHAR(32) NOT NULL, carrier_name VARCHAR(64) NOT NULL, contact_name VARCHAR(32), contact_phone VARCHAR(32), daily_pickup_time VARCHAR(16), status TINYINT DEFAULT 1, create_time VARCHAR(19), UNIQUE KEY uk_carrier_code (carrier_code));

CREATE TABLE IF NOT EXISTS employee (id BIGINT AUTO_INCREMENT PRIMARY KEY, employee_no VARCHAR(32) NOT NULL, employee_name VARCHAR(32) NOT NULL, role VARCHAR(32) NOT NULL, team VARCHAR(32), phone VARCHAR(32), status TINYINT DEFAULT 1, create_time VARCHAR(19), UNIQUE KEY uk_employee_no (employee_no));

CREATE TABLE IF NOT EXISTS location (id BIGINT AUTO_INCREMENT PRIMARY KEY, location_code VARCHAR(32) NOT NULL, zone VARCHAR(8), zone_name VARCHAR(32), aisle VARCHAR(8), rack VARCHAR(8), level VARCHAR(8), location_type VARCHAR(16), status VARCHAR(16), preferred_carrier_code VARCHAR(32), path_seq INT, UNIQUE KEY uk_location_code (location_code));

CREATE TABLE IF NOT EXISTS sku (id BIGINT AUTO_INCREMENT PRIMARY KEY, sku_code VARCHAR(32) NOT NULL, sku_name VARCHAR(128) NOT NULL, spec VARCHAR(32), unit VARCHAR(8), category VARCHAR(32), barcode VARCHAR(32), default_location_code VARCHAR(32), weight DECIMAL(10,2), safety_stock INT, stock_qty INT, status TINYINT DEFAULT 1, UNIQUE KEY uk_sku_code (sku_code));

CREATE TABLE IF NOT EXISTS `order` (id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR(32) NOT NULL, platform VARCHAR(32), carrier_code VARCHAR(32), carrier_name VARCHAR(64), receiver_name VARCHAR(32), receiver_phone VARCHAR(32), receiver_address VARCHAR(128), province VARCHAR(32), city VARCHAR(32), priority VARCHAR(16), pay_time VARCHAR(19), expected_delivery_time VARCHAR(19), status VARCHAR(16), wave_no VARCHAR(32), sku_count INT, total_qty INT, amount DECIMAL(12,2), buyer_remark VARCHAR(255), create_time VARCHAR(19), UNIQUE KEY uk_order_no (order_no));

CREATE TABLE IF NOT EXISTS order_item (id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR(32) NOT NULL, line_no INT, sku_code VARCHAR(32), sku_name VARCHAR(128), spec VARCHAR(32), unit VARCHAR(8), qty INT, picked_qty INT DEFAULT 0, location_code VARCHAR(32), price DECIMAL(12,2), amount DECIMAL(12,2), remark VARCHAR(255));

CREATE TABLE IF NOT EXISTS wave (id BIGINT AUTO_INCREMENT PRIMARY KEY, wave_no VARCHAR(32) NOT NULL, wave_name VARCHAR(128), strategy VARCHAR(32), status VARCHAR(16), order_count INT, sku_count INT, total_qty INT, location_count INT, province_scope VARCHAR(64), creator_no VARCHAR(32), creator_name VARCHAR(32), picker_no VARCHAR(32), picker_name VARCHAR(32), reviewer_no VARCHAR(32), reviewer_name VARCHAR(32), create_time VARCHAR(19), assign_time VARCHAR(19), pick_start_time VARCHAR(19), pick_finish_time VARCHAR(19), review_finish_time VARCHAR(19), handover_time VARCHAR(19), pick_duration_sec INT, total_duration_sec INT, has_shortage TINYINT DEFAULT 0, remark VARCHAR(255), UNIQUE KEY uk_wave_no (wave_no));

CREATE TABLE IF NOT EXISTS wave_order (id BIGINT AUTO_INCREMENT PRIMARY KEY, wave_no VARCHAR(32) NOT NULL, order_no VARCHAR(32) NOT NULL, seq_no INT);

CREATE TABLE IF NOT EXISTS pick_task (id BIGINT AUTO_INCREMENT PRIMARY KEY, task_no VARCHAR(32) NOT NULL, wave_no VARCHAR(32), picker_no VARCHAR(32), picker_name VARCHAR(32), status VARCHAR(16), item_count INT, finished_count INT, location_count INT, should_qty INT, actual_qty INT, shortage_qty INT, order_count INT, start_time VARCHAR(19), finish_time VARCHAR(19), duration_sec INT, path_text VARCHAR(512), remark VARCHAR(255), create_time VARCHAR(19), UNIQUE KEY uk_task_no (task_no));

CREATE TABLE IF NOT EXISTS pick_item (id BIGINT AUTO_INCREMENT PRIMARY KEY, task_no VARCHAR(32) NOT NULL, wave_no VARCHAR(32), seq_no INT, location_code VARCHAR(32), sku_code VARCHAR(32), sku_name VARCHAR(128), spec VARCHAR(32), unit VARCHAR(8), should_qty INT, actual_qty INT DEFAULT 0, shortage_qty INT DEFAULT 0, status VARCHAR(16), related_order_nos VARCHAR(512), scan_time VARCHAR(19), remark VARCHAR(255));

CREATE TABLE IF NOT EXISTS shortage (id BIGINT AUTO_INCREMENT PRIMARY KEY, shortage_no VARCHAR(32) NOT NULL, wave_no VARCHAR(32), task_no VARCHAR(32), order_no VARCHAR(32), location_code VARCHAR(32), sku_code VARCHAR(32), sku_name VARCHAR(128), should_qty INT, shortage_qty INT, reason VARCHAR(32), reporter_no VARCHAR(32), reporter_name VARCHAR(32), report_time VARCHAR(19), status VARCHAR(16), handle_type VARCHAR(32), handler_no VARCHAR(32), handler_name VARCHAR(32), handle_time VARCHAR(19), handle_remark VARCHAR(255), substitute_sku_code VARCHAR(32), create_time VARCHAR(19), UNIQUE KEY uk_shortage_no (shortage_no));

CREATE TABLE IF NOT EXISTS review (id BIGINT AUTO_INCREMENT PRIMARY KEY, review_no VARCHAR(32) NOT NULL, wave_no VARCHAR(32), order_no VARCHAR(32), reviewer_no VARCHAR(32), reviewer_name VARCHAR(32), status VARCHAR(16), should_sku_count INT, should_qty INT, actual_qty INT, diff_type VARCHAR(16), diff_qty INT, diff_detail VARCHAR(512), pack_material VARCHAR(16), package_count INT, weight DECIMAL(10,2), start_time VARCHAR(19), finish_time VARCHAR(19), duration_sec INT, remark VARCHAR(255), create_time VARCHAR(19), UNIQUE KEY uk_review_no (review_no));

CREATE TABLE IF NOT EXISTS review_diff (id BIGINT AUTO_INCREMENT PRIMARY KEY, review_no VARCHAR(32), wave_no VARCHAR(32), order_no VARCHAR(32), sku_code VARCHAR(32), sku_name VARCHAR(128), diff_type VARCHAR(16), should_qty INT, actual_qty INT, diff_qty INT, responsible_stage VARCHAR(16), responsible_no VARCHAR(32), remark VARCHAR(255), create_time VARCHAR(19));

CREATE TABLE IF NOT EXISTS handover (id BIGINT AUTO_INCREMENT PRIMARY KEY, handover_no VARCHAR(32) NOT NULL, wave_nos VARCHAR(512), carrier_code VARCHAR(32), carrier_name VARCHAR(64), order_count INT, package_count INT, total_weight DECIMAL(12,2), status VARCHAR(16), handover_user_no VARCHAR(32), handover_user_name VARCHAR(32), receive_name VARCHAR(32), vehicle_no VARCHAR(32), create_time VARCHAR(19), handover_time VARCHAR(19), remark VARCHAR(255), UNIQUE KEY uk_handover_no (handover_no));

CREATE TABLE IF NOT EXISTS operation_log (id BIGINT AUTO_INCREMENT PRIMARY KEY, biz_type VARCHAR(32), biz_no VARCHAR(32), action VARCHAR(32), action_desc VARCHAR(64), operator_no VARCHAR(32), operator_name VARCHAR(32), operator_role VARCHAR(32), detail VARCHAR(512), operate_time VARCHAR(19), create_time VARCHAR(19));

CREATE TABLE IF NOT EXISTS sys_user (id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(32) NOT NULL, password VARCHAR(128) NOT NULL, employee_no VARCHAR(32), role VARCHAR(32), nickname VARCHAR(32), create_time VARCHAR(19), UNIQUE KEY uk_username (username));
















SET FOREIGN_KEY_CHECKS = 1;
