package org.jaqpot.core.service.resource;

//import io.swagger.annotations.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import javassist.runtime.Desc;
import org.apache.commons.validator.routines.UrlValidator;
import org.jaqpot.core.annotations.Jackson;
import org.jaqpot.core.data.DatasetHandler;
import org.jaqpot.core.data.DescriptorHandler;
import org.jaqpot.core.data.UserHandler;
import org.jaqpot.core.data.serialize.JSONSerializer;
import org.jaqpot.core.model.*;
import org.jaqpot.core.model.builder.DescriptorBuilder;
import org.jaqpot.core.model.dto.dataset.Dataset;
import org.jaqpot.core.model.dto.descriptor.DescriptorReqDTO;
import org.jaqpot.core.model.facades.UserFacade;
import org.jaqpot.core.model.util.ROG;
import org.jaqpot.core.service.annotations.TokenSecured;
import org.jaqpot.core.service.authentication.RoleEnum;
import org.jaqpot.core.service.data.DescriptorService;
import org.jaqpot.core.service.exceptions.JaqpotDocumentSizeExceededException;
import org.jaqpot.core.service.exceptions.JaqpotForbiddenException;
import org.jaqpot.core.service.exceptions.QuotaExceededException;
import org.jaqpot.core.service.exceptions.parameter.*;
import org.jaqpot.core.service.validator.ParameterValidator;

import javax.ejb.EJB;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.util.*;
import java.util.logging.Logger;

@Path("/descriptor")
//@Api(value = "/descriptor", description = "Descriptors API")
@Produces({"application/json", "text/uri-list"})
@Tag(name = "descriptor")
@SecurityScheme(name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        in = SecuritySchemeIn.HEADER,
        scheme = "bearer",
        description = "add the token retreived from oidc. Example:  Bearer <API_KEY>"
        )
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
public class DescriptorResource {

    @EJB
    DescriptorService descriptorService;

    @EJB
    DescriptorHandler descriptorHandler;

    @Context
    SecurityContext securityContext;

    @EJB
    UserHandler userHandler;

    @EJB
    DatasetHandler datasetHandler;

    @Inject
    @Jackson
    JSONSerializer serializer;

    @Inject
    ParameterValidator parameterValidator;

    @Context
    UriInfo uriInfo;

    private static final Logger LOG = Logger.getLogger(DescriptorResource.class.getName());

    private static final String DEFAULT_DESCRIPTOR = "{\n"
            + "  \"descriptorService\":\"http://z.ch/t/a\",\n"
            + "  \"parameters\": [\n"
            + "    {\n"
            + "       \"name\":\"alpha\",\n"
            + "       \"scope\":\"OPTIONAL\",\n"
            + "       \"value\":101.635\n"
            + "    }\n"
            + "  ]\n"
            + "}";

