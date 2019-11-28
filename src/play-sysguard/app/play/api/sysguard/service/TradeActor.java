package play.api.sysguard.service;

import akka.actor.AbstractActor;
import akka.japi.pf.ReceiveBuilder;
import com.typesafe.config.Config;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.Configuration;
import play.Play;
import play.api.libs.changestream.actors.StateActor;
import play.api.libs.changestream.actors.SyncActor;
import play.api.libs.changestream.events.MutationWithInfo;
import play.libs.Scala;
import scala.PartialFunction;
import scala.collection.immutable.ListMap;
import scala.runtime.BoxedUnit;
import scala.util.Success;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

public class TradeActor implements SyncActor {
    private static final Logger LOGGER = LoggerFactory.getLogger(TradeActor.class);
    private StateActor sa;
    private Configuration conf;
    private TradeProcess tradeProcess;

    private int tradeNotpay;
    private int successState;
    private int notNotifyState;
    private int halfHourTimeout;
    private String tradeType;
    private String bankNo;

    public TradeActor(Config conf, StateActor sa){
        this.conf = new Configuration(conf);
        this.sa = sa;

        this.tradeNotpay = this.conf.getInt("trade.trade_notpay",1);
        this.successState = this.conf.getInt("trade.trade_success",2);
        this.notNotifyState = this.conf.getInt("trade.trade_notify",0);
        this.tradeType = this.conf.getString("trade.trade_type");
        this.bankNo = this.conf.getString("trade.not_bank_no");
        this.halfHourTimeout = this.conf.getInt("trade.half_hour",1800) * 1000; //秒变毫秒
    }

    private TradeProcess getTradeProcess(){
        if(this.tradeProcess == null){
            this.tradeProcess = Play.application().injector().instanceOf(TradeProcess.class);
            this.tradeProcess.setConf(this.conf);
        }
        return this.tradeProcess;
    }

    @Override
    public PartialFunction<Object, BoxedUnit> receive() {
        return createReceive().onMessage();
    }

    public AbstractActor.Receive createReceive(){
        return ReceiveBuilder.create()
                .match(MutationWithInfo.class,mu -> {
                    if(mu.message().isEmpty()) return; //没有成功解析该条记录
                    ListMap<String, Object> rows = (ListMap<String, Object>)mu.message().get();
                    Map<String, Object> data = Scala.asJava(rows);
                    proccessRow(data);
                    sa.receive().apply(Success.apply(200)); //保存binlog位置信息
                 })
                .matchAny(o -> LOGGER.info("received unknown message"))
                .build();
    }

    /**
     * 交易层查询主动通知：不能启用
     *
     * 刷卡无通知
     * 1、刷卡没有主动通知，所以要设置定时任务主动进行查询
     * 2、查询成功后就进入通知队列，主动去通知商户
     * 3、查询失败，通知失败的单，会进入30分钟延迟队列，在做一次查询、或者通知
     * 4、如果查询成功则会主动通知给商户
     * 其余有通知的情况
     * 1、下单后直接进入延时队列 30分钟后进行查询
     * 2、如果30分钟还没通知成功，则触发主动通知
     *
     * @param data
     * @throws IOException
     */
    private void proccessRow(Map<String, Object> data) throws IOException{
        int tradeState = (Integer) data.get("trade_state"); //支付状态
        int notifyState = (Integer) data.get("notify_state");
        String tradeType = (String) data.get("trans_id"); //支付类型
        String bankNo = String.valueOf(data.get("bank_no")); //银行编号
        String orderNo = (String)data.get("order_no"); //订单编号
        String notifyUrl = (String)data.get("notify_url"); //通知地址
        Date addTime = (Date) data.get("add_time"); //下单时间

        //需要进行通知的交易
        if(tradeType.equals(this.tradeType) && !bankNo.equals(this.bankNo) && StringUtils.isNotBlank(notifyUrl)){
            if(tradeState == this.tradeNotpay){ //创建订单
                doQueryProcess(orderNo,tradeState,data);
                //*创建订单——>成功支付同时发生，先不处理
            }else if(tradeState == this.successState && notifyState == this.notNotifyState){ //支付成功、且未通知
                doNotifyProcess(orderNo,tradeState,data);
            }
        }else if(!bankNo.equals(this.bankNo)){ //其它除刷卡之外的支付类型,这种支付类型本身就有通知,所以半个小时后未通知,则需要进行查询,要解决半个小时内已经通知则无需查询的问题
            if(tradeState == this.tradeNotpay) { //创建订单,进入半小时队列等待查询
                doHalfHourProcess(orderNo,tradeState,data);
            }else if(tradeState == this.successState){//支付成功,则进入leveldb,用于本地判断支付成功,减少查询
                //如果当前时间-支付时间大于了半小时等待队列的时间，且是通知失败的,则需要主动补通知
                //其实第三方都会通知的,可以不用通知,但是避免极端异常情况,还是延迟10秒去通知
                boolean isAfter = (System.currentTimeMillis() - addTime.getTime()) > this.halfHourTimeout;
                if(isAfter && StringUtils.isNotBlank(notifyUrl) && notifyState == this.notNotifyState){ //未通知
                    doNotifyProcess(orderNo,tradeState,data); //主动通知
                }else {
                    doSuccessProcess(orderNo, tradeState, data);//进入leveldb做简单本地kv验证,避免调用过多的查询
                }
            }
        }
    }

