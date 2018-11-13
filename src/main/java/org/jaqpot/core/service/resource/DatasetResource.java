/*
 *
 * JAQPOT Quattro
 *
 * JAQPOT Quattro and the components shipped with it (web applications and beans)
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
package org.jaqpot.core.service.resource;

import io.swagger.annotations.*;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.validator.routines.UrlValidator;
import org.jaqpot.core.data.*;
import org.jaqpot.core.data.wrappers.DatasetLegacyWrapper;
import org.jaqpot.core.model.*;
import org.jaqpot.core.model.builder.MetaInfoBuilder;
import org.jaqpot.core.model.dto.dataset.Dataset;
import org.jaqpot.core.model.dto.jpdi.TrainingRequest;
import org.jaqpot.core.model.facades.UserFacade;
import org.jaqpot.core.model.factory.DatasetFactory;
import org.jaqpot.core.model.util.ROG;
import org.jaqpot.core.properties.PropertyManager;
import org.jaqpot.core.service.annotations.TokenSecured;
import org.jaqpot.core.service.annotations.UnSecure;
import org.jaqpot.core.service.authentication.RoleEnum;
import org.jaqpot.core.service.client.ambit.Ambit;
import org.jaqpot.core.service.client.jpdi.JPDIClient;
import org.jaqpot.core.service.exceptions.JaqpotDocumentSizeExceededException;
import org.jaqpot.core.service.exceptions.JaqpotForbiddenException;
import org.jaqpot.core.service.exceptions.QuotaExceededException;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.apache.commons.lang3.math.NumberUtils;
import org.jaqpot.core.model.dto.dataset.FeatureInfo;
import org.jaqpot.core.service.exceptions.parameter.ParameterInvalidURIException;
import org.jaqpot.core.service.exceptions.parameter.ParameterIsNullException;
import org.jaqpot.core.service.exceptions.parameter.ParameterRangeException;
import org.jaqpot.core.service.exceptions.parameter.ParameterScopeException;
import org.jaqpot.core.service.exceptions.parameter.ParameterTypeException;
import org.jaqpot.core.service.filter.AuthorizationEnum;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;

/**
 *
 * @author Charalampos Chomenidis
 * @author Pantelis Sopasakis
 */
@Path("dataset")
@Api(value = "dataset", description = "Dataset API")
@Produces({"application/json", "text/uri-list"})
public class DatasetResource {

    private static final Logger LOG = Logger.getLogger(DatasetResource.class.getName());

    @EJB
    DatasetHandler datasetHandler;

    @EJB
    UserHandler userHandler;

    @Context
    UriInfo uriInfo;

    @EJB
    ModelHandler modelHandler;

    @EJB
    AlgorithmHandler algorithmHandler;

    @EJB
    DataEntryHandler dataEntryHandler;

    @EJB
    ReportHandler reportHandler;

    @EJB
    DatasetLegacyWrapper datasetLegacyWrapper;

    @Inject
    Ambit ambitClient;

    @Inject
    @UnSecure
    Client client;

    @Inject
    JPDIClient jpdiClient;

    @Inject
    PropertyManager properyManager;

    @Context
    SecurityContext securityContext;

    @GET
    @TokenSecured({RoleEnum.DEFAULT_USER})
//    @Authorize({AuthorizationEnum.ORGANIZATION,AuthorizationEnum.OWNER})
    @Produces({MediaType.APPLICATION_JSON, "text/uri-list"})
    @ApiOperation(value = "Finds all Datasets",
            notes = "Finds all Datasets in the DB of Jaqpot and returns them in a list. Results can be obtained "
            + "either in the form of a URI list or as a JSON list as specified by the Accept HTTP header. "
            + "In the latter case, a list will be returned containing only the IDs of the datasets, their metadata "
            + "and their ontological classes. The parameter max, which specifies the maximum number of IDs to be "
            + "listed is limited to 500; if the client specifies a larger value, an HTTP Warning Header will be "
            + "returned (RFC 2616) with code P670.",
            position = 1)
    @ApiResponses(value = {
        @ApiResponse(code = 200, response = Dataset.class, responseContainer = "List",
                message = "Datasets found and are listed in the response body"),
        @ApiResponse(code = 401, response = ErrorReport.class, message = "You are not authorized to access this resource"),
        @ApiResponse(code = 403, response = ErrorReport.class, message = "This request is forbidden (e.g., no authentication token is provided)"),
        @ApiResponse(code = 500, response = ErrorReport.class, message = "Internal server error - this request cannot be served.")
    })
    public Response listDatasets(
            @ApiParam(value = "start", defaultValue = "0") @QueryParam("start") Integer start,
            @ApiParam(value = "max - the server imposes an upper limit of 500 on this "
                    + "parameter.", defaultValue = "10") @QueryParam("max") Integer max,
            @ApiParam(value = "description for the dataset", required = false, allowableValues = "UPLOADED, CREATED, TRANSFORMED, PREDICTED, FROMPRETRAINED, DESCRIPTORS, ALL") @QueryParam("existence") String datasetexistence,
            @ApiParam(value = "organization for the dataset", required = false) @QueryParam("organization") String organization
    ) {
        start = start != null ? start : 0;
        if (max == null || max > 500) {
            max = 500;
        }
        String creator = securityContext.getUserPrincipal().getName();

        List<Dataset> datasets = new ArrayList();
        Number total = null;
        if (datasetexistence == null || datasetexistence.equals("ALL")) {
            datasets.addAll(datasetHandler.listMetaOfCreator(creator, start, max));
            total = datasetHandler.countAllOfCreator(creator);
        } else {
            switch (datasetexistence) {
                case "UPLOADED":
                    datasets.addAll(datasetHandler.listDatasetCreatorsExistence(creator, Dataset.DatasetExistence.UPLOADED, start, max));
                    total = datasetHandler.countCreatorsExistenseDatasets(creator, Dataset.DatasetExistence.UPLOADED);
                    break;
                case "CREATED":
                    datasets.addAll(datasetHandler.listDatasetCreatorsExistence(creator, Dataset.DatasetExistence.CREATED, start, max));
                    total = datasetHandler.countCreatorsExistenseDatasets(creator, Dataset.DatasetExistence.CREATED);
                    break;
                case "TRANSFORMED":
                    datasets.addAll(datasetHandler.listDatasetCreatorsExistence(creator, Dataset.DatasetExistence.TRANFORMED, start, max));
                    total = datasetHandler.countCreatorsExistenseDatasets(creator, Dataset.DatasetExistence.TRANFORMED);
                    break;
                case "PREDICTED":
                    datasets.addAll(datasetHandler.listDatasetCreatorsExistence(creator, Dataset.DatasetExistence.PREDICTED, start, max));
                    total = datasetHandler.countCreatorsExistenseDatasets(creator, Dataset.DatasetExistence.PREDICTED);
                    break;
                case "PRETRAINED":
                    datasets.addAll(datasetHandler.listDatasetCreatorsExistence(creator,Dataset.DatasetExistence.FROMPRETRAINED , start, max));
                    total = datasetHandler.countCreatorsExistenseDatasets(creator, Dataset.DatasetExistence.FROMPRETRAINED);
                    break;
                case "DESCRIPTORS":
                    datasets.addAll(datasetHandler.listDatasetCreatorsExistence(creator, Dataset.DatasetExistence.DESCRIPTORSADDED, start, max));
                    total = datasetHandler.countCreatorsExistenseDatasets(creator, Dataset.DatasetExistence.DESCRIPTORSADDED);
                    break;
            }

        }

        return Response.ok(datasets)
                .status(Response.Status.OK)
                .header("total", total)
                .build();
    }

