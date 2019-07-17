/*
 *
 * JAQPOT Quattro
 *
 * JAQPOT Quattro and the components shipped with it, in particular:
 * (i)   JaqpotCoreServices
 * (ii)  JaqpotAlgorithmServices
 * (iii) JaqpotDB
 * (iv)  JaqpotDomain
 * (v)   JaqpotEAR
 * are licensed by GPL v3 as specified hereafter. Additional components may ship
 * with some other licence as will be specified therein.
 *
 * Copyright (C) 2014-2015 KinkyDesign (Charalampos Chomenidis, Pantelis Sopasakis)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * Source code:
 * The source code of JAQPOT Quattro is available on github at:
 * https://github.com/KinkyDesign/JaqpotQuattro
 * All source files of JAQPOT Quattro that are stored on github are licensed
 * with the aforementioned licence. 
 */
package org.jaqpot.core.service.client.jpdi;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.jaqpot.core.data.FeatureHandler;
import org.jaqpot.core.data.serialize.JSONSerializer;
import org.jaqpot.core.model.*;
import org.jaqpot.core.model.builder.MetaInfoBuilder;
import org.jaqpot.core.model.DataEntry;
import org.jaqpot.core.model.dto.dataset.Dataset;
import org.jaqpot.core.model.dto.dataset.FeatureInfo;
import org.jaqpot.core.model.dto.jpdi.*;
import org.jaqpot.core.model.factory.DatasetFactory;
import org.jaqpot.core.model.util.ROG;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.jaqpot.core.data.AlgorithmHandler;
import org.jaqpot.core.model.factory.FeatureFactory;

import org.jaqpot.core.service.exceptions.JaqpotDocumentSizeExceededException;

/**
 *
 * @author Charalampos Chomenidis
 * @author Pantelis Sopasakis
 */
public class JPDIClientImpl implements JPDIClient {

    private static final Logger LOG = Logger.getLogger(JPDIClientImpl.class.getName());
    private final CloseableHttpAsyncClient client;
    private final JSONSerializer serializer;
    private final FeatureHandler featureHandler;
    private final AlgorithmHandler algorithmHandler;
    private final String baseURI;
    private final ROG randomStringGenerator;
    private  ExecutorService PIPER = Executors.newCachedThreadPool();

    private final Map<String, Future> futureMap;

    public JPDIClientImpl(CloseableHttpAsyncClient client, JSONSerializer serializer, FeatureHandler featureHandler, AlgorithmHandler algorithmHandler, String baseURI) {
        this.client = client;
        client.start();
        this.serializer = serializer;
        this.featureHandler = featureHandler;
        this.algorithmHandler = algorithmHandler;
        this.baseURI = baseURI;
        this.futureMap = new ConcurrentHashMap<>(20);
        this.randomStringGenerator = new ROG(true);
    }

