package play.api.rest;

import play.api.rest.http.Body;
import play.api.rest.http.HeaderMap;
import play.api.rest.http.POST;
import play.libs.concurrent.Promise;

import java.util.Map;

public interface TestApi {

    @POST("/query/getTransApi")
    public Promise<Map<String,Object>> get(@Body Map<String,Object> params, @HeaderMap Map<String,String> headers);


    @POST("/cloud/dealercodesubmch/searchpagelist")
    public Promise<String> getString(@Body Map<String,Object> params, @HeaderMap Map<String,String> headers);
}
