package play.api.sysguard.service;

import com.google.common.collect.Maps;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.apache.commons.io.IOUtils;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.Configuration;
import play.Environment;
import play.api.mq.rabbit.RabbitMQ;
import play.inject.ApplicationLifecycle;
import play.libs.Json;
import play.libs.concurrent.Promise;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.iq80.leveldb.impl.Iq80DBFactory.factory;

@Singleton
public class TradeProcess {
    private static final Logger LOGGER = LoggerFactory.getLogger(TradeProcess.class);

    private Configuration conf;
    private RabbitMQ rabbitMQ;
    private TradeSyncApi tradeSyncApi;
    private Environment env;

    private Channel queryChannel;
    private Channel halfHourChannel;
    private DB tradeDb;

    private String queryDelayQueue = "query_delay_queue"; //延迟队列
    private String halfHourDelayQueue = "half_hour_delay_queue"; //半小时处理队列
    private String queryProcessQueue = "query_process_queue"; //处理队列
    private String exchangeQueryDelay = "amq.direct"; //死信交换
    private String leveldbWorkDir = "workdir/tradedb";
    private String tradePrefix = "trade_";

    @Inject
    public TradeProcess(RabbitMQ rabbitMQ, TradeSyncApi tradeSyncApi, Environment env, ApplicationLifecycle lifecycle) throws IOException {
        this.rabbitMQ = rabbitMQ;
        this.tradeSyncApi = tradeSyncApi;
        this.env = env;
        this.bindQueueEvent();
        this.openLevelDb(env.getFile(leveldbWorkDir));
        lifecycle.addStopHook(this::shutdown);
    }

    /**
     * 关闭
     */
    protected CompletionStage<Object> shutdown() throws Exception{
        if(this.tradeDb != null){
            IOUtils.closeQuietly(this.tradeDb);
        }
        if(this.queryChannel != null){
            this.queryChannel.close();
        }
        if(this.halfHourChannel != null){
            this.halfHourChannel.close();
        }
        return Promise.pure(true);
    }

    /**
     * 打开leveldb
     */
    protected void openLevelDb(File tradeDb) throws IOException {
        Options options = new Options();
        options.createIfMissing(true);
        options.cacheSize(100 * 1048576);//100M

        this.tradeDb = factory.open(tradeDb, options);
    }

