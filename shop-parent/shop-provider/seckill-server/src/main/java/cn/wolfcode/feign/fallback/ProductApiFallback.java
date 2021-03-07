package cn.wolfcode.feign.fallback;

import cn.wolfcode.common.web.Result;
import cn.wolfcode.domain.Product;
import cn.wolfcode.feign.ProductApi;

import java.util.List;

public class ProductApiFallback implements ProductApi {
    @Override
    public Result<List<Product>> getByIds(List<Long> ids) {
        return null;
    }
}