    @GET
    @TokenSecured({RoleEnum.DEFAULT_USER})
    @Produces({"text/csv", MediaType.APPLICATION_JSON})

    @Path("{id}")
    @ApiOperation(value = "Finds Dataset by Id",
            notes = "Finds specified Dataset"
    )
    @ApiResponses(value = {
        @ApiResponse(code = 200, response = Dataset.class, message = "Dataset was found")
        ,
            @ApiResponse(code = 404, response = ErrorReport.class, message = "Dataset was not found in the system")
        ,
            @ApiResponse(code = 401, response = ErrorReport.class, message = "You are not authorized to access this resource")
        ,
            @ApiResponse(code = 403, response = ErrorReport.class, message = "This request is forbidden (e.g., no authentication token is provided)")
        ,
            @ApiResponse(code = 500, response = ErrorReport.class, message = "Internal server error - this request cannot be served.")
    })
    public Response getDataset(
            @ApiParam(value = "Authorization token") @HeaderParam("Authorization") String api_key,
            @PathParam("id") String id,
            @QueryParam("dataEntries") Boolean dataEntries,
            @QueryParam("rowStart") Integer rowStart,
            @QueryParam("rowMax") Integer rowMax,
            @QueryParam("colStart") Integer colStart,
            @QueryParam("colMax") Integer colMax,
            @QueryParam("stratify") String stratify,
            @QueryParam("seed") Long seed,
            @QueryParam("folds") Integer folds,
            @QueryParam("target_feature") String targetFeature) {
        Dataset dataset = null;
        if (dataEntries==null) dataEntries=false;
        if (dataEntries)
                dataset = datasetLegacyWrapper.find(id, rowStart, rowMax, colStart, colMax);
         else
            dataset = datasetHandler.find(id);
        if (dataset == null)
            throw new NotFoundException("Could not find Dataset with id:" + id);
        return Response.ok(dataset).build();
    }

    @GET
    @TokenSecured({RoleEnum.DEFAULT_USER})
    @Path("/featured")
    @Produces({MediaType.APPLICATION_JSON, "text/uri-list"})
    @ApiOperation(value = "Finds all Datasets",
            notes = "Finds Featured Datasets in the DB of Jaqpot and returns them in a list. Results can be obtained "
            + "either in the form of a URI list or as a JSON list as specified by the Accept HTTP header. "
            + "In the latter case, a list will be returned containing only the IDs of the datasets, their metadata "
            + "and their ontological classes. The parameter max, which specifies the maximum number of IDs to be "
            + "listed is limited to 500; if the client specifies a larger value, an HTTP Warning Header will be "
            + "returned (RFC 2616) with code P670.",
            position = 1)
    @ApiResponses(value = {
        @ApiResponse(code = 200, response = Dataset.class, responseContainer = "List", message = "Datasets found and are listed in the response body")
        ,
        @ApiResponse(code = 401, response = ErrorReport.class, message = "You are not authorized to access this resource")
        ,
        @ApiResponse(code = 403, response = ErrorReport.class, message = "This request is forbidden (e.g., no authentication token is provided)")
        ,
        @ApiResponse(code = 500, response = ErrorReport.class, message = "Internal server error - this request cannot be served.")
    })
    public Response listFeaturedDatasets(
            @ApiParam(value = "Authorization token") @HeaderParam("Authorization") String api_key,
            @ApiParam(value = "start", defaultValue = "0") @QueryParam("start") Integer start,
            @ApiParam(value = "max - the server imposes an upper limit of 500 on this "
                    + "parameter.", defaultValue = "10") @QueryParam("max") Integer max
    ) {
        start = start != null ? start : 0;
        boolean doWarnMax = false;
        if (max == null || max > 500) {
            max = 500;
        }
        return Response.ok(datasetHandler.findFeatured(start, max))
                .status(Response.Status.OK)
                .header("total", datasetHandler.countFeatured())
                .build();

    }

    @GET
    @TokenSecured({RoleEnum.DEFAULT_USER})
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{id}/features")
    @ApiOperation(value = "Finds Features of Dataset by Id",
            notes = "Finds specified Dataset's features")
    @ApiResponses(value = {
        @ApiResponse(code = 200, response = Dataset.class, message = "Dataset's features found and are listed in the response body"),
            @ApiResponse(code = 404, response = ErrorReport.class, message = "Dataset was not found in the system"),
            @ApiResponse(code = 401, response = ErrorReport.class, message = "You are not authorized to access this resource"),
            @ApiResponse(code = 403, response = ErrorReport.class, message = "This request is forbidden (e.g., no authentication token is provided)"),
            @ApiResponse(code = 500, response = ErrorReport.class, message = "Internal server error - this request cannot be served.")})
    public Response getDatasetFeatures(
            @ApiParam(value = "Authorization token") @HeaderParam("Authorization") String api_key,
            @PathParam("id") String id
    ) {
        Dataset dataset = datasetHandler.find(id);
        if (dataset == null) {
            throw new NotFoundException("Could not find Dataset with id:" + id);
        }
        return Response.ok(dataset.getFeatures()).build();
    }

