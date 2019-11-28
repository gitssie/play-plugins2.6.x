package play.api.rest;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;

import java.net.URI;
import java.util.Map;

public class ReqBuilder {
    private URI baseUrl;
    private String relativeUrl;
    private String contentType;
    private RequestBuilder requestBuilder;
    private boolean hasBody;

    public ReqBuilder(String method, String baseUrl, String relativeUrl, String contentType, Map<String,String> headers, boolean hasBody) {
        this.baseUrl = URI.create(baseUrl);
        this.relativeUrl = relativeUrl;
        this.contentType = contentType;
        this.requestBuilder = RequestBuilder.create(method);
        this.hasBody = hasBody;

        if(headers != null){
            for(Map.Entry<String,String> entry : headers.entrySet()){
                requestBuilder.addHeader(entry.getKey(),entry.getValue());
            }
        }
    }

    public ReqBuilder addHeader(final String name, final String value) {
        this.requestBuilder.addHeader(name,value);
        return this;
    }

    public HttpEntity getEntity() {
        return requestBuilder.getEntity();
    }

    public ReqBuilder setEntity(final HttpEntity entity) {
        requestBuilder.setEntity(entity);
        return this;
    }

    public ReqBuilder addQueryParam(final String name, final String value) {
        requestBuilder.addParameter(name,value);
        return this;
    }

    public ReqBuilder addParameter(final String name, final String value) {
        requestBuilder.addParameter(name,value);
        return this;
    }

    public ReqBuilder setDefaultConfig(RequestConfig requestConfig) {
        requestBuilder.setConfig(requestConfig);
        return this;
    }

    public ReqBuilder setConfig(RequestConfig requestConfig) {
        requestBuilder.setConfig(requestConfig);
        return this;
    }

    public HttpUriRequest build(){
        URI relativeUrl = URI.create(this.relativeUrl);
        URI url = null;
        if(StringUtils.isNotBlank(relativeUrl.getScheme())){
            url = relativeUrl;
        }else{
            url = baseUrl.resolve(relativeUrl);
        }
        if (url == null) {
            throw new IllegalArgumentException(
                    "Malformed URL. Base: " + baseUrl + ", Relative: " + relativeUrl);
        }
        requestBuilder.setUri(url);
        return requestBuilder.build();
    }


    public void addPathParam(String name, String value) {
        if (relativeUrl == null) {
            // The relative URL is cleared when the first query parameter is set.
            throw new AssertionError();
        }
        relativeUrl = relativeUrl.replace("{" + name + "}", value);
    }

    public void setRelativeUrl(Object relativeUrl) {
        this.relativeUrl = relativeUrl.toString();
    }

}
