package play.libs.transport.thrift;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.thrift.TBase;
import org.apache.thrift.TFieldIdEnum;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TIOStreamTransport;
import org.apache.thrift.transport.TTransport;
import play.libs.concurrent.Promise;
import play.mvc.Http.RawBuffer;
import play.mvc.Result;
import play.mvc.Results;

import java.io.ByteArrayInputStream;
import java.io.InputStream;


public class Thrifts {

    private static GenericObjectPool<ByteArrayOutputStream>  BYTE_POOL = null;


    private static GenericObjectPool<ByteArrayOutputStream> getPool(){
        if(BYTE_POOL != null){
            return BYTE_POOL;
        }else{
            synchronized (Thrifts.class){
                if(BYTE_POOL == null){
                    GenericObjectPoolConfig config = new GenericObjectPoolConfig();
                    config.setMaxTotal(1 << 14);
                    config.setBlockWhenExhausted(false);

                    BYTE_POOL = new GenericObjectPool(new BasePooledObjectFactory<ByteArrayOutputStream>(){
                        @Override
                        public ByteArrayOutputStream create() {
                            return new ByteArrayOutputStream();
                        }
                        @Override
                        public PooledObject<ByteArrayOutputStream> wrap(ByteArrayOutputStream buffer) {
                            return new DefaultPooledObject<ByteArrayOutputStream>(buffer);
                        }
                        @Override
                        public void passivateObject(PooledObject<ByteArrayOutputStream> pooledObject) {
                            pooledObject.getObject().reset();
                        }
                    },config);
                }
            }
        }
        return BYTE_POOL;
    }

    public static <A> ThriftPromise<A> promise(){
        return new ThriftPromise<A>();
    }
    
    public static TProtocol createTProtocol(TTransport transport){
        return new TBinaryProtocol(transport); //使用二进制传输
    }
    
    public static <A extends TBase> byte[] toBytes(A base){
        ByteArrayOutputStream out = null;
        try {
            out = getPool().borrowObject();
            TIOStreamTransport sport = new TIOStreamTransport(out);
            TProtocol protocol = createTProtocol(sport);
            base.write(protocol);
            sport.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }finally {
            if(out != null){
                getPool().returnObject(out);
            }
        }
    }
    
    public static <A extends TBase> A newInstance(Class<A> clazz){
        try {
            A inst = clazz.newInstance();
            return inst;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public static <A extends TBase> A newInstance(Class<A> clazz,TFieldIdEnum field,Object fieldValue){
        try {
            A inst = clazz.newInstance();
            inst.setFieldValue(field, fieldValue);
            return inst;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public static <A extends TBase> A setFieldValue(A inst,TFieldIdEnum field,Object fieldValue){
        try {
            inst.setFieldValue(field, fieldValue);
            return inst;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public static <A extends TBase> InputStream toStream(A base){
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            TIOStreamTransport sport = new TIOStreamTransport(out);
            TProtocol protocol = createTProtocol(sport);
            base.write(protocol);
            sport.close();
            return new ByteArrayInputStream(out.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 需要处理协议转换的错误
     * @author 尹有松
     * @param p
     * @return
     */
    public static <A extends TBase> Promise<Result> toResult(Promise<A> p){
        return p.map(a -> {
            byte[] bytes = Thrifts.toBytes(a);
            return Results.ok(bytes);
        });
    }
    
    public static <A extends TBase> A parseForm(Class<A> clazz,RawBuffer buf){
        return parseForm(clazz, buf.asBytes().toArray());
    }
    
    public static <A extends TBase> A parseForm(Class<A> clazz,byte[] bytes){
        try {
            A instance = clazz.newInstance();
            return parseForm(instance, bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public static <A extends TBase> A parseForm(A instance,byte[] bytes) throws Exception{
        TIOStreamTransport sport = new TIOStreamTransport(new ByteArrayInputStream(bytes));
        TProtocol protocol = createTProtocol(sport);
        instance.read(protocol);
        return instance;
    }
    
    public static <A extends TBase> A parseForm(A instance,InputStream in) throws Exception{
        TIOStreamTransport sport = new TIOStreamTransport(in);
        TProtocol protocol = createTProtocol(sport);
        instance.read(protocol);
        return instance;
    }
}
