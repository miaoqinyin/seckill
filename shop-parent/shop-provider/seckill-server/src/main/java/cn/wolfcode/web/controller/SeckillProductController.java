package cn.wolfcode.web.controller;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.SeckillProduct;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.service.ISeckillProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/seckillProduct")
@Slf4j
public class SeckillProductController {
    @Autowired
    private ISeckillProductService seckillProductService;
   @RequestMapping("/productList")
    public Result<List<SeckillProductVo>> productList(int time){
      List<SeckillProductVo>   seckillProductVos = seckillProductService.listByTime(time);
      return Result.success(seckillProductVos);
    }

    @RequestMapping("/queryByTime")
    public Result<List<SeckillProductVo>> queryByTime(int time){

        List<SeckillProductVo>  seckillProductVos =  seckillProductService.queryByTime(time);
        return Result.success(seckillProductVos);
    }

    @RequestMapping("/find")
    public Result<SeckillProductVo> find(int time,Long seckillId ){
     return Result.success(seckillProductService.find(time,seckillId));
    }
}