    @GET
    @TokenSecured({RoleEnum.DEFAULT_USER})
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{id}/meta")
    @ApiOperation(value = "Finds MetaData of Dataset by Id",
            notes = "Finds specified Dataset's MetaData")
    @ApiResponses(value = {
        @ApiResponse(code = 200, response = Dataset.class, message = "Dataset's meta data found and are listed in the response body")
        ,
            @ApiResponse(code = 404, response = ErrorReport.class, message = "Dataset was not found in the system")
        ,
            @ApiResponse(code = 401, response = ErrorReport.class, message = "You are not authorized to access this resource")
        ,
            @ApiResponse(code = 403, response = ErrorReport.class, message = "This request is forbidden (e.g., no authentication token is provided)")
        ,
            @ApiResponse(code = 500, response = ErrorReport.class, message = "Internal server error - this request cannot be served.")
    })
    public Response getDatasetMeta(
            @ApiParam(value = "Authorization token") @HeaderParam("Authorization") String api_key,
            @PathParam("id") String id) {
        Dataset dataset = datasetHandler.find(id);
        dataset.setDataEntry(new ArrayList<>());
        if (dataset == null) {
            throw new NotFoundException("Could not find Dataset with id:" + id);
        }

        return Response.ok(dataset).build();
    }

    @POST
    @TokenSecured({RoleEnum.DEFAULT_USER})
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({"text/uri-list", MediaType.APPLICATION_JSON})
    @ApiOperation(value = "Creates a new Dataset",
            notes = "The new Dataset created will be assigned on a random generated Id")
    @ApiResponses(value = {
        @ApiResponse(code = 200, response = Dataset.class, message = "Dataset was created succesfully"),
            @ApiResponse(code = 403, response = ErrorReport.class, message = "Dataset quota has been exceeded"),
            @ApiResponse(code = 401, response = ErrorReport.class, message = "You are not authorized to access this resource"),
            @ApiResponse(code = 403, response = ErrorReport.class, message = "This request is forbidden (e.g., no authentication token is provided)"),
            @ApiResponse(code = 500, response = ErrorReport.class, message = "Internal server error - this request cannot be served.")
    })
    public Response createDataset(
            @ApiParam(value = "Authorization token") @HeaderParam("Authorization") String api_key,
            Dataset dataset) throws QuotaExceededException, URISyntaxException, JaqpotDocumentSizeExceededException {

        User user = userHandler.find(securityContext.getUserPrincipal().getName());
        long datasetCount = datasetHandler.countAllOfCreator(user.getId());
        int maxAllowedDatasets = new UserFacade(user).getMaxDatasets();

        if (datasetCount > maxAllowedDatasets) {
            LOG.info(String.format("User %s has %d datasets while maximum is %d",
                    user.getId(), datasetCount, maxAllowedDatasets));
            throw new QuotaExceededException("Dear " + user.getId()
                    + ", your quota has been exceeded; you already have " + datasetCount + " datasets. "
                    + "No more than " + maxAllowedDatasets + " are allowed with your subscription.");
        }

        ROG randomStringGenerator = new ROG(true);
        dataset.setId(randomStringGenerator.nextString(14));
        dataset.setFeatured(Boolean.FALSE);
        if (dataset.getMeta() == null) {
            dataset.setMeta(new MetaInfo());
        }
        dataset.getMeta().setCreators(new HashSet<>(Arrays.asList(securityContext.getUserPrincipal().getName())));
        dataset.setVisible(Boolean.TRUE);
        datasetLegacyWrapper.create(dataset);
        //datasetHandler.create(dataset);

        return Response.created(new URI(dataset.getId())).entity(dataset).build();

    }

    @POST
    @TokenSecured({RoleEnum.DEFAULT_USER})
    @Path("/empty")
    @Produces({"text/uri-list", MediaType.APPLICATION_JSON})
    @ApiOperation(value = "Creates a new empty Dataset",
            notes = "The new empty Dataset created will be assigned on a random generated Id")
    @ApiResponses(value = {
        @ApiResponse(code = 200, response = Dataset.class, message = "Dataset was created succesfully")
        ,
            @ApiResponse(code = 403, response = ErrorReport.class, message = "Dataset quota has been exceeded")
        ,
            @ApiResponse(code = 401, response = ErrorReport.class, message = "You are not authorized to access this resource")
        ,
            @ApiResponse(code = 403, response = ErrorReport.class, message = "This request is forbidden (e.g., no authentication token is provided)")
        ,
            @ApiResponse(code = 500, response = ErrorReport.class, message = "Internal server error - this request cannot be served.")
    })
    public Response createEmptyDataset(
            @ApiParam(value = "Authorization token") @HeaderParam("Authorization") String api_key,
            @FormParam("title") String title,
            @FormParam("description") String description) throws URISyntaxException, QuotaExceededException, JaqpotDocumentSizeExceededException {

        User user = userHandler.find(securityContext.getUserPrincipal().getName());
        long datasetCount = datasetHandler.countAllOfCreator(user.getId());
        int maxAllowedDatasets = new UserFacade(user).getMaxDatasets();

        if (datasetCount > maxAllowedDatasets) {
            LOG.info(String.format("User %s has %d datasets while maximum is %d",
                    user.getId(), datasetCount, maxAllowedDatasets));
            throw new QuotaExceededException("Dear " + user.getId()
                    + ", your quota has been exceeded; you already have " + datasetCount + " datasets. "
                    + "No more than " + maxAllowedDatasets + " are allowed with your subscription.");
        }

        Dataset emptyDataset = DatasetFactory.createEmpty(0);
        ROG randomStringGenerator = new ROG(true);
        emptyDataset.setId(randomStringGenerator.nextString(14));
        emptyDataset.setFeatured(Boolean.FALSE);
        emptyDataset.setMeta(MetaInfoBuilder.builder()
                .addTitles(title)
                .addDescriptions(description)
                .build()
        );

        emptyDataset.getMeta().setCreators(new HashSet<>(Arrays.asList(securityContext.getUserPrincipal().getName())));
        emptyDataset.setVisible(Boolean.TRUE);
        datasetLegacyWrapper.create(emptyDataset);
        //datasetHandler.create(emptyDataset);

        return Response.created(new URI(emptyDataset.getId())).entity(emptyDataset).build();
    }

