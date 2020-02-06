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


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import org.jaqpot.core.data.TaskHandler;
import org.jaqpot.core.model.MetaInfo;
import org.jaqpot.core.model.Task;
import org.jaqpot.core.service.annotations.TokenSecured;
import org.jaqpot.core.service.authentication.RoleEnum;
import org.jaqpot.core.service.client.jpdi.JPDIClient;
import org.jaqpot.core.service.exceptions.JaqpotForbiddenException;

/**
 *
 * @author Pantelis Sopasakis
 * @author Charalampos Chomenidis
 *
 */
@Path("/task")
//@Api(value = "/task", description = "Tasks API")
@Tag(name = "task")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
public class TaskResource {

    @Context
    UriInfo uriInfo;

    @EJB
    TaskHandler taskHandler;

    @Inject
    JPDIClient jpdiClient;

    @Context
    SecurityContext securityContext;

    @Resource
    private ManagedExecutorService executor;

    @GET
    @Produces({MediaType.APPLICATION_JSON, "text/uri-list"})
    @TokenSecured({RoleEnum.DEFAULT_USER})

    @Operation(summary = "Finds all Tasks",
            description = "Finds all Tasks from Jaqpot Dataset. One may specify various ",
            responses = {
                @ApiResponse(responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Task.class))), description = "Success; the list of tasks is found in the response"),
                @ApiResponse(responseCode = "500", description = "Internal server error - this request cannot be served.")
            })
    public Response listTasks(
            @Parameter(name = "Authorization", description = "Authorization token", schema = @Schema(implementation = String.class), in = ParameterIn.HEADER) @HeaderParam("Authorization") String api_key,
            @Parameter(name = "status", description = "Status of the task", schema = @Schema(implementation = String.class, allowableValues = {"RUNNING", "QUEUED", "COMPLETED", "ERROR", "CANCELLED", "REJECTED"})) @QueryParam("status") String status,
            @Parameter(name = "start", description = "start", schema = @Schema(implementation = Integer.class, defaultValue = "0")) @QueryParam("start") Integer start,
            @Parameter(name = "max", description = "max - the server imposes an upper limit of 500 on this "
                      + "parameter.", schema = @Schema(implementation = Integer.class, defaultValue = "10")) @QueryParam("max") Integer max
    ) {
        start = start != null ? start : 0;
        if (max == null || max > 500) {
            max = 500;
        }
        List<Task> foundTasks;
        Long totalTasks;
        String creator = securityContext.getUserPrincipal().getName();
        if (status == null) {
            foundTasks = taskHandler.findByUser(creator, start, max);
            totalTasks = taskHandler.countAllOfCreator(creator);
        } else {
            foundTasks = taskHandler.findByUserAndStatus(creator, Task.Status.valueOf(status), start, max);
            totalTasks = taskHandler.countByUserAndStatus(creator, Task.Status.valueOf(status));
        }
        foundTasks.stream().forEach(task -> {
            if (task.getResult() != null) {
                task.setResultUri(uriInfo.getBaseUri() + task.getResult().toString());
            }
        });
        return Response.ok(foundTasks)
                .header("total", totalTasks)
                .build();
    }

    @GET
    @TokenSecured({RoleEnum.DEFAULT_USER})
   // @Produces({MediaType.APPLICATION_JSON, "text/uri-list"})
    @Produces({MediaType.APPLICATION_JSON})
    @Path("/{id}")
   
    @Operation(summary = "Finds Task by Id",
            description = "Finds specified Task",
            responses = {
                @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Task.class)), description = "Task is found"),
                @ApiResponse(responseCode = "201", description = "Task is created (see content - redirects to other task)"),
                @ApiResponse(responseCode = "202", description = "Task is accepted (still running)"),
                @ApiResponse(responseCode = "404", description = "This task was not found."),
                @ApiResponse(responseCode = "500", description = "Internal server error - this request cannot be served.")
            })
    public Response getTask(  
            @Parameter(name = "subjectid", schema = @Schema(implementation = String.class)) @HeaderParam("subjectid") String subjectId,
            @Parameter(name = "id", description = "ID of the task to be retrieved", schema = @Schema(implementation = String.class)) @PathParam("id") String id) {
        Task task = taskHandler.find(id);
        if (task == null) {
            throw new NotFoundException("Task " + uriInfo.getPath() + "not found");
        }
        if (task.getResult() != null) {
            task.setResultUri(uriInfo.getBaseUri() + task.getResult().toString());
        }

        Response.ResponseBuilder builder = Response
                .ok(task)
                .status(task.getHttpStatus());
        if (Task.Status.COMPLETED == task.getStatus()) {
            builder.header("Location", task.getResultUri());
        }
        return builder.build();
    }

    @DELETE
    @TokenSecured({RoleEnum.DEFAULT_USER})
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}")
   
    @Operation(summary = "Deletes a Task of given ID",
            description = "Deletes a Task given its ID in the URI. When the DELETE method is applied, the task "
            + "is interrupted and tagged as CANCELLED. Note that this method does not return a response "
            + "on success. If the task does not exist, an error report will be returned to the client "
            + "accompanied by an HTTP status code 404. Note also that authentication and authorization "
            + "restrictions apply, so clients need to be authenticated with a valid token and have "
            + "appropriate rights to be able to successfully apply this method.",
            responses = {
                @ApiResponse(responseCode = "200", description = "Task deleted successfully"),
                @ApiResponse(responseCode = "401", description = "Wrong, missing or insufficient credentials. Error report is produced."),
                @ApiResponse(responseCode = "403", description = "This is a forbidden operation (do not attempt to repeat it)."),
                @ApiResponse(responseCode = "500", description = "Internal server error - this request cannot be served.")
            })
    public Response deleteTask(
            @Parameter(name = "id", description = "ID of the task which is to be cancelled.", required = true, schema = @Schema(implementation = String.class)) @PathParam("id") String id,
            @Parameter(name = "subjectid", schema = @Schema(implementation = String.class)) @HeaderParam("subjectid") String subjectId) throws JaqpotForbiddenException {

        Task task = taskHandler.find(id);
        if (task == null) {
            throw new NotFoundException("Task with ID:" + id + " was not found on the server.");
        }

        MetaInfo metaInfo = task.getMeta();
        if (metaInfo.getLocked()) {
            throw new JaqpotForbiddenException("You cannot delete a Task that is locked.");
        }

        String userName = securityContext.getUserPrincipal().getName();
        if (!task.getMeta().getCreators().contains(userName)) {
            throw new ForbiddenException("You cannot cancel a Task not created by you.");
        }

        if (task.getStatus().equals(Task.Status.QUEUED)) {
            task.setStatus(Task.Status.CANCELLED);
            task.getMeta().getComments().add("Task was cancelled by the user.");
            taskHandler.edit(task);
        }

        if (task.getStatus().equals(Task.Status.RUNNING)) {
            boolean cancelled = jpdiClient.cancel(id);
            if (!cancelled) {
                task.setStatus(Task.Status.CANCELLED);
                task.getMeta().getComments().add("Task was cancelled by the user.");
                taskHandler.edit(task);
            }
        }

        return Response.ok().build();
    }

    @GET
    @Path("/{id}/poll")
    @TokenSecured({RoleEnum.DEFAULT_USER})
    @Produces({MediaType.APPLICATION_JSON})
    @Operation(summary = "Poll Task by Id",
            description = "Implements long polling",
            responses = {
                @ApiResponse(content = @Content(schema = @Schema(implementation = Task.class))),})
    public void poll(
            @HeaderParam("Authorization") String api_key,
            @Suspended final AsyncResponse asyncResponse,
            @PathParam("id") String id) {

        executor.submit(() -> {
            asyncResponse.setTimeout(3, TimeUnit.MINUTES);
            try {
                Task task = taskHandler.longPoll(id);
                asyncResponse.resume(task);
            } catch (InterruptedException ex) {
                asyncResponse.resume(ex);
            }
        });
    }
}
