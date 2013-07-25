package org.openstack.atlas.adapter.itest;


import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openstack.atlas.adapter.exceptions.InsufficientRequestException;
import org.openstack.atlas.adapter.exceptions.RollBackException;
import org.openstack.atlas.adapter.exceptions.StmRollBackException;
import org.openstack.atlas.adapter.helpers.ZxtmNameBuilder;
import org.openstack.atlas.service.domain.util.Constants;
import org.rackspace.stingray.client.exception.StingrayRestClientException;
import org.rackspace.stingray.client.exception.StingrayRestClientObjectNotFoundException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.rmi.RemoteException;

public class GlobalErrorFileITest extends STMTestBase {
    String defaultPageContent = "DEFAULT ERROR PAGE CONTENT";
    String customPageContent = "CUSTOM ERROR PAGE CONTENT";
    String customPageContent2 = "CUSTOM ERROR PAGE CONTENT 2";
    String vsName;


    //TODO: needs more testing for lb and its error files..
    @BeforeClass
    public static void setupClass() throws InterruptedException {
        Thread.sleep(SLEEP_TIME_BETWEEN_TESTS);
        setupIvars();
        createSimpleLoadBalancer();
    }

    @AfterClass
    public static void tearDownClass() throws RollBackException, InsufficientRequestException, RemoteException {
        stmAdapter.deleteLoadBalancer(config, lb);
    }

    @Test
    public void testDefaultErrorFileOperations() throws Exception {
        setDefaultErrorFile();
    }

    @Test
    public void testCustomErrorFileOperations() throws Exception {
        setCustomErrorFile();
    }

    @Test(expected = StingrayRestClientObjectNotFoundException.class)
    public void testSimpleDeleteErrorFileOperations() throws Exception {
        setCustomErrorFile();
        deleteErrorFile();
    }

    private void setDefaultErrorFile() throws Exception {
        vsName = ZxtmNameBuilder.genVSName(lb);

        //This is mgmt call to set 'default' file other than stm Default, lb should have Default at this point.
        stmAdapter.uploadDefaultErrorFile(config, defaultPageContent);
        File file = stmClient.getExtraFile(Constants.DEFAULT_ERRORFILE);
        Assert.assertNotNull(file);

        BufferedReader reader = new BufferedReader(new FileReader(file));
        String content = reader.readLine();
        Assert.assertEquals(defaultPageContent, content);
    }

    private void setCustomErrorFile() throws Exception {
        vsName = ZxtmNameBuilder.genVSName(lb);

        Assert.assertFalse(customPageContent.equals(customPageContent2)); //assert our assumption

        stmAdapter.setErrorFile(config, lb, customPageContent);
        File file = stmClient.getExtraFile(errorFileName());
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String content = reader.readLine();
        Assert.assertEquals(customPageContent, content);

        stmAdapter.setErrorFile(config, lb, customPageContent2);
        file = stmClient.getExtraFile(errorFileName());
        reader = new BufferedReader(new FileReader(file));
        content = reader.readLine();
        Assert.assertEquals(customPageContent2, content);
    }

    private void deleteErrorFile() throws InsufficientRequestException, StmRollBackException, StingrayRestClientException, StingrayRestClientObjectNotFoundException {
        final String errorFileName = errorFileName();
        File errorFile = stmClient.getExtraFile(errorFileName);

        Assert.assertNotNull(errorFile);

        stmAdapter.deleteErrorFile(config, lb);
        stmClient.getExtraFile(errorFileName); //expect ONFException
    }
}
