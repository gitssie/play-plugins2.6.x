package play.libs.util;

import com.google.common.collect.Maps;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.util.*;

public class XmlUtils {

	/**
	 * 转XMLmap
	 * 
	 * @author 尹有松
	 * @param xmlBytes
	 * @param charset
	 * @return
	 * @throws Exception
	 */
	public static Map<String, String> toMap(byte[] xmlBytes, String charset)
			throws Exception {
		SAXReader reader = new SAXReader(false);
		reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
		reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		InputSource source = new InputSource(new ByteArrayInputStream(xmlBytes));
		source.setEncoding(charset);
		Document doc = reader.read(source);
		Map<String, String> params = XmlUtils.toMap(doc.getRootElement());
		return params;
	}

	/**
	 * 转MAP
	 * @author 尹有松
	 * @param element
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, String> toMap(Element element) {
		Map<String, String> rest = new HashMap<String, String>();
		List<Element> els = element.elements();
		for (Element el : els) {
			rest.put(el.getName().toLowerCase(), el.getText());
		}
		return rest;
	}

	public static String toXml(Map<String, String> params) {
		StringBuilder buf = new StringBuilder();
		List<String> keys = new ArrayList<String>(params.keySet());
		Collections.sort(keys);
		buf.append("<xml>");
		for (String key : keys) {
			buf.append("<").append(key).append(">");
			buf.append("<![CDATA[").append(params.get(key)).append("]]>");
			buf.append("</").append(key).append(">\n");
		}
		buf.append("</xml>");
		return buf.toString();
	}
	public static Map<String, String> toJdMap(String body, String charset)
			throws Exception {
		SAXReader reader = new SAXReader(false);
		reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
		reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		InputSource source = new InputSource(new ByteArrayInputStream(body.getBytes("UTF-8")));
		source.setEncoding(charset);
		Document doc = reader.read(source);
		Map<String, String> params = XmlUtils.toJdMap(doc.getRootElement());
		return params;
	}
	public static Map<String, String> toJdMap(Element element) {
		Map<String, String> rest = new HashMap<String, String>();
		List<Element> els1 = element.elements();
		for (Element el1 : els1) {
			rest.put(el1.getName().toLowerCase(), el1.getText());
			List<Element> els2 = el1.elements();
			for (Element el2 : els2) {
				List<Element> els3 = el2.elements();
				rest.put(el2.getName().toLowerCase(), el2.getText());
				for (Element el3 : els3) {
					rest.put(el3.getName().toLowerCase(), el3.getText());
				}
			}
		}
		if (rest.containsKey("result")) {
			rest.remove("result");
		}
		return rest;
	}
	public static String toJdXml(Map<String, String> params) {
		StringBuilder buf = new StringBuilder();
		List<String> keys = new ArrayList<String>(params.keySet());
		buf.append("<jdpay>\n");
		for (String key : keys) {
			buf.append("  <").append(key).append(">");
			buf.append(params.get(key));
			buf.append("</").append(key).append(">\n");
		}
		buf.append("</jdpay>");
		return buf.toString();
	}

	/**
	 * 支付宝扫码
	 * 
	 * @param xmlBytes
	 * @param charset
	 * @return
	 * @throws Exception
	 */
	public static Map<String, String> toAlipayMap(byte[] xmlBytes,
			String charset) throws Exception {
		SAXReader reader = new SAXReader(false);
		reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
		reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		InputSource source = new InputSource(new ByteArrayInputStream(xmlBytes));
		source.setEncoding(charset);
		Document doc = reader.read(source);
		Map<String, String> params=new HashMap<String,String>();
		params.putAll(getMap(doc));
		return params;
	}

	/**
	 * 支付宝查询接口
	 * 
	 * @param xmlBytes
	 * @param charset
	 * @return
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public static Map<String, String> toAlipayQueryMap(byte[] xmlBytes,
			String charset) throws Exception {
		SAXReader reader = new SAXReader(false);
		reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
		reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		InputSource source = new InputSource(new ByteArrayInputStream(xmlBytes));
		source.setEncoding(charset);
		Document doc = reader.read(source);
		Map<String, String> params=new HashMap<String,String>();
		params.putAll(getMap(doc));
		if (params.containsKey("result_code")) {
		  if ("success".equalsIgnoreCase(params.get("result_code"))) {// 查询成功了
			Element e = doc.getRootElement().element("response").element("alipay").element("fund_bill_list");
			if (e != null) {
				List<Element> fund_bill_list = e.elements();
				StringBuffer sb = new StringBuffer();
				for (Element el : fund_bill_list) {
					sb.append(el.asXML());
				}
				params.put("fund_bill_list", "<fund_bill_list>" + sb.toString()+"</fund_bill_list>");
			}
		}
	  }
		return params;
	}
	
	/**
	 * 支付宝xml解析公共方法
	 * @param doc
	 * @return
	 */
	public static Map<String, String> getMap(Document doc){
		Map<String, String> params = XmlUtils.toMap(doc.getRootElement());
	
		if (params.containsKey("response")) {
			params.remove("response");
		}
		if (params.containsKey("request")) {
			params.remove("request");
		}
		//判断是否请求成功
		if("T".equals(params.get("is_success"))){//请求成功
			Map<String, String> params1 = XmlUtils.toMap(doc.getRootElement().element("response").element("alipay"));
			params.putAll(params1);
		}	
		return params;
	}
	
