package org.openstack.atlas.api.async;

import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.openstack.atlas.api.async.util.STMTestBase;
import org.openstack.atlas.api.integration.ReverseProxyLoadBalancerStmService;
import org.openstack.atlas.service.domain.entities.LoadBalancerStatus;
import org.openstack.atlas.service.domain.entities.Node;
import org.openstack.atlas.service.domain.events.entities.CategoryType;
import org.openstack.atlas.service.domain.events.entities.EventSeverity;
import org.openstack.atlas.service.domain.events.entities.EventType;
import org.openstack.atlas.service.domain.exceptions.EntityNotFoundException;
import org.openstack.atlas.service.domain.services.LoadBalancerService;
import org.openstack.atlas.service.domain.services.LoadBalancerStatusHistoryService;
import org.openstack.atlas.service.domain.services.NotificationService;
import org.openstack.atlas.service.domain.services.helpers.AlertType;

import javax.jms.ObjectMessage;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class DeleteNodeListenerTest extends STMTestBase {
    private Integer LOAD_BALANCER_ID;
    private Integer ACCOUNT_ID;
    private String USERNAME = "SOME_USERNAME";
    private Node nodeToDelete;

    @Mock
    private ObjectMessage objectMessage;
    @Mock
    private LoadBalancerService loadBalancerService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ReverseProxyLoadBalancerStmService reverseProxyLoadBalancerStmService;
    @Mock
    private LoadBalancerStatusHistoryService loadBalancerStatusHistoryService;

    private DeleteNodeListener deleteNodeListener;

    @Before
    public void standUp() {
        MockitoAnnotations.initMocks(this);
        setupIvars();
        Set<Node> nodes = new HashSet<Node>();
        nodeToDelete = new Node();
        nodeToDelete.setId(10);
        nodes.add(nodeToDelete);
        lb.setNodes(nodes);
        LOAD_BALANCER_ID = lb.getId();
        ACCOUNT_ID = lb.getAccountId();
        lb.setUserName(USERNAME);
        deleteNodeListener = new DeleteNodeListener();
        deleteNodeListener.setLoadBalancerService(loadBalancerService);
        deleteNodeListener.setNotificationService(notificationService);
        deleteNodeListener.setReverseProxyLoadBalancerStmService(reverseProxyLoadBalancerStmService);
        deleteNodeListener.setLoadBalancerStatusHistoryService(loadBalancerStatusHistoryService);
    }

    @Test
    public void testDeleteNode() throws Exception {
        when(objectMessage.getObject()).thenReturn(lb);
        when(loadBalancerService.get(LOAD_BALANCER_ID, ACCOUNT_ID)).thenReturn(lb);

        Assert.assertTrue(lb.getNodes().contains(nodeToDelete));
        deleteNodeListener.doOnMessage(objectMessage);

        verify(reverseProxyLoadBalancerStmService).removeNode(lb, nodeToDelete);
        Assert.assertFalse(lb.getNodes().contains(nodeToDelete));
        Assert.assertEquals(lb.getStatus(), LoadBalancerStatus.ACTIVE);
        verify(loadBalancerService).update(lb);
        verify(loadBalancerStatusHistoryService).save(ACCOUNT_ID, LOAD_BALANCER_ID, LoadBalancerStatus.ACTIVE);
        verify(notificationService).saveNodeEvent(eq(USERNAME), eq(ACCOUNT_ID), eq(LOAD_BALANCER_ID), eq(nodeToDelete.getId()), anyString(), anyString(), eq(EventType.DELETE_NODE), eq(CategoryType.DELETE), eq(EventSeverity.INFO));
    }

    @Test
    public void testUpdateInvalidLoadBalancer() throws Exception {
        EntityNotFoundException entityNotFoundException = new EntityNotFoundException();
        when(objectMessage.getObject()).thenReturn(lb);
        when(loadBalancerService.get(LOAD_BALANCER_ID, ACCOUNT_ID)).thenThrow(entityNotFoundException);

        deleteNodeListener.doOnMessage(objectMessage);

        verify(notificationService).saveAlert(eq(ACCOUNT_ID), eq(LOAD_BALANCER_ID), eq(entityNotFoundException), eq(AlertType.DATABASE_FAILURE.name()), anyString());
        verify(notificationService).saveLoadBalancerEvent(eq(USERNAME), eq(ACCOUNT_ID), eq(LOAD_BALANCER_ID), anyString(), anyString(), eq(EventType.DELETE_NODE), eq(CategoryType.DELETE), eq(EventSeverity.CRITICAL));
    }

    @Test
    public void testDeleteInvalidNode() throws Exception {
        Exception exception = new Exception();
        when(objectMessage.getObject()).thenReturn(lb);
        when(loadBalancerService.get(LOAD_BALANCER_ID, ACCOUNT_ID)).thenReturn(lb);
        doThrow(exception).when(reverseProxyLoadBalancerStmService).removeNode(lb, nodeToDelete);

        deleteNodeListener.doOnMessage(objectMessage);

        verify(reverseProxyLoadBalancerStmService).removeNode(lb, nodeToDelete);
        verify(loadBalancerService).setStatus(lb, LoadBalancerStatus.ERROR);
        verify(notificationService).saveAlert(eq(ACCOUNT_ID), eq(LOAD_BALANCER_ID), eq(exception), eq(AlertType.ZEUS_FAILURE.name()), anyString());
        verify(notificationService).saveNodeEvent(eq(USERNAME), eq(ACCOUNT_ID), eq(LOAD_BALANCER_ID), eq(nodeToDelete.getId()), anyString(), anyString(), eq(EventType.DELETE_NODE), eq(CategoryType.DELETE), eq(EventSeverity.CRITICAL));
    }
}