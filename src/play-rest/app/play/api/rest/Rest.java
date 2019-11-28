package play.api.rest;

import brave.Tracer;
import brave.Tracing;
import com.google.common.collect.Maps;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.Configuration;
import play.api.rest.converter.jackson.JacksonConverterFactory;
import play.libs.concurrent.Promise;
import play.libs.transport.http.PromiseHttpClient;
import play.libs.transport.http.client.HttpClientFactory;
import play.libs.transport.http.client.IOType;
import scala.concurrent.ExecutionContext;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.unmodifiableList;

@Singleton
public class Rest {
    public static final Logger LOGGER = LoggerFactory.getLogger(Rest.class);
    private final Map<Method, ServiceMethod<?, ?>> serviceMethodCache = new ConcurrentHashMap<>();
    private Tracer tracer;
    private PromiseHttpClient callFactory;
    private ExecutionContext callbackExecutor;
    private RequestConfig requestConfig;
    private String baseUrl;
    private boolean validateEagerly;
    private List<Converter.Factory> converterFactories;
    private Map<Class<?>,Object> apis = Maps.newConcurrentMap();

    protected Rest(){}

    @Inject
    public Rest(Tracer tracer, Configuration conf){
        PromiseHttpClient callFactory = HttpClientFactory.createHttpClient(IOType.ASYNC_NIO,conf);
        String baseUrl = conf.getString("play.rest.base-url");
        if(StringUtils.isBlank(baseUrl)){
            baseUrl = resolveBaseUrl(conf);
        }
        // Make a defensive copy of the converters.
        List<Converter.Factory> converterFactories = new ArrayList<>();
        converterFactories.add(new BuiltInConverters());
        converterFactories.add(JacksonConverterFactory.create()); //默认使用Json进行转换

        createRest(tracer,callFactory,baseUrl,converterFactories,null,false,null);
    }

    public Rest(Tracer tracer,PromiseHttpClient callFactory, String baseUrl, List<Converter.Factory> converterFactories,ExecutionContext callbackExecutor, boolean validateEagerly,RequestConfig requestConfig) {
        createRest(tracer,callFactory,baseUrl,converterFactories,callbackExecutor,validateEagerly,requestConfig);
    }

    private void createRest(Tracer tracer,PromiseHttpClient callFactory, String baseUrl, List<Converter.Factory> converterFactories,ExecutionContext callbackExecutor, boolean validateEagerly,RequestConfig requestConfig) {
        this.callFactory = callFactory;
        this.baseUrl = baseUrl;
        this.converterFactories = converterFactories;
        this.callbackExecutor = callbackExecutor;
        this.validateEagerly = validateEagerly;
        this.requestConfig = requestConfig;
        this.tracer = tracer;
        if(this.tracer == null){
            this.tracer = Tracing.newBuilder().build().tracer();
        }
        if(this.callFactory == null){
             this.callFactory = HttpClientFactory.getHttpClient();
        }
    }

    private String resolveBaseUrl(Configuration conf) {
        return String.format("%s://%s:%s","http",conf.getString("play.server.http.address"),conf.getInt("play.server.http.port"));
    }

    /**
     * Returns an unmodifiable list of the factories tried when creating a
     * {@linkplain #requestBodyConverter(Type, Annotation[], Annotation[]) request body converter}, a
     * {@linkplain #responseBodyConverter(Type, Annotation[]) response body converter}, or a
     * {@linkplain #stringConverter(Type, Annotation[]) string converter}.
     */
    public List<Converter.Factory> converterFactories() {
        return converterFactories;
    }

    /**
     * Returns a {@link Converter} for {@code type} to {@link HttpEntity} from the available
     * {@linkplain #converterFactories() factories}.
     *
     * @throws IllegalArgumentException if no converter available for {@code type}.
     */
    public <T> Converter<T, HttpEntity> requestBodyConverter(Type type,
                                                              Annotation[] parameterAnnotations, Annotation[] methodAnnotations) {
        return nextRequestBodyConverter(null, type, parameterAnnotations, methodAnnotations);
    }