    @Override
    public Future<Dataset> calculate(byte[] file, Algorithm algorithm, Map<String, Object> parameters, String taskId) {
        CompletableFuture<Dataset> futureDataset = new CompletableFuture<>();

        //TODO Create a calculateService for algorithms.
        final HttpPost request = new HttpPost(algorithm.getReportService());

        CalculateRequest calculateRequest = new CalculateRequest();
        calculateRequest.setFile(file);
        calculateRequest.setParameters(parameters);

        PipedOutputStream out = new PipedOutputStream();
        PipedInputStream in;
        try {
            in = new PipedInputStream(out);
        } catch (IOException ex) {
            futureDataset.completeExceptionally(ex);
            return futureDataset;
        }
        InputStreamEntity entity = new InputStreamEntity(in, ContentType.APPLICATION_JSON);
        entity.setChunked(true);

        request.setEntity(entity);
        request.addHeader("Accept", "application/json");

        Future futureResponse = client.execute(request, new FutureCallback<HttpResponse>() {

            @Override
            public void completed(final HttpResponse response) {
                futureMap.remove(taskId);
                int status = response.getStatusLine().getStatusCode();
                try {
                    InputStream responseStream = response.getEntity().getContent();

                    switch (status) {
                        case 200:
                        case 201:
                            //TODO handle successful return of Dataset
                            CalculateResponse calculateResponse = serializer.parse(responseStream, CalculateResponse.class);
                            Dataset entries = calculateResponse.getEntries();
                            entries.setId(UUID.randomUUID().toString());
                            entries.setVisible(Boolean.TRUE);
                            ROG randomStringGenerator = new ROG(true);
                            entries.setId(randomStringGenerator.nextString(14));
                            futureDataset.complete(entries);
                            break;
                        case 400:
                            String message = new BufferedReader(new InputStreamReader(responseStream))
                                    .lines().collect(Collectors.joining("\n"));
                            futureDataset.completeExceptionally(new BadRequestException(message));
                            break;
                        case 500:
                            message = new BufferedReader(new InputStreamReader(responseStream))
                                    .lines().collect(Collectors.joining("\n"));
                            futureDataset.completeExceptionally(new InternalServerErrorException(message));
                            break;
                        default:
                            message = new BufferedReader(new InputStreamReader(responseStream))
                                    .lines().collect(Collectors.joining("\n"));
                            futureDataset.completeExceptionally(new InternalServerErrorException(message));
                    }
                } catch (IOException | UnsupportedOperationException ex) {
                    futureDataset.completeExceptionally(ex);
                }
            }

            @Override
            public void failed(final Exception ex) {
                futureMap.remove(taskId);
                futureDataset.completeExceptionally(ex);
            }

            @Override
            public void cancelled() {
                futureMap.remove(taskId);
                futureDataset.cancel(true);
            }

        });
        serializer.write(calculateRequest, out);
        try {
            out.close();
        } catch (IOException ex) {
            futureDataset.completeExceptionally(ex);
        }

        futureMap.put(taskId, futureResponse);
        return futureDataset;
    }

    @Override
    public Future<Dataset> descriptor(Dataset dataset, Descriptor descriptor, Map<String, Object> parameters, String taskId) {
        CompletableFuture<Dataset> futureDataset = new CompletableFuture<>();
        
        //TODO Create a calculateService for algorithms.
        final HttpPost request = new HttpPost(descriptor.getDescriptorService());
       
        DescriptorRequest descriptorRequest = new DescriptorRequest();
        descriptorRequest.setDataset(dataset);
        descriptorRequest.setParameters(parameters);
//        final HttpEntity entity;
//         final PipedOutputStream pipedOutputStream = new PipedOutputStream();
//         final PipedInputStream pipedInputStream;
//        
//            pipedInputStream = new PipedInputStream(pipedOutputStream);
//            PIPER.submit(new ExceptingRunnable(){
//                    @Override
//                    protected void go() throws Exception {
//                        try {
//                            serializer.write(descriptorRequest, pipedOutputStream);
//                            pipedOutputStream.flush();
//                        } finally {
//                            IOUtils.closeQuietly(pipedOutputStream);
//                        }
//                    }
//                });
//            entity = new InputStreamEntity(pipedInputStream, ContentType.APPLICATION_JSON);
//       
               

        //PipedOutputStream out = new PipedOutputStream();
        //PipedInputStream in;
        
        //try {
            String serDescrReq = serializer.write(descriptorRequest);
          //  in = new PipedInputStream(out);
            
        //} catch (IOException ex) {
          //  futureDataset.completeExceptionally(ex);
           // return futureDataset;
        //}
        //InputStreamEntity entity = new InputStreamEntity(in, ContentType.APPLICATION_JSON);
        HttpEntity entity = new StringEntity(serDescrReq, ContentType.APPLICATION_JSON);
        //entity.setChunked(true);
       
        request.setEntity(entity);
        request.addHeader("Accept", "application/json");
        
        //request.addHeader("Content-Type", "application/json");
        
        Future futureResponse = client.execute(request, new FutureCallback<HttpResponse>() {

            @Override
            public void completed(final HttpResponse response) {
                futureMap.remove(taskId);
                int status = response.getStatusLine().getStatusCode();
                try {
                    InputStream responseStream = response.getEntity().getContent();

                    switch (status) {
                        case 200:
                        case 201:
                            //TODO handle successful return of Dataset
                            DescriptorResponse descriptorResponse = serializer.parse(responseStream, DescriptorResponse.class);
//                            Dataset descriptorResponseDataset = serializer.parse(responseStream,Dataset.class);
                            Dataset descriptorResponseDataset = descriptorResponse.getResponseDataset();
                            descriptorResponseDataset.setId(UUID.randomUUID().toString());
                            descriptorResponseDataset.setVisible(Boolean.TRUE);
                            ROG randomStringGenerator = new ROG(true);
                            descriptorResponseDataset.setId(randomStringGenerator.nextString(14));
                            futureDataset.complete(descriptorResponseDataset);
                            break;
                        case 400:
                            String message = new BufferedReader(new InputStreamReader(responseStream))
                                    .lines().collect(Collectors.joining("\n"));
                            futureDataset.completeExceptionally(new BadRequestException(message));
                            break;
                        case 500:
                            message = new BufferedReader(new InputStreamReader(responseStream))
                                    .lines().collect(Collectors.joining("\n"));
                            futureDataset.completeExceptionally(new InternalServerErrorException(message));
                            break;
                        default:
                            message = new BufferedReader(new InputStreamReader(responseStream))
                                    .lines().collect(Collectors.joining("\n"));
                            futureDataset.completeExceptionally(new InternalServerErrorException(message));
                    }
                } catch (IOException | UnsupportedOperationException ex) {
                    futureDataset.completeExceptionally(ex);
                }
            }

            @Override
            public void failed(final Exception ex) {
                futureMap.remove(taskId);
                futureDataset.completeExceptionally(ex);
            }

            @Override
            public void cancelled() {
                futureMap.remove(taskId);
                futureDataset.cancel(true);
            }

        });
        //serializer.write(calculateRequest, out);
//        try {
//            pipedOutputStream.close();
//        } catch (IOException ex) {
//            futureDataset.completeExceptionally(ex);
//        }

        futureMap.put(taskId, futureResponse);
        return futureDataset;
    }

