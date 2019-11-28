package play.api.rest;

import com.google.common.collect.Maps;
import org.junit.Test;
import play.api.rest.converter.jackson.JacksonConverterFactory;
import play.libs.concurrent.Promise;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RestTest {

    @Test
    public void testRest() throws Exception {
        Rest rest = new Rest.Builder()
                .baseUrl("https://cloud.ulopay.com")
                .addConverterFactory(JacksonConverterFactory.create())
                .build();


        TestApi api = rest.create(TestApi.class);
        Map<String, Object> params = Maps.newHashMap();
        params.put("size", 10);
        params.put("page", 0);

        Map<String, String> headers = Maps.newHashMap();
        headers.put("X-Name","JACK");
        headers.put("Cookie","SESSION=59fda363-bc5e-4478-b658-10e5555cdbe6");

        Promise<?> p = api.get(params,headers).map((resp) -> {
            System.out.println(resp);
            return null;
        });

        p.onFailure((r) -> {
            r.printStackTrace();
        });
        p.get(10, TimeUnit.SECONDS);
    }
}