    /**
     * 构建队列监听
     * @throws IOException
     */
    protected void bindQueueEvent() throws IOException {
        final Channel channel = this.rabbitMQ.getChannel();
//
        //channel.exchangeDeclare(this.exchangeQueryDelay, "direct",true);
        channel.queueDeclare(this.queryProcessQueue, true, false, false,null);

        channel.queueBind(this.queryProcessQueue, this.exchangeQueryDelay,this.queryProcessQueue);

        channel.basicConsume(this.queryProcessQueue,false,new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                TradeInfo tradeInfo = Json.fromJson(Json.parse(body),TradeInfo.class);
                //需要一个速率控制
                //com.google.common.util.concurrent.RateLimiter
                onEvent(tradeInfo).whenComplete((a,e) -> {
                    try {
                        if(e != null){
                            LOGGER.error("handle message error",e);
                            if(env.isProd()) {
                                channel.basicReject(envelope.getDeliveryTag(), true);
                                return;
                            }
                        }
                        channel.basicAck(envelope.getDeliveryTag(), false);
                    } catch (IOException e1) {
                        LOGGER.error("handle message error",e1);
                    }
                });
            }
        });
    }

    private Promise<Boolean> onEvent(TradeInfo tradeInfo) {
        if(tradeInfo.getTradeState() == 2){ //支付成功、就通知
            return onNotifyEvent(tradeInfo);
        }else if(tradeInfo.getTradeState() == 1){ //未支付、就查询
            if(tradeInfo.getMsgType() == 3){//半个小时后的一次查询,需要先判断leveldb里面是否包含了该笔订单为成功,如果包含则无需查询,不包含则进入下一步查询
                boolean isSuccess = isSuccessInDb(tradeInfo,true);
                if(isSuccess){
                    return Promise.pure(isSuccess);
                }
            }
            return onQueryEvent(tradeInfo);
        }else{
            return Promise.pure(true);
        }
    }

    /**
     * 执行订单查询，这里要剔除掉，订单状态为：PAYERROR 的单，这种单一定是失败的
     * @param tradeInfo
     */
    public Promise<Boolean> onQueryEvent(final TradeInfo tradeInfo){
        return this.tradeSyncApi.doQuery(tradeInfo).map((state) -> {
            if(tradeInfo.getMsgType() == 3){
                return state;
            }
            try{
                if(!state){ //未得到最终状态,需要进行查询
                    boolean nextQuery = doQueryProcess(tradeInfo,tradeInfo.getQueryCount() + 1); //执行下一次查询
                    if(!nextQuery){//最终完毕,进入30分钟队列,进行下一次查询
                        doHalfHourProcess(tradeInfo);
                    }
                }
            }catch (Exception e){
                throw new RuntimeException(e);
            }
            return state;
        });
    }


    public Promise<Boolean> onNotifyEvent(TradeInfo tradeInfo){
        return this.tradeSyncApi.doNotify(tradeInfo).map((state) -> {
           if(tradeInfo.getMsgType() == 3){
               return state;
           }
           try{
                if(!state){ //未得到下游成功得反馈状态,需要进行下一次通知
                    boolean nextNotify = doNotifyProcess(tradeInfo,tradeInfo.getNotifyCount() + 1);
                    if(!nextNotify){//最终完毕,进入30分钟队列,进行下一次通知
                        doHalfHourProcess(tradeInfo);
                    }
                }
           } catch (Exception e){
               LOGGER.error("onNotifyEvent",e);
           }
           return state;
        });
    }
    public boolean doQueryProcess(TradeInfo tradeInfo) throws IOException {
        return doQueryProcess(tradeInfo,0);
    }

    public boolean doQueryProcess(TradeInfo tradeInfo,int queryCount) throws IOException {
        Channel queryChannel = getQueryChannel();
        List<Integer> timeoutDelay =  conf.getIntList("trade.query_delay");
        if(queryCount >= timeoutDelay.size()){
            LOGGER.info("query order:{},trade_state:{},finish",tradeInfo.getOrderNo(),tradeInfo.getTradeState());
            return false; //同步次数已经完毕
        }
        LOGGER.info("query order:{},trade_state:{},count:{}",tradeInfo.getOrderNo(),tradeInfo.getTradeState(),queryCount);

        int timeout = timeoutDelay.get(queryCount) * 1000; //单位为秒

        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .deliveryMode(2)
                .expiration(timeout + "")
                .build();

        tradeInfo.setQueryCount(queryCount);
        tradeInfo.setLastAccess(System.currentTimeMillis());

        byte[] body = Json.toJson(tradeInfo).toString().getBytes();

        queryChannel.basicPublish("",queryDelayQueue,properties,body);
        return true;
    }

    public boolean doNotifyProcess(TradeInfo tradeInfo) throws IOException{
        return doNotifyProcess(tradeInfo,0);
    }

    public boolean doNotifyProcess(TradeInfo tradeInfo,int notifyCount) throws IOException{
        Channel notifyChannel = getQueryChannel();
        List<Integer> timeoutDelay = conf.getIntList("trade.notify_delay");
        if(notifyCount >= timeoutDelay.size()){
            LOGGER.info("notify order:{},trade_state:{},finish",tradeInfo.getOrderNo(),tradeInfo.getTradeState());
            return false; //同步次数已经完毕
        }

        LOGGER.info("notify order:{},trade_state:{},count:{}",tradeInfo.getOrderNo(),tradeInfo.getTradeState(),notifyCount);

        int timeout = timeoutDelay.get(notifyCount) * 1000; //单位为秒

        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .deliveryMode(2)
                .expiration(timeout + "")
                .build();

        tradeInfo.setNotifyCount(notifyCount);
        tradeInfo.setLastAccess(System.currentTimeMillis());

        byte[] body = Json.toJson(tradeInfo).toString().getBytes();

        notifyChannel.basicPublish("",queryDelayQueue,properties,body);
        return true;
    }

    public boolean doHalfHourProcess(TradeInfo tradeInfo) throws IOException{
        LOGGER.info("half hour process order:{},trade_state:{}",tradeInfo.getOrderNo(),tradeInfo.getTradeState());

        Channel channel = getHalfHourChannel();

        int timeout = conf.getInt("trade.half_hour",1800) * 1000; //单位为秒

        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .deliveryMode(2)
                .expiration(timeout + "")
                .build();

        tradeInfo.setMsgType(3);
        tradeInfo.setLastAccess(System.currentTimeMillis());

        byte[] body = Json.toJson(tradeInfo).toString().getBytes();

        channel.basicPublish("",halfHourDelayQueue,properties,body);
        return true;
    }

    /**
     * 支付成功的订单,简单进入leveldb
     * @param orderNo
     * @param tradeState
     */
    public boolean doSuccessProcess(String orderNo, int tradeState) {
        this.tradeDb.put((this.tradePrefix + orderNo).getBytes(),new byte[]{(byte)tradeState});
        return true;
    }

    /**
     * 到Leveldb进行查询,查询是否包含在leveldb里面
     * @param tradeInfo
     * @return
     */
    public boolean isSuccessInDb(TradeInfo tradeInfo,boolean autoDelete) {
       try{
           byte[] key = (this.tradePrefix + tradeInfo.getOrderNo()).getBytes();
           byte[] value = this.tradeDb.get(key);
           boolean exists = value != null;
           if(autoDelete){
               this.tradeDb.delete(key);
           }
           return exists;
       }catch (Exception e){
           LOGGER.error("read trade in db error",e);
           return false;
       }
    }

    public void setConf(Configuration conf) {
        this.conf = conf;
    }

    /**
     * 1分钟内延迟队列
     * @return
     * @throws IOException
     */
    protected Channel getQueryChannel() throws IOException{
        if(queryChannel == null){
            queryChannel = rabbitMQ.getChannel();
            Map<String, Object> args = Maps.newHashMap();
            args.put("x-dead-letter-exchange", this.exchangeQueryDelay);
            args.put("x-dead-letter-routing-key",this.queryProcessQueue);

            queryChannel.queueDeclare(this.queryDelayQueue,true,false,false,args);
        }
        return queryChannel;
    }

    /**
     * 半小时延迟队列
     * @return
     * @throws IOException
     */
    protected Channel getHalfHourChannel() throws IOException{
        if(halfHourChannel == null){
            halfHourChannel = rabbitMQ.getChannel();
            Map<String, Object> args = Maps.newHashMap();
            args.put("x-dead-letter-exchange", this.exchangeQueryDelay);
            args.put("x-dead-letter-routing-key",this.queryProcessQueue);

            halfHourChannel.queueDeclare(this.halfHourDelayQueue,true,false,false,args);
        }
        return halfHourChannel;
    }

}
