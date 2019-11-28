package play.libs.util;

import com.google.common.collect.Maps;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import play.Logger;
import play.api.libs.Codecs;

import javax.crypto.*;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.*;


/**
 * ClassName:SignUtils
 * Function: 签名用的工具箱
 * Date:     2014-6-27 下午3:22:33
 * @author   尹有松
 */
public class SignUtils {

    /**
     * 签名
     * @param signType
     * @param charset
     * @param signKey
     * @param params
     * @return
     */
    public static String sign(String signType, String charset, String signKey, Map<String, String> params) {
        params = SignUtils.paraFilter(params); //过滤掉一些不需要签名的字段
        StringBuilder buf = new StringBuilder((params.size() +1) * 10);
        SignUtils.buildPayParams(buf,params,false);
        buf.append("&key=").append(signKey.trim());
        String preStr = buf.toString();
        String sign = "";
        try{
            if("MD5".equalsIgnoreCase(signType)){
                sign = DigestUtils.md5Hex(preStr.getBytes(charset));
            }else{
                return null;
            }
        }catch (Exception e){
            throw new RuntimeException(e);
        }
        return sign;
    }

    /***
     * 跟拒键值-过滤字符
     * @param sArray
     * @return
     */
    public static Map<String, String> paraFilterByKeySet(Map<String, String> sArray, Set<String> set) {
        Map<String, String> result = new HashMap<String, String>(sArray.size());
        if (sArray == null || sArray.size() <= 0) {
            return result;
        }
        for (String key : sArray.keySet()) {
            String value = sArray.get(key);
            if (value == null || value.equals("") || set.contains(key)) {
                continue;
            }
            result.put(key, value);
        }
        return result;
    }

    /**
     * 过滤参数
     * @author 尹有松
     * @param sArray
     * @return
     */
    public static Map paraFilter(Map<String, ?> sArray) {
        Map<String, Object> result = Maps.newHashMap();
        if (sArray == null || sArray.size() <= 0) {
            return result;
        }
        for (String key : sArray.keySet()) {
            String value = sArray.get(key) == null ? null : String.valueOf(sArray.get(key));
            if (value == null || value.equals("") || key.equalsIgnoreCase("sign")
                    || key.equalsIgnoreCase("sign_key")) {
                continue;
            }
            result.put(key,sArray.get(key));
        }
        return result;
    }

    /**
     * 过滤参数
     * @author 尹有松
     * @param sArray
     * @return
     */
    public static Map paraFilter(Map<String, ?> sArray,String ... keys) {
        Map<String, Object> result = Maps.newHashMap();
        if (sArray == null || sArray.size() <= 0) {
            return result;
        }
        for (String key : sArray.keySet()) {
            String value = sArray.get(key) == null ? null : String.valueOf(sArray.get(key));
            if (value == null || value.equals("") || Libs.inArray(keys, key)) {
                continue;
            }
            result.put(key,sArray.get(key));
        }
        return result;
    }

