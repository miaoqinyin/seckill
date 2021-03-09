package cn.wolfcode.mq;


import cn.wolfcode.service.impl.OrderInfoSeviceImpl;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
        consumerGroup = "MQOrderPeddingListenner",
        topic = MQConstant.ORDER_PEDDING_TOPIC
)
public class MQOrderPeddingListenner implements RocketMQListener<OrderMessage> {

     @Autowired
     private OrderInfoSeviceImpl orderInfoSevice;
     @Autowired
     private RocketMQTemplate rocketMQTemplate;
    @Override
    public void onMessage(OrderMessage orderMessage) {
        OrderMQResult orderMQResult = new OrderMQResult();
        //获取消息里面的数据
        Long seckillId = orderMessage.getSeckillId();
        Integer time = orderMessage.getTime();
        String token = orderMessage.getToken();
        Long userPhone = orderMessage.getUserPhone();
        //封装订单结果的数据
          orderMQResult.setSeckillId(seckillId);
          orderMQResult.setToken(token);
          orderMQResult.setTime(time);

         String tag = "";
        try {
            String orderNo = orderInfoSevice.doSeckill(seckillId, time, userPhone);
            orderMQResult.setOrderNo(orderNo);
            tag=MQConstant.ORDER_RESULT_SUCCESS_TAG;
            //向MQorderTimeoutListener发送消息
            rocketMQTemplate.syncSend(MQConstant.ORDER_PAY_TIMEOUT_TOPIC, MessageBuilder.withPayload(orderMQResult).build(), 5000, 10);


        }catch (Exception e){
            orderMQResult.setCode(SeckillCodeMsg.SECKILL_ERROR.getCode());
            orderMQResult.setMsg(SeckillCodeMsg.SECKILL_ERROR.getMsg());
            tag=MQConstant.ORDER_RESULT_FAIL_TAG;
        }
        rocketMQTemplate.syncSend(MQConstant.ORDER_RESULT_TOPIC+":"+tag,orderMQResult);

    }
}
