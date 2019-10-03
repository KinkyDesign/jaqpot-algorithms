/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jaqpot.core.service.mdb;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.ejb.ActivationConfigProperty;
import javax.ejb.EJB;
import javax.ejb.EJBTransactionRolledbackException;
import javax.ejb.MessageDriven;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;

import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.MediaType;
import org.jaqpot.core.annotations.Jackson;
import org.jaqpot.core.data.DescriptorHandler;
import org.jaqpot.core.data.DoaHandler;
import org.jaqpot.core.data.FeatureHandler;
import org.jaqpot.core.data.ModelHandler;
import org.jaqpot.core.data.TaskHandler;
import org.jaqpot.core.data.serialize.JSONSerializer;
import org.jaqpot.core.data.wrappers.DatasetLegacyWrapper;
import org.jaqpot.core.model.Descriptor;
import org.jaqpot.core.model.Doa;
import org.jaqpot.core.model.Feature;
import org.jaqpot.core.model.MetaInfo;
import org.jaqpot.core.model.Model;
import org.jaqpot.core.model.Task;
import org.jaqpot.core.model.builder.FeatureBuilder;
import org.jaqpot.core.model.builder.MetaInfoBuilder;
import org.jaqpot.core.model.dto.dataset.CalculationsFormatter;
import org.jaqpot.core.model.dto.dataset.Dataset;
import org.jaqpot.core.model.dto.dataset.FeatureInfo;
import org.jaqpot.core.model.factory.DatasetFactory;
import org.jaqpot.core.model.util.ROG;
import org.jaqpot.core.properties.PropertyManager;
import org.jaqpot.core.service.annotations.Secure;
import org.jaqpot.core.service.authentication.AAService;
import org.jaqpot.core.service.client.jpdi.JPDIClient;
import org.jaqpot.core.service.exceptions.JaqpotDocumentSizeExceededException;

/**
 *
 * @author aggel
 */
@MessageDriven(activationConfig = {
    @ActivationConfigProperty(propertyName = "destinationLookup",
            propertyValue = "java:jboss/exported/jms/topic/compositePrediction"),
    @ActivationConfigProperty(propertyName = "destinationType",
            propertyValue = "javax.jms.Topic")
})

public class CompositePredictionProcedure extends AbstractJaqpotProcedure implements MessageListener {

    private static final Logger LOG = Logger.getLogger(PreparationProcedure.class.getName());

    @Inject
    JPDIClient jpdiClient;

    @Inject
    @Secure
    Client client;

    @EJB
    DatasetLegacyWrapper datasetLegacyWrapper;

    @EJB
    AAService aaService;

    @Inject
    @Jackson
    JSONSerializer serializer;

    @Inject
    PropertyManager propertyManager;

    @EJB
    DescriptorHandler descriptorHandler;

    @EJB
    FeatureHandler featureHandler;

    @EJB
    ModelHandler modelHandler;

    @EJB
    DoaHandler doaHandler;

    public CompositePredictionProcedure() {
        super(null);
        //        throw new IllegalStateException("Cannot use empty constructor, instantiate with TaskHandler");
    }

    @Inject
    public CompositePredictionProcedure(TaskHandler taskHandler) {
        super(taskHandler);
    }