    /**
     * 执行查询逻辑
     * 使用一个数组来确定及配置次数:[15,15,60,300]
     * 使用配置项来进行配置
     * @param orderNo
     * @param tradeState
     * @param data
     */
    private void doQueryProcess(String orderNo, int tradeState, Map<String, Object> data) throws IOException{
        TradeInfo tradeInfo = new TradeInfo();
        tradeInfo.setOrderNo(orderNo);
        tradeInfo.setTradeState(tradeState);
        tradeInfo.setQueryCount(0);
        tradeInfo.setMchNo(String.valueOf(data.get("merchant_no"))); //商户号
        tradeInfo.setGroupNo(String.valueOf(data.get("groupno")));
        tradeInfo.setReqType((Integer) data.get("reqtype"));
        tradeInfo.setLastAccess(System.currentTimeMillis());


        getTradeProcess().doQueryProcess(tradeInfo);
    }

    /**
     * 执行通知逻辑
     * 1、查询成功后，本身会自动通知一次
     * 2、15秒后执行一次通知
     * 3、通知次数及时间间隔如何使用一个公式来确定:[15,30,60,1800,3600]
     * 4、使用配置项来进行配置
     * @param orderNo
     * @param tradeState
     * @param data
     */
    private void doNotifyProcess(String orderNo, int tradeState, Map<String, Object> data) throws IOException{
        TradeInfo tradeInfo = new TradeInfo();
        tradeInfo.setOrderNo(orderNo);
        tradeInfo.setTradeState(tradeState);
        tradeInfo.setNotifyCount(0);
        tradeInfo.setMchNo(String.valueOf(data.get("merchant_no"))); //商户号
        tradeInfo.setGroupNo(String.valueOf(data.get("groupno")));
        tradeInfo.setReqType((Integer) data.get("reqtype"));
        tradeInfo.setLastAccess(System.currentTimeMillis());

        getTradeProcess().doNotifyProcess(tradeInfo);
    }


    /**
     * 执行半小时后需要的逻辑
     * 1、未支付则进入等待队列，等待查询
     * 2、支付成功则进入leveldb,等待定时任务来清除记录
     * @param orderNo
     * @param tradeState
     * @param data
     * @throws IOException
     */
    private void doHalfHourProcess(String orderNo, int tradeState, Map<String, Object> data) throws IOException{
        TradeInfo tradeInfo = new TradeInfo();
        tradeInfo.setOrderNo(orderNo);
        tradeInfo.setTradeState(tradeState);
        tradeInfo.setMsgType(3);
        tradeInfo.setMchNo(String.valueOf(data.get("merchant_no"))); //商户号
        tradeInfo.setGroupNo(String.valueOf(data.get("groupno")));
        tradeInfo.setReqType((Integer) data.get("reqtype"));
        tradeInfo.setLastAccess(System.currentTimeMillis());

        getTradeProcess().doHalfHourProcess(tradeInfo);
    }

    /**
     * 支付成功的订单
     * @param orderNo
     * @param tradeState
     * @param data
     */
    private void doSuccessProcess(String orderNo, int tradeState, Map<String, Object> data) {
        getTradeProcess().doSuccessProcess(orderNo,tradeState);
    }
}
