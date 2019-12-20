/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jaqpot.core.service.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import org.apache.commons.lang3.math.NumberUtils;
import org.jaqpot.core.annotations.Jackson;
import org.jaqpot.core.data.DatasetHandler;
import org.jaqpot.core.data.DescriptorHandler;
import org.jaqpot.core.data.FeatureHandler;
import org.jaqpot.core.data.UserHandler;
import org.jaqpot.core.data.serialize.JSONSerializer;
import org.jaqpot.core.data.wrappers.DatasetLegacyWrapper;
import org.jaqpot.core.model.DataEntry;
import org.jaqpot.core.model.Descriptor;
import org.jaqpot.core.model.ErrorReport;
import org.jaqpot.core.model.Feature;
import org.jaqpot.core.model.MetaInfo;
import org.jaqpot.core.model.Task;
import org.jaqpot.core.model.Task.Status;
import org.jaqpot.core.model.User;
import org.jaqpot.core.model.builder.FeatureBuilder;
import org.jaqpot.core.model.builder.MetaInfoBuilder;
import org.jaqpot.core.model.dto.dataset.Dataset;
import org.jaqpot.core.model.dto.dataset.EntryId;
import org.jaqpot.core.model.dto.dataset.FeatureInfo;
import org.jaqpot.core.model.dto.predict.CompositePredictRequest;
import org.jaqpot.core.model.dto.predict.CompositePredictResponse;
import org.jaqpot.core.model.facades.UserFacade;
import org.jaqpot.core.model.factory.DatasetFactory;
import org.jaqpot.core.model.util.ROG;
import org.jaqpot.core.properties.PropertyManager;
import org.jaqpot.core.service.annotations.TokenSecured;
import org.jaqpot.core.service.authentication.AAService;
import org.jaqpot.core.service.authentication.RoleEnum;
import org.jaqpot.core.service.exceptions.JaqpotDocumentSizeExceededException;
import org.jaqpot.core.service.exceptions.QuotaExceededException;
import org.jaqpot.core.service.exceptions.parameter.ParameterIsNullException;
import org.jaqpot.core.service.data.CompositePredictionService;
import org.jaqpot.core.service.exceptions.JaqpotNotAuthorizedException;
import org.jaqpot.core.service.exceptions.parameter.ParameterRangeException;
import org.jaqpot.core.service.exceptions.parameter.ParameterScopeException;
import org.jaqpot.core.service.exceptions.parameter.ParameterTypeException;
import org.jaqpot.core.service.validator.ParameterValidator;
import static org.jaqpot.core.util.CSVUtils.parseLine;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

/**
 *
 * @author aggel
 */

/**
 * Between a set of alternative architectural approaches that could have been used for the development of the
 * ChemPredictorFeature, it was preferred to be used that already used in Jaqpot 4 & 5 for reasons of keeping
 * uniformity in the coding style.
 */

@Path("/chemPredictor")
@Tag(name = "chemPredictor")
@SecurityScheme(name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        in = SecuritySchemeIn.HEADER,
        scheme = "bearer",
        description = "add the token retreived from oidc. Example:  Bearer <API_KEY>"
)
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
public class ChemPredictorResource {

    @EJB
    UserHandler userHandler;

    @EJB
    DescriptorHandler descriptorHandler;

    @Context
    SecurityContext securityContext;

    @EJB
    DatasetHandler datasetHandler;

    @EJB
    FeatureHandler featureHandler;

    @EJB
    AAService aaService;

    @Inject
    @Jackson
    JSONSerializer serializer;

    @Inject
    ParameterValidator parameterValidator;

    @Inject
    PropertyManager propertyManager;

    @EJB
    DatasetLegacyWrapper datasetLegacyWrapper;

    @Context
    UriInfo uriInfo;

    @EJB
    CompositePredictionService compositePredictionService;

    private static final Logger LOG = Logger.getLogger(DescriptorResource.class.getName());

    @POST
    @TokenSecured({RoleEnum.DEFAULT_USER})
    @Path("/apply")
    //@Consumes({MediaType.MULTIPART_FORM_DATA})
    @Consumes({MediaType.APPLICATION_JSON})
    
