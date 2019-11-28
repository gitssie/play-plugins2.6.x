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

import com.fasterxml.jackson.databind.ObjectReader;
import org.apache.http.HttpResponse;
import play.api.rest.Converter;
import play.api.rest.Rest;
import play.libs.transport.http.HTTPUtils;

import java.io.IOException;

final class JacksonResponseBodyConverter<T> implements Converter<HttpResponse, T> {
  private final ObjectReader adapter;

  JacksonResponseBodyConverter(ObjectReader adapter) {
    this.adapter = adapter;
  }

  @Override
  public T convert(HttpResponse value) throws IOException {
    try {
      String body = HTTPUtils.toString(value.getEntity(),"UTF-8");
      if(Rest.LOGGER.isDebugEnabled()){
        Rest.LOGGER.debug("response body:{}",body);
      }
      return adapter.readValue(body);
    } finally {
      HTTPUtils.closeQuietly(value);
    }
  }
}