package play.libs.transport.http;

import com.google.common.collect.Maps;
import play.core.parsers.FormUrlEncodedParser;
import scala.collection.JavaConversions;
import scala.collection.Seq;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.BitSet;
import java.util.Map;
import java.util.Set;

public class URLEncodedUtils {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final BitSet URLENCODER = new BitSet(256);

    public static final String CONTENT_TYPE = "application/x-www-form-urlencoded";
    
    public static String format(Map parameters, String charset) {
        return format(parameters, '&', charset);
    }

    public static String format(Map parameters, char parameterSeparator, String charset)
    {
        StringBuilder result = new StringBuilder();
        Set<String> keys = parameters.keySet();
        for (String key : keys) {
//            String encodedName = encodeFormFields(key, charset);
            String encodedName = key ;
            String encodedValue = encodeFormFields((String) parameters.get(key), charset);
            if (result.length() > 0) {
                result.append(parameterSeparator);
            }
            result.append(encodedName);
            if (encodedValue != null) {
                result.append("=");
                result.append(encodedValue);
            }
        }
        return result.toString();
    }

    private static String encodeFormFields(String content, String charset){
        if (content == null) {
            return null;
        }
        return urlEncode(content, (charset != null) ? Charset.forName(charset) : UTF_8, URLENCODER, true);
    }

    private static String encodeFormFields(String content, Charset charset) {
        if (content == null) {
            return null;
        }
        return urlEncode(content, (charset != null) ? charset : UTF_8, URLENCODER, true);
    }

    private static String urlEncode(String content, Charset charset, BitSet safechars, boolean blankAsPlus)
    {
        if (content == null) {
            return null;
        }
        StringBuilder buf = new StringBuilder();
        ByteBuffer bb = charset.encode(content);
        while (bb.hasRemaining()) {
            int b = bb.get() & 0xFF;
            if (safechars.get(b)) {
                buf.append((char) b);
            } else if ((blankAsPlus) && (b == 32)) {
                buf.append('+');
            } else {
                buf.append("%");
                char hex1 = Character.toUpperCase(Character.forDigit(b >> 4 & 0xF, 16));
                char hex2 = Character.toUpperCase(Character.forDigit(b & 0xF, 16));
                buf.append(hex1);
                buf.append(hex2);
            }
        }
        return buf.toString();
    }
    
    public static Map<String, String> urlDecode(byte[] body,String encode) throws IOException{
        return urlDecode(new String(body,encode),encode);
    }
    
    public static Map<String, String> urlDecode(String body,String encode) throws IOException{
        Map<String, String> postData = Maps.newHashMap();
        scala.collection.immutable.Map<String, Seq<String>> formData = FormUrlEncodedParser.parse(body,encode);
        Map<String, Seq<String>> map = JavaConversions.mapAsJavaMap(formData);
        for(Map.Entry<String, Seq<String>> entry : map.entrySet()){
            postData.put(entry.getKey(), entry.getValue().apply(0));
        }
        return postData;
    }
}
