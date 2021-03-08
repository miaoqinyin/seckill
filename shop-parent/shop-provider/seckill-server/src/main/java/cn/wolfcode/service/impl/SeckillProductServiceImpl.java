package cn.wolfcode.service.impl;

import cn.wolfcode.common.exception.BusinessException;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.Product;
import cn.wolfcode.domain.SeckillProduct;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.feign.ProductApi;
import cn.wolfcode.mapper.SeckillProductMapper;
import cn.wolfcode.redis.SeckillRedisKey;
import cn.wolfcode.service.ISeckillProductService;
import cn.wolfcode.web.msg.SeckillCodeMsg;
import com.alibaba.fastjson.JSON;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SeckillProductServiceImpl implements ISeckillProductService {
    @Autowired
    private SeckillProductMapper seckillProductMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    @Autowired
    private ProductApi productApi;

    @Override
    public List<SeckillProductVo> listByTime(int time) {
        List<SeckillProduct> seckillProducts = seckillProductMapper.queryCurrentlySeckillProduct(time);

        List<Long> ids = new ArrayList<>();
        for (SeckillProduct seckillProduct : seckillProducts) {
            ids.add(seckillProduct.getProductId());
        }
        //获取product集合
        Result<List<Product>> result = productApi.getByIds(ids);
        //数据判空处理
        if(result == null || result.hasError()){
            throw new BusinessException(SeckillCodeMsg.SECKILL_PRODUCT_ERROR);
        }
        List<Product> products = result.getData();
        Map<Long, Product> map = new HashMap<>();
        for (Product product : products) {
            map.put(product.getId(),product);
        }
        List<SeckillProductVo> seckillProductVos = new ArrayList<>();
        for (SeckillProduct seckillProduct : seckillProducts) {
            SeckillProductVo vo = new SeckillProductVo();

            BeanUtils.copyProperties(map.get(seckillProduct.getProductId()),vo);
            BeanUtils.copyProperties(seckillProduct,vo);
            seckillProductVos.add(vo);
        }
        return seckillProductVos;
    }

    @Override
    public List<SeckillProductVo> queryByTime(int time) {
        List<Object> values = redisTemplate.opsForHash().values(SeckillRedisKey.SECKILL_PRODUCT_LIST.getRealKey(time + ""));
        ArrayList<SeckillProductVo> seckillProductVos = new ArrayList<>();
        for (Object vo : values) {
            SeckillProductVo seckillProductVo = JSON.parseObject(vo + "", SeckillProductVo.class);
            seckillProductVos.add(seckillProductVo);
        }
        return seckillProductVos;
    }

    @Override
    public SeckillProductVo find(int time, Long seckillId) {
        Object obj = redisTemplate.opsForHash().get(SeckillRedisKey.SECKILL_PRODUCT_LIST.getRealKey(time + ""), seckillId + "");
        if(obj == null){
            throw new BusinessException(SeckillCodeMsg.SECKILL_OPERATION_ERROR);
        }
        SeckillProductVo seckillProductVo = JSON.parseObject(obj + "", SeckillProductVo.class);
        return seckillProductVo;
    }

    @Override
    public int getStockCount(Long seckillId) {
        int count = seckillProductMapper.getStockCount(seckillId);
        return count;
    }

    @Override
    public int decrStock(Long seckillId) {
      return seckillProductMapper.decrStock(seckillId);
    }

    @Override
    public void incrStock(Long seckillId) {
        seckillProductMapper.incrStock(seckillId);
    }

    @Override
    public void updateRedisCount(Integer time, Long seckillId) {
        int count = seckillProductMapper.getStockCount(seckillId);
        redisTemplate.opsForHash().put(SeckillRedisKey.SECKILL_STOCK_COUNT_HASH.getRealKey(time+""),seckillId+"",count+"");
    }
}
