package cn.wolfcode.service;

import cn.wolfcode.domain.Product;

import java.util.List;


public interface IProductService {


    List<Product> getByIds(List<Long> ids);



}