    /**
     * Returns a {@link Converter} for {@code type} to {@link HttpEntity} from the available
     * {@linkplain #converterFactories() factories} except {@code skipPast}.
     *
     * @throws IllegalArgumentException if no converter available for {@code type}.
     */
    public <T> Converter<T, HttpEntity> nextRequestBodyConverter(
            @Nullable Converter.Factory skipPast, Type type, Annotation[] parameterAnnotations,
            Annotation[] methodAnnotations) {

        int start = converterFactories.indexOf(skipPast) + 1;
        for (int i = start, count = converterFactories.size(); i < count; i++) {
            Converter.Factory factory = converterFactories.get(i);
            Converter<?, HttpEntity> converter =
                    factory.requestBodyConverter(type, parameterAnnotations, methodAnnotations, this);
            if (converter != null) {
                //noinspection unchecked
                return (Converter<T, HttpEntity>) converter;
            }
        }

        StringBuilder builder = new StringBuilder("Could not locate RequestBody converter for ")
                .append(type)
                .append(".\n");
        if (skipPast != null) {
            builder.append("  Skipped:");
            for (int i = 0; i < start; i++) {
                builder.append("\n   * ").append(converterFactories.get(i).getClass().getName());
            }
            builder.append('\n');
        }
        builder.append("  Tried:");
        for (int i = start, count = converterFactories.size(); i < count; i++) {
            builder.append("\n   * ").append(converterFactories.get(i).getClass().getName());
        }
        throw new IllegalArgumentException(builder.toString());
    }


    /**
     * Returns a {@link Converter} for {@link HttpEntity} to {@code type} from the available
     * {@linkplain #converterFactories() factories}.
     *
     * @throws IllegalArgumentException if no converter available for {@code type}.
     */
    public <T> Converter<HttpResponse, T> responseBodyConverter(Type type, Annotation[] annotations) {
        return nextResponseBodyConverter(null, type, annotations);
    }

    /**
     * Returns a {@link Converter} for {@link HttpEntity} to {@code type} from the available
     * {@linkplain #converterFactories() factories} except {@code skipPast}.
     *
     * @throws IllegalArgumentException if no converter available for {@code type}.
     */
    public <T> Converter<HttpResponse, T> nextResponseBodyConverter(
            @Nullable Converter.Factory skipPast, Type type, Annotation[] annotations) {

        int start = converterFactories.indexOf(skipPast) + 1;
        for (int i = start, count = converterFactories.size(); i < count; i++) {
            Converter<HttpResponse, ?> converter =
                    converterFactories.get(i).responseBodyConverter(type, annotations, this);
            if (converter != null) {
                //noinspection unchecked
                return (Converter<HttpResponse, T>) converter;
            }
        }

        StringBuilder builder = new StringBuilder("Could not locate ResponseBody converter for ")
                .append(type)
                .append(".\n");
        if (skipPast != null) {
            builder.append("  Skipped:");
            for (int i = 0; i < start; i++) {
                builder.append("\n   * ").append(converterFactories.get(i).getClass().getName());
            }
            builder.append('\n');
        }
        builder.append("  Tried:");
        for (int i = start, count = converterFactories.size(); i < count; i++) {
            builder.append("\n   * ").append(converterFactories.get(i).getClass().getName());
        }
        throw new IllegalArgumentException(builder.toString());
    }

    /**
     * Returns a {@link Converter} for {@code type} to {@link String} from the available
     * {@linkplain #converterFactories() factories}.
     */
    public <T> Converter<T, String> stringConverter(Type type, Annotation[] annotations) {
        for (int i = 0, count = converterFactories.size(); i < count; i++) {
            Converter<?, String> converter =
                    converterFactories.get(i).stringConverter(type, annotations, this);
            if (converter != null) {
                //noinspection unchecked
                return (Converter<T, String>) converter;
            }
        }

        // Nothing matched. Resort to default converter which just calls toString().
        //noinspection unchecked
        return (Converter<T, String>) BuiltInConverters.ToStringConverter.INSTANCE;
    }

    public String baseUrl() {
        return this.baseUrl;
    }

