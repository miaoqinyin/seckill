package cn.wolfcode.web.controller;

import cn.wolfcode.common.domain.UserInfo;
import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.common.web.anno.RequireLogin;
import cn.wolfcode.domain.OrderInfo;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.mq.MQConstant;
import cn.wolfcode.mq.OrderMessage;
import cn.wolfcode.redis.CommonRedisKey;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.IOrderInfoService;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.util.DateUtil;
import cn.wolfcode.util.UserUtil;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@RestController
@RequestMapping("/order")
@Slf4j
public class OrderInfoController {
    @Autowired
    private ISeckillProductService seckillProductService;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private IOrderInfoService orderInfoService;

    public final static Map<Long,Boolean> local_flag =  new ConcurrentHashMap<>();
    @RequestMapping("/doSeckill")
    @RequireLogin
    public Result<String> doSeckill(int time, Long seckillId, HttpServletRequest request){
        //进行判空处理
       if(StringUtils.isEmpty(time+"") || StringUtils.isEmpty(seckillId)){
           throw new BusinessException(SeckillCodeMsg.SECKILL_OPERATION_ERROR);
       }
        Boolean flag = local_flag.get(seckillId);
       if(flag != null && flag){
           throw new BusinessException(SeckillCodeMsg.SECKILL_STOCK_OVER);
       }
        //判断活动是否在秒杀时间内
        SeckillProductVo seckillProductVo = seckillProductService.find(time, seckillId);
       /*if(!DateUtil.isLegalTime(seckillProductVo.getStartDate(),time)){
            throw  new BusinessException(SeckillCodeMsg.SECKILL_TIME_ERROR);
        }*/
        //判断是否重复下单
        String token = request.getHeader("token");
        UserInfo userInfo= UserUtil.getUser(redisTemplate, token);

      /*  Boolean ret = redisTemplate.opsForSet().isMember(SeckillRedisKey.SECKILL_ORDER_SET.getRealKey(time + ""), userInfo.getPhone() + ":" + seckillId);
        if(ret){
            throw new BusinessException(SeckillCodeMsg.REPEAT_SECKILL);
        }*/

        //每次秒杀,库存都-1,小于0就改变标识为true;
        Long count = redisTemplate.opsForHash().increment(SeckillRedisKey.SECKILL_STOCK_COUNT_HASH.getRealKey(time + ""), seckillId + "", -1);
        if(count == null && count < 0){
            local_flag.put(seckillId,true);
            throw new BusinessException(SeckillCodeMsg.SECKILL_STOCK_OVER);
        }

        //判断库存是否足够
     if(seckillProductVo.getStockCount() <= 0){
         throw new BusinessException(SeckillCodeMsg.SECKILL_STOCK_OVER);
     }
      //执行秒杀逻辑
      //String orderNum = orderInfoService.doSeckill(seckillId,time,userInfo.getPhone());
     //封装数据
     OrderMessage orderMessage = new OrderMessage();
     orderMessage.setSeckillId(seckillId);
     orderMessage.setTime(time);
     orderMessage.setToken(token);
     orderMessage.setUserPhone(userInfo.getPhone());

     rocketMQTemplate.syncSend(MQConstant.ORDER_PEDDING_TOPIC,orderMessage);
     return Result.success("正在秒杀中,请稍后!");
    }

    @RequestMapping("/find")
    public Result<OrderInfo> find(String orderNo,HttpServletRequest request){

        String token = request.getHeader("token");
        UserInfo user = UserUtil.getUser(redisTemplate, token);
        OrderInfo orderInfo = orderInfoService.find(orderNo,user.getPhone());
     return Result.success(orderInfo);
    }

}
