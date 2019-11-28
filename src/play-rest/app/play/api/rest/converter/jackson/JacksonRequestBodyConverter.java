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

import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.http.HttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import play.api.rest.Converter;
import play.api.rest.Rest;

import java.io.IOException;

final class JacksonRequestBodyConverter<T> implements Converter<T, HttpEntity> {
  private final ObjectWriter adapter;

  JacksonRequestBodyConverter(ObjectWriter adapter) {
    this.adapter = adapter;
  }

  @Override
  public HttpEntity convert(T value) throws IOException {
    String bytes = adapter.writeValueAsString(value);
    if(Rest.LOGGER.isDebugEnabled()){
      Rest.LOGGER.debug("request body:{}",bytes);
    }
    return new StringEntity(bytes,ContentType.APPLICATION_JSON);
  }
}