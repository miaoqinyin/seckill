package cn.wolfcode.mq;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.mapper.OrderInfoMapper;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.service.impl.OrderInfoSeviceImpl;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RocketMQMessageListener(
        consumerGroup = "MQOrderTimeoutListener",
        topic = MQConstant.ORDER_PAY_TIMEOUT_TOPIC
)
public class MQOrderTimeoutListener implements RocketMQListener<OrderMQResult> {
    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Override
    public void onMessage(OrderMQResult orderMQResult) {
        log.info("进入到延时消费队列..............");
        //判断订单是否支付
        String orderNo = orderMQResult.getOrderNo();
        OrderInfo orderInfo = orderInfoMapper.find(orderNo);
        //如果没有支付
        if(OrderInfo.STATUS_ARREARAGE.equals(orderInfo.getStatus())){
            //修改订单状态
            int count = orderInfoMapper.updateCancelStatus(orderNo, OrderInfo.STATUS_TIMEOUT);
            if(count == 0){
                throw new BusinessException(SeckillCodeMsg.CANCEL_ORDER_ERROR);
            }
            //回补真实库存
            seckillProductService.incrStock(orderMQResult.getSeckillId());
            //回补预库存
            seckillProductService.updateRedisCount(orderMQResult.getTime(),orderMQResult.getSeckillId());
            //修改本地标识
            rocketMQTemplate.syncSend(MQConstant.CANCEL_SECKILL_OVER_SIGE_TOPIC,orderMQResult.getSeckillId());
        }
        log.info("发送修改本地标识的消息");


    }
}
