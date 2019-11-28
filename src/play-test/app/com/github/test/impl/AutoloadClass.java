package com.github.test.impl;


import akka.actor.AbstractActor;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.client.methods.RequestBuilder;

import java.util.Map;

public class AutoloadClass extends AbstractActor {


    @Override
    public Receive createReceive() {

        return receiveBuilder()
                .match(Map.class, hello -> {
                    Map<String,Object> a = hello;

                }).build();
    }
}
