/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Thibault Meyer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package play.api.mq.rabbit;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.typesafe.config.Config;
import play.Logger;
import play.inject.ApplicationLifecycle;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implementation of {@code RabbitMQModule}.
 *
 * @author Thibault Meyer
 * @version 17.06.29
 * @see RabbitMQ
 * @since 16.03.19
 */
@Singleton
public class RabbitMQImpl implements RabbitMQ {

    /**
     * The logger to use in this module.
     *
     * @since 16.05.19
     */
    private static final Logger.ALogger LOGGER = Logger.of(RabbitMQImpl.class);

    /**
     * @since 16.05.19
     */
    private static final String RABBITMQ_CONN_URI = "play.rabbitmq.conn.uri";

    /**
     * @since 16.05.19
     */
    private static final String RABBITMQ_CONN_HEARTBEAT = "play.rabbitmq.conn.heartbeat";

    /**
     * @since 16.05.19
     */
    private static final String RABBITMQ_CONN_RECOVERY = "play.rabbitmq.conn.networkRecoveryInterval";

    /**
     * @since 16.05.19
     */
    private static final String RABBITMQ_CONN_TIMEOUT = "play.rabbitmq.conn.connectionTimeOut";

    /**
     * @since 16.05.19
     */
    private static final String RABBITMQ_EXECUTOR = "play.rabbitmq.conn.executorService";

    /**
     * @since 16.05.26
     */
    private static final String RABBITMQ_AUTO_RECOVERY = "play.rabbitmq.conn.automaticRecovery";

    /**
     * @since 16.09.06
     */
    private static final String RABBITMQ_BYPASS_ERROR = "play.rabbitmq.conn.bypassInitError";

    /**
     * Play application configuration.
     *
     * @since 16.05.19
     */
    private final Config configuration;

    /**
     * Connection to the RabbitMQ server.
     *
     * @since 16.05.19
     */
    private Connection rabbitConnection;

    /**
     * Build an instance.
     *
     * @param lifecycle     The current application lifecyle
     * @param configuration The current application configuration
     * @since 16.05.19
     */
    @Inject
    public RabbitMQImpl(final ApplicationLifecycle lifecycle, final Config configuration) {
        this.configuration = configuration;
        try {
            final String uri = configuration.getString(RabbitMQImpl.RABBITMQ_CONN_URI);
            if (uri == null || uri.isEmpty()) {
                throw new RuntimeException("URI is empty");
            }
            final ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.setUri(uri);
            connectionFactory.setRequestedHeartbeat(configuration.getInt(RabbitMQImpl.RABBITMQ_CONN_HEARTBEAT));
            connectionFactory.setNetworkRecoveryInterval(configuration.getInt(RabbitMQImpl.RABBITMQ_CONN_RECOVERY));
            connectionFactory.setConnectionTimeout(configuration.getInt(RabbitMQImpl.RABBITMQ_CONN_TIMEOUT));
            connectionFactory.setAutomaticRecoveryEnabled(configuration.getBoolean(RabbitMQImpl.RABBITMQ_AUTO_RECOVERY));
            if (uri.toLowerCase(Locale.ENGLISH).startsWith("amqps://")) {
                connectionFactory.useSslProtocol();
            }

            final ExecutorService es = Executors.newFixedThreadPool(configuration.getInt(RabbitMQImpl.RABBITMQ_EXECUTOR));
            this.rabbitConnection = connectionFactory.newConnection(es);
            RabbitMQImpl.LOGGER.info(
                "RabbitMQ connected at {}",
                String.format(
                    "amqp%s://%s:%d/%s",
                    connectionFactory.isSSL() ? "s" : "",
                    connectionFactory.getHost(),
                    connectionFactory.getPort(),
                    connectionFactory.getVirtualHost()
                )
            );
        } catch (Exception ex) {
            this.rabbitConnection = null;
            if (!this.configuration.getBoolean(RabbitMQImpl.RABBITMQ_BYPASS_ERROR)) {
                RabbitMQImpl.LOGGER.error("Can't initialize RabbitMQ module", ex);
                throw new RuntimeException(ex);
            } else {
                RabbitMQImpl.LOGGER.warn("Can't initialize RabbitMQ module: {}", ex.getMessage());
            }
        }

        lifecycle.addStopHook(() -> {
            RabbitMQImpl.LOGGER.info("Shutting down RabbitMQ");
            if (this.rabbitConnection != null) {
                this.rabbitConnection.close();
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    @Override
    public Map<String, Object> getServerProperties() {
        if (this.rabbitConnection == null) {
            return null;
        }
        return this.rabbitConnection.getServerProperties();
    }

    @Override
    public int getChannelMax() {
        if (this.rabbitConnection == null) {
            return 0;
        }
        return this.rabbitConnection.getChannelMax();
    }

    @Override
    public long getMessageCount(final String queueName) throws IOException {
        if (this.rabbitConnection == null) {
            return 0;
        }
        return this.rabbitConnection.createChannel().messageCount(queueName);
    }

    @Override
    public long getConsumerCountCount(final String queueName) throws IOException {
        if (this.rabbitConnection == null) {
            return 0;
        }
        return this.rabbitConnection.createChannel().consumerCount(queueName);
    }

    @Override
    public Channel getChannel() throws IOException {
        if (this.rabbitConnection == null) {
            return null;
        }
        return this.rabbitConnection.createChannel();
    }

    @Override
    public Channel getChannel(final String queueName) throws IOException {
        if (this.rabbitConnection == null) {
            return null;
        }
        final Channel channel = this.rabbitConnection.createChannel();
        final String key = "play.rabbitmq.channels." + queueName.replace(" ", "_") + ".";
        channel.queueDeclare(
            queueName,
            !this.configuration.hasPath(key + "durable") || this.configuration.getBoolean(key + "durable"),
            this.configuration.hasPath(key + "exclusive") && this.configuration.getBoolean(key + "exclusive"),
            this.configuration.hasPath(key + "autoDelete") && this.configuration.getBoolean(key + "autoDelete"),
            null
        );
        return channel;
    }
}