    @Operation(summary = "SUMMARY",
            description = "DESCRIPTION",
            responses = {
                @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = String.class)),
                        description = ""),
                @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorReport.class)), description = ""),
                @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorReport.class)), description = "You are not authorized to access this resource"),
                @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorReport.class)), description = "This request is forbidden (e.g., no authentication token is provided)"),
                @ApiResponse(responseCode = "500", content = @Content(schema = @Schema(implementation = ErrorReport.class)), description = "Internal server error - this request cannot be served.")
            })
    @org.jaqpot.core.service.annotations.Task
    public Response apply(
           // @Parameter(name = "file", description = "csv file", schema = @Schema(type = "string", format = "binary")) @FormParam("file") String file,
//            @Parameter(name = "smilesInput", description = "List of smiles molecules", schema = @Schema(implementation = String.class)) @FormParam("smilesInput") String smilesInput,
//            //@Parameter(name = "algorithmId", schema = @Schema(type = "String")) @FormParam("algorithmId") String algorithmId,
//            @Parameter(name = "predictionFeature", schema = @Schema(type = "String")) @FormParam("predictionFeature") String predictionFeature,
//            @Parameter(name = "parameters", description = "The parameters for the descriptor that will be applied", schema = @Schema(implementation = String.class)) @FormParam("parameters") String parameters,
//            @Parameter(name = "modelId", schema = @Schema(type = "String")) @FormParam("modelId") String modelId,
//            @Parameter(name = "Authorization", description = "Authorization token", schema = @Schema(implementation = String.class)) @HeaderParam("Authorization") String api_key,
//            @Parameter(description = "multipartFormData input", hidden = true) MultipartFormDataInput input)
            CompositePredictRequest request,
            @Parameter(name = "Authorization", description = "Authorization token", schema = @Schema(implementation = String.class)) @HeaderParam("Authorization") String api_key)
            throws URISyntaxException, QuotaExceededException, JaqpotDocumentSizeExceededException, ParameterIsNullException, ParameterTypeException, ParameterRangeException, ParameterScopeException, JaqpotNotAuthorizedException {

        String[] apiA = api_key.split("\\s+");
        String apiKey = apiA[1];
        User user = userHandler.find(securityContext.getUserPrincipal().getName());

//        long datasetCount = datasetHandler.countAllOfCreator(user.getId());
//        int maxAllowedDatasets = new UserFacade(user).getMaxDatasets();
//
//        if (datasetCount > maxAllowedDatasets) {
//            LOG.info(String.format("User %s has %d datasets while maximum is %d",
//                    user.getId(), datasetCount, maxAllowedDatasets));
//            throw new QuotaExceededException("Dear " + user.getId()
//                    + ", your quota has been exceeded; you already have " + datasetCount + " datasets. "
//                    + "No more than " + maxAllowedDatasets + " are allowed with your subscription.");
//        }
        //Map<String, List<InputPart>> uploadForm = input.getFormDataMap();
        //List<InputPart> inputParts = uploadForm.get("file");
        //List<String> smilesList = null;
        List<List<Entry<String, String>>> smilesList = new ArrayList();
//        try {
//            //smilesInput = uploadForm.get("smilesInput").get(0).getBodyAsString();
//        } catch (IOException ex) {
       //     Logger.getLogger(ChemPredictorResource.class.getName()).log(Level.SEVERE, null, ex);
       // }
//        if (smilesInput == null || smilesInput.equals("")) {
//            if (inputParts == null || inputParts.isEmpty()) {
//                throw new ParameterIsNullException("smiles");
//            }
//        }
        String smilesInput = request.getSmilesInput();
        String modelId = request.getModelId();
        String parameters = serializer.write(request.getParameters());
        String predictionFeature = request.getPredictionFeature();
        
        if (smilesInput == null || smilesInput.equals("")) {
                throw new ParameterIsNullException("smiles");
        }

//        try {
//            algorithmId = uploadForm.get("algorithmId").get(0).getBodyAsString();
//
//        } catch (IOException ex) {
//            Logger.getLogger(ChemPredictorResource.class.getName()).log(Level.SEVERE, null, ex);
//        }

//        try {
//            modelId = uploadForm.get("modelId").get(0).getBodyAsString();
//
//        } catch (IOException ex) {
//            Logger.getLogger(ChemPredictorResource.class.getName()).log(Level.SEVERE, null, ex);
//        }

//        try {
//            predictionFeature = uploadForm.get("predictionFeature").get(0).getBodyAsString();
//
//        } catch (IOException ex) {
//            Logger.getLogger(ChemPredictorResource.class.getName()).log(Level.SEVERE, null, ex);
//        }
//
//        if (uploadForm.get("parameters") != null && !uploadForm.get("parameters").isEmpty()) {
//            try {
//                parameters = uploadForm.get("parameters").get(0).getBodyAsString();
//            } catch (IOException ex) {
//                Logger.getLogger(ChemPredictorResource.class.getName()).log(Level.SEVERE, null, ex);
//            }
//        }

        String featureName = "smiles";

//        if (algorithmId == null) {
//            throw new ParameterIsNullException("algorithmId");
//        }

        if (predictionFeature == null) {
            throw new ParameterIsNullException("predictionFeature");
        }

        if (modelId == null) {
            throw new ParameterIsNullException("modelId");
        }

        Date date = new Date();
        Dataset dataset = new Dataset();

//        if (inputParts != null && !inputParts.isEmpty()) {
//            for (InputPart inputPart : inputParts) {
//                try {
//                    MultivaluedMap<String, String> header = inputPart.getHeaders();
//                    InputStream inputStream = inputPart.getBody(InputStream.class, null);
//                    calculateRowsAndColumns(dataset, inputStream);
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
//        } else {
            if (smilesInput != null && !smilesInput.equals("")) {
                List<String> smilesStrings = Arrays.asList(smilesInput.split(","));

                smilesStrings.stream().forEach((String str) -> {
                               HashMap featMap = new HashMap();
                               featMap.put("smiles", str);
                               Entry e = (Entry) featMap.entrySet().iterator().next();
                               List<Entry<String,String>> featuresList = new ArrayList();
                               featuresList.add(e);
                               smilesList.add(featuresList);
                });
                String featureTemplate = propertyManager.getProperty(PropertyManager.PropertyType.JAQPOT_BASE_SERVICE) + "feature/" + featureName;
                dataset = DatasetFactory.create(smilesList, featureTemplate);
            }
       // }

        if (dataset.getDataEntry() == null || dataset.getDataEntry().isEmpty()) {
            throw new IllegalArgumentException("Resulting dataset is empty");
        }

        populateFeatures(dataset);

        ROG randomStringGenerator = new ROG(true);
        dataset.setId(randomStringGenerator.nextString(14));

        //datasetLegacyWrapper.create(dataset);
        String newDatasetURI = propertyManager.getProperty(PropertyManager.PropertyType.JAQPOT_BASE_SERVICE) + "dataset/" + dataset.getId();
        dataset.setDatasetURI(newDatasetURI);
        //String newFeatureURI = propertyManager.getProperty(PropertyManager.PropertyType.JAQPOT_BASE_SERVICE) + "feature/" + feature.getId();
        // dataset.getFeatures().iterator().next().setURI(newFeatureURI);

        dataset.setFeatured(Boolean.FALSE);
        MetaInfo datasetMeta = MetaInfoBuilder.builder()
                .addTitles(" cdk -" + date.toString())
                .addDescriptions("Descriptor used: cdk")
                .addComments("Created from raw data entries")
                .addCreators(aaService.getUserFromSSO(apiKey).getId())
                .addSources(newDatasetURI)
                .build();
        dataset.setMeta(datasetMeta);
        dataset.setExistence(Dataset.DatasetExistence.DESCRIPTORSADDED);

        datasetLegacyWrapper.create(dataset);
        //       featureHandler.create(feature);

        Map<String, Object> options = new HashMap<>();
        options.put("api_key", apiKey);
        options.put("parameters", parameters);
        options.put("base_uri", uriInfo.getBaseUri().toString());
        options.put("creator", securityContext.getUserPrincipal().getName());
        options.put("datasetURI", dataset.getDatasetURI());

        HashSet<String> featureURIs = new HashSet();
        featureURIs = dataset.getFeatures().stream()
                .map(fi -> {
                    return fi.getURI();
                }).collect(Collectors.toCollection(HashSet::new));

        String featureUrisSer = serializer.write(featureURIs);

        options.put("featureURIs", featureUrisSer);

        String id = new ROG(Boolean.TRUE).nextString(12);
        options.put("generatedDatasetId", id);
        String generatedDatasetURI = propertyManager.getProperty(PropertyManager.PropertyType.JAQPOT_BASE_SERVICE) + "dataset/" + id;
        options.put("generatedDatasetURI", generatedDatasetURI);

        options.put("dataset_uri", generatedDatasetURI);
        options.put("creator", securityContext.getUserPrincipal().getName());
        options.put("modelId", modelId);

        Descriptor descriptor = descriptorHandler.find("cdk");
        parameterValidator.validate(parameters, descriptor.getParameters());

        Task taskCP = compositePredictionService.initiateCompositePrediction(options, securityContext.getUserPrincipal().getName());
 
       
        CompositePredictResponse response = new CompositePredictResponse();
        
        response.setTaskId(taskCP.getId());
        return Response.ok(taskCP).build();

    }

    private void calculateRowsAndColumns(Dataset dataset, InputStream stream) {
        Scanner scanner = new Scanner(stream);

        Set<FeatureInfo> featureInfoList = new HashSet<>();
        List<DataEntry> dataEntryList = new ArrayList<>();
        List<String> feature = new LinkedList<>();
        boolean firstLine = true;
        int count = 0;
        while (scanner.hasNext()) {

            List<String> line = parseLine(scanner.nextLine());
            if (firstLine) {
                for (String l : line) {
                    String pseudoURL = "/feature/" + l.trim().replaceAll("[ .]", "_"); //uriInfo.getBaseUri().toString()+
                    feature.add(pseudoURL);
                    featureInfoList.add(new FeatureInfo(pseudoURL, l, "NA", new HashMap<>(), Dataset.DescriptorCategory.EXPERIMENTAL));
                }
                firstLine = false;
            } else {
                Iterator<String> it1 = feature.iterator();
                Iterator<String> it2 = line.iterator();
                TreeMap<String, Object> values = new TreeMap<>();
                while (it1.hasNext() && it2.hasNext()) {
                    String it = it2.next();
                    if (!NumberUtils.isParsable(it)) {
                        values.put(it1.next(), it);
                    } else {
                        values.put(it1.next(), Float.parseFloat(it));
                    }
                }

                DataEntry dataEntry = new DataEntry();
                dataEntry.setValues(values);
                EntryId entryId = new EntryId();
                entryId.setName("row" + count);
                //entryId.setURI(propertyManager.getProperty(PropertyManager.PropertyType.JAQPOT_BASE_SERVICE) + "substance/" + new ROG(true).nextString(12));
                //entryId.setOwnerUUID("7da545dd-2544-43b0-b834-9ec02553f7f2");

                dataEntry.setEntryId(entryId);
                dataEntryList.add(dataEntry);
            }
            count++;
        }
        scanner.close();
        dataset.setFeatures(featureInfoList);
        dataset.setDataEntry(dataEntryList);
    }

    private void populateFeatures(Dataset dataset) throws JaqpotDocumentSizeExceededException {
        int key = 0;
        for (FeatureInfo featureInfo : dataset.getFeatures()) {

            String trimmedFeatureURI = propertyManager.getProperty(PropertyManager.PropertyType.JAQPOT_BASE_SERVICE) + "feature/" + featureInfo.getName().replaceAll("\\s+", " ").replaceAll("[ .]", "_") + "_" + new ROG(true).nextString(12);

            String trimmedFeatureName = featureInfo.getName().replaceAll("\\s+", " ").replaceAll("[.]", "_").replaceAll("[.]", "_");

            Feature f = FeatureBuilder.builder(trimmedFeatureURI.split("feature/")[1])
                    .addTitles(featureInfo.getName()).build();
            featureHandler.create(f);

            //Update FeatureURIS in Data Entries
//            for (DataEntry dataentry : dataset.getDataEntry()) {
//                Object value = dataentry.getValues().remove(featureInfo.getURI());
//                dataentry.getValues().put(String.valueOf(key), value);
//            }
            //Update FeatureURI in Feature Info
            featureInfo.setURI(trimmedFeatureURI);
            featureInfo.setKey(String.valueOf(key));
            featureInfo.setName(trimmedFeatureName);
            key += 1;
        }
    }

}