	/**
     * 国采查询返回接口解析
     * 
     * @param xmlBytes
     * @param charset
     * @return
     * @throws Exception
     */
    public static Map<String, String> toGCWXMap(byte[] xmlBytes,
            String charset) throws Exception {
        SAXReader reader = new SAXReader(false);
		reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
		reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        InputSource source = new InputSource(new ByteArrayInputStream(xmlBytes));
        source.setEncoding(charset);
        Document doc = reader.read(source);
        Map<String, String> params=new HashMap<String,String>();
        params.putAll(getMapW(doc));
        return params;
    }
	
	public static Map<String,String> getMapW(Document doc){
	    Map<String, String> params = XmlUtils.toMap(doc.getRootElement());
	    if("00".equals(params.get("retcode"))){
	        Element el = doc.getRootElement().element("data");
	        if(null != el){
	            List<Element> list = el.elements();
	            if(list.size() > 1){
	                params.put("record_count", list.size() + "");
	                Map<String,String> recordMap = Maps.newHashMap();
	                for (int i = 0;i<list.size();i ++) {
	                    Map<String, String> params1 = XmlUtils.toMap(list.get(i));
	                    Iterator<String> ite = params1.keySet().iterator();
	                    while (ite.hasNext()) {
                            String key = ite.next();
                            recordMap.put(key+"_"+i, params1.get(key));
                        }
                    }
	                params.putAll(recordMap);
	            }else{
	                params.put("record_count", "1");
	                Map<String, String> params1 = XmlUtils.toMap(el.element("record"));
	                params.putAll(params1);
	            }
	        }
	    }
	    return params;
	}

	@SuppressWarnings("unchecked")
	public static Map<String, String> toMapA(Element element) {
		Map<String, String> rest = new HashMap<String, String>();
		List<Element> els = element.elements();
		for (Element el : els) {
			rest.put(el.attributeValue("name").toLowerCase(), el.getText());
		}
		return rest;
	}
	
	public static void main(String[] args) {
        String xml = "<?xml version=\"1.0\" encoding=\"GB2312\" ?>"
                    +"<root>"
                    +"<cur_type>CNY</cur_type>"
                    +"<listid>3021800559974160422000013148</listid>"
                    +"<pay_params></pay_params>"
                    +"<pay_type>800201</pay_type>"
                    +"<qrcode>weixin://wxpay/bizpayurl?pr=MF8TucE</qrcode>"
                    +"<retcode>00</retcode>"
                    +"<retmsg>操作成功</retmsg>"
                    +"<sign>a154e7ff5b1c0137aa246439403c42a6</sign>"
                    +"<sp_billno>1000201604221000001</sp_billno>"
                    +"<spid>1800559974</spid>"
                    +"<sysd_time>20160422152008</sysd_time>"
                    +"<tran_amt>1</tran_amt>"
                    +"</root>";
        
        String xmls = "<?xml version=\"1.0\" encoding=\"GB2312\" ?>"
                +"<root>"
                    +"<data>"
                        +"<record>"
                           +"<item_attach></item_attach>"
                           +"<item_name></item_name>"
                           +"<listid>3021800559974160423000013174</listid>"
                           +"<pay_type>800201</pay_type>"
                           +"<sp_billno>1000201604231000011</sp_billno>"
                           +"<spid>1800559974</spid>"
                            +"<state>5</state>"
                            +"<tran_amt>1</tran_amt>"
                           +"</record>"
                    +"</data>"
                    +"<retcode>00</retcode>"
                    +"<retmsg>操作成功</retmsg>"
                    +"<sign>12fdc9d4fb415be9c3486fac272d9a59</sign>"
                    +"<sign_type>MD5</sign_type>"
                    +"<spid>1800559974</spid>"
                +"</root>";
        
        try {
            Map<String, String> map = toGCWXMap(xmls.getBytes(), "UTF-8");
//            Pattern p = Pattern.compile("<data>(.*)</data>");  
//            Matcher m = p.matcher(xmls);  
//            if(m.find()){  
//            System.out.println(m.group(1));  
//            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
	

}