    @Override
    @TransactionAttribute(value = TransactionAttributeType.REQUIRES_NEW)
    public void onMessage(Message msg) {

        Map<String, Object> messageBody;
        try {
            messageBody = msg.getBody(Map.class);
        } catch (JMSException ex) {
            LOG.log(Level.SEVERE, "JMS message could not be read", ex);
            return;
        }

        String taskId = messageBody.get("taskId").toString();
        String apiKey = messageBody.get("api_key").toString();

        String parameters = messageBody.get("parameters").toString();
        String featureUrisSer = messageBody.get("featureURIs").toString();
        String descriptorId = messageBody.get("descriptorId").toString();
        String datasetURI = messageBody.get("datasetURI").toString();
        String generatedDatasetId = messageBody.get("generatedDatasetId").toString();
        String newDatasetURI = messageBody.get("generatedDatasetURI").toString();

        try {
            init(taskId);
            checkCancelled();
            start(Task.Type.PREPARATION);

            progress(5f, "Descriptor Procedure is now running with ID " + Thread.currentThread().getName());
            progress(10f, "Starting descriptor calculation...");
            checkCancelled();

            HashMap parameterMap = null;
            if (parameters != null && !parameters.isEmpty()) {
                parameterMap = serializer.parse(parameters, HashMap.class);
            }

            Set<String> featureURIs = null;
            if (featureUrisSer != null && !featureUrisSer.isEmpty()) {
                featureURIs = serializer.parse(featureUrisSer, Set.class);
            }

            Descriptor descriptor = descriptorHandler.find(descriptorId);

            if (descriptor == null) {
                errNotFound("Descriptor with id:" + descriptorId + " was not found.");
                return;
            }

            checkCancelled();
            Dataset initialDataset;
            try {
                initialDataset = client.target(datasetURI)
                        .queryParam("dataEntries", true)
                        .request()
                        .header("Authorization", "Bearer " + apiKey)
                        .accept(MediaType.APPLICATION_JSON)
                        .get(Dataset.class);
            } catch (NotFoundException e) {
                String[] splitted = datasetURI.split("/");
                initialDataset = datasetLegacyWrapper.find(splitted[splitted.length - 1]);
            }

            if (initialDataset == null) {
                errNotFound("Dataset with id:" + Arrays.toString(datasetURI.split("/")) + " was not found.");
                return;
            }
            Dataset subDataset = null;

            //Case of all applying descriptor service to all featureURIs
            if (featureURIs.toString().contains("all")) {
                HashSet<String> featUris = new HashSet();
                //ArrayList<String> featUris = new ArrayList();
                initialDataset.getFeatures().stream().forEach(f -> {
                    featUris.add(f.getURI());
                });
                subDataset = DatasetFactory.select(initialDataset, featUris);
            } else {
                subDataset = DatasetFactory.select(initialDataset, (HashSet<String>) featureURIs);
            }

            subDataset.setMeta(null);
            subDataset.setId(null);
            Future<Dataset> futureDataset = jpdiClient.descriptor(subDataset, descriptor, parameterMap, taskId);

            Dataset dataset = futureDataset.get();
            dataset.setId(generatedDatasetId);

            copyFeatures(initialDataset, newDatasetURI);
            populateFeatures(dataset, newDatasetURI);
            dataset = DatasetFactory.mergeColumns(dataset, initialDataset);

            progress("JPDI Descriptor calculation procedure completed successfully.");
            progress(50f, "Dataset ready.");
            progress("Saving to database...");
            checkCancelled();

            MetaInfo datasetMeta = MetaInfoBuilder.builder()
                    .addTitles((String) messageBody.get("title"))
                    .addDescriptions((String) messageBody.get("description"))
                    .addComments("Created by task " + taskId)
                    .addCreators(aaService.getUserFromSSO(apiKey).getId())
                    .addSources(datasetURI)
                    .build();
            dataset.setMeta(datasetMeta);
            dataset.setVisible(Boolean.TRUE);
            dataset.setExistence(Dataset.DatasetExistence.DESCRIPTORSADDED);
            if (dataset.getDataEntry() == null || dataset.getDataEntry().isEmpty()) {
                throw new IllegalArgumentException("Resulting dataset is empty");
            } else {
                Set<String> entryValueKeys = new HashSet<>(dataset.getDataEntry().get(0).getValues().keySet());
                HashSet<String> featuresIndxs = dataset.getFeatures().stream().map(FeatureInfo::getKey).collect(Collectors.toCollection(HashSet::new));
                if (!featuresIndxs.equals(entryValueKeys)) {
                    CalculationsFormatter cf = new CalculationsFormatter();
                    dataset = cf.format(dataset, dataset.getFeatures());
                }
                datasetLegacyWrapper.create(dataset);
            }
            
            progress(100f, "Dataset saved successfully.");
            checkCancelled();
            progress("Calculation Task is now completed.");
            complete("dataset/" + dataset.getId());
        } catch (IllegalArgumentException ex) {
            LOG.log(Level.SEVERE, "Preparation procedure execution error", ex);
            errInternalServerError(ex, "JPDI Preparation procedure error");
        } catch (BadRequestException ex) {
            errBadRequest(ex, "Error while processing input.");
            LOG.log(Level.SEVERE, null, ex);
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "JPDI Preparation procedure unknown error", ex);
            errInternalServerError(ex, "JPDI Preparation procedure unknown error");
        }

