package play.api.rest.converter.thrift;

import org.apache.http.HttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.thrift.TBase;
import play.api.rest.Converter;
import play.api.rest.Rest;
import play.libs.transport.thrift.Thrifts;

import java.io.IOException;

public class ThriftRequestBodyConverter<T extends TBase> implements Converter<T, HttpEntity> {
    @Override
    public HttpEntity convert(T value) throws IOException {
        byte[] bytes = Thrifts.toBytes(value);
        if(Rest.LOGGER.isDebugEnabled()){
            Rest.LOGGER.debug(value.toString());
        }
        return new ByteArrayEntity(bytes, ContentType.APPLICATION_JSON);
    }
}
