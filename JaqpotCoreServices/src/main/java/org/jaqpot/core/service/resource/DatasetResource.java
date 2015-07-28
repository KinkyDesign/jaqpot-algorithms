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

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import javax.ejb.EJB;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.jaqpot.core.data.DatasetHandler;
import org.jaqpot.core.model.dto.dataset.Dataset;

/**
 *
 * @author hampos
 */
@Path("dataset")
@Api(value = "/dataset", description = "Dataset API")
@Produces({"application/json", "text/uri-list"})
public class DatasetResource {

    @EJB
    DatasetHandler datasetHandler;

    @GET
    @Produces({MediaType.APPLICATION_JSON, "text/uri-list"})
    @ApiOperation(value = "Finds all Datasets",
            notes = "Finds all Datasets in the DB of Jaqpot and returns them in a list. Results can be obtained "
            + "either in the form of a URI list or as a JSON list as specified by the Accept HTTP header. "
            + "In the latter case, a list will be returned containing only the IDs of the datasets, their metadata "
            + "and their ontological classes. The parameter max, which specifies the maximum number of IDs to be "
            + "listed is limited to 500; if the client specifies a larger value, an HTTP Warning Header will be "
            + "returned (RFC 2616) with code P670.",
            response = Dataset.class,
            responseContainer = "List",
            position = 1)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Datasets found and are listed in the response body"),
        @ApiResponse(code = 401, message = "You are not authorized to access this resource"),
        @ApiResponse(code = 403, message = "This request is forbidden (e.g., no authentication token is provided)"),
        @ApiResponse(code = 500, message = "Internal server error - this request cannot be served.")
    })
    public Response listDatasets(
            @ApiParam(value = "start", defaultValue = "0") @QueryParam("start") Integer start,
            @ApiParam(value = "max - the server imposes an upper limit of 500 on this "
                    + "parameter.", defaultValue = "10") @QueryParam("max") Integer max,
            @ApiParam(value = "creator") @QueryParam("creator") String creator
    ) {
        start = start != null ? start : 0;
        boolean doWarnMax = false;
        if (max == null || max > 500) {
            max = 500;
            doWarnMax = true;
        }
        Response.ResponseBuilder responseBuilder = Response
                .ok(datasetHandler.listOnlyIDsOfCreator(creator, start, max))
                .status(Response.Status.OK);
        if (doWarnMax) {
            responseBuilder.header("Warning", "P670 Parameter max has been limited to 500");
        }
        return responseBuilder.build();
    }

    @GET
    @Path("/count")
    @Produces(MediaType.TEXT_PLAIN)
    @ApiOperation("Counts all datasets")
    public Response countDatasets(@QueryParam("createdBy") String createdBy) {
        return Response.ok(datasetHandler.countAllOfCreator(createdBy)).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}/partial")
    @ApiOperation(value = "Finds Dataset by Id",
            notes = "Finds specified Dataset",
            response = Dataset.class)
    public Response getPartialDataset(@PathParam("id") String id,
            @QueryParam("rowStart") Integer rowStart,
            @QueryParam("rowMax") Integer rowMax,
            @QueryParam("colStart") Integer colStart,
            @QueryParam("colMax") Integer colMax) {
        Dataset dataset = datasetHandler.find(id, rowStart, rowMax, colStart, colMax);
        if (dataset == null) {
            throw new NotFoundException("Could not find Dataset with id:" + id);
        }
        return Response.ok(dataset).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    @ApiOperation(value = "Finds Dataset by Id",
            notes = "Finds specified Dataset",
            response = Dataset.class)
    public Response getDataset(@PathParam("id") String id) {
        Dataset dataset = datasetHandler.find(id);
        if (dataset == null) {
            throw new NotFoundException("Could not find Dataset with id:" + id);
        }
        return Response.ok(dataset).build();
    }

}
