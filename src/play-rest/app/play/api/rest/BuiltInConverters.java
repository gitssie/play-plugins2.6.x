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
package play.api.rest;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import play.api.rest.converter.StringConverterFactory;
import play.api.rest.http.Streaming;
import play.libs.transport.http.HTTPUtils;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

final class BuiltInConverters extends Converter.Factory {
  @Override
  public Converter<HttpResponse, ?> responseBodyConverter(Type type, Annotation[] annotations, Rest retrofit) {
    if (type == HttpResponse.class) {
      return Utils.isAnnotationPresent(annotations, Streaming.class)
          ? StreamingResponseBodyConverter.INSTANCE
          : BufferingResponseBodyConverter.INSTANCE;
    }
    if (type == Void.class) {
      return VoidResponseBodyConverter.INSTANCE;
    }
    if (type == String.class){
      return StringResponseBodyConverter.INSTANCE;
    }
    return null;
  }

  @Override
  public Converter<?, HttpEntity> requestBodyConverter(Type type,
      Annotation[] parameterAnnotations, Annotation[] methodAnnotations, Rest retrofit) {
    if (HttpEntity.class.isAssignableFrom(Utils.getRawType(type))) {
        return RequestBodyConverter.INSTANCE;
    }
    return null;
  }

  static final class VoidResponseBodyConverter implements Converter<HttpResponse, Void> {
    static final VoidResponseBodyConverter INSTANCE = new VoidResponseBodyConverter();

    @Override
    public Void convert(HttpResponse value) {
      HTTPUtils.closeQuietly(value);
      return null;
    }
  }

  static final class StringResponseBodyConverter implements Converter<HttpResponse, String> {
    static final StringResponseBodyConverter INSTANCE = new StringResponseBodyConverter();

    @Override
    public String convert(HttpResponse value) {
      try{
        return HTTPUtils.toString(value.getEntity());
      }finally {
        HTTPUtils.closeQuietly(value);
      }
    }
  }

  static final class RequestBodyConverter implements Converter<HttpEntity, HttpEntity> {
    static final RequestBodyConverter INSTANCE = new RequestBodyConverter();

    @Override
    public HttpEntity convert(HttpEntity value) {
      return value;
    }
  }

  static final class StreamingResponseBodyConverter
      implements Converter<HttpResponse, HttpResponse> {
    static final StreamingResponseBodyConverter INSTANCE = new StreamingResponseBodyConverter();

    @Override
    public HttpResponse convert(HttpResponse value) {
      return value;
    }
  }
  static final class BufferingResponseBodyConverter
      implements Converter<HttpResponse, HttpResponse> {
    static final BufferingResponseBodyConverter INSTANCE = new BufferingResponseBodyConverter();

    @Override
    public HttpResponse convert(HttpResponse value) throws IOException {
      return value;
    }
  }
  static final class ToStringConverter implements Converter<Object, String> {
    static final ToStringConverter INSTANCE = new ToStringConverter();

    @Override
    public String convert(Object value) {
      return value.toString();
    }
  }
}