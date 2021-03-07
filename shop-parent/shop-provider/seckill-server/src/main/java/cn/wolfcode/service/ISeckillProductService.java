package cn.wolfcode.service;

import cn.wolfcode.domain.SeckillProductVo;

import java.util.List;


public interface ISeckillProductService {
    /**
     * 根据场次查询秒杀商品
     * @param time
     * @return
     */
    List<SeckillProductVo> listByTime(int time);

    /**
     * 根据场次从redis中获取数据
     * @param time
     * @return
     */
    List<SeckillProductVo> queryByTime(int time);

    /**
     * 根据场次和秒杀商品的id查询秒杀商品的详情
     * @param time
     * @param seckillId
     * @return
     */
    SeckillProductVo find(int time, Long seckillId);

    /**
     * 根据秒杀商品获取库存
     * @param seckillId
     * @return
     */
    int getStockCount(Long seckillId);

    /**
     * 对秒杀商品进行-1操作
     * @param seckillId
     * @return
     */

    int decrStock(Long seckillId);
}
