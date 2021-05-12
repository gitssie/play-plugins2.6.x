package com.github.test;


import brave.Tracer;
import brave.internal.HexCodec;
import org.joda.time.DateTime;
import play.api.jobs.TaskScheduler;
import play.api.trace.Traced;
import play.mvc.Controller;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Date;

@Singleton
public class ServiceMain extends Controller implements TaskScheduler{

    private final Tracer tracer;

    @Inject
    public ServiceMain(Tracer tracer) {
        this.tracer = tracer;
    }

    //@Traced
    public String sayHello(){
        return "My trace Id " +HexCodec.toLowerHex(tracer.currentSpan().context().traceId()) + ",spanId:"+ HexCodec.toLowerHex(tracer.currentSpan().context().spanId());
    }

    @Override
    public void runInternal(DateTime fireTime,Object msg) {
        System.out.println("execution task at :" + fireTime);
    }

    @Override
    public void stopInternal(DateTime fireTime,Object msg) {

    }

}
