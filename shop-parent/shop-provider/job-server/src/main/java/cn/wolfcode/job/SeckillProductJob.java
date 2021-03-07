package cn.wolfcode.job;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.feign.SeckillProductApi;
import cn.wolfcode.redis.SeckillRedisKey;
import com.alibaba.fastjson.JSON;
import com.dangdang.ddframe.job.api.ShardingContext;
import com.dangdang.ddframe.job.api.simple.SimpleJob;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Setter
@Getter
@Slf4j
public class SeckillProductJob implements SimpleJob {
    @Value("${jobCron.initSeckillProduct}")
    private String cron;
     @Autowired
     private SeckillProductApi seckillProductApi;
     @Autowired
     private StringRedisTemplate template;

    @Override
    public void execute(ShardingContext shardingContext) {
       doWork(shardingContext.getShardingParameter());
    }

    private void doWork(String time) {
        //需要我们远程调用秒杀服务,获取seckillproductVo,所以我们需要使用feign来进行远程调用
        Result<List<SeckillProductVo>> result = seckillProductApi.productList(Integer.parseInt(time));
        /**
         * result 有几种情况
         * 如果调用秒杀服务挂了,直接降级  result = null
         * retulst没挂
         * 正常  sucess
         * 失败 code:5020x msg:"错误信息"
         */
        //获取vos集合
        //每次存秒杀商品前,都清除以前添加的
        template.delete(SeckillRedisKey.SECKILL_PRODUCT_LIST.getRealKey(time+""));
        if(result != null && !result.hasError()){
            List<SeckillProductVo> seckillProductVos = result.getData();
           if(seckillProductVos != null || seckillProductVos.size()>0){
               for (SeckillProductVo seckillProductVo : seckillProductVos) {
                   template.opsForHash().put(SeckillRedisKey.SECKILL_PRODUCT_LIST.getRealKey(time+""),
                           seckillProductVo.getId()+"",JSON.toJSONString(seckillProductVo));
                   template.opsForHash().put(SeckillRedisKey.SECKILL_STOCK_COUNT_HASH.getRealKey(time),seckillProductVo.getId()+"",seckillProductVo.getStockCount()+"");
               }
           }
        }
        log.info("成功添加到redis中"+"=============================");
    }
}
