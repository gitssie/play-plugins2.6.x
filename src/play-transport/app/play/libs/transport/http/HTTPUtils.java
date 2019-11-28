package play.libs.transport.http;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;

import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;

public class HTTPUtils {
    
    public static void closeQuietly(final HttpResponse resp){
        if(resp instanceof Closeable){
            IOUtils.closeQuietly((Closeable)resp);
        }
    }

    public static String toString(HttpEntity entity){
        try {
            return EntityUtils.toString(entity);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String toString(HttpEntity entity,String charset){
        try {
            return EntityUtils.toString(entity,charset);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void closeQuietly(HttpEntity entity){
        try {
            IOUtils.closeQuietly(entity.getContent());
        } catch (IOException e) {}
    }

    public static boolean isPresentAsCause(Throwable throwableToSearchIn,
        Collection<Class<? extends Throwable>> throwableToSearchFor) {
    int infiniteLoopPreventionCounter = 10;
    while (throwableToSearchIn != null && infiniteLoopPreventionCounter > 0) {
        infiniteLoopPreventionCounter--;
        for (Class<? extends Throwable> c: throwableToSearchFor) {
            if (c.isAssignableFrom(throwableToSearchIn.getClass())) {
                return true;
            }
        }
        throwableToSearchIn = throwableToSearchIn.getCause();
    }
    return false;
}
}

