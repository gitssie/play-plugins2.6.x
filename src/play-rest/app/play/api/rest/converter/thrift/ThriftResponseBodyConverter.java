package play.api.rest.converter.thrift;

import org.apache.http.HttpResponse;
import org.apache.thrift.TBase;
import play.api.rest.Converter;
import play.api.rest.Rest;
import play.libs.transport.http.HTTPUtils;
import play.libs.transport.thrift.Thrifts;

import java.io.IOException;

public class ThriftResponseBodyConverter<T extends TBase> implements Converter<HttpResponse, T> {
    private Class<T> clazz;

    public ThriftResponseBodyConverter(Class<T> clazz){
        this.clazz = clazz;
    }

    @Override
    public T convert(HttpResponse value) throws IOException {
        try {
            T instance = clazz.newInstance();
            instance = Thrifts.parseForm(instance,value.getEntity().getContent());
            if(Rest.LOGGER.isDebugEnabled()){
                Rest.LOGGER.debug(instance.toString());
            }
            return instance;
        }catch (Exception e){
            throw new RuntimeException(e);
        }finally {
            HTTPUtils.closeQuietly(value);
        }
    }
}
