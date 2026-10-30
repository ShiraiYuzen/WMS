package com.example.wms.service;

import com.example.wms.dao.ErrorDao;
import com.example.wms.dao.InventoryDao;
import com.example.wms.dao.OrderDao;
import com.example.wms.dao.OutboundDao;
import com.example.wms.dao.ReviewDao;
import com.example.wms.dao.ShortageDao;
import com.example.wms.dao.StatsDao;
import com.example.wms.dao.WaveDao;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class StatsService {

    private final OrderDao orderDao;
    private final WaveDao waveDao;
    private final ReviewDao reviewDao;
    private final ErrorDao errorDao;
    private final ShortageDao shortageDao;
    private final OutboundDao outboundDao;
    private final InventoryDao inventoryDao;
    private final StatsDao statsDao;

    public StatsService(OrderDao orderDao, WaveDao waveDao, ReviewDao reviewDao, ErrorDao errorDao,
                        ShortageDao shortageDao, OutboundDao outboundDao, InventoryDao inventoryDao, StatsDao statsDao) {
        this.orderDao = orderDao;
        this.waveDao = waveDao;
        this.reviewDao = reviewDao;
        this.errorDao = errorDao;
        this.shortageDao = shortageDao;
        this.outboundDao = outboundDao;
        this.inventoryDao = inventoryDao;
        this.statsDao = statsDao;
    }

    /** 工作台总览 */
    public Map<String, Object> dashboard() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderPending", orderDao.countByStatus("待分波"));
        m.put("orderTotal", orderDao.countAll());
        m.put("orderDone", orderDao.countByStatus("已出库"));
        m.put("orderToday", orderDao.countToday());

        m.put("waveTodo", waveDao.countByStatus("待拣货"));
        m.put("wavePicking", waveDao.countByStatus("拣货中"));
        m.put("waveToReview", waveDao.countByStatus("待复核"));
        m.put("waveToOutbound", waveDao.countByStatus("待出库"));
        m.put("waveDone", waveDao.countByStatus("已出库"));
        m.put("waveTotal", waveDao.countAll());
        m.put("waveToday", waveDao.countTodayWaves());

        long reviewTotal = reviewDao.countAll();
        long errorTotal = errorDao.countAll();
        m.put("reviewTotal", reviewTotal);
        m.put("errorTotal", errorTotal);
        m.put("errorRate", rate(errorTotal, reviewTotal));

        m.put("shortageTotal", shortageDao.countAll());
        m.put("shortagePending", shortageDao.countByStatus("待处理"));
        m.put("outboundTotal", outboundDao.countAll());
        m.put("outboundQty", outboundDao.sumOutboundQty());
        m.put("lowStock", inventoryDao.countLowStock(10));

        m.put("platformDist", orderDao.countGroupByPlatform());
        m.put("orderRecent", orderDao.countRecentDays());
        m.put("waveRecent", statsDao.waveRecentDays());
        m.put("waveStatusDist", statsDao.waveStatusDistribution());
        m.put("shortageReason", statsDao.shortageByReason());
        return m;
    }

    /** 效率复盘：拣货员 / 复核员 人效 */
    public Map<String, Object> efficiency() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pickers", statsDao.pickerEfficiency());
        m.put("checkers", statsDao.checkerEfficiency());
        m.put("overall", statsDao.overall());
        return m;
    }

    /** 差错统计 */
    public Map<String, Object> errors() {
        long reviewTotal = reviewDao.countAll();
        long errorTotal = errorDao.countAll();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reviewTotal", reviewTotal);
        m.put("errorTotal", errorTotal);
        m.put("passTotal", reviewDao.countByResult("通过"));
        m.put("abnormalTotal", reviewDao.countByResult("异常"));
        m.put("errorRate", rate(errorTotal, reviewTotal));
        m.put("byType", errorDao.countGroupByType());
        m.put("byPicker", errorDao.countGroupByPicker());
        m.put("recent", errorDao.countRecentDays());
        m.put("shortageByReason", statsDao.shortageByReason());
        return m;
    }

    private double rate(long part, long whole) {
        if (whole <= 0) {
            return 0.0;
        }
        return Math.round(part * 10000.0 / whole) / 100.0;
    }
}