    @POST
    @TokenSecured({RoleEnum.DEFAULT_USER})
    @Path("/merge")
    @ApiOperation(value = "Merges Datasets",
            notes = "The new intersected Dataset created will be assigned on a random generated Id")
    @ApiResponses(value = {
        @ApiResponse(code = 200, response = Dataset.class, message = "Dataset was created succesfully")
        ,
            @ApiResponse(code = 403, response = ErrorReport.class, message = "Dataset quota has been exceeded")
        ,
            @ApiResponse(code = 401, response = ErrorReport.class, message = "You are not authorized to access this resource")
        ,
            @ApiResponse(code = 403, response = ErrorReport.class, message = "This request is forbidden (e.g., no authentication token is provided)")
        ,
            @ApiResponse(code = 500, response = ErrorReport.class, message = "Internal server error - this request cannot be served.")
    })
    public Response mergeDatasets(
            @FormParam("dataset_uris") String datasetURIs,
            @HeaderParam("Authorization") String api_key) throws URISyntaxException, QuotaExceededException, JaqpotDocumentSizeExceededException {

        String[] apiA = api_key.split("\\s+");
        String apiKey = apiA[1];
        User user = userHandler.find(securityContext.getUserPrincipal().getName());
        long datasetCount = datasetHandler.countAllOfCreator(user.getId());
        int maxAllowedDatasets = new UserFacade(user).getMaxDatasets();

        if (datasetCount > maxAllowedDatasets) {
            LOG.info(String.format("User %s has %d datasets while maximum is %d",
                    user.getId(), datasetCount, maxAllowedDatasets));
            throw new QuotaExceededException("Dear " + user.getId()
                    + ", your quota has been exceeded; you already have " + datasetCount + " datasets. "
                    + "No more than " + maxAllowedDatasets + " are allowed with your subscription.");
        }

        String[] datasets = datasetURIs.split(",");
        Dataset dataset = null;
        for (String datasetURI : datasets) {
            Dataset d = client.target(datasetURI)
                    .request()
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .get(Dataset.class);
            //dataset = DatasetFactory.mergeRows(dataset, d);
        }
        ROG randomStringGenerator = new ROG(true);
        dataset.setId(randomStringGenerator.nextString(14));
        dataset.setFeatured(Boolean.FALSE);
        dataset.setVisible(true);
        if (dataset.getMeta() == null) {
            dataset.setMeta(new MetaInfo());
        }
        dataset.getMeta().setCreators(new HashSet<>(Arrays.asList(securityContext.getUserPrincipal().getName())));

        datasetLegacyWrapper.create(dataset);
        //datasetHandler.create(dataset);

        return Response.created(new URI(dataset.getId())).entity(dataset).build();

    }

    @POST
    @TokenSecured({RoleEnum.DEFAULT_USER})
    @Path("/merge/features")
    @ApiOperation(value = "Merges the features of two or more Datasets",
            notes = "The new intersected Dataset created will be assigned on a random generated Id")
    @ApiResponses(value = {
        @ApiResponse(code = 200, response = Dataset.class, message = "Dataset was created succesfully"),
            @ApiResponse(code = 403, response = ErrorReport.class, message = "Dataset quota has been exceeded"),
            @ApiResponse(code = 401, response = ErrorReport.class, message = "You are not authorized to access this resource"),
            @ApiResponse(code = 500, response = ErrorReport.class, message = "Internal server error - this request cannot be served.")
    })
    public Response mergeFeaturesDatasets(
            @FormParam("dataset_uris") String datasetURIs,
            @HeaderParam("Authorization") String api_key) throws URISyntaxException, QuotaExceededException, JaqpotDocumentSizeExceededException {

        String[] apiA = api_key.split("\\s+");
        String apiKey = apiA[1];
        User user = userHandler.find(securityContext.getUserPrincipal().getName());
        long datasetCount = datasetHandler.countAllOfCreator(user.getId());
        int maxAllowedDatasets = new UserFacade(user).getMaxDatasets();

        if (datasetCount > maxAllowedDatasets) {
            LOG.info(String.format("User %s has %d datasets while maximum is %d",
                    user.getId(), datasetCount, maxAllowedDatasets));
            throw new QuotaExceededException("Dear " + user.getId()
                    + ", your quota has been exceeded; you already have " + datasetCount + " datasets. "
                    + "No more than " + maxAllowedDatasets + " are allowed with your subscription.");
        }

        String[] datasets = datasetURIs.split(",");
        Dataset dataset = null;
        for (String datasetURI : datasets) {
            Dataset d = client.target(datasetURI)
                    .request()
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .get(Dataset.class);
            //dataset = DatasetFactory.mergeColumns(dataset, d);
        }
        ROG randomStringGenerator = new ROG(true);
        dataset.setId(randomStringGenerator.nextString(14));
        dataset.setFeatured(Boolean.FALSE);
        dataset.setVisible(true);
        if (dataset.getMeta() == null) {
            dataset.setMeta(new MetaInfo());
        }
        dataset.getMeta().setCreators(new HashSet<>(Arrays.asList(securityContext.getUserPrincipal().getName())));

        datasetLegacyWrapper.create(dataset);
        //datasetHandler.create(dataset);

        return Response.created(new URI(dataset.getId())).entity(dataset).build();

    }

