package cn.wolfcode.mq;

import cn.wolfcode.web.controller.OrderInfoController;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
        consumerGroup = "MQSeckillOverSigeListener",
        topic = MQConstant.CANCEL_SECKILL_OVER_SIGE_TOPIC,
        messageModel = MessageModel.BROADCASTING
)
@Slf4j
public class MQSeckillOverSigeListener implements RocketMQListener<Long> {
    @Override
    public void onMessage(Long seckillId) {
      log.info("进入本地标识修改队列");
        OrderInfoController.local_flag.put(seckillId,false);
    }
}
