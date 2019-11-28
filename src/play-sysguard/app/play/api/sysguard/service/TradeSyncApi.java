package play.api.sysguard.service;

import com.google.common.collect.Maps;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.Configuration;
import play.api.rest.Rest;
import play.api.rest.http.Body;
import play.api.rest.http.Headers;
import play.api.rest.http.POST;
import play.libs.concurrent.Promise;
import play.libs.util.Libs;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

@Singleton
public class TradeSyncApi {
    private static Logger LOGGER = LoggerFactory.getLogger(TradeSyncApi.class);

    private TradeRestApi restApi;

    private String appid;
    private String signKey;

    @Inject
    public TradeSyncApi(Rest rest, Configuration conf){
        this.restApi = rest.create(TradeRestApi.class);
        this.appid = conf.getString("play.rest.appid");
        this.signKey = conf.getString("play.rest.sign-key");
    }

    /** 订单状态
     *  NOTPAY—未支付        1
     *  SUCCESS—支付成功 2
     *  CLOSED—已关闭        3
     *  REFUND—转入退款    4
     *  REVERSE—已冲正     8
     *  REVOKED—已撤销     9
     *  USERPAYING-用户正支付   10
     *  PAYERROR-支付错误    11
     */
    public Promise<Boolean> doQuery(TradeInfo tradeInfo){
        Map<String, String> params = Maps.newHashMap();
        params.put("appid", appid);
        params.put("apicode", "03"); //查询
        params.put("nonce_str", Libs.currentSeconds()+"");
        params.put("mch_id",tradeInfo.getMchNo()); //商户号
        params.put("transaction_id",tradeInfo.getOrderNo());
        if(tradeInfo.getReqType() == 3 || tradeInfo.getReqType() == 103){
            params.put("provider_no",tradeInfo.getGroupNo());
        }
        params.put("sign",sign(signKey,params));

        return restApi.doQuery(params).map((rs) -> {
            String resultCode = rs.get("result_code");
            String tradeState = rs.get("trade_state");
            if(!StringUtils.equalsIgnoreCase("SUCCESS",resultCode)){
                LOGGER.info("query fail:{}",rs.get("return_msg"));
                return false;//业务失败,需要重新查询
            }
            //支付状态
            boolean notSure = StringUtils.equalsIgnoreCase("NOTPAY",tradeState) ||
                    StringUtils.equalsIgnoreCase("CLOSED",tradeState) ||
                    StringUtils.equalsIgnoreCase("USERPAYING",tradeState);

            return !notSure;
        });
    }

    public Promise<Boolean> doNotify(TradeInfo tradeInfo){
        Map<String, String> params = Maps.newHashMap();
        params.put("appid", appid);
        params.put("apicode", "10"); //主动通知
        params.put("nonce_str", Libs.currentSeconds()+"");
        params.put("mch_id",tradeInfo.getMchNo()); //商户号
        params.put("transaction_id",tradeInfo.getOrderNo());
        if(tradeInfo.getReqType() == 3 || tradeInfo.getReqType() == 103){
            params.put("provider_no",tradeInfo.getGroupNo());
        }
        params.put("sign",sign(signKey,params));

        return restApi.doNotify(params).map((rs) -> {
            return StringUtils.equalsIgnoreCase("SUCCESS",rs.get("result_code")) &&
                    StringUtils.equalsIgnoreCase("SUCCESS",rs.get("notify_state"));
        }).recover(r -> false);
    }

    public String sign(String signKey, Map<String, String> params) {
        params = SignUtils.paraFilter(params); //过滤掉一些不需要签名的字段
        StringBuilder buf = new StringBuilder((params.size() +1) * 10);
        SignUtils.buildPayParams(buf,params,false);
        buf.append("&key=").append(signKey);
        String preStr = buf.toString();

        String sign = "";
        //获得签名验证结果
        try {
            sign = DigestUtils.md5Hex(preStr.getBytes("UTF-8")).toUpperCase();
        } catch (Exception e) {
            sign = DigestUtils.md5Hex(preStr.getBytes()).toUpperCase();
        }

        return sign;
    }

    public interface TradeRestApi{

        @Headers({"X-FORMAT: json"})
        @POST("/secapi/pay/spi")
        Promise<Map<String,String>> doQuery(@Body Map<String,String> params);

        @Headers({"X-FORMAT: json"})
        @POST("/secapi/pay/spi")
        Promise<Map<String,String>> doNotify(@Body Map<String,String> params);
    }
}