    @DELETE
    @TokenSecured({RoleEnum.DEFAULT_USER})
    @Path("{id}")
    @ApiOperation("Deletes dataset")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Dataset was succesfully deleted")
        ,
            @ApiResponse(code = 404, response = ErrorReport.class, message = "Dataset was not found in the system")
        ,
            @ApiResponse(code = 403, response = ErrorReport.class, message = "Dataset quota has been exceeded")
        ,
            @ApiResponse(code = 401, response = ErrorReport.class, message = "You are not authorized to access this resource")
        ,
            @ApiResponse(code = 403, response = ErrorReport.class, message = "This request is forbidden (e.g., no authentication token is provided)")
        ,
            @ApiResponse(code = 500, response = ErrorReport.class, message = "Internal server error - this request cannot be served.")
    })
    public Response deleteDataset(
            @ApiParam(value = "Authorization token") @HeaderParam("Authorization") String api_key,
            @PathParam("id") String id) throws JaqpotForbiddenException {
        Dataset ds = datasetHandler.find(id);
        if (ds == null) {
            throw new NotFoundException("Dataset with id:" + id + " was not found on the server.");
        }
        MetaInfo metaInfo = ds.getMeta();
        if (metaInfo.getLocked()) {
            throw new JaqpotForbiddenException("You cannot delete a Dataset that is locked.");
        }

        String userName = securityContext.getUserPrincipal().getName();
        if (!ds.getMeta().getCreators().contains(userName)) {
            return Response.status(Response.Status.FORBIDDEN).entity("You cannot delete a Dataset that was not created by you.").build();
        }
        datasetHandler.remove(ds);
        return Response.ok().build();
    }

    @POST
    @TokenSecured({RoleEnum.DEFAULT_USER})
    @Path("{id}/qprf")
    @ApiOperation("Creates QPRF Report")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Dataset was succesfully deleted"),
            @ApiResponse(code = 400, response = ErrorReport.class, message = "Bad Request. More details can be found in details of ErrorReport"),
            @ApiResponse(code = 404, response = ErrorReport.class, message = "Dataset was not found in the system"),
            @ApiResponse(code = 403, response = ErrorReport.class, message = "Dataset quota has been exceeded"),
            @ApiResponse(code = 401, response = ErrorReport.class, message = "You are not authorized to access this resource"),
            @ApiResponse(code = 403, response = ErrorReport.class, message = "This request is forbidden (e.g., no authentication token is provided)"),
            @ApiResponse(code = 500, response = ErrorReport.class, message = "Internal server error - this request cannot be served.")
    })

    public Response createQPRFReport(
            @ApiParam(value = "Authorization token") @HeaderParam("Authorization") String api_key,
            @PathParam("id") String id,
            @FormParam("substance_uri") String substanceURI,
            @FormParam("title") String title,
            @FormParam("description") String description
    ) throws QuotaExceededException, ExecutionException, InterruptedException, JaqpotDocumentSizeExceededException {

        String[] apiA = api_key.split("\\s+");
        String apiKey = apiA[1];
        User user = userHandler.find(securityContext.getUserPrincipal().getName());
        long reportCount = reportHandler.countAllOfCreator(user.getId());
        int maxAllowedReports = new UserFacade(user).getMaxReports();

        if (reportCount > maxAllowedReports) {
            LOG.info(String.format("User %s has %d algorithms while maximum is %d",
                    user.getId(), reportCount, maxAllowedReports));
            throw new QuotaExceededException("Dear " + user.getId()
                    + ", your quota has been exceeded; you already have " + reportCount + " reports. "
                    + "No more than " + maxAllowedReports + " are allowed with your subscription.");
        }

        Dataset ds = datasetLegacyWrapper.find(id);
        //Dataset ds = datasetHandler.find(id);
        if (ds == null) {
            throw new NotFoundException("Dataset with id:" + id + " was not found on the server.");
        }
        if (ds.getByModel() == null || ds.getByModel().isEmpty()) {
            throw new BadRequestException("Selected dataset was not produced by a valid model.");
        }
        Model model = modelHandler.find(ds.getByModel());
        if (model == null) {
            throw new BadRequestException("Selected dataset was not produced by a valid model.");
        }
        String datasetURI = model.getDatasetUri();
        if (datasetURI == null || datasetURI.isEmpty()) {
            throw new BadRequestException("The model that created this dataset does not point to a valid training dataset.");
        }
        Dataset trainingDS = client.target(datasetURI)
                .queryParam("dataEntries",true)
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .get(Dataset.class);
        if (trainingDS == null) {
            throw new BadRequestException("The model that created this dataset does not point to a valid training dataset.");
        }

        if (model.getTransformationModels() != null) {
            for (String transModelURI : model.getTransformationModels()) {
                Model transModel = modelHandler.find(transModelURI.split("model/")[1]);
                if (transModel == null) {
                    throw new NotFoundException("Transformation model with id:" + transModelURI + " was not found.");
                }
                try {
                    trainingDS = jpdiClient.predict(trainingDS, transModel, trainingDS.getMeta(), UUID.randomUUID().toString()).get();
                } catch (InterruptedException ex) {
                    LOG.log(Level.SEVERE, "JPDI Training procedure interupted", ex);
                    throw new InternalServerErrorException("JPDI Training procedure interupted", ex);
                } catch (ExecutionException ex) {
                    LOG.log(Level.SEVERE, "Training procedure execution error", ex.getCause());
                    throw new InternalServerErrorException("JPDI Training procedure error", ex.getCause());
                } catch (CancellationException ex) {
                    throw new InternalServerErrorException("Procedure was cancelled");
                }
            }
        }

        List<String> retainableFeatures = new ArrayList<>(model.getIndependentFeatures());
        retainableFeatures.addAll(model.getDependentFeatures());

        trainingDS.getDataEntry().parallelStream()
                .forEach(dataEntry -> {
                    dataEntry.getValues().keySet().retainAll(retainableFeatures);
                });

        DataEntry dataEntry = ds.getDataEntry().stream()
                .filter(de -> de.getEntryId().getURI().equals(substanceURI))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(""));

        trainingDS.getDataEntry().add(dataEntry);
        trainingDS.getMeta().setCreators(new HashSet<>(Arrays.asList(user.getId())));

        Map<String, Object> parameters = new HashMap<>();

        UrlValidator urlValidator = new UrlValidator();
        if (urlValidator.isValid(substanceURI)) {

            String substanceId = substanceURI.split("substance/")[1];

            Dataset structures = ambitClient.getDatasetStructures(substanceId, apiKey);

            List<Map<String, String>> structuresList = structures.getDataEntry()
                    .stream()
                    .map(de -> {
                        String compound = de.getEntryId().getURI();
                        String casrn = Optional.ofNullable(de.getValues().get("https://apps.ideaconsult.net/enmtest/feature/http%3A%2F%2Fwww.opentox.org%2Fapi%2F1.1%23CASRNDefault")).orElse("").toString();
                        String einecs = Optional.ofNullable(de.getValues().get("https://apps.ideaconsult.net/enmtest/feature/http%3A%2F%2Fwww.opentox.org%2Fapi%2F1.1%23EINECSDefault")).orElse("").toString();
                        String iuclid5 = Optional.ofNullable(de.getValues().get("https://apps.ideaconsult.net/enmtest/feature/http%3A%2F%2Fwww.opentox.org%2Fapi%2F1.1%23IUCLID5_UUIDDefault")).orElse("").toString();
                        String inchi = Optional.ofNullable(de.getValues().get("https://apps.ideaconsult.net/enmtest/feature/http%3A%2F%2Fwww.opentox.org%2Fapi%2F1.1%23InChI_stdDefault")).orElse("").toString();
                        String reach = Optional.ofNullable(de.getValues().get("https://apps.ideaconsult.net/enmtest/feature/http%3A%2F%2Fwww.opentox.org%2Fapi%2F1.1%23REACHRegistrationDateDefault")).orElse("").toString();
                        String iupac = Optional.ofNullable(de.getValues().get("https://apps.ideaconsult.net/enmtest/feature/http%3A%2F%2Fwww.opentox.org%2Fapi%2F1.1%23IUPACNameDefault")).orElse("").toString();

                        Map<String, String> structuresMap = new HashMap<>();
                        structuresMap.put("Compound", compound);
                        structuresMap.put("CasRN", casrn);
                        structuresMap.put("EC number", einecs);
                        structuresMap.put("REACH registration date", reach);
                        structuresMap.put("IUCLID 5 Reference substance UUID", iuclid5);
                        structuresMap.put("Std. InChI", inchi);
                        structuresMap.put("IUPAC name", iupac);

                        return structuresMap;
                    })
                    .collect(Collectors.toList());
            if (structuresList.isEmpty()) {
                Map<String, String> structuresMap = new HashMap<>();
                structuresMap.put("Compound", "");
                structuresMap.put("CasRN", "");
                structuresMap.put("EC number", "");
                structuresMap.put("REACH registration date", "");
                structuresMap.put("IUCLID 5 Reference substance UUID", "");
                structuresMap.put("Std. InChI", "");
                structuresMap.put("IUPAC name", "");
                structuresList.add(structuresMap);
                parameters.put("structures", structuresList);
            }
            parameters.put("structures", structuresList);
        } else {
            List<Map<String, String>> structuresList = new ArrayList<>();
            Map<String, String> structuresMap = new HashMap<>();
            structuresMap.put("Compound", "");
            structuresMap.put("CasRN", "");
            structuresMap.put("EC number", "");
            structuresMap.put("REACH registration date", "");
            structuresMap.put("IUCLID 5 Reference substance UUID", "");
            structuresMap.put("Std. InChI", "");
            structuresMap.put("IUPAC name", "");
            structuresList.add(structuresMap);
            parameters.put("structures", structuresList);
        }

        parameters.put("predictedFeature",
                model
                        .getPredictedFeatures()
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new BadRequestException("Model does not have a valid predicted feature")));

        parameters.put("algorithm", algorithmHandler.find(model.getAlgorithm().getId()));
        parameters.put("substanceURI", substanceURI);
        if (model.getLinkedModels() != null && !model.getLinkedModels().isEmpty()) {
            Model doa = modelHandler.find(model.getLinkedModels().get(0).split("model/")[1]);
            if (doa != null) {
                parameters.put("doaURI", doa.getPredictedFeatures().get(0));
                parameters.put("doaMethod", doa.getAlgorithm().getId());
            }
        }
        TrainingRequest request = new TrainingRequest();

        request.setDataset(trainingDS);
        request.setParameters(parameters);
        request.setPredictionFeature(model.getDependentFeatures()
                .stream()
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Model does not have a valid prediction feature")));
        String qprfHost = properyManager.getPropertyOrDefault(PropertyManager.PropertyType.JAQPOT_QPRF);
        LOG.log(Level.INFO, qprfHost);
        Report report = client.target(qprfHost)
                .request()
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .post(Entity.json(request), Report.class);

        report.setMeta(MetaInfoBuilder.builder()
                .addTitles(title)
                .addDescriptions(description)
                .addCreators(securityContext.getUserPrincipal().getName())
                .build()
        );
        report.setId(new ROG(true).nextString(15));
        report.setVisible(Boolean.TRUE);
        reportHandler.create(report);

        return Response.ok(report).build();
    }

    @POST
    @TokenSecured({RoleEnum.DEFAULT_USER})
    @Path("{id}/qprf-dummy")
    @ApiOperation("Creates QPRF Dummy Report")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Dataset was succesfully deleted")
        ,
            @ApiResponse(code = 400, response = ErrorReport.class, message = "Bad Request. More details can be found in details of ErrorReport")
        ,
            @ApiResponse(code = 404, response = ErrorReport.class, message = "Dataset was not found in the system")
        ,
            @ApiResponse(code = 403, response = ErrorReport.class, message = "Dataset quota has been exceeded")
        ,
            @ApiResponse(code = 401, response = ErrorReport.class, message = "You are not authorized to access this resource")
        ,
            @ApiResponse(code = 403, response = ErrorReport.class, message = "This request is forbidden (e.g., no authentication token is provided)")
        ,
            @ApiResponse(code = 500, response = ErrorReport.class, message = "Internal server error - this request cannot be served.")
    })
    public Response createQPRFReportDummy(
            @ApiParam(value = "Authorization token") @HeaderParam("Authorization") String api_key,
            @PathParam("id") String id,
            @FormParam("substance_uri") String substanceURI,
            @FormParam("title") String title,
            @FormParam("description") String description
    ) {

        String[] apiA = api_key.split("\\s+");
        String apiKey = apiA[1];
        Dataset ds = datasetLegacyWrapper.find(id);
        //Dataset ds = datasetHandler.find(id);
        if (ds == null) {
            throw new NotFoundException("Dataset with id:" + id + " was not found on the server.");
        }
        if (ds.getByModel() == null || ds.getByModel().isEmpty()) {
            throw new BadRequestException("Selected dataset was not produced by a valid model.");
        }
        Model model = modelHandler.find(ds.getByModel());
        if (model == null) {
            throw new BadRequestException("Selected dataset was not produced by a valid model.");
        }
        String datasetURI = model.getDatasetUri();
        if (datasetURI == null || datasetURI.isEmpty()) {
            throw new BadRequestException("The model that created this dataset does not point to a valid training dataset.");
        }
        Dataset trainingDS = client.target(datasetURI)
                .queryParam("dataEntries", true)
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .get(Dataset.class);
        if (trainingDS == null) {
            throw new BadRequestException("The model that created this dataset does not point to a valid training dataset.");
        }

        if (model.getTransformationModels() != null) {
            for (String transModelURI : model.getTransformationModels()) {
                Model transModel = modelHandler.find(transModelURI.split("model/")[1]);
                if (transModel == null) {
                    throw new NotFoundException("Transformation model with id:" + transModelURI + " was not found.");
                }
                try {
                    trainingDS = jpdiClient.predict(trainingDS, transModel, trainingDS.getMeta(), UUID.randomUUID().toString()).get();
                } catch (InterruptedException ex) {
                    LOG.log(Level.SEVERE, "JPDI Training procedure interupted", ex);
                    throw new InternalServerErrorException("JPDI Training procedure interupted", ex);
                } catch (ExecutionException ex) {
                    LOG.log(Level.SEVERE, "Training procedure execution error", ex.getCause());
                    throw new InternalServerErrorException("JPDI Training procedure error", ex.getCause());
                } catch (CancellationException ex) {
                    throw new InternalServerErrorException("Procedure was cancelled");
                }
            }
        }

        List<String> retainableFeatures = new ArrayList<>(model.getIndependentFeatures());
        retainableFeatures.addAll(model.getDependentFeatures());

        trainingDS.getDataEntry().parallelStream()
                .forEach(dataEntry -> {
                    dataEntry.getValues().keySet().retainAll(retainableFeatures);
                });

        DataEntry dataEntry = ds.getDataEntry().stream()
                .filter(de -> de.getEntryId().getURI().equals(substanceURI))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(""));

        trainingDS.getDataEntry().add(dataEntry);

        Map<String, Object> parameters = new HashMap<>();

        UrlValidator urlValidator = new UrlValidator();
        if (urlValidator.isValid(substanceURI)) {
            Dataset structures = client.target(substanceURI + "/structures")
                    .request()
                    .accept(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .get(Dataset.class);
            List<Map<String, String>> structuresList = structures.getDataEntry()
                    .stream()
                    .map(de -> {
                        String compound = de.getEntryId().getURI();
                        String casrn = Optional.ofNullable(de.getValues().get("https://apps.ideaconsult.net/enmtest/feature/http%3A%2F%2Fwww.opentox.org%2Fapi%2F1.1%23CASRNDefault")).orElse("").toString();
                        String einecs = Optional.ofNullable(de.getValues().get("https://apps.ideaconsult.net/enmtest/feature/http%3A%2F%2Fwww.opentox.org%2Fapi%2F1.1%23EINECSDefault")).orElse("").toString();
                        String iuclid5 = Optional.ofNullable(de.getValues().get("https://apps.ideaconsult.net/enmtest/feature/http%3A%2F%2Fwww.opentox.org%2Fapi%2F1.1%23IUCLID5_UUIDDefault")).orElse("").toString();
                        String inchi = Optional.ofNullable(de.getValues().get("https://apps.ideaconsult.net/enmtest/feature/http%3A%2F%2Fwww.opentox.org%2Fapi%2F1.1%23InChI_stdDefault")).orElse("").toString();
                        String reach = Optional.ofNullable(de.getValues().get("https://apps.ideaconsult.net/enmtest/feature/http%3A%2F%2Fwww.opentox.org%2Fapi%2F1.1%23REACHRegistrationDateDefault")).orElse("").toString();
                        String iupac = Optional.ofNullable(de.getValues().get("https://apps.ideaconsult.net/enmtest/feature/http%3A%2F%2Fwww.opentox.org%2Fapi%2F1.1%23IUPACNameDefault")).orElse("").toString();

                        Map<String, String> structuresMap = new HashMap<>();
                        structuresMap.put("Compound", compound);
                        structuresMap.put("CasRN", casrn);
                        structuresMap.put("EC number", einecs);
                        structuresMap.put("REACH registration date", reach);
                        structuresMap.put("IUCLID 5 Reference substance UUID", iuclid5);
                        structuresMap.put("Std. InChI", inchi);
                        structuresMap.put("IUPAC name", iupac);

                        return structuresMap;
                    })
                    .collect(Collectors.toList());
            parameters.put("structures", structuresList);
        } else {
            List<Map<String, String>> structuresList = new ArrayList<>();
            Map<String, String> structuresMap = new HashMap<>();
            structuresMap.put("Compound", "");
            structuresMap.put("CasRN", "");
            structuresMap.put("EC number", "");
            structuresMap.put("REACH registration date", "");
            structuresMap.put("IUCLID 5 Reference substance UUID", "");
            structuresMap.put("Std. InChI", "");
            structuresMap.put("IUPAC name", "");
            structuresList.add(structuresMap);
            parameters.put("structures", structuresList);
        }

        parameters.put("predictedFeature",
                model
                        .getPredictedFeatures()
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new BadRequestException("Model does not have a valid predicted feature")));

        parameters.put("algorithm", algorithmHandler.find(model.getAlgorithm().getId()));
        parameters.put("substanceURI", substanceURI);
        if (model.getLinkedModels() != null && !model.getLinkedModels().isEmpty()) {
            Model doa = modelHandler.find(model.getLinkedModels().get(0).split("model/")[1]);
            if (doa != null) {
                parameters.put("doaURI", doa.getPredictedFeatures().get(0));
                parameters.put("doaMethod", doa.getAlgorithm().getId());
            }
        }
        TrainingRequest request = new TrainingRequest();

        request.setDataset(trainingDS);
        request.setParameters(parameters);
        request.setPredictionFeature(model.getDependentFeatures()
                .stream()
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Model does not have a valid prediction feature")));

        return Response.ok(request).build();
//        Report report = client.target("http://147.102.82.32:8094/pws/qprf")
//                .request()
//                .header("Content-Type", MediaType.APPLICATION_JSON)
//                .accept(MediaType.APPLICATION_JSON)
//                .post(Entity.json(request), Report.class);
//
//        report.setMeta(MetaInfoBuilder.builder()
//                .addTitles(title)
//                .addDescriptions(description)
//                .addCreators(securityContext.getUserPrincipal().getName())
//                .build()
//        );
//        report.setId(new ROG(true).nextString(15));
//        report.setVisible(Boolean.TRUE);
//        reportHandler.create(report);
//
//        return Response.ok(report).build();
    }
    
    @GET
    @TokenSecured({RoleEnum.DEFAULT_USER})
    @Produces({"application/json", MediaType.APPLICATION_JSON})

    @Path("{did}/dataentry/{id}")
    @ApiOperation(value = "Finds Data Entry by Id",
            notes = "Finds specified Data Entry"
    )
    @ApiResponses(value = {
            @ApiResponse(code = 200, response = DataEntry.class, message = "Dataset Entry was found"),
            @ApiResponse(code = 404, response = ErrorReport.class, message = "Dataset Entry was not found in the system"),
            @ApiResponse(code = 401, response = ErrorReport.class, message = "You are not authorized to access this resource"),
            @ApiResponse(code = 403, response = ErrorReport.class, message = "This request is forbidden (e.g., no authentication token is provided)"),
            @ApiResponse(code = 500, response = ErrorReport.class, message = "Internal server error - this request cannot be served.")
    })
    public Response getDataEntry(
            @ApiParam(value = "Authorization token") @HeaderParam("Authorization") String api_key,
            @PathParam("did") String datasetId,
            @PathParam("id") String id)
            throws NotFoundException,IllegalArgumentException
    {
        Dataset dataset = datasetHandler.find(datasetId);
        if (dataset == null)
            throw new NotFoundException("Could not find Dataset with id:" + datasetId);

        DataEntry dataEntry = dataEntryHandler.find(id);
        if (dataEntry == null)
            throw new NotFoundException("Could not find DataEntry with id:" + id);
        if (!dataEntry.getDatasetId().equals(dataset.getId()))
            throw new IllegalArgumentException("Data Entry "+id+" is not part of Dataset with id :"+datasetId);
        return Response.ok(dataEntry).build();
    }

    @GET
    @TokenSecured({RoleEnum.DEFAULT_USER})
    @Produces({"application/json", MediaType.APPLICATION_JSON})

    @Path("{id}/dataentry")
    @ApiOperation(value = "Finds Data Entries of Dataset with given id",
            notes = "Finds Data entries of specified Dataset"
    )
    @ApiResponses(value = {
            @ApiResponse(code = 200, response = DataEntry.class,responseContainer = "List",  message = "Dataset Entry was found"),
            @ApiResponse(code = 404, response = ErrorReport.class, message = "Dataset Entry was not found in the system"),
            @ApiResponse(code = 401, response = ErrorReport.class, message = "You are not authorized to access this resource"),
            @ApiResponse(code = 403, response = ErrorReport.class, message = "This request is forbidden (e.g., no authentication token is provided)"),
            @ApiResponse(code = 500, response = ErrorReport.class, message = "Internal server error - this request cannot be served.")
    })
    public Response getDataEntries(
            @ApiParam(value = "Authorization token") @HeaderParam("Authorization") String api_key,
            @PathParam("id") String id,
            @ApiParam(value = "rowStart", defaultValue = "0")@QueryParam("rowStart") Integer rowStart,
            @ApiParam(value = "rowMax") @QueryParam("rowMax") Integer rowMax,
            @QueryParam("colStart") Integer colStart,
            @QueryParam("colMax") Integer colMax,
            @QueryParam("stratify") String stratify,
            @QueryParam("seed") Long seed,
            @QueryParam("folds") Integer folds,
            @QueryParam("target_feature") String targetFeature
    ) throws NotFoundException {
        Dataset dataset = datasetHandler.find(id);
        if (dataset == null) {
            throw new NotFoundException("Could not find Dataset with id:" + id);
        }
        List<DataEntry> dataEntries = dataEntryHandler.findDataEntriesByDatasetId(id, rowStart, rowMax, colStart, colMax);
        if (dataEntries == null || dataEntries.isEmpty()){
            throw new NotFoundException("Could not find DataEntries associated with dataset with id:" + id);
        }
        return Response.ok(dataEntries).build();
    }

    @POST
    @TokenSecured({RoleEnum.DEFAULT_USER})
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({"application/json", MediaType.APPLICATION_JSON})
    @Path("{id}/dataentry")
    @ApiOperation(value = "Creates DataEntry",
            notes = "The new Data Entry created will be assigned on a random generated Id")
    @ApiResponses(value = {
            @ApiResponse(code = 200, response = DataEntry.class, message = "DataEntry was created succesfully"),
            @ApiResponse(code = 401, response = ErrorReport.class, message = "You are not authorized to access this resource"),
            @ApiResponse(code = 403, response = ErrorReport.class, message = "This request is forbidden (e.g., no authentication token is provided)"),
            @ApiResponse(code = 500, response = ErrorReport.class, message = "Internal server error - this request cannot be served.")
    })
    public Response createDataEntry(
            @ApiParam(value = "Authorization token") @HeaderParam("Authorization") String api_key,
            @PathParam("id")String id,
            DataEntry dataentry) throws URISyntaxException, JaqpotDocumentSizeExceededException {
        Dataset dataset = datasetHandler.find(id);
        if (dataset == null)
            throw new NotFoundException("Could not find Dataset with id:" + id);
        dataentry.setDatasetId(id);
        ROG randomStringGenerator = new ROG(true);
        dataentry.setId(randomStringGenerator.nextString(14));
        dataEntryHandler.create(dataentry);
        datasetHandler.updateField(id,"totalRows",dataset.getTotalRows()+1);
        return Response.created(new URI(dataentry.getId())).entity(dataentry).build();

    }

    @PUT
    @TokenSecured({RoleEnum.DEFAULT_USER})
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({"application/json", MediaType.APPLICATION_JSON})
    @Path("{id}/meta")
    @ApiOperation(value = "Updates meta info of a dataset",
            notes = "TUpdates meta info of a dataset")
    @ApiResponses(value = {
            @ApiResponse(code = 200, response = DataEntry.class, message = "Meta was updated succesfully"),
            @ApiResponse(code = 401, response = ErrorReport.class, message = "You are not authorized to access this resource"),
            @ApiResponse(code = 403, response = ErrorReport.class, message = "This request is forbidden (e.g., no authentication token is provided)"),
            @ApiResponse(code = 500, response = ErrorReport.class, message = "Internal server error - this request cannot be served.")
    })
    public Response updateMeta(
            @ApiParam(value = "Authorization token") @HeaderParam("Authorization") String api_key,
            @PathParam("id")String id,
            Dataset datasetForUpdate) throws URISyntaxException, JaqpotDocumentSizeExceededException {
        Dataset dataset = datasetHandler.find(id);
        if (dataset == null)
            throw new NotFoundException("Could not find Dataset with id:" + id);
        datasetHandler.updateMeta(id, datasetForUpdate.getMeta());
        return Response.accepted().entity(datasetForUpdate.getMeta()).build();
        
    }
        
}
