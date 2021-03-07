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
}
