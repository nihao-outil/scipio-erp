package org.ofbiz.salestarget.rest;

import java.util.List;

import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.util.EntityQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestApi {
    
    @RequestMapping("/hello")
    public String hello() {
        return "Hello World";
    }

    @Autowired
    private Delegator delegator;

    @RequestMapping("/party")
    public List<GenericValue> getParties() throws GenericEntityException {

        /*List<GenericValue> data_resources =  delegator.findAll("DataResource", false);
        return data_resources;*/

        return EntityQuery.use(delegator).from("Party").queryList();
    }
}
