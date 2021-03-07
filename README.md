





##### 登录流程                                                             

1.用户填写账号密码,后端获取到用户的信息

![image-20210307105212671](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210307105212671.png)

```java
//获取请求的真实ip,因为我们所有的请求都是从网关转过来的,所以需要通过网关在请求头里设置真实的ip
String ip = request.getHeader(CommonConstants.REAL_IP);
//获取请求头里面的token
String token = request.getHeader(CommonConstants.TOKEN_NAME);
//进行登录，并将这个token返回给前台
UserResponse userResponse = userService.login(userLogin.getPhone(),userLogin.getPassword(),ip,token);

return Result.success(userResponse);
```

2.执行login()方法.



```java
@Override
public UserResponse login(Long phone, String password, String ip, String token) {
    //无论成功失败都打印日志
    LoginLog loginLog = new LoginLog(phone,ip,new Date());
    //用token从redis中获取userinfo对象,如果是第一次登录就是null
    UserInfo userInfo = getByToken(token);
        rocketMQTemplate.sendOneWay(MQConstant.LOGIN_TOPIC,loginLog);
         //获取userLogin信息,
        UserLogin userLogin = this.getUser(phone);

        if(userLogin==null || !userLogin.getPassword().equals(MD5Util.encode(password,userLogin.getSalt()))){

            loginLog.setState(LoginLog.LOGIN_FAIL);
  
            rocketMQTemplate.sendOneWay(MQConstant.LOGIN_TOPIC+":"+LoginLog.LOGIN_FAIL,loginLog);

            throw new BusinessException(UAACodeMsg.LOGIN_ERROR);
        }
        if(userInfo == null){

    
        userInfo = userMapper.selectUserInfoByPhone(phone);
        userInfo.setLoginIp(ip);
        token = createToken(userInfo);
        rocketMQTemplate.sendOneWay(MQConstant.LOGIN_TOPIC,loginLog);
    }
    return new UserResponse(token,userInfo);
}
```

①.从redis中用token获取userinfo对象 getByToken(token)

```java
private UserInfo getByToken(String token){
    String strObj = redisTemplate.opsForValue().get(CommonRedisKey.USER_TOKEN.getRealKey(token));
    if(StringUtils.isEmpty(strObj)){
        return null;
    }
    return JSON.parseObject(strObj,UserInfo.class);
}
```

②.使用phone从redis中获取userLogin信息  this.getUser(phone);

在这里我们向redis中存了两个key,hashkey(用来存储用户登录的信息,避免每次都从数据库查询增加mysql的压力)

