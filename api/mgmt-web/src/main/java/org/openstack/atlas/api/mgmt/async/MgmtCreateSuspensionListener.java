package org.openstack.atlas.api.mgmt.async;

import org.openstack.atlas.service.domain.entities.LoadBalancer;
import org.openstack.atlas.service.domain.entities.LoadBalancerStatus;
import org.openstack.atlas.service.domain.events.UsageEvent;
import org.openstack.atlas.service.domain.exceptions.EntityNotFoundException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openstack.atlas.service.domain.exceptions.UsageEventCollectionException;
import org.openstack.atlas.usagerefactor.SnmpUsage;

import javax.jms.Message;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import static org.openstack.atlas.service.domain.services.helpers.AlertType.DATABASE_FAILURE;
import static org.openstack.atlas.service.domain.services.helpers.AlertType.ZEUS_FAILURE;
import static org.openstack.atlas.service.domain.events.entities.CategoryType.UPDATE;
import static org.openstack.atlas.service.domain.events.entities.EventSeverity.CRITICAL;
import static org.openstack.atlas.service.domain.events.entities.EventSeverity.INFO;
import static org.openstack.atlas.service.domain.events.entities.EventType.UPDATE_LOADBALANCER;

public class MgmtCreateSuspensionListener extends BaseListener {

    private final Log LOG = LogFactory.getLog(MgmtCreateSuspensionListener.class);

    @Override
    public void doOnMessage(final Message message) throws Exception {
        LOG.debug("Entering " + getClass());
        LOG.debug(message);
        LoadBalancer requestLb = getEsbRequestFromMessage(message).getLoadBalancer();
        LoadBalancer dbLoadBalancer;
        @Deprecated
        Long bytesOut;
        @Deprecated
        Long bytesIn;
        @Deprecated
        Integer concurrentConns;
        @Deprecated
        Long bytesOutSsl;
        @Deprecated
        Long bytesInSsl;
        @Deprecated
        Integer concurrentConnsSsl;

        try {
            dbLoadBalancer = loadBalancerService.get(requestLb.getId());
        } catch (EntityNotFoundException enfe) {
            String alertDescription = String.format("Load balancer '%d' not found in database.", requestLb.getId());
            LOG.error(alertDescription, enfe);
            notificationService.saveAlert(requestLb.getAccountId(), requestLb.getId(), enfe, DATABASE_FAILURE.name(), alertDescription);
            sendErrorToEventResource(requestLb);
            return;
        }

        // Try to get non-ssl usage
        try {
            bytesOut = reverseProxyLoadBalancerService.getLoadBalancerBytesOut(dbLoadBalancer, false);
            bytesIn = reverseProxyLoadBalancerService.getLoadBalancerBytesIn(dbLoadBalancer, false);
            concurrentConns = reverseProxyLoadBalancerService.getLoadBalancerCurrentConnections(dbLoadBalancer, false);
        } catch (Exception e) {
            LOG.warn("Couldn't retrieve load balancer usage stats. Setting them to null.");
            bytesOut = null;
            bytesIn = null;
            concurrentConns = null;
        }

        // Try to get ssl usage
        try {
            bytesOutSsl = reverseProxyLoadBalancerService.getLoadBalancerBytesOut(dbLoadBalancer, true);
            bytesInSsl = reverseProxyLoadBalancerService.getLoadBalancerBytesIn(dbLoadBalancer, true);
            concurrentConnsSsl = reverseProxyLoadBalancerService.getLoadBalancerCurrentConnections(dbLoadBalancer, true);
        } catch (Exception e) {
            LOG.warn("Couldn't retrieve load balancer usage stats for ssl virtual server. Setting them to null.");
            bytesOutSsl = null;
            bytesInSsl = null;
            concurrentConnsSsl = null;
        }

        List<SnmpUsage> usages = new ArrayList<SnmpUsage>();
        try {
            LOG.info(String.format("Collecting SUSPEND_LOADBALANCER usage for load balancer %s...", dbLoadBalancer.getId()));
            usages = usageEventCollection.getUsage(dbLoadBalancer);
            LOG.info(String.format("Successfully collected SUSPEND_LOADBALANCER usage for load balancer %s", dbLoadBalancer.getId()));
        } catch (UsageEventCollectionException e) {
            LOG.error(String.format("Collection of the SUSPEND_LOADBALANCER usage event failed for " +
                    "load balancer: %s :: Exception: %s", dbLoadBalancer.getId(), e));
        }

        try {
            LOG.debug(String.format("Suspending load balancer '%d' in Zeus...", dbLoadBalancer.getId()));
            reverseProxyLoadBalancerService.suspendLoadBalancer(dbLoadBalancer);
            LOG.debug(String.format("Successfully suspended load balancer '%d' in Zeus.", dbLoadBalancer.getId()));
        } catch (Exception e) {
            loadBalancerService.setStatus(dbLoadBalancer, LoadBalancerStatus.ERROR);

            String alertDescription = String.format("Error suspending load balancer '%d' in Zeus.", dbLoadBalancer.getId());
            LOG.error(alertDescription, e);
            notificationService.saveAlert(dbLoadBalancer.getAccountId(), dbLoadBalancer.getId(), e, ZEUS_FAILURE.name(), alertDescription);
            sendErrorToEventResource(requestLb);
            return;
        }

        // Update load balancer in DB
        LOG.debug("Adding the suspension to the database...");
        loadBalancerService.createSuspension(dbLoadBalancer, requestLb.getSuspension());
        loadBalancerService.setStatus(dbLoadBalancer, LoadBalancerStatus.SUSPENDED);

        // Add atom entry
        String atomTitle = "Load Balancer Suspended";
        String atomSummary = "Load balancer suspended. Please contact support if you have any questions.";
        notificationService.saveLoadBalancerEvent(requestLb.getUserName(), dbLoadBalancer.getAccountId(), dbLoadBalancer.getId(), atomTitle, atomSummary, UPDATE_LOADBALANCER, UPDATE, INFO);

        // DEPRECATED
        Calendar eventTime = Calendar.getInstance();
        usageEventHelper.processUsageEvent(dbLoadBalancer, UsageEvent.SUSPEND_LOADBALANCER, bytesOut, bytesIn, concurrentConns, bytesOutSsl, bytesInSsl, concurrentConnsSsl, eventTime);
        // END DEPRECATED

        //New usage process
        usageEventCollection.processUsageEvent(usages, dbLoadBalancer, UsageEvent.SUSPEND_LOADBALANCER, eventTime);


        LOG.info(String.format("Suspend load balancer operation complete for load balancer '%d'.", dbLoadBalancer.getId()));
    }

    private void sendErrorToEventResource(LoadBalancer lb) {
        String title = "Error Suspending Load Balancer";
        String desc = "Could not suspend the loadbalancer at this time.";
        notificationService.saveLoadBalancerEvent(lb.getUserName(), lb.getAccountId(), lb.getId(), title, desc, UPDATE_LOADBALANCER, UPDATE, CRITICAL);
    }
}