        String dataset_uri = messageBody.get("generatedDatasetURI").toString();
        String modelId = messageBody.get("modelId").toString();
        String creator = messageBody.get("creator").toString();
     //   String doa = messageBody.get("doa").toString();

        Model model = null;
        try {
            init(taskId);
            checkCancelled();
            start(Task.Type.PREDICTION);

            progress(5f, "Prediction Task is now running.");

            model = modelHandler.find(modelId);
            if (model == null) {
                errNotFound("Model with id:" + modelId + " was not found.");
                return;
            }
            progress(10f, "Model retrieved successfully.");
            checkCancelled();

            Dataset dataset;
            if (dataset_uri != null && !dataset_uri.isEmpty()) {
                progress("Searching dataset...");
                try {
                    dataset = client.target(dataset_uri)
                            .queryParam("dataEntries", true)
                            .request()
                            .header("Authorization", "Bearer " + apiKey)
                            .accept(MediaType.APPLICATION_JSON)
                            .get(Dataset.class);
                } catch (NotFoundException e) {
                    String[] splitted = dataset_uri.split("/");
                    dataset = datasetLegacyWrapper.find(splitted[splitted.length - 1]);
                    //dataset = datasetHandler.find(splitted[splitted.length -1]);
                }
                dataset.setDatasetURI(dataset_uri);
                progress("Dataset has been retrieved.");
            } else {
                dataset = DatasetFactory.createEmpty(0);
            }
            progress(20f);
            checkCancelled();

            if (model.getTransformationModels() != null && !model.getTransformationModels().isEmpty()) {
                progress("--", "Processing transformations...");
                for (String transModelURI : model.getTransformationModels()) {
                    checkCancelled();
                    Model transModel = modelHandler.find(transModelURI.split("model/")[1]);
                    if (transModel == null) {
                        errNotFound("Transformation model with id:" + transModelURI + " was not found.");
                        return;
                    }
                    dataset = jpdiClient.predict(dataset, transModel, dataset != null ? dataset.getMeta() : null, taskId, null).get();
                    addProgress(5f, "Transformed successfull by model:" + transModel.getId());
                }
                progress("Done processing transformations.", "--");
            }
            progress(50f);
            checkCancelled();

            MetaInfo datasetMeta = dataset.getMeta();
            HashSet<String> creators = new HashSet<>(Arrays.asList(creator));
            datasetMeta.setCreators(creators);

            Doa doaM = null;
//            if (doa != null && doa.equals("true")) {
//                doaM = doaHandler.findBySourcesWithDoaMatrix("model/" + model.getId());
//            }

            progress("Starting Prediction...");

            dataset = jpdiClient.predict(dataset, model, datasetMeta, taskId, doaM).get();
            progress("Prediction completed successfully.");
            progress(80f, "Dataset was built successfully.");
            checkCancelled();

            if (model.getLinkedModels() != null & !model.getLinkedModels().isEmpty()) {
                progress("--", "Processing linked models...");
                Dataset copyDataset = DatasetFactory.copy(dataset);
                for (String linkedModelURI : model.getLinkedModels()) {
                    checkCancelled();
                    Model linkedModel = modelHandler.find(linkedModelURI.split("model/")[1]);
                    if (linkedModel == null) {
                        errNotFound("Transformation model with id:" + linkedModelURI + " was not found.");
                        return;
                    }
                    Dataset linkedDataset = jpdiClient.predict(copyDataset, linkedModel, dataset != null ? dataset.getMeta() : null, taskId, null).get();
                    dataset = DatasetFactory.mergeColumns(dataset, linkedDataset);
                    addProgress(5f, "Prediction successfull by model:" + linkedModel.getId());

                }
                progress("Done processing linked models.", "--");
            }
            checkCancelled();
            progress(90f, "Now saving to database...");
            dataset.setVisible(Boolean.TRUE);
            dataset.setFeatured(Boolean.FALSE);
            dataset.setExistence(Dataset.DatasetExistence.PREDICTED);
            dataset.setByModel(model.getId());
            datasetLegacyWrapper.create(dataset);
            //datasetHandler.create(dataset);

            complete("dataset/" + dataset.getId());
//            rabbitMQClient.sendMessage(creator,"Prediction:"+dataset.getId()+":"+model.getMeta().getTitles().iterator().next());

        } catch (InterruptedException ex) {
            LOG.log(Level.SEVERE, "JPDI Prediction procedure interupted", ex);
            errInternalServerError(ex, "JPDI Prediction procedure interupted");
//            sendException(creator,"Error while applying model "+ model.getMeta().getTitles().iterator().next() +". Interrupted Error.");
        } catch (ExecutionException ex) {
            LOG.log(Level.SEVERE, "Prediction procedure execution error", ex.getCause());
            errInternalServerError(ex.getCause(), "JPDI Training procedure error");
//            sendException(creator,"Error while applying model "+ model.getMeta().getTitles().iterator().next() +". Execution Error.");
        } catch (CancellationException ex) {
            LOG.log(Level.INFO, "Task with id:{0} was cancelled", taskId);
//            sendException(creator,"Error while applying model "+ model.getMeta().getTitles().iterator().next() +". Cancel Error.");
            cancel();
        } catch (BadRequestException | IllegalArgumentException ex) {
            errBadRequest(ex, "null");
        } catch (EJBTransactionRolledbackException ex) {
            LOG.log(Level.SEVERE, "Task with id:{0} was canceled due to Ejb rollback exception", taskId);
            errInternalServerError(ex.getCause(), "JPDI could not create dataset");
            errBadRequest(ex, null);
//            sendException(creator,"Error while applying model "+ model.getMeta().getTitles().iterator().next() +". Bad Request.");
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "JPDI Prediction procedure unknown error", ex);
            errInternalServerError(ex, "JPDI Prediction procedure unknown error");
//            sendException(creator,"Error while applying model "+ model.getMeta().getTitles().iterator().next() +". Unknown Error.");
        }

    }

    //Copies Features that were in the initial Dataset
    private void copyFeatures(@NotNull Dataset dataset, String datasetURI) throws JaqpotDocumentSizeExceededException {
        for (FeatureInfo featureInfo : dataset.getFeatures()) {
            Feature feature = featureHandler.find(featureInfo.getURI().split("feature/")[1]);
            if (feature == null) {
                throw new NullPointerException("Feature with URI " + featureInfo.getURI() + " was not found in the system");
            }
            Feature f = FeatureBuilder.builder(feature)
                    .addDescriptions("Copy of " + feature.getId() + " feature")
                    .addSources(datasetURI).build();
            f.setId(new ROG(true).nextString(12));
            featureHandler.create(f);
            String featureURI = propertyManager.getProperty(PropertyManager.PropertyType.JAQPOT_BASE_SERVICE) + "feature/" + f.getId();

            //Update FeatureURIs in Data Entries
//            for (DataEntry dataentry : dataset.getDataEntry()) {
//                Object value = dataentry.getValues().remove(featureInfo.getURI());
//                dataentry.getValues().put(featureURI, value);
//            }
            featureInfo.setURI(featureURI);
        }
    }

    //Creates Features that were appended to the Dataset
    private void populateFeatures(@NotNull Dataset dataset, String datasetURI) throws JaqpotDocumentSizeExceededException {
        for (FeatureInfo featureInfo : dataset.getFeatures()) {
            String featureURI = null;
            Feature f;
            if (featureInfo.getConditions() != null && featureInfo.getConditions().values() != null) {
                f = FeatureBuilder.builder(new ROG(true).nextString(12))
                        .addTitles(featureInfo.getURI())
                        .addIdentifiers(String.valueOf(featureInfo.getConditions().values()))
                        .addDescriptions(featureInfo.getName())
                        .addSources(datasetURI)
                        .build();
            } else {
                f = FeatureBuilder.builder(new ROG(true).nextString(12))
                        .addTitles(featureInfo.getURI())
                        .addDescriptions(featureInfo.getName())
                        .addSources(datasetURI)
                        .build();
            }

            featureHandler.create(f);
            featureURI = propertyManager.getProperty(PropertyManager.PropertyType.JAQPOT_BASE_SERVICE) + "feature/" + f.getId();

            //Update FeatureURIs in Data Entries
//            for (DataEntry dataentry : dataset.getDataEntry()) {
//                Object value = dataentry.getValues().remove(featureInfo.getURI());
//                dataentry.getValues().put(featureURI, value);
//            }
            //Update FeatureURI in Feature Info
            featureInfo.setConditions(null);
            featureInfo.setName(featureInfo.getURI());
            featureInfo.setURI(featureURI);
        }
    }

}
