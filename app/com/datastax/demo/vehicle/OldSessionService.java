package com.datastax.demo.vehicle;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.policies.DCAwareRoundRobinPolicy;
import com.datastax.driver.core.policies.DefaultRetryPolicy;
import com.datastax.driver.core.policies.TokenAwarePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.InvalidParameterException;

public class OldSessionService {

    private static final Logger logger = LoggerFactory.getLogger(OldSessionService.class);

    private static final String CONTACT_POINTS = "contactPoints";

    private String contactPoints;
    private Cluster cluster;
    private Session session;

    public OldSessionService(String contactPoints) {
        this.contactPoints = contactPoints;
    }

    private String resolveContactPoints() {
        String resolvedContactPoints = contactPoints;
        if (resolvedContactPoints == null) {
            resolvedContactPoints = System.getProperty(CONTACT_POINTS, "127.0.0.1");
        }
        if (this.contactPoints == null) {
            throw new InvalidParameterException("contactPoints must be provided via constructor or via -D" + CONTACT_POINTS);
        }
        return resolvedContactPoints;
    }

    public Session getSession() {
        if (this.session == null) {
            synchronized (this) {
                if (this.session == null) {
                    this.session = getCluster().newSession();
                    logger.info("Session created on thread [" + Thread.currentThread().getName() + "]:" + session.toString());
                }
            }
        }

        logger.info("Raw Session dispatched on thread [" + Thread.currentThread().getName() + "]:" + session.toString());

        return this.session;
    }

    public Cluster getCluster() {
        if (this.cluster == null) {
            synchronized (this) {
                if (this.cluster == null) {
                    this.cluster = buildCluster();
                }
            }
        }

        logger.info("Raw Cluster dispatched on thread [" + Thread.currentThread().getName() + "]:" + cluster.toString());

        return this.cluster;
    }

    private Cluster buildCluster() {
        cluster = Cluster.builder()
                .addContactPoints(contactPoints.split(","))
                .withRetryPolicy(DefaultRetryPolicy.INSTANCE)
                // .withCredentials("cassandra", "cassandra")
                .withLoadBalancingPolicy(
                        new TokenAwarePolicy(DCAwareRoundRobinPolicy.builder().build()))
                .build();

        logger.info("Built Cluster on thread [" + Thread.currentThread().getName() + "]:" + cluster.toString());

        return cluster;
    }
}
