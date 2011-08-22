package org.openstack.atlas.api.resource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;

@Controller//("CORE-RootResource")
@Scope(value = "request")
@Path("{id: [-+]?[0-9][0-9]*}")
public class RootResource {

    @Context
    HttpHeaders requestHeaders;

    @PathParam("id")
    private Integer accountId;
    @Autowired
    //@Qualifier("CORE-LoadBalancersResource")
    private LoadBalancersResource loadBalancersResource;

    @Path("loadbalancers")
    public LoadBalancersResource retrieveLoadBalancersResource() {
        loadBalancersResource.setRequestHeaders(requestHeaders);
        loadBalancersResource.setAccountId(accountId);
        return loadBalancersResource;
    }

    public void setRequestHeaders(HttpHeaders requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    public void setAccountId(Integer accountId) {
        this.accountId = accountId;
    }
}
