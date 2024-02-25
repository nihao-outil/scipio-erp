package org.ofbiz.salestarget;

import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.DelegatorFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Use delegator and dispatcher from OFBiz as Spring-managed beans.
 */
@Configuration
public class SalesTargetConfiguration {

    @Bean
    public Delegator delegator() {
        return DelegatorFactory.getDelegator("default");
    }
}
