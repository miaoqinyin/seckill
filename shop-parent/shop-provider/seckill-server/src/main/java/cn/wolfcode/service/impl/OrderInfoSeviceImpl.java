package cn.wolfcode.service.impl;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.feign.ProductApi;
import cn.wolfcode.mapper.OrderInfoMapper;
import cn.wolfcode.mapper.PayLogMapper;
import cn.wolfcode.mapper.RefundLogMapper;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.util.IdGenerateUtil;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * Created by wolfcode-lanxw
 */
@Service
public class OrderInfoSeviceImpl implements IOrderInfoService {
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private PayLogMapper payLogMapper;
    @Autowired
    private RefundLogMapper refundLogMapper;
    @Autowired
    private ProductApi productApi;

    @Override
    public String doSeckill(Long seckillId, int time, Long phone) {
        //减少真实的库存
        int row = seckillProductService.decrStock(seckillId);
        if(row == 0){
            throw new BusinessException(SeckillCodeMsg.SECKILL_STOCK_OVER);
        }
        //创建订单
     String orderNum = creatOrder(phone,time,seckillId);
        //往redis存数据
        redisTemplate.opsForSet().add(SeckillRedisKey.SECKILL_ORDER_SET.getRealKey(time+""),phone+":"+seckillId);
      return orderNum;
    }

    private String creatOrder(Long phone, int time, Long seckillId) {
        // 1 创建一个订单对象然后把订单对象保存到数据库中
        SeckillProductVo seckillProductVo = seckillProductService.find(time,seckillId);

        OrderInfo orderInfo =  new OrderInfo();
        orderInfo.setCreateDate(new Date());
        orderInfo.setDeliveryAddrId(1L);
        orderInfo.setUserId(phone);
        orderInfo.setProductCount(seckillProductVo.getStockCount());
        orderInfo.setProductImg(seckillProductVo.getProductImg());
        orderInfo.setProductId(seckillProductVo.getProductId());
        orderInfo.setProductPrice(seckillProductVo.getProductPrice());
        orderInfo.setProductName(seckillProductVo.getProductName());
        orderInfo.setIntergral(seckillProductVo.getIntergral());
        orderInfo.setSeckillDate(new Date());
        orderInfo.setSeckillId(seckillProductVo.getId());
        orderInfo.setSeckillTime(time);
        orderInfo.setSeckillPrice(seckillProductVo.getSeckillPrice());
        orderInfo.setOrderNo(IdGenerateUtil.get().nextId()+"");
        orderInfoMapper.insert(orderInfo);

        return orderInfo.getOrderNo();
    }
}
