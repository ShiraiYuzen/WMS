package com.example.wms.service;

import com.example.wms.dao.ErrorDao;
import com.example.wms.dao.OrderDao;
import com.example.wms.dao.OrderItemDao;
import com.example.wms.dao.ReviewDao;
import com.example.wms.dao.UserDao;
import com.example.wms.dao.WaveDao;
import com.example.wms.dto.ReviewSubmitRequest;
import com.example.wms.entity.ErrorRecord;
import com.example.wms.entity.Order;
import com.example.wms.entity.ReviewRecord;
import com.example.wms.entity.Sku;
import com.example.wms.dao.SkuDao;
import com.example.wms.entity.User;
import com.example.wms.entity.Wave;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReviewService {

    private final WaveDao waveDao;
    private final OrderDao orderDao;
    private final OrderItemDao orderItemDao;
    private final ReviewDao reviewDao;
    private final ErrorDao errorDao;
    private final SkuDao skuDao;
    private final UserDao userDao;

    public ReviewService(WaveDao waveDao, OrderDao orderDao, OrderItemDao orderItemDao, ReviewDao reviewDao,
                         ErrorDao errorDao, SkuDao skuDao, UserDao userDao) {
        this.waveDao = waveDao;
        this.orderDao = orderDao;
        this.orderItemDao = orderItemDao;
        this.reviewDao = reviewDao;
        this.errorDao = errorDao;
        this.skuDao = skuDao;
        this.userDao = userDao;
    }

    public Map<String, Object> detail(Long waveId) {
        Wave wave = waveDao.findById(waveId);
        if (wave == null) {
            throw new IllegalArgumentException("波次不存在");
        }
        List<Order> orders = orderDao.findByWaveId(waveId);
        List<Map<String, Object>> orderList = new ArrayList<>();
        for (Order o : orders) {
            Map<String, Object> m = new HashMap<>();
            m.put("order", o);
            m.put("items", orderItemDao.findByOrderId(o.getId()));
            orderList.add(m);
        }
        Map<String, Object> map = new HashMap<>();
        map.put("wave", wave);
        map.put("orders", orderList);
        map.put("reviews", reviewDao.findByWaveId(waveId));
        return map;
    }

    @Transactional
    public void submit(ReviewSubmitRequest req, String username) {
        if (req == null || req.getWaveId() == null) {
            throw new IllegalArgumentException("缺少波次 ID");
        }
        Wave wave = waveDao.findById(req.getWaveId());
        if (wave == null) {
            throw new IllegalArgumentException("波次不存在");
        }
        if (!"待复核".equals(wave.getStatus())) {
            throw new IllegalArgumentException("仅「待复核」波次可提交复核，当前状态：" + wave.getStatus());
        }
        if (req.getReviews() == null || req.getReviews().isEmpty()) {
            throw new IllegalArgumentException("请至少复核一个订单");
        }
        User checker = userDao.findByUsername(username);
        if (checker != null) {
            waveDao.assignChecker(wave.getId(), checker.getId(), checker.getNickname());
        }

        for (ReviewSubmitRequest.OrderReview or : req.getReviews()) {
            Order order = orderDao.findById(or.getOrderId());
            if (order == null) {
                continue;
            }
            int errorCount = or.getErrors() == null ? 0 : or.getErrors().size();
            String result = (errorCount > 0 || "异常".equals(or.getResult())) ? "异常" : "通过";

            ReviewRecord rr = new ReviewRecord();
            rr.setWaveId(wave.getId());
            rr.setWaveNo(wave.getWaveNo());
            rr.setOrderId(order.getId());
            rr.setOrderNo(order.getOrderNo());
            if (checker != null) {
                rr.setCheckerId(checker.getId());
                rr.setCheckerName(checker.getNickname());
            }
            rr.setResult(result);
            rr.setPackCount(or.getPackCount() == null ? order.getItemCount() : or.getPackCount());
            rr.setErrorCount(errorCount);
            rr.setRemark(or.getRemark());
            reviewDao.insert(rr);

            if (or.getErrors() != null) {
                for (ReviewSubmitRequest.ErrorLine el : or.getErrors()) {
                    Sku sku = el.getSkuCode() == null ? null : skuDao.findByCode(el.getSkuCode());
                    ErrorRecord er = new ErrorRecord();
                    er.setWaveId(wave.getId());
                    er.setWaveNo(wave.getWaveNo());
                    er.setOrderId(order.getId());
                    er.setOrderNo(order.getOrderNo());
                    if (sku != null) {
                        er.setSkuId(sku.getId());
                        er.setSkuCode(sku.getSkuCode());
                        er.setSkuName(sku.getName());
                    } else {
                        er.setSkuCode(el.getSkuCode());
                    }
                    er.setErrorType(el.getErrorType() == null ? "其他" : el.getErrorType());
                    er.setQty(el.getQty() == null ? 1 : el.getQty());
                    if (checker != null) {
                        er.setCheckerId(checker.getId());
                        er.setCheckerName(checker.getNickname());
                    }
                    er.setDescription(el.getDescription());
                    errorDao.insert(er);
                }
            }
        }

        waveDao.markReviewFinish(wave.getId());
        List<Order> orders = orderDao.findByWaveId(wave.getId());
        orderDao.updateStatusByIds(orders.stream().map(Order::getId).toList(), "已复核");
    }
}
