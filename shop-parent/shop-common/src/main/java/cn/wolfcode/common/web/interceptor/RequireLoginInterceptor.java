package cn.wolfcode.common.web.interceptor;

import cn.wolfcode.common.constants.CommonConstants;
import cn.wolfcode.common.domain.UserInfo;
import cn.wolfcode.common.web.CommonCodeMsg;
import cn.wolfcode.common.web.Result;
import cn.wolfcode.common.web.anno.RequireLogin;
import cn.wolfcode.redis.CommonRedisKey;
import com.alibaba.fastjson.JSON;
import org.apache.commons.lang.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class RequireLoginInterceptor implements HandlerInterceptor {
    private StringRedisTemplate redisTemplate;
    public RequireLoginInterceptor(StringRedisTemplate redisTemplate){
        this.redisTemplate = redisTemplate;
    }
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if(handler instanceof HandlerMethod){
            //如果是控制器的方法,就进行强转
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            //从请求头里面获取信息,判断是用户访问还是服务间调用,用户访问的请求在网关filter设置false,fegin服务间
            //服务,是feign在拦截器里面设置进去的
            String feignRequest = request.getHeader(CommonConstants.FEIGN_REQUEST_KEY);
            //如果是false,表明是用户访问,方法上贴有注解,那么就要进行登录判断
            if(!StringUtils.isEmpty(feignRequest) && CommonConstants.FEIGN_REQUEST_FALSE.equals(feignRequest) && handlerMethod.getMethodAnnotation(RequireLogin.class)!=null){
                response.setContentType("application/json;charset=utf-8");
                //请求头里面获取token
                String token = request.getHeader(CommonConstants.TOKEN_NAME);
                // 如果token是空的
                if(StringUtils.isEmpty(token)){
                    response.getWriter().write(JSON.toJSONString(Result.error(CommonCodeMsg.TOKEN_INVALID)));
                    return false;
                }
                //使用token去redis中获取用户信息
                UserInfo userInfo = JSON.parseObject(redisTemplate.opsForValue().get(CommonRedisKey.USER_TOKEN.getRealKey(token)),UserInfo.class);
                if(userInfo==null){
                    response.getWriter().write(JSON.toJSONString(Result.error(CommonCodeMsg.TOKEN_INVALID)));
                    return false;
                }
                //判断redis中的用户ip和这次ip是否相同,是否异地登录
                String ip = request.getHeader(CommonConstants.REAL_IP);
                if(!userInfo.getLoginIp().equals(ip)){
                    response.getWriter().write(JSON.toJSONString(Result.error(CommonCodeMsg.LOGIN_IP_CHANGE)));
                    return false;
                }
            }
        }
        return true;
    }
}

