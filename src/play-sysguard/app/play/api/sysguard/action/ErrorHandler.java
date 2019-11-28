package play.api.sysguard.action;

import com.typesafe.config.Config;
import play.Environment;
import play.api.OptionalSourceMapper;
import play.api.routing.Router;
import play.http.DefaultHttpErrorHandler;
import play.mvc.Http.RequestHeader;
import play.mvc.Result;
import play.mvc.Results;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
public class ErrorHandler extends DefaultHttpErrorHandler {
    private Environment environment;

    @Inject
    public ErrorHandler(Config config, Environment environment,
                        OptionalSourceMapper sourceMapper, Provider<Router> routes) {
        super(config, environment, sourceMapper, routes);
        this.environment = environment;
    }

    @Override
    public CompletionStage<Result> onClientError(RequestHeader request, int statusCode, String message) {
        if(statusCode == 401){
            return CompletableFuture.completedFuture(Results.redirect("/assets/login.html"));
        }else{
            return super.onClientError(request,statusCode,message);
        }
    }
}