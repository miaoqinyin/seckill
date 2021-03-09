package cn.wolfcode.mq;

import cn.wolfcode.service.ISeckillProductService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.annotation.SelectorType;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
        consumerGroup = "MQOrderFailListener",
        topic = MQConstant.ORDER_RESULT_TOPIC,
        selectorExpression = MQConstant.ORDER_RESULT_FAIL_TAG
)
public class MQOrderFailListener implements RocketMQListener<OrderMQResult> {

    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Override
    public void onMessage(OrderMQResult orderMQResult) {
        log.info("进入失败回补消息队列");
        //回补预库存
        seckillProductService.updateRedisCount(orderMQResult.getTime(),orderMQResult.getSeckillId());
        //修改本地标识
        rocketMQTemplate.syncSend(MQConstant.CANCEL_SECKILL_OVER_SIGE_TOPIC,orderMQResult.getSeckillId());
    }
}
