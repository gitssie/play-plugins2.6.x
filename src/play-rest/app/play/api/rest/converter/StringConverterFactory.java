package play.api.rest.converter;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import play.api.rest.Converter;
import play.api.rest.Rest;
import play.libs.transport.http.HTTPUtils;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

public class StringConverterFactory extends Converter.Factory{

    public static StringConverterFactory create() {
        return new StringConverterFactory();
    }

    @Override
    public Converter<HttpResponse, ?> responseBodyConverter(Type type, Annotation[] annotations, Rest retrofit) {
        return new StringResponseBodyConverter();
    }

    @Override
    public Converter<?, HttpEntity> requestBodyConverter(Type type, Annotation[] parameterAnnotations, Annotation[] methodAnnotations, Rest retrofit) {
        return new StringRequestBodyConverter();
    }

    public class StringRequestBodyConverter implements Converter<String, HttpEntity> {
        @Override
        public HttpEntity convert(String value) throws IOException {
            return new StringEntity(value,ContentType.TEXT_PLAIN);
        }
    }


    static final class StringResponseBodyConverter implements Converter<HttpResponse, String> {

        @Override
        public String convert(HttpResponse value) {
            try{
                return HTTPUtils.toString(value.getEntity());
            }finally {
                HTTPUtils.closeQuietly(value);
            }
        }
    }
}
