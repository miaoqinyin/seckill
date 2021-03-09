package cn.wolfcode.mq;

import cn.wolfcode.config.WebSocketServer;
import com.alibaba.fastjson.JSON;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.io.IOException;


@Component
@RocketMQMessageListener(
        consumerGroup = "MQOrderResultListener",
        topic = MQConstant.ORDER_RESULT_TOPIC
)
@Slf4j
public class MQOrderResultListener implements RocketMQListener<OrderMQResult> {

    @Override
    public void onMessage(OrderMQResult orderMQResult)  {
    log.info("进入到通知队列中..........");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String token = orderMQResult.getToken();
        WebSocketServer webSocketServer = WebSocketServer.clients.get(token);

        if(webSocketServer != null){
            try {
                webSocketServer.getSession().getBasicRemote().sendText(JSON.toJSONString(orderMQResult));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        log.info("成功通知用户!");
    }
}