    @GET
    @TokenSecured({RoleEnum.DEFAULT_USER})
    @Produces({MediaType.APPLICATION_JSON, "text/uri-list"})
    @Operation(
            summary = "Finds all Descriptors",
            description = "Finds all Descriptors JaqpotQuattro supports",
            extensions = {
                @Extension(properties = {
                    @ExtensionProperty(name = "orn-@type", value = "x-orn:Descriptor"),}
                ),
                @Extension(name = "orn:returns", properties = {
                    @ExtensionProperty(name = "x-orn-@id", value = "x-orn:DescriptorList")
                })
            },
            responses = {
                @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorReport.class)), description = "Wrong, missing or insufficient credentials. Error report is produced."),
                @ApiResponse(responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Descriptor.class))), description = "A list of descriptors in the Jaqpot framework"),
                @ApiResponse(responseCode = "500", content = @Content(schema = @Schema(implementation = ErrorReport.class)), description = "Internal server error - this request cannot be served.")

            })
    public Response getDescriptors(
            @Parameter(name = "Authorization", description = "Authorization token", schema = @Schema(implementation = String.class)) @HeaderParam("Authorization") String apiKey,
            @Parameter(name = "start", description = "start", schema = @Schema(implementation = Integer.class, defaultValue = "0")) @QueryParam("start") Integer start,
            @Parameter(name = "max", description = "max", schema = @Schema(implementation = Integer.class, defaultValue = "10")) @QueryParam("max") Integer max) {
        return Response
                .ok(descriptorHandler.findAll(start != null ? start : 0, max != null ? max : Integer.MAX_VALUE))
                .header("total", descriptorHandler.countAll())
                .build();
    }

    @POST
    @TokenSecured({RoleEnum.DEFAULT_USER})
    @Produces({MediaType.APPLICATION_JSON, "text/uri-list"})
    @Consumes({MediaType.APPLICATION_JSON})
    @Operation(
            summary = "Creates Desciptor",
            description = "Registers a new JPDI-compliant descriptor service.",
            extensions = {
                @Extension(properties = {
                    @ExtensionProperty(name = "orn-@type", value = "x-orn:Descriptor"),}),
                @Extension(name = "orn:expects", properties = {
                    @ExtensionProperty(name = "x-orn-@id", value = "x-orn:Descriptor")
                }),
                @Extension(name = "orn:returns", properties = {
                    @ExtensionProperty(name = "x-orn-@id", value = "x-orn:Status")
                })
            },
            responses = {
                @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorReport.class)), description = "Descriptor quota has been exceeded"),
                @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorReport.class)), description = "Wrong, missing or insufficient credentials. Error report is produced."),
                @ApiResponse(responseCode = "200", content = @Content(array = @ArraySchema(schema = @Schema(implementation = Descriptor.class))), description = "Descriptor successfully registered in the system"),
                @ApiResponse(responseCode = "500", content = @Content(schema = @Schema(implementation = ErrorReport.class)), description = "Internal server error - this request cannot be served.")
            })
    public Response createDescriptor(
            Descriptor descriptor,
            @Parameter(name = "Authorization", description = "Authorization token", schema = @Schema(implementation = String.class)) @HeaderParam("Authorization") String api_key,
            @Parameter(name = "title", description = "Title of your descriptor", schema = @Schema(implementation = String.class)) @HeaderParam("title") String title,
            @Parameter(name = "description", description = "Short description of your descriptor", schema = @Schema(implementation = String.class)) @HeaderParam("description") String description,
            @Parameter(name = "tags", description = "Tags for your descriptor (in a comma separated list) to facilitate look-up", schema = @Schema(implementation = String.class)) @HeaderParam("tags") String tags
    ) throws JaqpotDocumentSizeExceededException {
        if (descriptor.getId() == null) {
            ROG rog = new ROG(true);
            descriptor.setId(rog.nextString(14));
        }

        DescriptorBuilder descriptorBuilder = DescriptorBuilder.builder(descriptor);
        if (title != null) {
            descriptorBuilder.addTitles(title);
        }
        if (description != null) {
            descriptorBuilder.addDescriptions(description);
        }
        if (tags != null) {
            descriptorBuilder.addTagsCSV(tags);
        }

        descriptor = descriptorBuilder.build();
        if (descriptor.getMeta() == null) {
            descriptor.setMeta(new MetaInfo());
        }

        descriptor.getMeta().setCreators(new HashSet<>(Arrays.asList(securityContext.getUserPrincipal().getName())));
        descriptorHandler.create(descriptor);
        return Response
                .status(Response.Status.OK)
                .header("Location", uriInfo.getBaseUri().toString() + "descriptor/" + descriptor.getId())
                .entity(descriptor).build();
    }

    @GET
    @Path("/{id}")
    @TokenSecured({RoleEnum.DEFAULT_USER})
    @Produces({MediaType.APPLICATION_JSON, "text/uri-list", "application/ld+json"})
   
    @Operation(
            summary = "Finds Descriptor",
            description = "Finds Descriptor with provided id",
            extensions = {
                @Extension(properties = {
                    @ExtensionProperty(name = "orn-@type", value = "x-orn:Descriptor"),}),
                @Extension(name = "orn:expects", properties = {
                    @ExtensionProperty(name = "x-orn-@id", value = "x-orn:DescriptorId")
                }),
                @Extension(name = "orn:returns", properties = {
                    @ExtensionProperty(name = "x-orn-@id", value = "x-orn:Descriptor")
                })
            },
            responses = {
                @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorReport.class)), description = "Wrong, missing or insufficient credentials. Error report is produced."),
                @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorReport.class)), description = "Descriptor was not found"),
                @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Descriptor.class)), description = "Descriptor was found in the system"),
                @ApiResponse(responseCode = "500", content = @Content(schema = @Schema(implementation = ErrorReport.class)), description = "Internal server error - this request cannot be served.")
            })
    public Response getDescriptor(
            //  @ApiParam(value = "Authorization token")  @HeaderParam("Authorization") String api_key,
            @Parameter(name = "Authorization", description = "Authorization token", schema = @Schema(implementation = String.class)) @HeaderParam("Authorization") String api_key,
            @Parameter(name = "id", schema = @Schema(implementation = String.class)) @PathParam("id") String descriptorId) throws ParameterIsNullException {
        if (descriptorId == null) {
            throw new ParameterIsNullException("descriptorId");
        }

        Descriptor descriptor = descriptorHandler.find(descriptorId);
        if (descriptor == null) {
            throw new NotFoundException("Could not find Descriptor with id:" + descriptorId);
        }
        return Response.ok(descriptor).build();
    }

    @POST
    @Produces({MediaType.APPLICATION_JSON, "text/uri-list"})
    @TokenSecured({RoleEnum.DEFAULT_USER})
    @Path("/{id}")
    @Consumes({MediaType.APPLICATION_FORM_URLENCODED})
    @Operation(summary = "Apply Descriptor",
            description = "Applies Descriptor on Dataset and creates a new Dataset.",
            responses = {
                @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ErrorReport.class)), description = "Bad request. More info can be found in details of Error Report."),
                @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorReport.class)), description = "Wrong, missing or insufficient credentials. Error report is produced."),
                @ApiResponse(responseCode = "404", content = @Content(schema = @Schema(implementation = ErrorReport.class)), description = "Descriptor was not found."),
                @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = Task.class)), description = "The process has successfully been started. A task URI is returned."),
                @ApiResponse(responseCode = "500", content = @Content(schema = @Schema(implementation = ErrorReport.class)), description = "Internal server error - this request cannot be served.")
            })
    @org.jaqpot.core.service.annotations.Task
    public Response applydescriptor(
            @Parameter(name = "title", description = "title", required = true, schema = @Schema(implementation = String.class)) @FormParam("title") String title,
            @Parameter(name = "description", description = "description", required = true, schema = @Schema(implementation = String.class)) @FormParam("description") String description,
            @Parameter(name = "datasetURI", description = "datasetURI", schema = @Schema(implementation = String.class))@FormParam("datasetURI") String datasetURI,
            @Parameter(name = "descriptionFeatures", description = "description_features", array = @ArraySchema(schema = @Schema(type = "string"))) @FormParam("descriptionFeatures") Set<String> descriptionFeatures,
            @Parameter(name = "parameters", description = "parameters", schema = @Schema(implementation = String.class)) @FormParam("parameters") String parameters,
            @PathParam("id") String descriptorId,
            @HeaderParam("Authorization") String api_key) throws QuotaExceededException, ParameterIsNullException, ParameterInvalidURIException, ParameterTypeException, ParameterRangeException, ParameterScopeException, JaqpotDocumentSizeExceededException {
        UrlValidator urlValidator = new UrlValidator(UrlValidator.ALLOW_LOCAL_URLS);

        String[] apiA = api_key.split("\\s+");
        String apiKey = apiA[1];
        Descriptor descriptor = descriptorHandler.find(descriptorId);
        if (descriptor == null) {
            throw new NotFoundException("Could not find Descriptor with id:" + descriptorId);
        }

        if (datasetURI == null) {
            throw new ParameterIsNullException("datasetURI");
        }

        if (!urlValidator.isValid(datasetURI)) {
            throw new ParameterInvalidURIException("Not valid Dataset URI.");
        }

        String datasetId = datasetURI.split("dataset/")[1];
        Dataset datasetMeta = datasetHandler.findMeta(datasetId);

        if (datasetMeta.getTotalRows() != null && datasetMeta.getTotalRows() < 1) {
            throw new BadRequestException("Cannot apply descriptor on dataset with no rows.");
        }

        if (title == null) {
            throw new ParameterIsNullException("title");
        }
        if (description == null) {
            throw new ParameterIsNullException("description");
        }

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

        Map<String, Object> options = new HashMap<>();
        options.put("title", title);
        options.put("description", description);
        options.put("datasetURI", datasetURI);
        options.put("featureURIs", serializer.write(descriptionFeatures));
        options.put("api_key", apiKey);
        options.put("descriptorId", descriptorId);
        options.put("parameters", parameters);
        options.put("base_uri", uriInfo.getBaseUri().toString());
        options.put("creator", securityContext.getUserPrincipal().getName());

        parameterValidator.validate(parameters, descriptor.getParameters());

        Task task = descriptorService.initiateDescriptor(options, securityContext.getUserPrincipal().getName());

        return Response.ok(task).build();
    }

    @DELETE
    @TokenSecured({RoleEnum.ADMNISTRATOR})
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{id}")
   
    @Operation(summary = "Unregisters a descriptor of given ID",
            description = "Deletes a descriptor of given ID. The application of this method "
            + "requires authentication and assumes certain priviledges.",
            extensions = {
                @Extension(properties = {
                    @ExtensionProperty(name = "orn-@type", value = "x-orn:DeletesDescriptor"),}),
                @Extension(name = "orn:expects", properties = {
                    @ExtensionProperty(name = "x-orn-@id", value = "x-orn:DescriptorId"),
                    @ExtensionProperty(name = "x-orn-@id", value = "x-orn:OperationParameters")
                }),
                @Extension(name = "orn:returns", properties = {
                    @ExtensionProperty(name = "x-orn-@id", value = "x-orn:HttpStatus")
                })
            },
            responses = {
                @ApiResponse(responseCode = "200", description = "Descriptor deleted successfully"),
                @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ErrorReport.class)), description = "Wrong, missing or insufficient credentials. Error report is produced."),
                @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ErrorReport.class)), description = "This is a forbidden operation (do not attempt to repeat it)."),
                @ApiResponse(responseCode = "500", content = @Content(schema = @Schema(implementation = ErrorReport.class)), description = "Internal server error - this request cannot be served.")
            })
    public Response deleteDescriptor(
            //@ApiParam(value = "ID of the descriptor which is to be deleted.", required = true) @PathParam("id") String id,
            @Parameter(name = "id", description = "ID of the descriptor which is to be deleted.", required = true, schema = @Schema(implementation = String.class)) @PathParam("id") String id,
            @Parameter(name = "Authorization", description = "Authorization token", schema = @Schema(implementation = String.class)) @HeaderParam("Authorization") String apiKey) throws NotFoundException, ParameterIsNullException {

        if (id == null) {
            throw new ParameterIsNullException("id");
        }

        Descriptor descriptor = descriptorHandler.find(id);

        if (descriptor == null) {
            throw new NotFoundException("Descriptor with id " + id + " was not found in the system");
        }

        descriptorHandler.remove(new Descriptor(id));
        return Response.ok().build();
    }

}