    @Override
    public Future<Model> train(Dataset dataset, Algorithm algorithm, Map<String, Object> parameters, String predictionFeature, MetaInfo modelMeta, String taskId) {

        CompletableFuture<Model> futureModel = new CompletableFuture<>();

        TrainingRequest trainingRequest = new TrainingRequest();
        trainingRequest.setDataset(dataset);
        trainingRequest.setParameters(parameters);
        trainingRequest.setPredictionFeature(predictionFeature);

        final HttpPost request = new HttpPost(algorithm.getTrainingService());

        PipedOutputStream out = new PipedOutputStream();
        PipedInputStream in;
        try {
            in = new PipedInputStream(out);
        } catch (IOException ex) {
            futureModel.completeExceptionally(ex);
            return futureModel;
        }
        InputStreamEntity entity = new InputStreamEntity(in, ContentType.APPLICATION_JSON);
        entity.setChunked(true);

        request.setEntity(entity);
        request.addHeader("Accept", "application/json");
       

        Future futureResponse = client.execute(request, new FutureCallback<HttpResponse>() {

            @Override
            public void completed(final HttpResponse response) {
                futureMap.remove(taskId);
                int status = response.getStatusLine().getStatusCode();
                try {
                    InputStream responseStream = response.getEntity().getContent();

                    switch (status) {
                        case 200:
                        case 201:
                            TrainingResponse trainingResponse = serializer.parse(responseStream, TrainingResponse.class);
                            Model model = new Model();
                            model.setId(randomStringGenerator.nextString(20));
                            model.setActualModel(trainingResponse.getRawModel());
                            model.setPmmlModel(trainingResponse.getPmmlModel());
                            model.setAdditionalInfo(trainingResponse.getAdditionalInfo());
                            model.setAlgorithm(algorithm);
                            model.setParameters(parameters);
                            model.setDatasetUri(dataset != null ? dataset.getDatasetURI() : null);

                            //Check if independedFeatures of model exist in dataset
                            List<String> filteredIndependedFeatures = new ArrayList<String>();

                            if (dataset != null && dataset.getFeatures() != null && trainingResponse.getIndependentFeatures() != null) {
                                for (String feature : trainingResponse.getIndependentFeatures()) {
                                    for (FeatureInfo featureInfo : dataset.getFeatures()) {
                                        if (feature.equals(featureInfo.getURI())) {
                                            filteredIndependedFeatures.add(feature);
                                        }
                                    }
                                }
                            }

                            model.setIndependentFeatures(filteredIndependedFeatures);
                            model.setDependentFeatures(Arrays.asList(predictionFeature));
                            model.setMeta(modelMeta);

                            List<String> predictedFeatures = new ArrayList<>();
                            for (String featureTitle : trainingResponse.getPredictedFeatures()) {
                                Feature predictionFeatureResource = featureHandler.findByTitleAndSource(featureTitle, "algorithm/" + algorithm.getId());
                                if (predictionFeatureResource == null) {
                                    // Create the prediction features (POST /feature)
                                    String predFeatID = randomStringGenerator.nextString(12);
                                    predictionFeatureResource = new Feature();
                                    predictionFeatureResource.setId(predFeatID);
                                    predictionFeatureResource
                                            .setPredictorFor(predictionFeature);
                                    predictionFeatureResource.setMeta(MetaInfoBuilder
                                            .builder()
                                            .addSources(/*messageBody.get("base_uri") + */"algorithm/" + algorithm.getId())
                                            .addComments("Feature created to hold predictions by algorithm with ID " + model.getId())
                                            .addTitles(featureTitle)
                                            .addSeeAlso(predictionFeature)
                                            .addCreators(algorithm.getMeta().getCreators())
                                            .build());
                                    /* Create feature */
                                    featureHandler.create(predictionFeatureResource);
                                }
                                predictedFeatures.add(baseURI + "feature/" + predictionFeatureResource.getId());
                            }
                            model.setPredictedFeatures(predictedFeatures);
                            futureModel.complete(model);
                            break;
                        case 400:
                            String message = new BufferedReader(new InputStreamReader(responseStream))
                                    .lines().collect(Collectors.joining("\n"));
                            futureModel.completeExceptionally(new BadRequestException(message));
                            break;
                        case 500:
                            message = new BufferedReader(new InputStreamReader(responseStream))
                                    .lines().collect(Collectors.joining("\n"));
                            futureModel.completeExceptionally(new InternalServerErrorException(message));
                            break;
                        default:
                            message = new BufferedReader(new InputStreamReader(responseStream))
                                    .lines().collect(Collectors.joining("\n"));
                            futureModel.completeExceptionally(new InternalServerErrorException(message));
                    }
                } catch (IOException | UnsupportedOperationException ex) {
                    futureModel.completeExceptionally(ex);
                } catch (JaqpotDocumentSizeExceededException e) {
                    futureModel.completeExceptionally(e);
                    e.printStackTrace();
                }
            }

            @Override
            public void failed(final Exception ex) {
                futureMap.remove(taskId);
                futureModel.completeExceptionally(ex);
            }

            @Override
            public void cancelled() {
                futureMap.remove(taskId);
                futureModel.cancel(true);
            }

        });

        serializer.write(trainingRequest, out);
        try {
            out.close();
        } catch (IOException ex) {
            futureModel.completeExceptionally(ex);
        }

        futureMap.put(taskId, futureResponse);
        return futureModel;
    }

