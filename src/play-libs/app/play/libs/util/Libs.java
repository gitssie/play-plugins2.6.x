package play.libs.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Objects;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import play.libs.Json;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class Libs {

    public static boolean inArray(Object[] array,Object arr){
        return ArrayUtils.indexOf(array,arr) >= 0;
    }

    public static int toInt(String i,int dflt){
        return NumberUtils.toInt(i,dflt);
    }

    public static int toInt(Object i,int dflt){
        String str = i == null ? null : String.valueOf(i);
        return NumberUtils.toInt(str,dflt);
    }

    public static String decode(String str) {
        try {
            return URLDecoder.decode(str,"utf-8");
        } catch (UnsupportedEncodingException e) {
            return str;
        }
    }

    public static String encode(String str) {
        try {
            return URLEncoder.encode(str,"utf-8");
        } catch (UnsupportedEncodingException e) {
            return str;
        }
    }

    public static String dateTime(Date updated) {
        return FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss").format(updated);
    }

    public static String dateTime(Date date,String pattern){
        return FastDateFormat.getInstance(pattern).format(date);
    }

    public static Date dateTime(String dateAsStr,String pattern){
        try {
            return FastDateFormat.getInstance(pattern).parse(dateAsStr);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static Date dateTime(String dateAsStr) {
        try {
            return FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss").parse(dateAsStr);
        } catch (ParseException var2) {
            throw new RuntimeException(var2);
        }
    }

    public static Date dateOnly(String dateAsStr, Date dft) {
        try {
            return  FastDateFormat.getInstance("yyyy-MM-dd").parse(dateAsStr);
        } catch (ParseException var3) {
            return dft;
        }
    }

    public static Date dateOnly(String dateAsStr) {
        try {
            return  FastDateFormat.getInstance("yyyy-MM-dd").parse(dateAsStr);
        } catch (ParseException var2) {
            throw new RuntimeException(var2);
        }
    }

    public static String dateOnly(Date updated) {
        return FastDateFormat.getInstance("yyyy-MM-dd").format(updated);
    }

    public static int currentSeconds() {
        return (int)(System.currentTimeMillis() / 1000);
    }

    public static Date dateNow() {
        return new Date();
    }
    public static Date dateNow(Date d) {
        Calendar ca = Calendar.getInstance();
        if (d != null) {
            ca.setTime(d);
        }

        ca.set(11, 0);
        ca.set(12, 0);
        ca.set(13, 0);
        ca.set(14, 0);
        Date now = ca.getTime();
        return now;
    }

    public static long toLong(Long totalFee, int i) {
        return totalFee == null ? (long)i : totalFee.longValue();
    }

    public static long toLong(String phone, int i) {
        try {
            return phone == null ? (long)i : Long.valueOf(phone).longValue();
        } catch (Throwable var3) {
            return (long)i;
        }
    }

    public static double toDouble(Object d, double i) {
        if (d == null) {
            return i;
        } else {
            try {
                return d instanceof String ? Double.valueOf((String)d).doubleValue() : Double.valueOf(String.valueOf(d)).doubleValue();
            } catch (Exception var4) {
                return i;
            }
        }
    }

    public static int toFen(String totalFee) {
        double d = toDouble(totalFee, 0.0D);
        return toFen(d);
    }

    public static int toFen(double totalFee) {
        BigDecimal b = BigDecimal.valueOf(totalFee).multiply(BigDecimal.valueOf(100.0D));
        return b.intValue();
    }

    public static int toFen(BigDecimal totalFee) {
        BigDecimal b = totalFee.multiply(BigDecimal.valueOf(100.0D));
        return b.intValue();
    }

    public static double toYun(int totalFee) {
        BigDecimal b = BigDecimal.valueOf((long)totalFee).divide(BigDecimal.valueOf(100.0D));
        return b.doubleValue();
    }

    public static double toYun(long totalFee) {
        BigDecimal b = BigDecimal.valueOf(totalFee).divide(BigDecimal.valueOf(100.0D));
        return b.doubleValue();
    }

    public static boolean isEmpty(List<?> ls) {
        return ls == null || ls.size() == 0;
    }

    public static String either(String left, String right) {
        return !StringUtils.isEmpty(left) ? left : StringUtils.trim(right);
    }

    public static String either(Object left, String right) {
        return left != null ? StringUtils.trim(String.valueOf(left)) : StringUtils.trim(right);
    }


    public static String[] split(String str, String split) {
        if (str != null && split.length() >= 1 && split.trim().length() >= 1) {
            String[] sp = str.split(split);
            return sp;
        } else {
            return new String[0];
        }
    }

    public static String[] split(String str) {
        return split(str, ",");
    }

    public static List<Integer> splitInt(String str) {
        String[] ids = split(str, ",");
        List<Integer> list = new ArrayList(ids.length);

        for(int i = 0; i < ids.length; ++i) {
            try {
                list.add(Integer.valueOf(ids[i]));
            } catch (Exception var5) {
                ;
            }
        }

        return list;
    }


    public static Date dateOfDayEnd(Date day) {
        Calendar ca = Calendar.getInstance();
        ca.setTime(day);
        ca.add(11, 23);
        ca.add(12, 59);
        ca.add(13, 59);
        return ca.getTime();
    }

    public static Date firstDay(Date month) {
        Calendar ca = Calendar.getInstance();
        ca.setTime(month);
        ca.set(5, 1);
        ca.set(11, 0);
        ca.set(12, 0);
        ca.set(13, 0);
        ca.set(14, 0);
        return ca.getTime();
    }

    public static Date firstWeekDay(Date d) {
        Calendar ca = Calendar.getInstance();
        ca.setTime(d);
        ca.set(7, 2);
        ca.set(11, 0);
        ca.set(12, 0);
        ca.set(13, 0);
        ca.set(14, 0);
        return ca.getTime();
    }

    public static Date lastWeekDay(Date d) {
        Calendar ca = Calendar.getInstance();
        ca.setTime(d);
        ca.set(4, ca.get(4) + 1);
        ca.set(11, 0);
        ca.set(12, 0);
        ca.set(13, 0);
        ca.set(14, 0);
        return firstWeekDay(ca.getTime());
    }

    /**
     * 根据指定的属性名，判断这些属性在两个对象中是否一致，不一致则返回false，一致则返回true
     * @param left
     * @param right
     * @param fields
     * @return
     */
    public static boolean isEquals(Object left,Object right,String... fields){
        if(left == null || right == null){
            return false;
        }else if(left == right){
            return true;
        }else if(fields.length == 0){
            return Objects.equal(left,right);
        }
        JsonNode nodeLeft = Json.toJson(left);
        JsonNode nodeRight = Json.toJson(right);
        JsonNode node1,node2;
        for(String field : fields){
            node1 = nodeLeft.get(field);
            node2 = nodeRight.get(field);

            //对比两个属性值是否相等
            if(node1 != null && !node1.equals(node2)){
                return false;
            }else if(node1 == null && node2 != null){
                return false;
            }
        }
        return true;

    }
}
