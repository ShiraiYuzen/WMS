package com.example.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.wms.entity.WmsOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper extends BaseMapper<WmsOrder> {
}
