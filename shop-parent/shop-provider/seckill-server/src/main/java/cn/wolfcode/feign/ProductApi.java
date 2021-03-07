package cn.wolfcode.feign;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.Product;
import cn.wolfcode.feign.fallback.ProductApiFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name ="product-service",fallback = ProductApiFallback.class)
public interface ProductApi {

    @RequestMapping("/product/getByIds")
     Result< List<Product>> getByIds(@RequestParam("ids") List<Long> ids);
}