    @Override
    public Future<Dataset> predict(Dataset inputDataset, Model model, MetaInfo datasetMeta, String taskId, Doa doa) {

        CompletableFuture<Dataset> futureDataset = new CompletableFuture<>();

        Dataset dataset = DatasetFactory.copy(inputDataset);
        Dataset tempWithDependentFeatures = DatasetFactory.select(dataset, new HashSet<>(model.getDependentFeatures()));

//        dataset.getDataEntry().parallelStream()
//                .forEach(dataEntry -> {
//                    dataEntry.getValues().keySet().retainAll(model.getIndependentFeatures());
//                });
        PredictionRequest predictionRequest = new PredictionRequest();
        predictionRequest.setDataset(dataset);
        predictionRequest.setRawModel(model.getActualModel());
        predictionRequest.setAdditionalInfo(model.getAdditionalInfo());
        if (doa != null) {
            predictionRequest.setDoaMatrix(doa.getDoaMatrix());
        }
//        ObjectMapper mapper = new ObjectMapper();
//        try{
//            System.out.println(mapper.writeValueAsString(predictionRequest));
//        }catch(Exception e){
//            LOG.log(Level.SEVERE, e.getLocalizedMessage());
//        }
        Algorithm algo = algorithmHandler.find(model.getAlgorithm().getId());
        final HttpPost request = new HttpPost(algo.getPredictionService());
        request.addHeader("Accept", "application/json");
        request.addHeader("Content-Type", "application/json");

        PipedOutputStream out = new PipedOutputStream();
        PipedInputStream in;
        try {
            in = new PipedInputStream(out);
        } catch (IOException ex) {
            futureDataset.completeExceptionally(ex);
            return futureDataset;
        }
        request.setEntity(new InputStreamEntity(in, ContentType.APPLICATION_JSON));
        Future futureResponse = client.execute(request, new FutureCallback<HttpResponse>() {

            @Override
            public void completed(final HttpResponse response) {
                futureMap.remove(taskId);
                int status = response.getStatusLine().getStatusCode();
                try {
                    InputStream responseStream = response.getEntity().getContent();

                    switch (status) {
                        case 200:
                        case 201:
                            try {
                                PredictionResponse predictionResponse = serializer.parse(responseStream, PredictionResponse.class);

                                List<LinkedHashMap<String, Object>> predictions = predictionResponse.getPredictions();
                                if (dataset.getDataEntry().isEmpty()) {
                                    DatasetFactory.addEmptyRows(dataset, predictions.size());
                                }
                                if (model.getAlgorithm().getOntologicalClasses() != null && model.getAlgorithm().getOntologicalClasses().contains("ot:PBPK")) {
                                    DatasetFactory.addEmptyRows(dataset, predictions.size());
                                }
                                List<String> depFeats = model.getPredictedFeatures();
                                List<Feature> features = new ArrayList();
                                depFeats.forEach((String feat) -> {

                                    String[] splF = feat.split("/");
                                    String id = splF[splF.length - 1];
                                    features.add(featureHandler.find(id));

//                                    try {
//                                        String[] splF = feat.split("/");
//                                        String id = splF[splF.length - 1];
//                                        Feature feature = FeatureFactory.predictionFeature(featureHandler.find(id));
//                                        feature.getMeta().getCreators().clear();
//                                        feature.getMeta().setCreators(dataset.getMeta().getCreators());
//                                        featureHandler.create(feature);
//                                        
//                                        features.add(feature);
//                                    } catch (JaqpotDocumentSizeExceededException ex) {
//                                        futureDataset.completeExceptionally(ex);
//                                    }
                                });

//                                features = featureHandler.findBySource("algorithm/" + model.getAlgorithm().getId());
                                IntStream.range(0, dataset.getDataEntry().size())
                                        // .parallel()
                                        .forEach(i -> {
                                            Map<String, Object> row = predictions.get(i);
                                            DataEntry dataEntry = dataset.getDataEntry().get(i);
                                            if (model.getAlgorithm().getOntologicalClasses() != null) {
                                                if (model.getAlgorithm().getOntologicalClasses().contains("ot:Scaling")
                                                || model.getAlgorithm().getOntologicalClasses().contains("ot:Transformation")
                                                || model.getAlgorithm().getOntologicalClasses().contains("ot:ClearDataset")
                                                || model.getAlgorithm().getOntologicalClasses().contains("ot:PBPK")) {
                                                    dataEntry.getValues().clear();
                                                    dataset.getFeatures().clear();

                                                }
                                            }

                                            row.entrySet()
                                            .stream()
                                            .forEach(entry -> {
//                                                    Feature feature = featureHandler.findByTitleAndSource(entry.getKey(), "algorithm/" + model.getAlgorithm().getId());
                                                Feature feature = features.stream()
                                                .filter(f -> f.getMeta().getTitles().contains(entry.getKey()))
                                                .findFirst()
                                                .orElse(null);
                                                int size = dataEntry.getValues().size();
                                                if (entry.getKey().equals("DOA")) {
                                                    int sizeForDoa = dataEntry.getValues().size();
                                                    dataEntry.getValues().put(String.valueOf(size), entry.getValue());
                                                    FeatureInfo featInfoForDoa = new FeatureInfo(baseURI + "feature/doa", "DOA");
                                                    featInfoForDoa.setCategory(Dataset.DescriptorCategory.PREDICTED);
                                                    featInfoForDoa.setKey(String.valueOf(sizeForDoa));
                                                    dataset.getFeatures().add(featInfoForDoa);
                                                }
                                                if (feature == null) {
                                                    return;
                                                }

                                                dataEntry.getValues().put(String.valueOf(size), entry.getValue());
                                                FeatureInfo featInfo = new FeatureInfo(baseURI + "feature/" + feature.getId(), feature.getMeta().getTitles().stream().findFirst().get());
                                                featInfo.setCategory(Dataset.DescriptorCategory.PREDICTED);
                                                featInfo.setKey(String.valueOf(size));
                                                dataset.getFeatures().add(featInfo);

                                            });
                                        });
                                dataset.setId(randomStringGenerator.nextString(20));
                                dataset.setTotalRows(dataset.getDataEntry().size());
                                dataset.setMeta(datasetMeta);
                                dataset.setExistence(Dataset.DatasetExistence.PREDICTED);
                                futureDataset.complete(DatasetFactory.mergeColumns(dataset, tempWithDependentFeatures));
                            } catch (Exception ex) {
                                futureDataset.completeExceptionally(ex);
                            }
                            break;
                        case 400:
                            String message = new BufferedReader(new InputStreamReader(responseStream))
                                    .lines().collect(Collectors.joining("\n"));
                            futureDataset.completeExceptionally(new BadRequestException(message));
                            break;
                        case 404:
                            message = new BufferedReader(new InputStreamReader(responseStream))
                                    .lines().collect(Collectors.joining("\n"));
                            futureDataset.completeExceptionally(new NotFoundException(message));
                            break;
                        case 500:
                            message = new BufferedReader(new InputStreamReader(responseStream))
                                    .lines().collect(Collectors.joining("\n"));
                            futureDataset.completeExceptionally(new InternalServerErrorException(message));
                            break;
                        default:
                            message = new BufferedReader(new InputStreamReader(responseStream))
                                    .lines().collect(Collectors.joining("\n"));
                            futureDataset.completeExceptionally(new InternalServerErrorException(message));
                    }
                } catch (IOException | UnsupportedOperationException ex) {
                    futureDataset.completeExceptionally(ex);
                }
            }

            @Override
            public void failed(final Exception ex) {
                futureMap.remove(taskId);
                futureDataset.completeExceptionally(new InternalServerErrorException(ex));
            }

            @Override
            public void cancelled() {
                futureMap.remove(taskId);
                futureDataset.cancel(true);
            }
        });
        serializer.write(predictionRequest, out);
        try {
            out.close();
        } catch (IOException ex) {
            futureDataset.completeExceptionally(ex);
        }
        futureMap.put(taskId, futureResponse);
        return futureDataset;
    }

