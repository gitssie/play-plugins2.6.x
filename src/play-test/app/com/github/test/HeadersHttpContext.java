package com.github.test;

import org.apache.http.HttpRequest;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.Args;

public class HeadersHttpContext extends BasicHttpContext{

    @Override
    public void setAttribute(final String id, final Object obj) {
        Args.notNull(id, "Id");
        if(obj != null && obj instanceof HttpRequest){
            HttpRequest realReq = (HttpRequest) getAttribute(id);
            if(realReq != null){
                HttpRequest proxyReq = (HttpRequest) obj;
                proxyReq.addHeader(realReq.getFirstHeader(HTTP.USER_AGENT));
            }
        }
        super.setAttribute(id,obj);
    }
}
