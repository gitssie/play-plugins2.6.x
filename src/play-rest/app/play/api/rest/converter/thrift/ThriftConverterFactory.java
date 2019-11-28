package play.api.rest.converter.thrift;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import play.api.rest.Converter;
import play.api.rest.Rest;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

public class ThriftConverterFactory extends Converter.Factory{
    public static ThriftConverterFactory create() {
        return new ThriftConverterFactory();
    }

    @Override
    public Converter<HttpResponse, ?> responseBodyConverter(Type type, Annotation[] annotations, Rest retrofit) {
        return new ThriftResponseBodyConverter((Class) type);
    }

    @Override
    public Converter<?, HttpEntity> requestBodyConverter(Type type, Annotation[] parameterAnnotations, Annotation[] methodAnnotations, Rest retrofit) {
        return new ThriftRequestBodyConverter();
    }
}
