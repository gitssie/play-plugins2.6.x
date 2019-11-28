/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package play.api.rest.converter.jackson;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import play.api.rest.Converter;
import play.api.rest.Rest;
import play.libs.Json;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * A {@linkplain Converter.Factory converter} which uses Jackson.
 * <p>
 * Because Jackson is so flexible in the types it supports, this converter assumes that it can
 * handle all types. If you are mixing JSON serialization with something else (such as protocol
 * buffers), you must {@linkplain Retrofit.Builder#addConverterFactory(Converter.Factory) add this
 * instance} last to allow the other converters a chance to see their types.
 */
public final class JacksonConverterFactory extends Converter.Factory {
  /** Create an instance using a default {@link ObjectMapper} instance for conversion. */
  public static JacksonConverterFactory create() {
    return create(Json.newDefaultMapper());
  }

  /** Create an instance using {@code mapper} for conversion. */
  @SuppressWarnings("ConstantConditions") // Guarding public API nullability.
  public static JacksonConverterFactory create(ObjectMapper mapper) {
    if (mapper == null) throw new NullPointerException("mapper == null");
    return new JacksonConverterFactory(mapper);
  }

  private final ObjectMapper mapper;

  private JacksonConverterFactory(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Converter<HttpResponse, ?> responseBodyConverter(Type type, Annotation[] annotations, Rest retrofit) {
    JavaType javaType = mapper.getTypeFactory().constructType(type);
    ObjectReader reader = mapper.readerFor(javaType);
    return new JacksonResponseBodyConverter<>(reader);
  }

  @Override
  public Converter<?, HttpEntity> requestBodyConverter(Type type,Annotation[] parameterAnnotations, Annotation[] methodAnnotations, Rest retrofit) {
    JavaType javaType = mapper.getTypeFactory().constructType(type);
    ObjectWriter writer = mapper.writerFor(javaType);
    return new JacksonRequestBodyConverter<>(writer);
  }
}