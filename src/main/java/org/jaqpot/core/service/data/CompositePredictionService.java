/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jaqpot.core.service.data;

import org.jaqpot.core.data.TaskHandler;
import org.jaqpot.core.model.Task;
import org.jaqpot.core.model.factory.TaskFactory;
import org.jaqpot.core.service.exceptions.JaqpotDocumentSizeExceededException;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.jms.JMSContext;
import javax.jms.Topic;
import java.util.Map;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageListener;

/**
 *
 * @author aggel
 */


@Stateless
public class CompositePredictionService {

    @EJB
    TaskHandler taskHandler;

    @Resource(lookup = "java:jboss/exported/jms/topic/compositePrediction")
    private Topic compPredQueue;

    @Inject
    private JMSContext jmsContext;

    public Task initiateCompositePrediction(Map<String, Object> options, String userName) throws JaqpotDocumentSizeExceededException {
        Task task = TaskFactory.queuedTask("Preparation on file: " + options.get("title"),
                "A preparation procedure will return a Dataset if completed successfully."
                + "It may also initiate other procedures if desired.",
                userName);
        task.setType(Task.Type.PREPARATION);
        options.put("taskId", task.getId());

        task.setVisible(Boolean.TRUE);
        taskHandler.create(task);
        jmsContext.createProducer()
                .setDeliveryMode(DeliveryMode.NON_PERSISTENT)
                .setTimeToLive(3000000)
                .setDeliveryDelay(10)
                .send(compPredQueue, options);
        
        
        return task;
    }

    

}
