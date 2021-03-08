package cn.wolfcode.service;


import cn.wolfcode.domain.OrderInfo;

import java.util.Map;

/**
 * Created by wolfcode-lanxw
 */
public interface IOrderInfoService {
    /**
     * 创建订单
     * @param seckillId
     * @param time
     * @param phone
     * @return
     */
    String doSeckill(Long seckillId, int time, Long phone);

    /**
     * 根据订单号查询订单详情
     * @param orderNo 订单号
     * @return
     */
    OrderInfo find(String orderNo,Long phone);
}
