package com.github.test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.api.mq.kafka.Kafka;
import play.mvc.Controller;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class KafkaAction extends Controller{
    private static final Logger LOGGER = LoggerFactory.getLogger("play");

    private Kafka kafka;

    //@Inject
    public KafkaAction(){
        //kafka.subscribe("play.test.topics",String.class,this::onMessage);
    }

    public void onMessage(String msg){
        LOGGER.info("-----subscribe:"+msg);
    }


    public Result publish(){
        String msg = request().getQueryString("msg");
        kafka.publish("play.test.topics",msg);
        return ok("success");
    }


    public Result subscribe(){
        return ok("success");
    }

}