    @Override
    public Future<Dataset> transform(Dataset dataset, Algorithm algorithm, Map<String, Object> parameters, String predictionFeature, MetaInfo datasetMeta, String taskId, Doa doa) {
        try {
            Model model = this.train(dataset, algorithm, parameters, predictionFeature, datasetMeta, taskId).get();
            return this.predict(dataset, model, datasetMeta, taskId, doa);
        } catch (InterruptedException ex) {
            throw new RuntimeException("Error while transforming Dataset:" + dataset.getId() + " with Algorithm:" + algorithm.getId(), ex);
        } catch (ExecutionException ex) {
            throw new RuntimeException("Error while transforming Dataset:" + dataset.getId() + " with Algorithm:" + algorithm.getId(), ex.getCause());
        }
    }

    @Override
    public Future<Report> report(Dataset dataset, Algorithm algorithm, Map<String, Object> parameters, MetaInfo reportMeta, String taskId) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean cancel(String taskId) {
        Future future = futureMap.get(taskId);
        if (future != null && !future.isCancelled() && !future.isDone()) {
            future.cancel(true);
            return true;
        }
        return false;
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

}

//        try {
//            ObjectMapper mapper = new ObjectMapper();
//        String jsonInString = mapper.writeValueAsString(predictionRequest);
//        System.out.println(jsonInString);
//    }
//    catch (Exception r
//        ) {
//            System.out.println(r);
//    }