    /**
     * Create an implementation of the API endpoints defined by the {@code service} interface.
     * For example:
     * <pre>
     * public interface CategoryService {
     *   &#64;POST("category/{cat}/")
     *   Call&lt;List&lt;Item&gt;&gt; categoryList(@Path("cat") String a, @Query("page") int b);
     * }
     * </pre>
     */
    @SuppressWarnings("unchecked") // Single-interface proxy creation guarded by parameter safety.
    public <T> T create(final Class<T> service) {
        T apiInst = (T)apis.get(service);
        if(apiInst == null) {
            Utils.validateServiceInterface(service);
            if (validateEagerly) {
                eagerlyValidateMethods(service);
            }
            final Rest rest = this;
            apiInst = (T) Proxy.newProxyInstance(service.getClassLoader(), new Class<?>[]{service},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, @Nullable Object[] args) throws Throwable {
                            // If the method is a method from Object then defer to normal invocation.
                            if (method.getDeclaringClass() == Object.class) {
                                return method.invoke(this, args);
                            }
                            /**
                             if (platform.isDefaultMethod(method)) {
                             return platform.invokeDefaultMethod(method, service, proxy, args);
                             }**/
                            ServiceMethod<Object, Object> serviceMethod =
                                    (ServiceMethod<Object, Object>) loadServiceMethod(method);
                            Callable<Promise<Object>> okHttpCall = new HttpCallable(rest, serviceMethod, args);
                            return serviceMethod.adapt(okHttpCall);
                        }
                    });
            apis.put(service,apiInst);
        }
        return apiInst;
    }


    private void eagerlyValidateMethods(Class<?> service) {
        for (Method method : service.getDeclaredMethods()) {
            loadServiceMethod(method);
        }
    }

    ServiceMethod<?, ?> loadServiceMethod(Method method) {
        ServiceMethod<?, ?> result = serviceMethodCache.get(method);
        if (result != null) return result;

        synchronized (serviceMethodCache) {
            result = serviceMethodCache.get(method);
            if (result == null) {
                result = new ServiceMethod.Builder<>(this, method).build();
                serviceMethodCache.put(method, result);
            }
        }
        return result;
    }

    public PromiseHttpClient callFactory() {
        return this.callFactory;
    }

    public Tracer tracer() {
        return this.tracer;
    }

    public RequestConfig getRequestConfig() {
        return requestConfig;
    }

    /**
     * Build a new {@link Rest}.
     * <p>
     * Calling {@link #baseUrl} is required before calling {@link #build()}. All other methods
     * are optional.
     */
    public static final class Builder {
        private Tracer tracer;
        private PromiseHttpClient callFactory;
        private RequestConfig config;
        private String baseUrl;
        private final List<Converter.Factory> converterFactories = new ArrayList<>();
        private ExecutionContext callbackExecutor;
        private boolean validateEagerly;

        public Builder() {}

        Builder(Rest retrofit) {
            callFactory = retrofit.callFactory;
            baseUrl = retrofit.baseUrl;

            converterFactories.addAll(retrofit.converterFactories);
            // Remove the default BuiltInConverters instance added by build().
            converterFactories.remove(0);

            callbackExecutor = retrofit.callbackExecutor;
            validateEagerly = retrofit.validateEagerly;
        }

        /**
         * Specify a custom call factory for creating {@link Call} instances.
         * <p>
         * Note: Calling {@link #} automatically sets this value.
         */
        public Builder callFactory(PromiseHttpClient factory) {
            this.callFactory = factory;
            return this;
        }

        public Builder requestConfig(RequestConfig config){
            this.config = config;
            return this;
        }

        /**
         * Set the API base URL.
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder tracer(Tracer tracer) {
            this.tracer = tracer;
            return this;
        }

        /** Add converter factory for serialization and deserialization of objects. */
        public Builder addConverterFactory(Converter.Factory factory) {
            converterFactories.add(factory);
            return this;
        }

        /**
         * The executor on which {@link Rest} methods are invoked when returning {@link Call} from
         * your service method.
         * <p>
         * Note: {@code executor} is not used for {@linkplain # custom method
         * return types}.
         */
        public Builder callbackExecutor(ExecutionContext executor) {
            this.callbackExecutor = executor;
            return this;
        }

        /** Returns a modifiable list of converter factories. */
        public List<Converter.Factory> converterFactories() {
            return this.converterFactories;
        }

        /**
         * When calling {@link #create} on the resulting {@link Rest} instance, eagerly validate
         * the configuration of all methods in the supplied interface.
         */
        public Builder validateEagerly(boolean validateEagerly) {
            this.validateEagerly = validateEagerly;
            return this;
        }

        /**
         * Create the {@link Rest} instance using the configured values.
         * <p>
         * Note: If neither {@link #} nor {@link #callFactory} is called a default {@link
         * OkHttpClient} will be created and used.
         */
        public Rest build() {
            if (baseUrl == null) {
                throw new IllegalStateException("Base URL required.");
            }
            // Make a defensive copy of the converters.
            List<Converter.Factory> converterFactories =
                    new ArrayList<>(1 + this.converterFactories.size());

            // Add the built-in converter factory first. This prevents overriding its behavior but also
            // ensures correct behavior when using converters that consume all types.
            converterFactories.add(new BuiltInConverters());
            converterFactories.addAll(this.converterFactories);

            return new Rest(tracer,callFactory, baseUrl, unmodifiableList(converterFactories),callbackExecutor, validateEagerly,config);
        }
    }

    public PromiseHttpClient getCallFactory() {
        return callFactory;
    }

    public ExecutionContext getCallbackExecutor() {
        return callbackExecutor;
    }
}