    /**
     * 过滤参数
     * @author 尹有松
     * @param sArray
     * @return
     */
    public static Map paraFilter2(Map<String, ?> sArray,String ... keys) {
        Map<String, Object> result = new TreeMap<String, Object>();
        if (sArray == null || sArray.size() <= 0) {
            return result;
        }
        for (String key : sArray.keySet()) {
            String value = sArray.get(key) == null ? null : String.valueOf(sArray.get(key));
            if (value == null || value.equals("") || Libs.inArray(keys, key)) {
                continue;
            }
            result.put(key,sArray.get(key));
        }
        return result;
    }
    public static Map paraFilter3(Map<String, ?> sArray,String ... keys) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (sArray == null || sArray.size() <= 0) {
            return result;
        }
        for (String key : sArray.keySet()) {
            String value = sArray.get(key) == null ? null : String.valueOf(sArray.get(key));
            if (value == null || value.equals("") || Libs.inArray(keys, key)) {
                continue;
            }
            result.put(key,sArray.get(key));
        }
        return result;
    }

    /**
     * 过滤参数(支付宝专有，与微信不同的是签名的时候过滤了sign_type)
     * @author 尹有松
     * @param sArray
     * @return
     */
    public static Map<String, String> alipayParaFilter(Map<String, String> sArray) {
        Map<String, String> result = new HashMap<String, String>(sArray.size());
        if (sArray == null || sArray.size() <= 0) {
            return result;
        }
        for (String key : sArray.keySet()) {
            String value = sArray.get(key);
            if (value == null || value.equals("") || key.equalsIgnoreCase("sign")
                    || key.equalsIgnoreCase("sign_type")
                    || key.equalsIgnoreCase("sign_key")) {
                continue;
            }
            result.put(key, value);
        }
        return result;
    }

    public static String payParamsToString(Map<String, String> payParams){
        return payParamsToString(payParams,false);
    }

    public static String payParamsToString(Map<String, String> payParams,boolean encoding){
        return payParamsToString(new StringBuilder(),payParams,encoding);
    }
    /**
     * @author 尹有松
     * @param payParams
     * @return
     */
    public static String payParamsToString(StringBuilder sb,Map<String, String> payParams,boolean encoding){
        buildPayParams(sb,payParams,encoding);
        return sb.toString();
    }

    /**
     * @author 尹有松
     * @param payParams
     * @return
     */
    public static void buildPayParams(StringBuilder sb,Map<String, String> payParams,boolean encoding){
        List<String> keys = new ArrayList<String>(payParams.keySet());
        Collections.sort(keys);
        for(String key : keys){
            sb.append(key).append("=");
            if(encoding){
                sb.append(urlEncode(payParams.get(key)));
            }else{
                sb.append(payParams.get(key));
            }
            sb.append("&");
        }
        sb.setLength(sb.length() - 1);
    }

    /** <一句话功能简述>
     * <功能详细描述>支付宝扫码支付参数组装
     * @param sb
     * @param payParams
     * @param encoding
     * @see [类、类#方法、类#成员]
     */
    public static void buildAliPayParams(StringBuilder sb,Map<String, String> payParams,boolean encoding){
        List<String> keys = new ArrayList<String>(payParams.keySet());
        Collections.sort(keys);
        for(String key : keys){
            if(StringUtils.isNotEmpty(payParams.get(key)) && !"null".equals(payParams.get(key))){
                sb.append(key).append("=\"");
                if(encoding){
                    sb.append(urlEncode(payParams.get(key)));
                }else{
                    sb.append(payParams.get(key));
                }
                sb.append("\"&");
            }
        }
        sb.setLength(sb.length() - 1);
    }
    /** <一句话功能简述>
     * <功能详细描述>支付宝app支付参数组装
     * @param sb
     * @param payParams
     * @param encoding
     * @see [类、类#方法、类#成员]
     */
    public static void buildAliPayAppParams(StringBuilder sb,Map<String, String> payParams,boolean encoding){
        List<String> keys = new ArrayList<String>(payParams.keySet());
        Collections.sort(keys);
        for(String key : keys){
            if(StringUtils.isNotEmpty(payParams.get(key)) && !"null".equals(payParams.get(key))){
                sb.append(key).append("=");
                if(encoding){
                    sb.append(urlEncode(payParams.get(key)));
                }else{
                    sb.append(payParams.get(key));
                }
                sb.append("&");
            }
        }
        sb.setLength(sb.length() - 1);
    }

    public static String urlEncode(String str){
        try {
            return URLEncoder.encode(str, "UTF-8");
        } catch (Throwable e) {
            return str;
        }
    }

    /**
     * 旧版本的DES加密
     * @author 尹有松
     * @param src
     * @param decryptKey
     * @return
     */
    public static String encryptDES(String src, String decryptKey) {
        try {
            // DES算法要求有一个可信任的随机数源
            SecureRandom sr = new SecureRandom();
            // 从原始密匙数据创建DESKeySpec对象
            DESKeySpec dks = new DESKeySpec(decryptKey.getBytes("UTF-8"));
            // 创建一个密匙工厂，然后用它把DESKeySpec转换成
            // 一个SecretKey对象
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
            SecretKey securekey = keyFactory.generateSecret(dks);
            // Cipher对象实际完成加密操作
            Cipher cipher = Cipher.getInstance("DES");
            // 用密匙初始化Cipher对象
            cipher.init(Cipher.ENCRYPT_MODE, securekey, sr);
            // 现在，获取数据并加密
            // 正式执行加密操作
            byte[] bts = cipher.doFinal(src.getBytes("UTF-8"));
            return Codecs.toHexString(bts);
        } catch (Exception e) {
            Logger.error("encryptDES",e);
        }
        return null;
    }


    /**
     *
     * encryptDES:旧版本的DES解密
     *
     * @author 尹有松
     * @param hexStr
     * @param decryptKey
     * @return
     * @throws Exception
     */
    public static String decryptDES(String hexStr, String decryptKey){
        try {
            byte[] src = Codecs.hexStringToByte(hexStr);
            // DES算法要求有一个可信任的随机数源
            SecureRandom sr = new SecureRandom();
            // 从原始密匙数据创建一个DESKeySpec对象
            DESKeySpec dks = new DESKeySpec(decryptKey.getBytes("UTF-8"));
            // 创建一个密匙工厂，然后用它把DESKeySpec对象转换成
            // 一个SecretKey对象
            SecretKeyFactory keyFactory = SecretKeyFactory.getInstance("DES");
            SecretKey securekey = keyFactory.generateSecret(dks);
            // Cipher对象实际完成解密操作
            Cipher cipher = Cipher.getInstance("DES");
            // 用密匙初始化Cipher对象
            cipher.init(Cipher.DECRYPT_MODE, securekey, sr);
            // 现在，获取数据并解密
            // 正式执行解密操作
            return new String(cipher.doFinal(src));
        } catch (Exception e) {
            Logger.error("decryptDES",e);
        }
        return null;
    }

    public static String encryptAES(String content,String encryptKey){
        try{
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
            random.setSeed(encryptKey.getBytes());

            KeyGenerator kgen = KeyGenerator.getInstance("AES");
            kgen.init(128, random);
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kgen.generateKey().getEncoded(), "AES"));

            byte[] bytes = cipher.doFinal(content.getBytes());

            return org.apache.commons.codec.binary.Base64.encodeBase64String(bytes);
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    public static String decryptAES(String content,String encryptKey){
        try{
            SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
            random.setSeed(encryptKey.getBytes());

            KeyGenerator kgen = KeyGenerator.getInstance("AES");
            kgen.init(128, random);
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(kgen.generateKey().getEncoded(), "AES"));

            byte[] bytes = cipher.doFinal(org.apache.commons.codec.binary.Base64.decodeBase64(content));

            return new String(bytes);
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    /**
     *
     * encryptHMAC:HMACSHA1加密
     *
     * @author zhouhouqi
     * @param encryptText
     * @param encryptKey
     * @return
     */
    public static String encryptHMAC(String encryptText, String encryptKey){
        try {
            byte[] data = encryptKey.getBytes("UTF-8");
            //根据给定的字节数组构造一个密钥,第二参数指定一个密钥算法的名称
            SecretKey secretKey = new SecretKeySpec(data, "HmacSHA1");
            //生成一个指定 Mac 算法 的 Mac 对象
            Mac mac = Mac.getInstance("HmacSHA1");
            //用给定密钥初始化 Mac 对象
            mac.init(secretKey);
            byte[] text = encryptText.getBytes("UTF-8");
            //完成 Mac操作
            return org.apache.commons.codec.binary.Base64.encodeBase64String((mac.doFinal(text)));
        } catch (Exception e) {
            Logger.error("签名失败！"+e.getMessage(), e);
        }
        return null;
    }

    /**
     * @author wenbisheng
     * @param payParams
     * @return
     */
    public static String buildJoinPayParams(Map<String, String> payParams){
        StringBuilder sb = new StringBuilder();
        List<String> keys = new ArrayList<String>(payParams.keySet());
        for(String key : keys){
            sb.append(payParams.get(key));
        }
        return sb.toString();
    }

}