zsetkey(使用排行功能,以用户id(phone)为feild以登录时间毫秒值为value存到redis中,这样每天就可以获取7天以前的用户的ids,然后将hsah里面的用户进行删除,每次调用getUser()都会刷新时间



```java
private UserLogin getUser(Long phone){
    UserLogin userLogin; 
    //将登陆信息存在redis中下次就可以不用再访问数据库了,以后配合redis淘汰策略
    String hashKey = UaaRedisKey.USER_HASH.getRealKey("");
    //将登陆信息存到zset中用来删除7天前的用户登录信息
    String zSetKey = UaaRedisKey.USER_ZSET.getRealKey("");
    String userKey = String.valueOf(phone);
    //直接先去redis中查询看看有没有这个key
    String objStr = (String) redisTemplate.opsForHash().get(hashKey, String.valueOf(phone));
    if(StringUtils.isEmpty(objStr)){
        //缓存中并没有，从数据库中查询
        userLogin = userMapper.selectUserLoginByPhone(phone);
        //把用户的登录信息存储到Hash结构中.
        redisTemplate.opsForHash().put(hashKey,userKey,JSON.toJSONString(userLogin));
        //使用zSet结构,value存用户手机号码，分数为登录时间，在定时器中找出7天前登录的用户，然后再缓存中删除.
        //我们缓存中的只存储7天的用户登录信息(热点用户)
    }else{
        //缓存中有这个key,
        userLogin = JSON.parseObject(objStr,UserLogin.class);
    }
    redisTemplate.opsForZSet().add(zSetKey,userKey,new Date().getTime());
    return userLogin;
}
```

```java
//elastic-job里面定时任务,在每天凌晨定时删除7天没有登录过的用户



public class UserCacheJob implements SimpleJob {
    @Value("${jobCron.userCache}")
    private String cron;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Override
    public void execute(ShardingContext shardingContext) {
        doWork();
    }
    private void doWork() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE,-7);
        //获取7天前的日期
        Long max = calendar.getTime().getTime();
        String userZSetKey = JobRedisKey.USER_ZSET.getRealKey("");
        String userHashKey = JobRedisKey.USER_HASH.getRealKey("");
        //从zset里面获取7天没有登录过的用户ids
        Set<String> ids = redisTemplate.opsForZSet().rangeByScore(userZSetKey, 0, max);
        //删除7天前的用户缓存数据
        if(ids.size()>0){
            //根据ids删除hsah里面的用户
            redisTemplate.opsForHash().delete(userHashKey,ids.toArray());
        }
        //将zset里面七天前的用户也删除
        redisTemplate.opsForZSet().removeRangeByScore(userZSetKey,0,calendar.getTime().getTime());
    }


}
```

③如果userlogin或者加盐后的密码有错误,那就提示用户登录失败的错误信息,

​     如果是第一次登录userinfo==null,那就从数据库查询出对象,并且设置token和真实ip,然后返回给控制层





##### 通用响应类,统一异常处理

定义一个错误响应公共类,项目里面每个模块都有自己的XXXcodemsg继承这个类,然后有自己的响应信息

```java
public class CodeMsg implements Serializable {
    private Integer code;
    private String msg;
}
```

```java
public class SeckillCodeMsg extends CodeMsg {
    private SeckillCodeMsg(Integer code, String msg){
        super(code,msg);
    }
    public static final SeckillCodeMsg SECKILL_STOCK_OVER
        = new SeckillCodeMsg(500201,"您来晚了，商品已经被抢购完毕.");
    public static final SeckillCodeMsg REPEAT_SECKILL
        = new SeckillCodeMsg(500202,"您已经抢购到商品了，请不要重复抢购");
    public static final SeckillCodeMsg SECKILL_ERROR
        = new SeckillCodeMsg(500203,"秒杀失败");
    public static final SeckillCodeMsg CANCEL_ORDER_ERROR
        = new SeckillCodeMsg(500204,"超时取消失败");
    public static final SeckillCodeMsg PAY_SERVER_ERROR
        = new SeckillCodeMsg(500205,"支付服务繁忙，稍后再试");
    public static final SeckillCodeMsg REFUND_ERROR
        = new SeckillCodeMsg(500206,"退款失败，请联系管理员");
    public static final SeckillCodeMsg INTERGRAL_SERVER_ERROR
        = new SeckillCodeMsg(500207,"操作积分失败");
    public static final SeckillCodeMsg SECKILL_PRODUCT_ERROR
        = new SeckillCodeMsg(500208,"商品繁忙");
    public static final SeckillCodeMsg SECKILL_OPERATION_ERROR
        = new SeckillCodeMsg(500209,"操作错误");
    public static final SeckillCodeMsg SECKILL_TIME_ERROR
        = new SeckillCodeMsg(500210,"秒杀时间不对");
```

在公共类里面有统一异常处理父类,然后自己的模块去继承这个类再贴上controllerAdvice注解增强就可以了,还可以在自己的类里面写自己特有的异常处理方法

```java
public class CommonControllerAdvice {
    @ExceptionHandler(BusinessException.class)
    @ResponseBody
    public Result handleBusinessException(BusinessException ex){
        return Result.error(ex.getCodeMsg());
    }
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Result handleDefaultException(Exception ex){
        ex.printStackTrace();//在控制台打印错误消息.
        return Result.defaultError();
    }
}
```

```java
//每个模块自己定义一个类去继续父类,
@ControllerAdvice
public class SeckillControllerAdvice extends CommonControllerAdvice {
}
```



##### 登录控制权限@RequireLogin注解

自己定一个注解.起到一个标识的作用,然后再需要登录的方法上贴上这个注解,就能达到需要登录才能访问的权限

```java
@Target({ElementType.METHOD}) //贴的范围
@Retention(RetentionPolicy.RUNTIME)//注解在运行时有效
@Documented
public @interface RequireLogin {
}
```

```java
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
            //从请求头里面获取token信息
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
```

CommonConstants.FEIGN_REQUEST_FALSE.equals(feignRequest)

这个判断是服务间调用还是网关调用,

服务间调用在feign拦截器里面设置true

```java
//feign拦截器,拦截fegin请求
public class FeignRequestInterceptor implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {
        template.header(CommonConstants.FEIGN_REQUEST_KEY,CommonConstants.FEIGN_REQUEST_TRUE);
    }
}
```

用户访问,因为都会经过网关,所以在pre过滤器里面设置

```java
public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    /**
     * pre拦截逻辑
     * 在请求去到微服务之前，做了两个处理
     * 1.把客户端真实IP通过请求同的方式传递给微服务
     * 2.在请求头中添加FEIGN_REQUEST的请求头，值为0，标记请求不是Feign调用，而是客户端调用
     */
    ServerHttpRequest request = exchange.getRequest().mutate().
            header(CommonConstants.REAL_IP,exchange.getRequest().getRemoteAddress().getHostString()).
            header(CommonConstants.FEIGN_REQUEST_KEY,CommonConstants.FEIGN_REQUEST_FALSE).
            build();
    return chain.filter(exchange.mutate().request(request).build()).then(Mono.fromRunnable(()->{
        /**
         * post拦截逻辑
         * 在请求执行完微服务之后,需要刷新token在redis的时间
         * 判断token不为空 && Redis还存在这个token对于的key,这时候需要延长Redis中对应key的有效时间.
         */
        String token,redisKey;
        if(!StringUtils.isEmpty(token =exchange.getRequest().getHeaders().getFirst(CommonConstants.TOKEN_NAME))
                && redisTemplate.hasKey(redisKey = CommonRedisKey.USER_TOKEN.getRealKey(token))){
            redisTemplate.expire(redisKey, CommonRedisKey.USER_TOKEN.getExpireTime(), CommonRedisKey.USER_TOKEN.getUnit());
        }
    }));
}
```



#### 秒杀列表展示

商家需要在后台管理系统中设置哪些商品进行秒杀的活动,如果是第三方的,那需要平台客服对商品进行审核的操作,然后我们会把需要秒杀的商品存入到数据库中,

然后我们会有个定时任务,把当天要参与秒杀的商品存到redis中,以场次为key进行区分.因为涉及到2个不同数据库里面的表,所以不能使用join连表查询,我们需要使用feign远程调用服务,然后获取数据进行合表返回.



##### elastic-job

//自定义一个类实现simplejob,里面写业务逻辑

```java
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
 //循环遍历将,场次作为key,秒杀商品id作为field,秒杀商品作为value               template.opsForHash().put(SeckillRedisKey.SECKILL_PRODUCT_LIST.getRealKey(time+""),
                           seckillProductVo.getId()+"",JSON.toJSONString(seckillProductVo));
//将秒杀商品的库存存到redis中                   template.opsForHash().put(SeckillRedisKey.SECKILL_STOCK_COUNT_HASH.getRealKey(time),seckillProductVo.getId()+"",seckillProductVo.getStockCount()+"");
               }
           }
        }
        log.info("成功添加到redis中"+"=============================");
    }
}
```

使用feign远程调用服务,和本地调用一样简单, SeckillProductApi

```java
//服务名 seckill-service
@FeignClient(name = "seckill-service",fallback = SeckillProductFallback.class)
public interface SeckillProductApi {
//这里写的和controller方法一样,一定要贴@RequestParam()注解
    @RequestMapping("/seckillProduct/productList")
     Result<List<SeckillProductVo>> productList(@RequestParam("time") int time);
}
```

配置类里面创建一个@bean交给spring容器管理

```java
@Bean(initMethod = "init")
 public SpringJobScheduler seckillProduct(CoordinatorRegistryCenter registryCenter, SeckillProductJob seckillProductJob){
     LiteJobConfiguration jobConfiguration = ElasticJobUtil.createJobConfiguration(seckillProductJob.getClass(), seckillProductJob.getCron(),
//开3个线程,按场次分类                                                                                   
             3,"0=10,1=12,2=14",false);
     SpringJobScheduler springJobScheduler = new SpringJobScheduler(seckillProductJob, registryCenter,jobConfiguration );
     return springJobScheduler;
 }
```

合表过程,将product和秒杀商品的表合成 seckillProductVo

```java
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
```

这样我们就能将不同数据库的数据通过feign远程调用服务获取数据聚合在一起了,然后前端通过ajax请求

queryByTime(int time)获取需要的数据

```java
@RequestMapping("/queryByTime")
public Result<List<SeckillProductVo>> queryByTime(int time){

    List<SeckillProductVo>  seckillProductVos =  seckillProductService.queryByTime(time);
    return Result.success(seckillProductVos);
}
```

```java
public List<SeckillProductVo> queryByTime(int time) {
    //从redis中根据time获取对应场次数据
    List<Object> values = redisTemplate.opsForHash().values(SeckillRedisKey.SECKILL_PRODUCT_LIST.getRealKey(time + ""));
    //将Object转换成SeckillProductVo类型
    ArrayList<SeckillProductVo> seckillProductVos = new ArrayList<>();
    for (Object vo : values) {
        SeckillProductVo seckillProductVo = JSON.parseObject(vo + "", SeckillProductVo.class);
        seckillProductVos.add(seckillProductVo);
    }
    return seckillProductVos;
}
```

#### 秒杀商品详情

通过场次 和 商品id查询

```java
@RequestMapping("/find")
public Result<SeckillProductVo> find(int time,Long seckillId ){
 return Result.success(seckillProductService.find(time,seckillId));
}
```

```java
//从redis中获取指定的商品信息返回
public SeckillProductVo find(int time, Long seckillId) {
    Object obj = redisTemplate.opsForHash().get(SeckillRedisKey.SECKILL_PRODUCT_LIST.getRealKey(time + ""), seckillId + "");
    if(obj == null){
        throw new BusinessException(SeckillCodeMsg.SECKILL_OPERATION_ERROR);
    }
    SeckillProductVo seckillProductVo = JSON.parseObject(obj + "", SeckillProductVo.class);
    return seckillProductVo;
}
```

#### 秒杀功能

客户端点击立即抢购,发送/order/doSeckill?time&seckillId ,后端接受数据进行参数判断

#####    数据判断

1.参数不能为空

2.同一个用户不能重复下单  

``每次顾客下单后,我们会用redis里面的set类型存储信息,key是场次,value是用户的id+秒杀商品的id,然后我们可以用ismember来判断是否已经存在redis中.

3.判断该商品库存是否足够

``因为我们已经将秒杀商品存到人redis中了,所以我们可以从redis中获取商品的信息,里面有商品的库存量.

4.判断用户是否登录

 ``贴@RequireLogin注解

5.判断商品是否在秒杀时间内

``商品里面有商品秒杀开始的时间

```java
@RequireLogin
public Result<String> doSeckill(int time, Long seckillId, HttpServletRequest request){
    //进行判空处理
   if(StringUtils.isEmpty(time+"") || StringUtils.isEmpty(seckillId)){
       throw new BusinessException(SeckillCodeMsg.SECKILL_OPERATION_ERROR);
   }
    Boolean flag = map.get(seckillId);
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

    Boolean ret = redisTemplate.opsForSet().isMember(SeckillRedisKey.SECKILL_ORDER_SET.getRealKey(time + ""), userInfo.getPhone() + ":" + seckillId);
    if(ret){
        throw new BusinessException(SeckillCodeMsg.REPEAT_SECKILL);
    }
    //每次秒杀,库存都-1,小于0就改变标识为true;
    Long count = redisTemplate.opsForHash().increment(SeckillRedisKey.SECKILL_REAL_COUNT_HASH.getRealKey(time + ""), seckillId + "", -1);
    if(count == null && count < 0){
        map.put(seckillId,true);
    }

    //判断库存是否足够
 if(seckillProductVo.getStockCount() <= 0){
     throw new BusinessException(SeckillCodeMsg.SECKILL_STOCK_OVER);
 }
    //执行秒杀逻辑
  String orderNum = orderInfoService.doSeckill(seckillId,time,userInfo.getPhone());
 return Result.success(orderNum);
}
```

执行doseckill()逻辑,

1.秒杀是高并发多线程的,所以可能出现超卖的情况,我们通过sql语句里面 stock_count > 0 防止商品超级卖,

2.我们第一次判断是否重复下单,然后没有执行doseckill操作,所以redis中还没有对应的数据,另外一个线程进入判断肯定也是能进来的.为了防止这样情况,我们在订单表上建一个联合索引  (用户id+商品id)这样就能解决重复下单的情况.

```java

public String doSeckill(Long seckillId, int time, Long phone) {

    //减少真实的库存,超卖问题
    int row = seckillProductService.decrStock(seckillId);
    if(row == 0){
        throw new BusinessException(SeckillCodeMsg.SECKILL_STOCK_OVER);
    }
    //创建订单
 String orderNum = creatOrder(phone,time,seckillId);

    //往redis存数据
    redisTemplate.opsForSet().add(SeckillRedisKey.SECKILL_ORDER_SET.getRealKey(time+""),phone+":"+seckillId);
  return orderNum;
}
```

###### 防止超卖的解决方案

①乐观锁   版本号

在表里面添加一个版本号,每次更新前先获取表里面 的版本号,然后执行update操作的时候,判断版本号是否一致,如果不一致就执行失败 ,执行成功版本号+1

stock_count > 0 进行判断

```
update t_seckill_product set stock_count = stock_count - 1 where id = #{seckillId} and stock_count > 0
```

###### 防止卖完了还有请求进入的解决方案

如果卖完了还让线程进入的话会造成大量请求访问数据库,给数据库造成压力,所以我们需要在方法里面设置一个标识,一旦库存卖完了那么就不执行秒杀.

1.那我们怎么计数该库存卖完了改变标识呢?

可以使用redis,因为redis是单线程的并且是原子操作,线程安全,所以我们使用redis存储商品库存来计数

定时任务时,我们将商品存到redis中的时候,也将库存存到redis中 

```java
template.opsForHash().put(SeckillRedisKey.SECKILL_STOCK_COUNT_HASH.getRealKey(time),seckillProductVo.getId()+"",seckillProductVo.getStockCount()+"");
```

然后每次有请求访问doseckill方法,库存都会-1,当返回值<0的时候我们就改变标识,这样后面的请求就不会继续访问数据库.

```java
 Boolean flag = map.get(seckillId);
if(flag != null && flag){
    throw new BusinessException(SeckillCodeMsg.SECKILL_STOCK_OVER);
}
```

```java
Long count = redisTemplate.opsForHash().increment(SeckillRedisKey.SECKILL_REAL_COUNT_HASH.getRealKey(time + ""), seckillId + "", -1);
if(count == null && count < 0){
    map.put(seckillId,true);
    throw new BusinessException(SeckillCodeMsg.SECKILL_STOCK_OVER);
}
```





















### redis 应用总结

1.用来缓存用户登录的信息,利用redis的时效性,还有排序功能,

2.因为redis是内存级别的,所以我们用来缓存秒杀商品的数据

3.为了防止用户重复下单,每当用户下单成功我们就将信息存到redis中

4.为了控制请求大量访问数据库,我们利用redis的单线程,原子性,作为库存计数器,如果库存为0了,就不在让请求去访问数据库