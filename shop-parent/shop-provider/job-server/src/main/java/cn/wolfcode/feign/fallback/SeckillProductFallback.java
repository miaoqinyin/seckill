package cn.wolfcode.feign.fallback;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.SeckillProductVo;
import cn.wolfcode.feign.SeckillProductApi;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SeckillProductFallback implements SeckillProductApi {
    @Override
    public Result<List<SeckillProductVo>> productList(int time) {
        return null;
    }
}
