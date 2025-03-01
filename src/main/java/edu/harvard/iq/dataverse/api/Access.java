/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.harvard.iq.dataverse.api;

import edu.harvard.iq.dataverse.AuxiliaryFile;
import edu.harvard.iq.dataverse.AuxiliaryFileServiceBean;
import edu.harvard.iq.dataverse.DataCitation;
import edu.harvard.iq.dataverse.DataFile;
import edu.harvard.iq.dataverse.FileMetadata;
import edu.harvard.iq.dataverse.DataFileServiceBean;
import edu.harvard.iq.dataverse.Dataset;
import edu.harvard.iq.dataverse.DatasetVersion;
import edu.harvard.iq.dataverse.DatasetVersionServiceBean;
import edu.harvard.iq.dataverse.DatasetServiceBean;
import edu.harvard.iq.dataverse.Dataverse;
import edu.harvard.iq.dataverse.DataverseRequestServiceBean;
import edu.harvard.iq.dataverse.DataverseRoleServiceBean;
import edu.harvard.iq.dataverse.DataverseServiceBean;
import edu.harvard.iq.dataverse.DataverseSession;
import edu.harvard.iq.dataverse.DataverseTheme;
import edu.harvard.iq.dataverse.FileDownloadServiceBean;
import edu.harvard.iq.dataverse.GuestbookResponse;
import edu.harvard.iq.dataverse.GuestbookResponseServiceBean;
import edu.harvard.iq.dataverse.PermissionServiceBean;
import edu.harvard.iq.dataverse.PermissionsWrapper;
import edu.harvard.iq.dataverse.RoleAssignment;
import edu.harvard.iq.dataverse.UserNotification;
import edu.harvard.iq.dataverse.UserNotificationServiceBean;
import static edu.harvard.iq.dataverse.api.AbstractApiBean.error;
import static edu.harvard.iq.dataverse.api.Datasets.handleVersion;
import edu.harvard.iq.dataverse.authorization.DataverseRole;
import edu.harvard.iq.dataverse.authorization.Permission;
import edu.harvard.iq.dataverse.authorization.RoleAssignee;
import edu.harvard.iq.dataverse.authorization.users.AuthenticatedUser;
import edu.harvard.iq.dataverse.authorization.users.PrivateUrlUser;
import edu.harvard.iq.dataverse.authorization.users.GuestUser;
import edu.harvard.iq.dataverse.authorization.users.User;
import edu.harvard.iq.dataverse.dataaccess.DataAccess;
import edu.harvard.iq.dataverse.dataaccess.DataAccessRequest;
import edu.harvard.iq.dataverse.dataaccess.StorageIO;
import edu.harvard.iq.dataverse.dataaccess.DataFileZipper;
import edu.harvard.iq.dataverse.dataaccess.OptionalAccessService;
import edu.harvard.iq.dataverse.dataaccess.ImageThumbConverter;
import edu.harvard.iq.dataverse.datavariable.DataVariable;
import edu.harvard.iq.dataverse.datavariable.VariableServiceBean;
import edu.harvard.iq.dataverse.engine.command.Command;
import edu.harvard.iq.dataverse.engine.command.DataverseRequest;
import edu.harvard.iq.dataverse.engine.command.exception.CommandException;
import edu.harvard.iq.dataverse.engine.command.impl.AssignRoleCommand;
import edu.harvard.iq.dataverse.engine.command.impl.GetDatasetCommand;
import edu.harvard.iq.dataverse.engine.command.impl.GetDraftDatasetVersionCommand;
import edu.harvard.iq.dataverse.engine.command.impl.GetLatestAccessibleDatasetVersionCommand;
import edu.harvard.iq.dataverse.engine.command.impl.GetLatestPublishedDatasetVersionCommand;
import edu.harvard.iq.dataverse.engine.command.impl.GetSpecificPublishedDatasetVersionCommand;
import edu.harvard.iq.dataverse.engine.command.impl.RequestAccessCommand;
import edu.harvard.iq.dataverse.engine.command.impl.RevokeRoleCommand;
import edu.harvard.iq.dataverse.engine.command.impl.UpdateDatasetVersionCommand;
import edu.harvard.iq.dataverse.export.DDIExportServiceBean;
import edu.harvard.iq.dataverse.makedatacount.MakeDataCountLoggingServiceBean;
import edu.harvard.iq.dataverse.makedatacount.MakeDataCountLoggingServiceBean.MakeDataCountEntry;
import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import edu.harvard.iq.dataverse.util.BundleUtil;
import edu.harvard.iq.dataverse.util.FileUtil;
import edu.harvard.iq.dataverse.util.StringUtil;
import edu.harvard.iq.dataverse.util.SystemConfig;
import edu.harvard.iq.dataverse.util.json.NullSafeJsonBuilder;

import java.util.logging.Logger;
import javax.ejb.EJB;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import javax.inject.Inject;
import javax.json.Json;
import java.net.URI;
import javax.json.JsonArrayBuilder;
import javax.persistence.TypedQuery;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriInfo;


import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.QueryParam;
import javax.ws.rs.ServiceUnavailableException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import javax.ws.rs.core.StreamingOutput;
import static edu.harvard.iq.dataverse.util.json.JsonPrinter.json;
import java.net.URISyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.json.JsonObjectBuilder;
import javax.ws.rs.RedirectionException;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.core.MediaType;
import static javax.ws.rs.core.Response.Status.FORBIDDEN;
import static javax.ws.rs.core.Response.Status.UNAUTHORIZED;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;

/*
    Custom API exceptions [NOT YET IMPLEMENTED]
import edu.harvard.iq.dataverse.api.exceptions.NotFoundException;
import edu.harvard.iq.dataverse.api.exceptions.ServiceUnavailableException;
import edu.harvard.iq.dataverse.api.exceptions.PermissionDeniedException;
import edu.harvard.iq.dataverse.api.exceptions.AuthorizationRequiredException;
*/

/**
 *
 * @author Leonid Andreev
 * 
 * The data (file) access API is based on the DVN access API v.1.0 (that came 
 * with the v.3.* of the DVN app) and extended for DVN 4.0 to include some
 * extra fancy functionality, such as subsetting individual columns in tabular
 * data files and more.
 */

@Path("access")
public class Access extends AbstractApiBean {
    private static final Logger logger = Logger.getLogger(Access.class.getCanonicalName());
        
    @EJB
    DataFileServiceBean dataFileService;
    @EJB 
    DatasetServiceBean datasetService; 
    @EJB
    DatasetVersionServiceBean versionService;
    @EJB
    DataverseServiceBean dataverseService; 
    @EJB
    VariableServiceBean variableService;
    @EJB
    SettingsServiceBean settingsService;
    @EJB
    SystemConfig systemConfig;
    @EJB
    DDIExportServiceBean ddiExportService;
    @EJB
    PermissionServiceBean permissionService;
    @Inject
    DataverseSession session;
    @Inject
    DataverseRequestServiceBean dvRequestService;
    @EJB
    GuestbookResponseServiceBean guestbookResponseService;
    @EJB
    DataverseRoleServiceBean roleService;
    @EJB
    UserNotificationServiceBean userNotificationService;
    @EJB
    FileDownloadServiceBean fileDownloadService; 
    @EJB
    AuxiliaryFileServiceBean auxiliaryFileService;
    @Inject
    PermissionsWrapper permissionsWrapper;
    @Inject
    MakeDataCountLoggingServiceBean mdcLogService;
    
    
    private static final String API_KEY_HEADER = "X-Dataverse-key";    
    
    //@EJB
    
    // TODO: 
    // versions? -- L.A. 4.0 beta 10
    @Path("datafile/bundle/{fileId}")
    @GET
    @Produces({"application/zip"})
    public BundleDownloadInstance datafileBundle(@PathParam("fileId") String fileId, @QueryParam("fileMetadataId") Long fileMetadataId,@QueryParam("gbrecs") boolean gbrecs, @QueryParam("key") String apiToken, @Context UriInfo uriInfo, @Context HttpHeaders headers, @Context HttpServletResponse response) /*throws NotFoundException, ServiceUnavailableException, PermissionDeniedException, AuthorizationRequiredException*/ {
 

        GuestbookResponse gbr = null;
        
        DataFile df = findDataFileOrDieWrapper(fileId);
        
        if (apiToken == null || apiToken.equals("")) {
            apiToken = headers.getHeaderString(API_KEY_HEADER);
        }
        
        // This will throw a ForbiddenException if access isn't authorized: 
        checkAuthorization(df, apiToken);
        
        if (gbrecs != true && df.isReleased()){
            // Write Guestbook record if not done previously and file is released
            User apiTokenUser = findAPITokenUser(apiToken);
            gbr = guestbookResponseService.initAPIGuestbookResponse(df.getOwner(), df, session, apiTokenUser);
            guestbookResponseService.save(gbr);
            MakeDataCountEntry entry = new MakeDataCountEntry(uriInfo, headers, dvRequestService, df);                                        
            mdcLogService.logEntry(entry);
        }
        
        DownloadInfo dInfo = new DownloadInfo(df);
        BundleDownloadInstance downloadInstance = new BundleDownloadInstance(dInfo);

        FileMetadata fileMetadata = null;

        if (fileMetadataId == null) {
            fileMetadata = df.getFileMetadata();
        } else {
            fileMetadata = dataFileService.findFileMetadata(fileMetadataId);
        }
        
        downloadInstance.setFileCitationEndNote(new DataCitation(fileMetadata).toEndNoteString());
        downloadInstance.setFileCitationRIS(new DataCitation(fileMetadata).toRISString());
        downloadInstance.setFileCitationBibtex(new DataCitation(fileMetadata).toBibtexString());

        ByteArrayOutputStream outStream = null;
        outStream = new ByteArrayOutputStream();
        Long dfId = df.getId();
        try {
            ddiExportService.exportDataFile(
                    dfId,
                    outStream,
                    null,
                    null,
                    fileMetadataId);

            downloadInstance.setFileDDIXML(outStream.toString());

        } catch (Exception ex) {
            // if we can't generate the DDI, it's ok; 
            // we'll just generate the bundle without it. 
        }
        
        return downloadInstance;       
    
    }
    
    //Added a wrapper method since the original method throws a wrapped response 
    //the access methods return files instead of responses so we convert to a WebApplicationException
    
    private DataFile findDataFileOrDieWrapper(String fileId){
        
        DataFile df = null;
        
        try {
            df = findDataFileOrDie(fileId);
        } catch (WrappedResponse ex) {
            logger.warning("Access: datafile service could not locate a DataFile object for id "+fileId+"!");
            logger.warning(ex.getWrappedMessageWhenJson());
            throw new NotFoundException();
        }
         return df;
    }
        
            
    @Path("datafile/{fileId:.+}")
    @GET
    @Produces({"application/xml"})
    public Response datafile(@PathParam("fileId") String fileId, @QueryParam("gbrecs") boolean gbrecs, @QueryParam("key") String apiToken, @Context UriInfo uriInfo, @Context HttpHeaders headers, @Context HttpServletResponse response) /*throws NotFoundException, ServiceUnavailableException, PermissionDeniedException, AuthorizationRequiredException*/ {
        
        // check first if there's a trailing slash, and chop it: 
        while (fileId.lastIndexOf('/') == fileId.length() - 1) {
            fileId = fileId.substring(0, fileId.length() - 1);
        }
            
        if (fileId.indexOf('/') > -1) {
            // This is for embedding folder names into the Access API URLs;
            // something like /api/access/datafile/folder/subfolder/1234
            // instead of the normal /api/access/datafile/1234 notation. 
            // this is supported only for recreating folders during recursive downloads - 
            // i.e. they are embedded into the URL for the remote client like wget,
            // but can be safely ignored here.
            fileId = fileId.substring(fileId.lastIndexOf('/') + 1);
        }
                
        DataFile df = findDataFileOrDieWrapper(fileId);
        GuestbookResponse gbr = null;
        
        if (df.isHarvested()) {
            String errorMessage = "Datafile " + fileId + " is a harvested file that cannot be accessed in this Dataverse";
            throw new NotFoundException(errorMessage);
            // (nobody should ever be using this API on a harvested DataFile)!
        }
        
        if (apiToken == null || apiToken.equals("")) {
            apiToken = headers.getHeaderString(API_KEY_HEADER);
        }
         
        if (gbrecs != true && df.isReleased()){
            // Write Guestbook record if not done previously and file is released
            User apiTokenUser = findAPITokenUser(apiToken);
            gbr = guestbookResponseService.initAPIGuestbookResponse(df.getOwner(), df, session, apiTokenUser);
        }
               
        // This will throw a ForbiddenException if access isn't authorized: 
        checkAuthorization(df, apiToken);
        
        DownloadInfo dInfo = new DownloadInfo(df);

        logger.fine("checking if thumbnails are supported on this file.");
        if (FileUtil.isThumbnailSupported(df)) {
            dInfo.addServiceAvailable(new OptionalAccessService("thumbnail", "image/png", "imageThumb=true", "Image Thumbnail (64x64)"));
        }

        if (df.isTabularData()) {
            String originalMimeType = df.getDataTable().getOriginalFileFormat();
            dInfo.addServiceAvailable(new OptionalAccessService("original", originalMimeType, "format=original","Saved original (" + originalMimeType + ")"));
            dInfo.addServiceAvailable(new OptionalAccessService("tabular", "text/tab-separated-values", "format=tab", "Tabular file in native format"));
            dInfo.addServiceAvailable(new OptionalAccessService("R", "application/x-rlang-transport", "format=RData", "Data in R format"));
            dInfo.addServiceAvailable(new OptionalAccessService("preprocessed", "application/json", "format=prep", "Preprocessed data in JSON"));
            dInfo.addServiceAvailable(new OptionalAccessService("subset", "text/tab-separated-values", "variables=&lt;LIST&gt;", "Column-wise Subsetting"));
        }
        DownloadInstance downloadInstance = new DownloadInstance(dInfo);
        downloadInstance.setRequestUriInfo(uriInfo);
        downloadInstance.setRequestHttpHeaders(headers);
        
        if (gbr != null){
            downloadInstance.setGbr(gbr);
            downloadInstance.setDataverseRequestService(dvRequestService);
            downloadInstance.setCommand(engineSvc);
        }
        boolean serviceRequested = false;
        boolean serviceFound = false;

        for (String key : uriInfo.getQueryParameters().keySet()) {
            String value = uriInfo.getQueryParameters().getFirst(key);
            logger.fine("is download service supported? key=" + key + ", value=" + value);
            // The loop goes through all query params (e.g. including key, gbrecs, persistentId, etc. )
            // So we need to identify when a service is being called and then let checkIfServiceSupportedAndSetConverter see if the required one exists
            if (key.equals("imageThumb") || key.equals("format") || key.equals("variables") || key.equals("noVarHeader")) {
                serviceRequested = true;
                //In the dataset file table context a user is allowed to select original as the format
                //for download
                // if the dataset has tabular files - it should not be applied to instances 
                // where the file selected is not tabular see #6972
                if("format".equals(key) && "original".equals(value) && !df.isTabularData()) {
                    serviceRequested = false;
                    break;
                }
                //Only need to check if this key is associated with a service
                if (downloadInstance.checkIfServiceSupportedAndSetConverter(key, value)) {
                    // this automatically sets the conversion parameters in
                    // the download instance to key and value;
                    // TODO: I should probably set these explicitly instead.
                    logger.fine("yes!");

                    if (downloadInstance.getConversionParam().equals("subset")) {
                        String subsetParam = downloadInstance.getConversionParamValue();
                        String variableIdParams[] = subsetParam.split(",");
                        if (variableIdParams != null && variableIdParams.length > 0) {
                            logger.fine(variableIdParams.length + " tokens;");
                            for (int i = 0; i < variableIdParams.length; i++) {
                                logger.fine("token: " + variableIdParams[i]);
                                String token = variableIdParams[i].replaceFirst("^v", "");
                                Long variableId = null;
                                try {
                                    variableId = new Long(token);
                                } catch (NumberFormatException nfe) {
                                    variableId = null;
                                }
                                if (variableId != null) {
                                    logger.fine("attempting to look up variable id " + variableId);
                                    if (variableService != null) {
                                        DataVariable variable = variableService.find(variableId);
                                        if (variable != null) {
                                            if (downloadInstance.getExtraArguments() == null) {
                                                downloadInstance.setExtraArguments(new ArrayList<Object>());
                                            }
                                            logger.fine("putting variable id " + variable.getId() + " on the parameters list of the download instance.");
                                            downloadInstance.getExtraArguments().add(variable);

                                            // if (!variable.getDataTable().getDataFile().getId().equals(sf.getId())) {
                                            // variableList.add(variable);
                                            // }
                                        }
                                    } else {
                                        logger.fine("variable service is null.");
                                    }
                                }
                            }
                        }
                    }

                    logger.fine("downloadInstance: " + downloadInstance.getConversionParam() + "," + downloadInstance.getConversionParamValue());
                    serviceFound = true;
                    break;
                }
            } else {
                
            }
        }
        if (serviceRequested && !serviceFound) {
            // Service not supported/bad arguments, etc.:
            // One could return
            // a ServiceNotAvailableException. However, since the returns are all files of
            // some sort, it seems reasonable, and more standard, to just return
            // a NotFoundException.
            throw new NotFoundException("datafile access error: requested optional service (image scaling, format conversion, etc.) is not supported on this datafile.");
        } // Else - the file itself was requested or we have the info needed to invoke the service and get the derived info
        logger.fine("Returning download instance");
        /* 
         * Provide some browser-friendly headers: (?)
         */
        if (headers.getRequestHeaders().containsKey("Range")) {
            return Response.status(Response.Status.PARTIAL_CONTENT).entity(downloadInstance).build();
        }
        return Response.ok(downloadInstance).build();
    }
    
    
    /* 
     * Variants of the Access API calls for retrieving datafile-level 
     * Metadata.
    */
    
    
    // Metadata format defaults to DDI:
    @Path("datafile/{fileId}/metadata")
    @GET
    @Produces({"text/xml"})
    public String tabularDatafileMetadata(@PathParam("fileId") String fileId, @QueryParam("fileMetadataId") Long fileMetadataId, @QueryParam("exclude") String exclude, @QueryParam("include") String include, @Context HttpHeaders header, @Context HttpServletResponse response) throws NotFoundException, ServiceUnavailableException /*, PermissionDeniedException, AuthorizationRequiredException*/ {
        return tabularDatafileMetadataDDI(fileId, fileMetadataId, exclude, include, header, response);
    }
    
    /* 
     * This has been moved here, under /api/access, from the /api/meta hierarchy
     * which we are going to retire.
     */
    @Path("datafile/{fileId}/metadata/ddi")
    @GET
    @Produces({"text/xml"})
    public String tabularDatafileMetadataDDI(@PathParam("fileId") String fileId,  @QueryParam("fileMetadataId") Long fileMetadataId, @QueryParam("exclude") String exclude, @QueryParam("include") String include, @Context HttpHeaders header, @Context HttpServletResponse response) throws NotFoundException, ServiceUnavailableException /*, PermissionDeniedException, AuthorizationRequiredException*/ {
        String retValue = "";

        DataFile dataFile = null; 

        
        dataFile = findDataFileOrDieWrapper(fileId);
        
        if (!dataFile.isTabularData()) { 
           throw new BadRequestException("tabular data required");
        }
        
        if (dataFile.isRestricted() || FileUtil.isActivelyEmbargoed(dataFile)) {
            boolean hasPermissionToDownloadFile = false;
            DataverseRequest dataverseRequest;
            try {
                dataverseRequest = createDataverseRequest(findUserOrDie());
            } catch (WrappedResponse ex) {
                throw new BadRequestException("cannot find user");
            }
            if (dataverseRequest != null && dataverseRequest.getUser() instanceof GuestUser) {
                // We must be in the UI. Try to get a non-GuestUser from the session.
                dataverseRequest = dvRequestService.getDataverseRequest();
            }
            hasPermissionToDownloadFile = permissionService.requestOn(dataverseRequest, dataFile).has(Permission.DownloadFile);
            if (!hasPermissionToDownloadFile) {
                throw new BadRequestException("no permission to download file");
            }
        }

        response.setHeader("Content-disposition", "attachment; filename=\"dataverse_files.zip\"");

        FileMetadata fm = null;
        if (fileMetadataId == null) {
            fm = dataFile.getFileMetadata();
        } else {
            fm = dataFileService.findFileMetadata(fileMetadataId);
        }

        String fileName = fm.getLabel().replaceAll("\\.tab$", "-ddi.xml");

        response.setHeader("Content-disposition", "attachment; filename=\""+fileName+"\"");
        response.setHeader("Content-Type", "application/xml; name=\""+fileName+"\"");
        
        ByteArrayOutputStream outStream = null;
        outStream = new ByteArrayOutputStream();
        Long dataFileId = dataFile.getId();
        try {
            ddiExportService.exportDataFile(
                    dataFileId,
                    outStream,
                    exclude,
                    include,
                    fileMetadataId);

            retValue = outStream.toString();

        } catch (Exception e) {
            // For whatever reason we've failed to generate a partial 
            // metadata record requested. 
            // We return Service Unavailable.
            throw new ServiceUnavailableException();
        }

        return retValue;
    }


    /*
     * GET method for retrieving a list of auxiliary files associated with 
     * a tabular datafile.
     */
    
    @Path("datafile/{fileId}/auxiliary")
    @GET
    public Response listDatafileMetadataAux(@PathParam("fileId") String fileId,
            @QueryParam("key") String apiToken,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers,
            @Context HttpServletResponse response) throws ServiceUnavailableException {
        return listAuxiliaryFiles(fileId, null, apiToken, uriInfo, headers, response);
    }
    /*
     * GET method for retrieving a list auxiliary files associated with
     * a tabular datafile and having the specified origin.
     */
    
    @Path("datafile/{fileId}/auxiliary/{origin}")
    @GET
    public Response listDatafileMetadataAuxByOrigin(@PathParam("fileId") String fileId,
            @PathParam("origin") String origin,
            @QueryParam("key") String apiToken,
            @Context UriInfo uriInfo,
            @Context HttpHeaders headers,
            @Context HttpServletResponse response) throws ServiceUnavailableException {
        return listAuxiliaryFiles(fileId, origin, apiToken, uriInfo, headers, response);
    } 
    
    private Response listAuxiliaryFiles(String fileId, String origin, String apiToken, UriInfo uriInfo, HttpHeaders headers, HttpServletResponse response) {
          DataFile df = findDataFileOrDieWrapper(fileId);

        if (apiToken == null || apiToken.equals("")) {
            apiToken = headers.getHeaderString(API_KEY_HEADER);
        }

        List<AuxiliaryFile> auxFileList = auxiliaryFileService.findAuxiliaryFiles(df, origin);

        if (auxFileList == null || auxFileList.isEmpty()) {
            throw new NotFoundException("No Auxiliary files exist for datafile " + fileId + (origin==null ? "": " and the specified origin"));
        }
        boolean isAccessAllowed = isAccessAuthorized(df, apiToken);
        JsonArrayBuilder jab = Json.createArrayBuilder();
        auxFileList.forEach(auxFile -> {
            if (isAccessAllowed || auxFile.getIsPublic()) {
                NullSafeJsonBuilder job = NullSafeJsonBuilder.jsonObjectBuilder();
                job.add("formatTag", auxFile.getFormatTag());
                job.add("formatVersion", auxFile.getFormatVersion());
                job.add("fileSize", auxFile.getFileSize());
                job.add("contentType", auxFile.getContentType());
                job.add("isPublic", auxFile.getIsPublic());
                job.add("type", auxFile.getType());
                jab.add(job);
            }
        });
        return ok(jab);
    }

    /*
     * GET method for retrieving various auxiliary files associated with 
     * a tabular datafile.
     *
     */
    
    @Path("datafile/{fileId}/auxiliary/{formatTag}/{formatVersion}")
    @GET    
    public DownloadInstance downloadAuxiliaryFile(@PathParam("fileId") String fileId,
            @PathParam("formatTag") String formatTag,
            @PathParam("formatVersion") String formatVersion,
            @QueryParam("key") String apiToken, 
            @Context UriInfo uriInfo, 
            @Context HttpHeaders headers, 
            @Context HttpServletResponse response) throws ServiceUnavailableException {
    
        DataFile df = findDataFileOrDieWrapper(fileId);
        
        if (apiToken == null || apiToken.equals("")) {
            apiToken = headers.getHeaderString(API_KEY_HEADER);
        }
        
        DownloadInfo dInfo = new DownloadInfo(df);
        boolean publiclyAvailable = false; 

        DownloadInstance downloadInstance;
        AuxiliaryFile auxFile = null;
        
        /* 
          The special case for "preprocessed" metadata should not be here at all. 
          Access to the format should be handled by the /api/access/datafile/{id}?format=prep
          form exclusively (this is what Data Explorer has been using all along).
          We may have advertised /api/access/datafile/{id}/metadata/preprocessed
          in the past - but it has been broken since 5.3 anyway, since the /{formatVersion}
          element was added to the @Path. 
          Now that the api method has been renamed /api/access/datafile/{id}/auxiliary/..., 
          nobody should be using it to access the "preprocessed" format. 
          Leaving the special case below commented-out, for now. - L.A.
        
        // formatTag=preprocessed is handled as a special case. 
        // This is (as of now) the only aux. tabular metadata format that Dataverse
        // can generate (and cache) itself. (All the other formats served have 
        // to be deposited first, by the @POST version of this API).
        
        if ("preprocessed".equals(formatTag)) {
            dInfo.addServiceAvailable(new OptionalAccessService("preprocessed", "application/json", "format=prep", "Preprocessed data in JSON"));
            downloadInstance = new DownloadInstance(dInfo);
            if (downloadInstance.checkIfServiceSupportedAndSetConverter("format", "prep")) {
                logger.fine("Preprocessed data for tabular file "+fileId);
            }
        } else { */
        // All other (deposited) formats:
        auxFile = auxiliaryFileService.lookupAuxiliaryFile(df, formatTag, formatVersion);

        if (auxFile == null) {
            throw new NotFoundException("Auxiliary metadata format " + formatTag + " is not available for datafile " + fileId);
        }

        // Don't consider aux file public unless data file is published.
        if (auxFile.getIsPublic() && df.getPublicationDate() != null) {
            publiclyAvailable = true;
        }
        downloadInstance = new DownloadInstance(dInfo);
        downloadInstance.setAuxiliaryFile(auxFile);
        /*}*/
        
        // Unless this format is explicitly authorized to be publicly available, 
        // the following will check access authorization (based on the access rules
        // as defined for the DataFile itself), and will throw a ForbiddenException 
        // if access is denied:
        if (!publiclyAvailable) {
            checkAuthorization(df, apiToken);
        }
        
        return downloadInstance;
    }
    
    /* 
     * API method for downloading zipped bundles of multiple files. Uses POST to avoid long lists of file IDs that can make the URL longer than what's supported by browsers/servers
    */
    
    // TODO: Rather than only supporting looking up files by their database IDs,
    // consider supporting persistent identifiers.
    @Path("datafiles")
    @POST
    @Consumes("text/plain")
    @Produces({ "application/zip" })
    public Response postDownloadDatafiles(String fileIds, @QueryParam("gbrecs") boolean gbrecs, @QueryParam("key") String apiTokenParam, @Context UriInfo uriInfo, @Context HttpHeaders headers, @Context HttpServletResponse response) throws WebApplicationException {
        

        return downloadDatafiles(fileIds, gbrecs, apiTokenParam, uriInfo, headers, response);
    }

    @Path("dataset/{id}")
    @GET
    @Produces({"application/zip"})
    public Response downloadAllFromLatest(@PathParam("id") String datasetIdOrPersistentId, @QueryParam("gbrecs") boolean gbrecs, @QueryParam("key") String apiTokenParam, @Context UriInfo uriInfo, @Context HttpHeaders headers, @Context HttpServletResponse response) throws WebApplicationException {
        try {
            User user = findUserOrDie(); 
            DataverseRequest req = createDataverseRequest(user);
            final Dataset retrieved = findDatasetOrDie(datasetIdOrPersistentId);
            if (!(user instanceof GuestUser)) {
                // The reason we are only looking up a draft version for a NON-guest user
                // is that we know that guest never has the Permission.ViewUnpublishedDataset. 
                final DatasetVersion draft = versionService.getDatasetVersionById(retrieved.getId(), DatasetVersion.VersionState.DRAFT.toString());
                if (draft != null && permissionService.requestOn(req, retrieved).has(Permission.ViewUnpublishedDataset)) {                    
                    String fileIds = getFileIdsAsCommaSeparated(draft.getFileMetadatas());
                    // We don't want downloads from Draft versions to be counted, 
                    // so we are setting the gbrecs (aka "do not write guestbook response") 
                    // variable accordingly:
                    return downloadDatafiles(fileIds, true, apiTokenParam, uriInfo, headers, response);
                }
            }
            
            // OK, it was not the draft. Let's see if we can serve a published version.
            
            final DatasetVersion latest = versionService.getLatestReleasedVersionFast(retrieved.getId()); 
            
            // Make sure to throw a clean error code if we have failed to locate an 
            // accessible version:
            // (A "Not Found" would be more appropriate here, I believe, than a "Bad Request". 
            // But we've been using the latter for a while, and it's a popular API... 
            // and this return code is expected by our tests - so I'm choosing it to keep 
            // -- L.A.)
            
            if (latest == null) {
                return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.exception.dataset.not.found"));
                //throw new NotFoundException();
            }
            
            String fileIds = getFileIdsAsCommaSeparated(latest.getFileMetadatas());
            return downloadDatafiles(fileIds, gbrecs, apiTokenParam, uriInfo, headers, response);
        } catch (WrappedResponse wr) {
            return wr.getResponse();
        }
    }

    @Path("dataset/{id}/versions/{versionId}")
    @GET
    @Produces({"application/zip"})
    public Response downloadAllFromVersion(@PathParam("id") String datasetIdOrPersistentId, @PathParam("versionId") String versionId, @QueryParam("gbrecs") boolean gbrecs, @QueryParam("key") String apiTokenParam, @Context UriInfo uriInfo, @Context HttpHeaders headers, @Context HttpServletResponse response) throws WebApplicationException {
        try {
            DataverseRequest req = createDataverseRequest(findUserOrDie());
            final Dataset ds = execCommand(new GetDatasetCommand(req, findDatasetOrDie(datasetIdOrPersistentId)));
            DatasetVersion dsv = execCommand(handleVersion(versionId, new Datasets.DsVersionHandler<Command<DatasetVersion>>() {

                @Override
                public Command<DatasetVersion> handleLatest() {
                    return new GetLatestAccessibleDatasetVersionCommand(req, ds);
                }

                @Override
                public Command<DatasetVersion> handleDraft() {
                    return new GetDraftDatasetVersionCommand(req, ds);
                }

                @Override
                public Command<DatasetVersion> handleSpecific(long major, long minor) {
                    return new GetSpecificPublishedDatasetVersionCommand(req, ds, major, minor);
                }

                @Override
                public Command<DatasetVersion> handleLatestPublished() {
                    return new GetLatestPublishedDatasetVersionCommand(req, ds);
                }
            }));
            if (dsv == null) {
                // (A "Not Found" would be more appropriate here, I believe, than a "Bad Request". 
                // But we've been using the latter for a while, and it's a popular API... 
                // and this return code is expected by our tests - so I'm choosing it to keep 
                // -- L.A.)
                return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.exception.version.not.found"));
            }
            String fileIds = getFileIdsAsCommaSeparated(dsv.getFileMetadatas());
            // We don't want downloads from Draft versions to be counted, 
            // so we are setting the gbrecs (aka "do not write guestbook response") 
            // variable accordingly:
            if (dsv.isDraft()) {
                gbrecs = true;
            }
            return downloadDatafiles(fileIds, gbrecs, apiTokenParam, uriInfo, headers, response);
        } catch (WrappedResponse wr) {
            return wr.getResponse();
        }
    }

    private static String getFileIdsAsCommaSeparated(List<FileMetadata> fileMetadatas) {
        List<String> ids = new ArrayList<>();
        for (FileMetadata fileMetadata : fileMetadatas) {
            Long fileId = fileMetadata.getDataFile().getId();
            ids.add(String.valueOf(fileId));
        }
        return String.join(",", ids);
    }

    /*
     * API method for downloading zipped bundles of multiple files:
     */
    @Path("datafiles/{fileIds}")
    @GET
    @Produces({"application/zip"})
    public Response datafiles(@PathParam("fileIds") String fileIds, @QueryParam("gbrecs") boolean gbrecs, @QueryParam("key") String apiTokenParam, @Context UriInfo uriInfo, @Context HttpHeaders headers, @Context HttpServletResponse response) throws WebApplicationException {
        return downloadDatafiles(fileIds, gbrecs, apiTokenParam, uriInfo, headers, response);
    }

    private Response downloadDatafiles(String rawFileIds, boolean donotwriteGBResponse, String apiTokenParam, UriInfo uriInfo, HttpHeaders headers, HttpServletResponse response) throws WebApplicationException /* throws NotFoundException, ServiceUnavailableException, PermissionDeniedException, AuthorizationRequiredException*/ {
        final long zipDownloadSizeLimit = systemConfig.getZipDownloadLimit();
                
        logger.fine("setting zip download size limit to " + zipDownloadSizeLimit + " bytes.");
        
        if (rawFileIds == null || rawFileIds.equals("")) {
            throw new BadRequestException();
        }
        
        final String fileIds;
        if(rawFileIds.startsWith("fileIds=")) {
            fileIds = rawFileIds.substring(8); // String "fileIds=" from the front
        } else {
            fileIds=rawFileIds;
        }
        /* Note - fileIds coming from the POST ends in '\n' and a ',' has been added after the last file id number and before a
         * final '\n' - this stops the last item from being parsed in the fileIds.split(","); line below.
         */
        
        String customZipServiceUrl = settingsService.getValueForKey(SettingsServiceBean.Key.CustomZipDownloadServiceUrl);
        boolean useCustomZipService = customZipServiceUrl != null; 
        
        String apiToken = (apiTokenParam == null || apiTokenParam.equals("")) 
                ? headers.getHeaderString(API_KEY_HEADER) 
                : apiTokenParam;
        
        User apiTokenUser = findAPITokenUser(apiToken); //for use in adding gb records if necessary
        
        Boolean getOrig = false;
        for (String key : uriInfo.getQueryParameters().keySet()) {
            String value = uriInfo.getQueryParameters().getFirst(key);
            if("format".equals(key) && "original".equals(value)) {
                getOrig = true;
            }
        }
        
        if (useCustomZipService) {
            URI redirect_uri = null; 
            try {
                redirect_uri = handleCustomZipDownload(customZipServiceUrl, fileIds, apiToken, apiTokenUser, uriInfo, headers, donotwriteGBResponse, true); 
            } catch (WebApplicationException wae) {
                throw wae;
            }
            
            Response redirect = Response.seeOther(redirect_uri).build();
            logger.fine("Issuing redirect to the file location on S3.");
            throw new RedirectionException(redirect);

        }
        
        // Not using the "custom service" - API will zip the file,  
        // and stream the output, in the "normal" manner:
        
        final boolean getOriginal = getOrig; //to use via anon inner class
        
        StreamingOutput stream = new StreamingOutput() {

            @Override
            public void write(OutputStream os) throws IOException,
                    WebApplicationException {
                String fileIdParams[] = fileIds.split(",");
                DataFileZipper zipper = null; 
                String fileManifest = "";
                long sizeTotal = 0L;
                
                if (fileIdParams != null && fileIdParams.length > 0) {
                    logger.fine(fileIdParams.length + " tokens;");
                    for (int i = 0; i < fileIdParams.length; i++) {
                        logger.fine("token: " + fileIdParams[i]);
                        Long fileId = null;
                        try {
                            fileId = new Long(fileIdParams[i]);
                        } catch (NumberFormatException nfe) {
                            fileId = null;
                        }
                        if (fileId != null) {
                            logger.fine("attempting to look up file id " + fileId);
                            DataFile file = dataFileService.find(fileId);
                            if (file != null) {
                                if (isAccessAuthorized(file, apiToken)) { 
                                    
                                    logger.fine("adding datafile (id=" + file.getId() + ") to the download list of the ZippedDownloadInstance.");
                                    //downloadInstance.addDataFile(file);
                                    if (donotwriteGBResponse != true && file.isReleased()){
                                        GuestbookResponse  gbr = guestbookResponseService.initAPIGuestbookResponse(file.getOwner(), file, session, apiTokenUser);
                                        guestbookResponseService.save(gbr);
                                        MakeDataCountEntry entry = new MakeDataCountEntry(uriInfo, headers, dvRequestService, file);                                        
                                        mdcLogService.logEntry(entry);
                                    }
                                    
                                    if (zipper == null) {
                                        // This is the first file we can serve - so we now know that we are going to be able 
                                        // to produce some output.
                                        zipper = new DataFileZipper(os);
                                        zipper.setFileManifest(fileManifest);
                                        response.setHeader("Content-disposition", "attachment; filename=\"dataverse_files.zip\"");
                                        response.setHeader("Content-Type", "application/zip; name=\"dataverse_files.zip\"");
                                    }
                                    
                                    long size = 0L;
                                    // is the original format requested, and is this a tabular datafile, with a preserved original?
                                    if (getOriginal 
                                            && file.isTabularData() 
                                            && !StringUtil.isEmpty(file.getDataTable().getOriginalFileFormat())) {
                                        //This size check is probably fairly inefficient as we have to get all the AccessObjects
                                        //We do this again inside the zipper. I don't think there is a better solution
                                        //without doing a large deal of rewriting or architecture redo.
                                        //The previous size checks for non-original download is still quick.
                                        //-MAD 4.9.2
                                        // OK, here's the better solution: we now store the size of the original file in 
                                        // the database (in DataTable), so we get it for free. 
                                        // However, there may still be legacy datatables for which the size is not saved. 
                                        // so the "inefficient" code is kept, below, as a fallback solution. 
                                        // -- L.A., 4.10
                                        
                                        if (file.getDataTable().getOriginalFileSize() != null) {
                                            size = file.getDataTable().getOriginalFileSize();
                                        } else {
                                            DataAccessRequest daReq = new DataAccessRequest();
                                            StorageIO<DataFile> storageIO = DataAccess.getStorageIO(file, daReq);
                                            storageIO.open();
                                            size = storageIO.getAuxObjectSize(FileUtil.SAVED_ORIGINAL_FILENAME_EXTENSION);

                                            // save it permanently: 
                                            file.getDataTable().setOriginalFileSize(size);
                                            fileService.saveDataTable(file.getDataTable());
                                        }
                                        if (size == 0L){
                                            throw new IOException("Invalid file size or accessObject when checking limits of zip file");
                                        }
                                    } else {
                                        size = file.getFilesize();
                                    }
                                    if (sizeTotal + size < zipDownloadSizeLimit) {
                                        sizeTotal += zipper.addFileToZipStream(file, getOriginal);
                                    } else {
                                        String fileName = file.getFileMetadata().getLabel();
                                        String mimeType = file.getContentType();
                                        
                                        zipper.addToManifest(fileName + " (" + mimeType + ") " + " skipped because the total size of the download bundle exceeded the limit of " + zipDownloadSizeLimit + " bytes.\r\n");
                                    }
                                } else { 
                                    boolean embargoed = FileUtil.isActivelyEmbargoed(file);
                                    if (file.isRestricted() || embargoed) {
                                        if (zipper == null) {
                                            fileManifest = fileManifest + file.getFileMetadata().getLabel() + " IS "
                                                    + (embargoed ? "EMBARGOED" : "RESTRICTED")
                                                    + " AND CANNOT BE DOWNLOADED\r\n";
                                        } else {
                                            zipper.addToManifest(file.getFileMetadata().getLabel() + " IS "
                                                    + (embargoed ? "EMBARGOED" : "RESTRICTED")
                                                    + " AND CANNOT BE DOWNLOADED\r\n");
                                        }
                                    } else {
                                        fileId = null;
                                    }
                                }
                            
                            } if (null == fileId) {
                                // As of now this errors out.
                                // This is bad because the user ends up with a broken zip and manifest
                                // This is good in that the zip ends early so the user does not wait for the results
                                String errorMessage = "Datafile " + fileId + ": no such object available";
                                throw new NotFoundException(errorMessage);
                            }
                        }
                    }
                } else {
                    throw new BadRequestException();
                }

                if (zipper == null) {
                    // If the DataFileZipper object is still NULL, it means that 
                    // there were file ids supplied - but none of the corresponding 
                    // files were accessible for this user. 
                    // In which casew we don't bother generating any output, and 
                    // just give them a 403:
                    throw new ForbiddenException();
                }

                // This will add the generated File Manifest to the zipped output, 
                // then flush and close the stream:
                zipper.finalizeZipStream();
                
                //os.flush();
                //os.close();
            }
        };
        return Response.ok(stream).build();
    }
    
    /* 
     * Geting rid of the tempPreview API - it's always been a big, fat hack. 
     * the edit files page is now using the Base64 image strings in the preview 
     * URLs, just like the search and dataset pages.
    @Path("tempPreview/{fileSystemId}")
    @GET
    @Produces({"image/png"})
    public InputStream tempPreview(@PathParam("fileSystemId") String fileSystemId, @Context UriInfo uriInfo, @Context HttpHeaders headers, @Context HttpServletResponse response) {

    }*/
    
    
    
    // TODO: Rather than only supporting looking up files by their database IDs, consider supporting persistent identifiers.
    @Path("fileCardImage/{fileId}")
    @GET
    @Produces({ "image/png" })
    public InputStream fileCardImage(@PathParam("fileId") Long fileId, @Context UriInfo uriInfo, @Context HttpHeaders headers, @Context HttpServletResponse response) /*throws NotFoundException, ServiceUnavailableException, PermissionDeniedException, AuthorizationRequiredException*/ {        
        
        
        
        DataFile df = dataFileService.find(fileId);
        
        if (df == null) {
            logger.warning("Preview: datafile service could not locate a DataFile object for id "+fileId+"!");
            return null; 
        }
        
        StorageIO<DataFile> thumbnailDataAccess = null;
        
        try {
            StorageIO<DataFile> dataAccess = df.getStorageIO();
            if (dataAccess != null) { // && dataAccess.isLocalFile()) {
                dataAccess.open();

                if ("application/pdf".equalsIgnoreCase(df.getContentType())
                        || df.isImage()
                        || "application/zipped-shapefile".equalsIgnoreCase(df.getContentType())) {

                    thumbnailDataAccess = ImageThumbConverter.getImageThumbnailAsInputStream(dataAccess, 48);
                    if (thumbnailDataAccess != null && thumbnailDataAccess.getInputStream() != null) {
                        return thumbnailDataAccess.getInputStream();
                    }
                }
            }
        } catch (IOException ioEx) {
            return null;
        }

        return null; 
    }
    
    // Note:
    // the Dataverse page is no longer using this method.
    @Path("dsCardImage/{versionId}")
    @GET
    @Produces({ "image/png" })
    public InputStream dsCardImage(@PathParam("versionId") Long versionId, @Context UriInfo uriInfo, @Context HttpHeaders headers, @Context HttpServletResponse response) /*throws NotFoundException, ServiceUnavailableException, PermissionDeniedException, AuthorizationRequiredException*/ {        
        
        
        DatasetVersion datasetVersion = versionService.find(versionId);
        
        if (datasetVersion == null) {
            logger.warning("Preview: Version service could not locate a DatasetVersion object for id "+versionId+"!");
            return null; 
        }
        
        //String imageThumbFileName = null; 
        StorageIO thumbnailDataAccess = null;
        
        // First, check if this dataset has a designated thumbnail image: 
        
        if (datasetVersion.getDataset() != null) {
            
            DataFile logoDataFile = datasetVersion.getDataset().getThumbnailFile();
            if (logoDataFile != null) {
        
                try {
                    StorageIO<DataFile> dataAccess = logoDataFile.getStorageIO();
                    if (dataAccess != null) { // && dataAccess.isLocalFile()) {
                        dataAccess.open();
                        thumbnailDataAccess = ImageThumbConverter.getImageThumbnailAsInputStream(dataAccess, 48);
                    }
                    if (thumbnailDataAccess != null && thumbnailDataAccess.getInputStream() != null) {
                        return thumbnailDataAccess.getInputStream();
                    } 
                } catch (IOException ioEx) {
                    thumbnailDataAccess = null; 
                }
            }
                
               
        
            // If not, we'll try to use one of the files in this dataset version:
            /*
            if (thumbnailDataAccess == null) {

                if (!datasetVersion.getDataset().isHarvested()) {
                    thumbnailDataAccess = getThumbnailForDatasetVersion(datasetVersion); 
                }
            }*/
            
        }

        return null; 
    }
    
    @Path("dvCardImage/{dataverseId}")
    @GET
    @Produces({ "image/png" })
    public InputStream dvCardImage(@PathParam("dataverseId") Long dataverseId, @Context UriInfo uriInfo, @Context HttpHeaders headers, @Context HttpServletResponse response) /*throws NotFoundException, ServiceUnavailableException, PermissionDeniedException, AuthorizationRequiredException*/ {        
        logger.fine("entering dvCardImage");
        
        Dataverse dataverse = dataverseService.find(dataverseId);
        
        if (dataverse == null) {
            logger.warning("Preview: Version service could not locate a DatasetVersion object for id "+dataverseId+"!");
            return null; 
        }
        
        String imageThumbFileName = null; 
        
        // First, check if the dataverse has a defined logo: 
        
        if (dataverse.getDataverseTheme()!=null && dataverse.getDataverseTheme().getLogo() != null && !dataverse.getDataverseTheme().getLogo().equals("")) {
            File dataverseLogoFile = getLogo(dataverse);
            if (dataverseLogoFile != null) {
                logger.fine("dvCardImage: logo file found");
                String logoThumbNailPath = null;
                InputStream in = null;

                try {
                    if (dataverseLogoFile.exists()) {
                        logoThumbNailPath =  ImageThumbConverter.generateImageThumbnailFromFile(dataverseLogoFile.getAbsolutePath(), 48);
                        if (logoThumbNailPath != null) {
                            in = new FileInputStream(logoThumbNailPath);
                        }
                    }
                } catch (Exception ex) {
                    in = null; 
                }
                if (in != null) {
                    logger.fine("dvCardImage: successfully obtained thumbnail for dataverse logo.");
                    return in;
                }    
            }
        }
        
        // If there's no uploaded logo for this dataverse, go through its 
        // [released] datasets and see if any of them have card images:
        
        // TODO: figure out if we want to be doing this! 
        // (efficiency considerations...) -- L.A. 4.0 
        // And we definitely don't want to be doing this for harvested 
        // dataverses:
        /*
        StorageIO thumbnailDataAccess = null; 
        
        if (!dataverse.isHarvested()) {
            for (Dataset dataset : datasetService.findPublishedByOwnerId(dataverseId)) {
                logger.info("dvCardImage: checking dataset "+dataset.getGlobalId());
                if (dataset != null) {
                    DatasetVersion releasedVersion = dataset.getReleasedVersion();
                    logger.info("dvCardImage: obtained released version "+releasedVersion.getTitle());
                    thumbnailDataAccess = getThumbnailForDatasetVersion(releasedVersion); 
                    if (thumbnailDataAccess != null) {
                        logger.info("dvCardImage: obtained thumbnail for the version.");
                        break;
                    }
                }
            }
        }
        
        if (thumbnailDataAccess != null && thumbnailDataAccess.getInputStream() != null) {
            return thumbnailDataAccess.getInputStream();
        }
        */
        return null;
    }
    
    // helper methods:
    
    // What the method below does - going through all the files in the version -
    // is too expensive! Instead we are now selecting an available thumbnail and
    // giving the dataset card a direct link to that file thumbnail. -- L.A., 4.2.2
    /*
    private StorageIO getThumbnailForDatasetVersion(DatasetVersion datasetVersion) {
        logger.info("entering getThumbnailForDatasetVersion()");
        StorageIO thumbnailDataAccess = null;
        if (datasetVersion != null) {
            List<FileMetadata> fileMetadatas = datasetVersion.getFileMetadatas();

            for (FileMetadata fileMetadata : fileMetadatas) {
                DataFile dataFile = fileMetadata.getDataFile();
                logger.info("looking at file "+fileMetadata.getLabel()+" , file type "+dataFile.getContentType());

                if (dataFile != null && dataFile.isImage()) {

                    try {
                        StorageIO dataAccess = dataFile.getStorageIO();
                        if (dataAccess != null && dataAccess.isLocalFile()) {
                            dataAccess.open();

                            thumbnailDataAccess = ImageThumbConverter.getImageThumb((FileAccessIO) dataAccess, 48);
                        }
                    } catch (IOException ioEx) {
                        thumbnailDataAccess = null;
                    }
                }
                if (thumbnailDataAccess != null) {
                    logger.info("successfully generated thumbnail, returning.");
                    break;
                }
            }
        }
        return thumbnailDataAccess;
    }
    */
    // TODO: 
    // put this method into the dataverseservice; use it there
    // -- L.A. 4.0 beta14
    
    private File getLogo(Dataverse dataverse) {
        if (dataverse.getId() == null) {
            return null; 
        }
        
        DataverseTheme theme = dataverse.getDataverseTheme(); 
        if (theme != null && theme.getLogo() != null && !theme.getLogo().equals("")) {
            Properties p = System.getProperties();
            String domainRoot = p.getProperty("com.sun.aas.instanceRoot");
  
            if (domainRoot != null && !"".equals(domainRoot)) {
                return new File (domainRoot + File.separator + 
                    "docroot" + File.separator + 
                    "logos" + File.separator + 
                    dataverse.getLogoOwnerId() + File.separator + 
                    theme.getLogo());
            }
        }
            
        return null;         
    }
    
    /* 
        removing: 
    private String getWebappImageResource(String imageName) {
        String imageFilePath = null;
        String persistenceFilePath = null;
        java.net.URL persistenceFileUrl = Thread.currentThread().getContextClassLoader().getResource("META-INF/persistence.xml");
        
        if (persistenceFileUrl != null) {
            persistenceFilePath = persistenceFileUrl.getDataFile();
            if (persistenceFilePath != null) {
                persistenceFilePath = persistenceFilePath.replaceFirst("/[^/]*$", "/");
                imageFilePath = persistenceFilePath + "../../../resources/images/" + imageName;
                return imageFilePath; 
            }
            logger.warning("Null file path representation of the location of persistence.xml in the webapp root directory!"); 
        } else {
            logger.warning("Could not find the location of persistence.xml in the webapp root directory!");
        }

        return null;
    }
    */
    
    /**
     * 
     * @param fileId
     * @param formatTag
     * @param formatVersion
     * @param origin
     * @param isPublic
     * @param type
     * @param fileInputStream
     * @param contentDispositionHeader
     * @param formDataBodyPart
     * @return 
     *
     */
    @Path("datafile/{fileId}/auxiliary/{formatTag}/{formatVersion}")
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)

    public Response saveAuxiliaryFileWithVersion(@PathParam("fileId") Long fileId,
            @PathParam("formatTag") String formatTag,
            @PathParam("formatVersion") String formatVersion,
            @FormDataParam("origin") String origin,
            @FormDataParam("isPublic") boolean isPublic,
            @FormDataParam("type") String type,
            @FormDataParam("file") final FormDataBodyPart formDataBodyPart,
            @FormDataParam("file") InputStream fileInputStream
          
    ) {
        AuthenticatedUser authenticatedUser;
        try {
            authenticatedUser = findAuthenticatedUserOrDie();
        } catch (WrappedResponse ex) {
            return error(FORBIDDEN, "Authorized users only.");
        }

        DataFile dataFile = dataFileService.find(fileId);
        if (dataFile == null) {
            return error(BAD_REQUEST, "File not found based on id " + fileId + ".");
        }
        
         if (!permissionService.userOn(authenticatedUser, dataFile.getOwner()).has(Permission.EditDataset)) {
            return error(FORBIDDEN, "User not authorized to edit the dataset.");
        }

        MediaType mediaType = null;
        if (formDataBodyPart != null) {
            mediaType = formDataBodyPart.getMediaType();
        }

        AuxiliaryFile saved = auxiliaryFileService.processAuxiliaryFile(fileInputStream, dataFile, formatTag, formatVersion, origin, isPublic, type, mediaType);

        if (saved!=null) {
            return ok(json(saved));
        } else {
            return error(BAD_REQUEST, "Error saving Auxiliary file.");
        }
    }
  
    

    /**
     * 
     * @param fileId
     * @param formatTag
     * @param formatVersion
     * @param origin
     * @param isPublic
     * @param fileInputStream
     * @param contentDispositionHeader
     * @param formDataBodyPart
     * @return 
     */
    @Path("datafile/{fileId}/auxiliary/{formatTag}/{formatVersion}")
    @DELETE
    public Response deleteAuxiliaryFileWithVersion(@PathParam("fileId") Long fileId,
            @PathParam("formatTag") String formatTag,
            @PathParam("formatVersion") String formatVersion) {
        AuthenticatedUser authenticatedUser;
        try {
            authenticatedUser = findAuthenticatedUserOrDie();
        } catch (WrappedResponse ex) {
            return error(FORBIDDEN, "Authorized users only.");
        }

        DataFile dataFile = dataFileService.find(fileId);
        if (dataFile == null) {
            return error(BAD_REQUEST, "File not found based on id " + fileId + ".");
        }

        if (!permissionService.userOn(authenticatedUser, dataFile.getOwner()).has(Permission.EditDataset)) {
            return error(FORBIDDEN, "User not authorized to edit the dataset.");
        }

        try {
            auxiliaryFileService.deleteAuxiliaryFile(dataFile, formatTag, formatVersion);
        } catch (FileNotFoundException e) {
            throw new NotFoundException();
        } catch(IOException io) {
            throw new ServerErrorException("IO Exception trying remove auxiliary file", Response.Status.INTERNAL_SERVER_ERROR, io);
        }

        return ok("Auxiliary file deleted.");
    }

  
    
    /**
     * Allow (or disallow) access requests to Dataset
     *
     * @author sekmiller
     *
     * @param datasetToAllowAccessId
     * @param requestStr
     * @return
     */
    @PUT
    @Path("{id}/allowAccessRequest")
    public Response allowAccessRequest(@PathParam("id") String datasetToAllowAccessId, String requestStr) {

        DataverseRequest dataverseRequest = null;
        Dataset dataset;

        try {
            dataset = findDatasetOrDie(datasetToAllowAccessId);
        } catch (WrappedResponse ex) {
            List<String> args = Arrays.asList(datasetToAllowAccessId);
            return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.allowRequests.failure.noDataset", args));
        }

        boolean allowRequest = Boolean.valueOf(requestStr);

        try {
            dataverseRequest = createDataverseRequest(findUserOrDie());
        } catch (WrappedResponse wr) {
            List<String> args = Arrays.asList(wr.getLocalizedMessage());
            return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.fileAccess.failure.noUser", args));
        }

        dataset.getEditVersion().getTermsOfUseAndAccess().setFileAccessRequest(allowRequest);

        try {
            engineSvc.submit(new UpdateDatasetVersionCommand(dataset, dataverseRequest));
        } catch (CommandException ex) {
            List<String> args = Arrays.asList(dataset.getDisplayName(), ex.getLocalizedMessage());
            return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.fileAccess.failure.noSave", args));
        }

        String text = allowRequest ? BundleUtil.getStringFromBundle("access.api.allowRequests.allows") : BundleUtil.getStringFromBundle("access.api.allowRequests.disallows");
        List<String> args = Arrays.asList(dataset.getDisplayName(), text);
        return ok(BundleUtil.getStringFromBundle("access.api.allowRequests.success", args));
        
    }

    /**
     * Request Access to Restricted File
     *
     * @author sekmiller
     *
     * @param fileToRequestAccessId
     * @param apiToken
     * @param headers
     * @return
     */
    @PUT
    @Path("/datafile/{id}/requestAccess")
    public Response requestFileAccess(@PathParam("id") String fileToRequestAccessId, @Context HttpHeaders headers) {
        
        DataverseRequest dataverseRequest;
        DataFile dataFile;
        
        try {
            dataFile = findDataFileOrDie(fileToRequestAccessId);
        } catch (WrappedResponse ex) {
            List<String> args = Arrays.asList(fileToRequestAccessId);
            return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.requestAccess.fileNotFound", args));
        }

        if (!dataFile.getOwner().isFileAccessRequest()) {
            return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.requestAccess.requestsNotAccepted"));
        }

        AuthenticatedUser requestor;

        try {
            requestor = findAuthenticatedUserOrDie();
            dataverseRequest = createDataverseRequest(requestor);
        } catch (WrappedResponse wr) {
            List<String> args = Arrays.asList(wr.getLocalizedMessage());
            return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.fileAccess.failure.noUser", args));
        }

        if (isAccessAuthorized(dataFile, getRequestApiKey())) {
            return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.requestAccess.failure.invalidRequest"));
        }

        if (dataFile.getFileAccessRequesters().contains(requestor)) {
            return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.requestAccess.failure.requestExists"));
        }

        try {
            engineSvc.submit(new RequestAccessCommand(dataverseRequest, dataFile, true));
        } catch (CommandException ex) {
            List<String> args = Arrays.asList(dataFile.getDisplayName(), ex.getLocalizedMessage());
            return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.requestAccess.failure.commandError", args));
        }
        
        List<String> args = Arrays.asList(dataFile.getDisplayName());
        return ok(BundleUtil.getStringFromBundle("access.api.requestAccess.success.for.single.file", args));

    }

    /*
     * List Reqeusts to restricted file
     *
     * @author sekmiller
     *
     * @param fileToRequestAccessId
     * @param apiToken
     * @param headers
     * @return
     */
    @GET
    @Path("/datafile/{id}/listRequests")
    public Response listFileAccessRequests(@PathParam("id") String fileToRequestAccessId, @Context HttpHeaders headers) {

        DataverseRequest dataverseRequest;

        DataFile dataFile;
        try {
            dataFile = findDataFileOrDie(fileToRequestAccessId);
        } catch (WrappedResponse ex) {
            List<String> args = Arrays.asList(fileToRequestAccessId);
            return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.requestList.fileNotFound", args));
        }

        try {
            dataverseRequest = createDataverseRequest(findAuthenticatedUserOrDie());
        } catch (WrappedResponse wr) {
            List<String> args = Arrays.asList(wr.getLocalizedMessage());
            return error(UNAUTHORIZED, BundleUtil.getStringFromBundle("access.api.fileAccess.failure.noUser", args));
        }
        if (!(dataverseRequest.getAuthenticatedUser().isSuperuser() || permissionService.requestOn(dataverseRequest, dataFile).has(Permission.ManageFilePermissions))) {
            return error(FORBIDDEN, BundleUtil.getStringFromBundle("access.api.rejectAccess.failure.noPermissions"));
        }

        List<AuthenticatedUser> requesters = dataFile.getFileAccessRequesters();

        if (requesters == null || requesters.isEmpty()) {
            List<String> args = Arrays.asList(dataFile.getDisplayName());
            return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.requestList.noRequestsFound", args));
        }

        JsonArrayBuilder userArray = Json.createArrayBuilder();

        for (AuthenticatedUser au : requesters) {
            userArray.add(json(au));
        }

        return ok(userArray);

    }

    /**
     * Grant Access to Restricted File
     *
     * @author sekmiller
     *
     * @param fileToRequestAccessId
     * @param identifier
     * @param apiToken
     * @param headers
     * @return
     */
    @PUT
    @Path("/datafile/{id}/grantAccess/{identifier}")
    public Response grantFileAccess(@PathParam("id") String fileToRequestAccessId, @PathParam("identifier") String identifier, @Context HttpHeaders headers) {
        
        DataverseRequest dataverseRequest;
        DataFile dataFile;

        try {
            dataFile = findDataFileOrDie(fileToRequestAccessId);
        } catch (WrappedResponse ex) {
            List<String> args = Arrays.asList(fileToRequestAccessId);
            return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.requestAccess.fileNotFound", args));
        }

        RoleAssignee ra = roleAssigneeSvc.getRoleAssignee(identifier);

        if (ra == null) {
            List<String> args = Arrays.asList(identifier);
            return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.grantAccess.noAssigneeFound", args));
        }

        try {
            dataverseRequest = createDataverseRequest(findUserOrDie());
        } catch (WrappedResponse wr) {
            List<String> args = Arrays.asList(identifier);
            return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.fileAccess.failure.noUser", args));
        }

        DataverseRole fileDownloaderRole = roleService.findBuiltinRoleByAlias(DataverseRole.FILE_DOWNLOADER);

        try {
            engineSvc.submit(new AssignRoleCommand(ra, fileDownloaderRole, dataFile, dataverseRequest, null));
            if (dataFile.getFileAccessRequesters().remove(ra)) {
                dataFileService.save(dataFile);
            }

        } catch (CommandException ex) {
            List<String> args = Arrays.asList(dataFile.getDisplayName(), ex.getLocalizedMessage());
            return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.grantAccess.failure.commandError", args));
        }

        try {
            AuthenticatedUser au = (AuthenticatedUser) ra;
            userNotificationService.sendNotification(au, new Timestamp(new Date().getTime()), UserNotification.Type.GRANTFILEACCESS, dataFile.getOwner().getId());
        } catch (ClassCastException e) {
            //nothing to do here - can only send a notification to an authenticated user
        }

        List<String> args = Arrays.asList(dataFile.getDisplayName());
        return ok(BundleUtil.getStringFromBundle("access.api.grantAccess.success.for.single.file", args));

    }

    /**
     * Revoke Previously Granted Access to Restricted File
     *
     * @author sekmiller
     *
     * @param fileToRequestAccessId
     * @param identifier
     * @param apiToken
     * @param headers
     * @return
     */
    @DELETE
    @Path("/datafile/{id}/revokeAccess/{identifier}")
    public Response revokeFileAccess(@PathParam("id") String fileToRequestAccessId, @PathParam("identifier") String identifier, @Context HttpHeaders headers) {

        DataverseRequest dataverseRequest;
        DataFile dataFile;

        try {
            dataFile = findDataFileOrDie(fileToRequestAccessId);
        } catch (WrappedResponse ex) {
            List<String> args = Arrays.asList(fileToRequestAccessId);
            return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.requestAccess.fileNotFound", args));
        }

        try {
            dataverseRequest = createDataverseRequest(findUserOrDie());
        } catch (WrappedResponse wr) {
            List<String> args = Arrays.asList(wr.getLocalizedMessage());
            return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.fileAccess.failure.noUser", args));
        }

        if (identifier == null || identifier.equals("")) {
            return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.requestAccess.noKey"));
        }

        RoleAssignee ra = roleAssigneeSvc.getRoleAssignee(identifier);
        if (ra == null) {
            List<String> args = Arrays.asList(identifier);
            return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.grantAccess.noAssigneeFound", args));
        }

        DataverseRole fileDownloaderRole = roleService.findBuiltinRoleByAlias(DataverseRole.FILE_DOWNLOADER);
        TypedQuery<RoleAssignment> query = em.createNamedQuery(
                "RoleAssignment.listByAssigneeIdentifier_DefinitionPointId_RoleId",
                RoleAssignment.class);
        query.setParameter("assigneeIdentifier", ra.getIdentifier());
        query.setParameter("definitionPointId", dataFile.getId());
        query.setParameter("roleId", fileDownloaderRole.getId());
        List<RoleAssignment> roles = query.getResultList();

        if (roles == null || roles.isEmpty()) {
            List<String> args = Arrays.asList(identifier);
            return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.revokeAccess.noRoleFound", args));
        }

        try {
            for (RoleAssignment role : roles) {
                execCommand(new RevokeRoleCommand(role, dataverseRequest));
            }
        } catch (WrappedResponse wr) {
            return wr.getResponse();
        }

        List<String> args = Arrays.asList(ra.getIdentifier(), dataFile.getDisplayName());

        return ok(BundleUtil.getStringFromBundle("access.api.revokeAccess.success.for.single.file", args));

    }

    /**
     * Reject Access request to Restricted File
     *
     * @author sekmiller
     *
     * @param fileToRequestAccessId
     * @param identifier
     * @param apiToken
     * @param headers
     * @return
     */
    @PUT
    @Path("/datafile/{id}/rejectAccess/{identifier}")
    public Response rejectFileAccess(@PathParam("id") String fileToRequestAccessId, @PathParam("identifier") String identifier, @Context HttpHeaders headers) {

        DataverseRequest dataverseRequest;
        DataFile dataFile;

        try {
            dataFile = findDataFileOrDie(fileToRequestAccessId);
        } catch (WrappedResponse ex) {
            List<String> args = Arrays.asList(fileToRequestAccessId);
            return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.requestAccess.fileNotFound", args));
        }

        RoleAssignee ra = roleAssigneeSvc.getRoleAssignee(identifier);

        if (ra == null) {
            List<String> args = Arrays.asList(identifier);
            return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.grantAccess.noAssigneeFound", args));
        }

        try {
            dataverseRequest = createDataverseRequest(findUserOrDie());
        } catch (WrappedResponse wr) {
            List<String> args = Arrays.asList(identifier);
            return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.fileAccess.failure.noUser", args));
        }
        
        if (!(dataverseRequest.getAuthenticatedUser().isSuperuser() || permissionService.requestOn(dataverseRequest, dataFile).has(Permission.ManageFilePermissions))) {
            return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.rejectAccess.failure.noPermissions"));
        }

        if (dataFile.getFileAccessRequesters().contains(ra)) {
            dataFile.getFileAccessRequesters().remove(ra);
            dataFileService.save(dataFile);

            try {
                AuthenticatedUser au = (AuthenticatedUser) ra;
                userNotificationService.sendNotification(au, new Timestamp(new Date().getTime()), UserNotification.Type.REJECTFILEACCESS, dataFile.getOwner().getId());
            } catch (ClassCastException e) {
                //nothing to do here - can only send a notification to an authenticated user
            }

            List<String> args = Arrays.asList(dataFile.getDisplayName());
            return ok(BundleUtil.getStringFromBundle("access.api.rejectAccess.success.for.single.file", args));

        } else {
            List<String> args = Arrays.asList(dataFile.getDisplayName(), ra.getDisplayInfo().getTitle());
            return error(BAD_REQUEST, BundleUtil.getStringFromBundle("access.api.fileAccess.rejectFailure.noRequest", args));
        }
    }
    
    // checkAuthorization is a convenience method; it calls the boolean method
    // isAccessAuthorized(), the actual workhorse, tand throws a 403 exception if not.
    
    private void checkAuthorization(DataFile df, String apiToken) throws WebApplicationException {

        if (!isAccessAuthorized(df, apiToken)) {
            throw new ForbiddenException();
        }        
    }
    

    private boolean isAccessAuthorized(DataFile df, String apiToken) {
    // First, check if the file belongs to a released Dataset version: 
        
        boolean published = false; 
        
        //True if there's an embargo that hasn't yet expired
        //In this state, we block access as though the file is restricted (even if it is not restricted)
        boolean embargoed = FileUtil.isActivelyEmbargoed(df);
        
        
        /*
        SEK 7/26/2018 for 3661 relying on the version state of the dataset versions
            to which this file is attached check to see if at least one is  RELEASED
        */
        for (FileMetadata fm : df.getFileMetadatas()){
            if(fm.getDatasetVersion().isPublished()){
                 published = true; 
                 break;
            }
        }

        // TODO: (IMPORTANT!)
        // Business logic like this should NOT be maintained in individual 
        // application fragments. 
        // At the moment it is duplicated here, and inside the Dataset page.
        // There are also stubs for file-level permission lookups and caching
        // inside Gustavo's view-scoped PermissionsWrapper. 
        // All this logic needs to be moved to the PermissionServiceBean where it will be 
        // centrally maintained; with the PermissionsWrapper providing 
        // efficient cached lookups to the pages (that often need to make 
        // repeated lookups on the same files). Care will need to be taken 
        // to preserve the slight differences in logic utilized by the page and 
        // this Access call (the page checks the restriction flag on the
        // filemetadata, not the datafile - as it needs to reflect the permission 
        // status of the file in the version history).  
        // I will open a 4.[34] ticket. 
        //
        // -- L.A. 4.2.1
        
        
        // We don't need to check permissions on files that are 
        // from released Dataset versions and not restricted: 
        
        boolean restricted = false; 
        
        if (df.isRestricted()) {
            restricted = true;
        } else {
        
        // There is also a special case of a restricted file that only exists 
        // in a draft version (i.e., a new file, that hasn't been published yet).
        // Such files must be considered restricted, for access purposes. I.e., 
        // users with no download access to this particular file, but with the 
        // permission to ViewUnpublished on the dataset, should NOT be allowed 
        // to download it. 
        // Up until 4.2.1 restricting unpublished files was only restricting them 
        // in their Draft version fileMetadata, but not in the DataFile object. 
        // (this is what we still want to do for published files; restricting those
        // only restricts them in the new draft FileMetadata; until it becomes the 
        // published version, the restriction flag on the DataFile is what governs
        // the download authorization).
        
            //if (!published && df.getOwner().getVersions().size() == 1 && df.getOwner().getLatestVersion().isDraft()) {
            // !df.isReleased() really means just this: new file, only exists in a Draft version!
            if (!df.isReleased()) {
                if (df.getFileMetadata().isRestricted()) {
                    restricted = true;
                }
            }
        }
        
        if (!restricted && !embargoed) {
            // And if they are not published, they can still be downloaded, if the user
            // has the permission to view unpublished versions! (this case will 
            // be handled below)
            if (published) { 
                return true;
            }
        }
        
        User user = null;
        
        /** 
         * Authentication/authorization:
         * 
         * note that the fragment below - that retrieves the session object
         * and tries to find the user associated with the session - is really
         * for logging/debugging purposes only; for practical purposes, it 
         * would be enough to just call "permissionService.on(df).has(Permission.DownloadFile)"
         * and the method does just that, tries to authorize for the user in 
         * the current session (or guest user, if no session user is available):
         */
        
        if (session != null) {
            if (session.getUser() != null) {
                if (session.getUser().isAuthenticated()) {
                    user = session.getUser();
                } else {
                    logger.fine("User associated with the session is not an authenticated user.");
                    if (session.getUser() instanceof PrivateUrlUser) {
                        logger.fine("User associated with the session is a PrivateUrlUser user.");
                        user = session.getUser();
                    }
                    if (session.getUser() instanceof GuestUser) {
                        logger.fine("User associated with the session is indeed a guest user.");
                    }
                }
            } else {
                logger.fine("No user associated with the session.");
            }
        } else {
            logger.fine("Session is null.");
        } 
        
        User apiTokenUser = null;
        
        if ((apiToken != null)&&(apiToken.length()!=64)) {
            // We'll also try to obtain the user information from the API token, 
            // if supplied: 
        
            try {
                logger.fine("calling apiTokenUser = findUserOrDie()...");
                apiTokenUser = findUserOrDie();
            } catch (WrappedResponse wr) {
                logger.log(Level.FINE, "Message from findUserOrDie(): {0}", wr.getMessage());
            }
            
            if (apiTokenUser == null) {
                logger.warning("API token-based auth: Unable to find a user with the API token provided.");
            }
        }
        
        // OK, let's revisit the case of non-restricted files, this time in        
        // an unpublished version:         
        // (if (published) was already addressed above)
        
        if (!restricted && !embargoed) {
            // If the file is not published, they can still download the file, if the user
            // has the permission to view unpublished versions:
            
            if ( user != null ) {
                // used in JSF context
                if (permissionService.requestOn(dvRequestService.getDataverseRequest(), df.getOwner()).has(Permission.ViewUnpublishedDataset)) {
                    // it's not unthinkable, that a null user (i.e., guest user) could be given
                    // the ViewUnpublished permission!
                    logger.log(Level.FINE, "Session-based auth: user {0} has access rights on the non-restricted, unpublished datafile.", user.getIdentifier());
                    return true;
                }
            }

            if (apiTokenUser != null) {
                // used in an API context
                if (permissionService.requestOn( createDataverseRequest(apiTokenUser), df.getOwner()).has(Permission.ViewUnpublishedDataset)) {
                    logger.log(Level.FINE, "Token-based auth: user {0} has access rights on the non-restricted, unpublished datafile.", apiTokenUser.getIdentifier());
                    return true;
                }
            }

            // last option - guest user in either contexts
            // Guset user is impled by the code above.
            if ( permissionService.requestOn(dvRequestService.getDataverseRequest(), df.getOwner()).has(Permission.ViewUnpublishedDataset) ) {
                return true;
            }
                    
        } else {
            
            // OK, this is a restricted and/or embargoed file. 
            
            boolean hasAccessToRestrictedBySession = false; 
            boolean hasAccessToRestrictedByToken = false; 
            
            if (permissionService.on(df).has(Permission.DownloadFile)) {
            // Note: PermissionServiceBean.on(Datafile df) will obtain the 
            // User from the Session object, just like in the code fragment 
            // above. That's why it's not passed along as an argument.
                hasAccessToRestrictedBySession = true; 
            } else if (apiTokenUser != null && permissionService.requestOn(createDataverseRequest(apiTokenUser), df).has(Permission.DownloadFile)) {
                hasAccessToRestrictedByToken = true; 
            }
            
            if (hasAccessToRestrictedBySession || hasAccessToRestrictedByToken) {
                if (published) {
                    if (hasAccessToRestrictedBySession) {
                        if (user != null) {
                            logger.log(Level.FINE, "Session-based auth: user {0} is granted access to the restricted, published datafile.", user.getIdentifier());
                        } else {
                            logger.fine("Session-based auth: guest user is granted access to the restricted, published datafile.");
                        }
                    } else {
                        logger.log(Level.FINE, "Token-based auth: user {0} is granted access to the restricted, published datafile.", apiTokenUser.getIdentifier());
                    }
                    return true;
                } else {
                    // if the file is NOT published, we will let them download the 
                    // file ONLY if they also have the permission to view 
                    // unpublished versions:
                    // Note that the code below does not allow a case where it is the
                    // session user that has the permission on the file, and the API token 
                    // user with the ViewUnpublished permission, or vice versa!
                    if (hasAccessToRestrictedBySession) {
                        if (permissionService.on(df.getOwner()).has(Permission.ViewUnpublishedDataset)) {
                            if (user != null) {
                                logger.log(Level.FINE, "Session-based auth: user {0} is granted access to the restricted, unpublished datafile.", user.getIdentifier());
                            } else {
                                logger.fine("Session-based auth: guest user is granted access to the restricted, unpublished datafile.");
                            }
                            return true;
                        } 
                    } else {
                        if (apiTokenUser != null && permissionService.requestOn(createDataverseRequest(apiTokenUser), df.getOwner()).has(Permission.ViewUnpublishedDataset)) {
                            logger.log(Level.FINE, "Token-based auth: user {0} is granted access to the restricted, unpublished datafile.", apiTokenUser.getIdentifier());
                            return true;
                        }
                    }
                }
            }
        } 

        
        if ((apiToken != null)) {
            // Will try to obtain the user information from the API token, 
            // if supplied: 
        
            try {
                logger.fine("calling user = findUserOrDie()...");
                user = findUserOrDie();
            } catch (WrappedResponse wr) {
                logger.log(Level.FINE, "Message from findUserOrDie(): {0}", wr.getMessage());
            }
            
            if (user == null) {
                logger.warning("API token-based auth: Unable to find a user with the API token provided.");
                return false;
            } 
            
            
            //Doesn't this ~duplicate logic above - if so, if there's a way to get here, I think it still works for embargoed files (you only get access if you have download permissions, and, if not published, also view unpublished)
            if (permissionService.requestOn(createDataverseRequest(user), df).has(Permission.DownloadFile)) {
                if (published) {
                    logger.log(Level.FINE, "API token-based auth: User {0} has rights to access the datafile.", user.getIdentifier());
                    //Same case as line 1809 (and part of 1708 though when published you don't need the DownloadFile permission)
                    return true; 
                } else {
                    // if the file is NOT published, we will let them download the 
                    // file ONLY if they also have the permission to view 
                    // unpublished versions:
                    if (permissionService.requestOn(createDataverseRequest(user), df.getOwner()).has(Permission.ViewUnpublishedDataset)) {
                        logger.log(Level.FINE, "API token-based auth: User {0} has rights to access the (unpublished) datafile.", user.getIdentifier());
                        //Same case as line 1843?
                        return true;
                    } else {
                        logger.log(Level.FINE, "API token-based auth: User {0} is not authorized to access the (unpublished) datafile.", user.getIdentifier());
                    }
                }
            } else {
                logger.log(Level.FINE, "API token-based auth: User {0} is not authorized to access the datafile.", user.getIdentifier());
            }
            
            return false;
        } 
        
        if (user != null) {
            logger.log(Level.FINE, "Session-based auth: user {0} has NO access rights on the requested datafile.", user.getIdentifier());
        } 
        
        if (apiTokenUser != null) {
            logger.log(Level.FINE, "Token-based auth: user {0} has NO access rights on the requested datafile.", apiTokenUser.getIdentifier());
        } 
        
        if (user == null && apiTokenUser == null) {
            logger.fine("Unauthenticated access: No guest access to the datafile.");
        }
        
        return false; 
    }   
    

        
    private User findAPITokenUser(String apiToken) {
        User apiTokenUser = null;

        if ((apiToken != null) && (apiToken.length() != 64)) {
            // We'll also try to obtain the user information from the API token, 
            // if supplied: 

            try {
                logger.fine("calling apiTokenUser = findUserOrDie()...");
                apiTokenUser = findUserOrDie();
                return apiTokenUser;
            } catch (WrappedResponse wr) {
                logger.log(Level.FINE, "Message from findUserOrDie(): {0}", wr.getMessage());
                return null;
            }

        }
        return apiTokenUser;
    }

    private URI handleCustomZipDownload(String customZipServiceUrl, String fileIds, String apiToken, User apiTokenUser, UriInfo uriInfo, HttpHeaders headers, boolean donotwriteGBResponse, boolean orig) throws WebApplicationException {
        
        String zipServiceKey = null; 
        Timestamp timestamp = null; 
        
        String fileIdParams[] = fileIds.split(",");
        int validIdCount = 0; 
        int validFileCount = 0;
        int downloadAuthCount = 0; 

        if (fileIdParams == null || fileIdParams.length == 0) {
            throw new BadRequestException();
        }
        
        for (int i = 0; i < fileIdParams.length; i++) {
            Long fileId = null;
            try {
                fileId = new Long(fileIdParams[i]);
                validIdCount++;
            } catch (NumberFormatException nfe) {
                fileId = null;
            }
            if (fileId != null) {
                DataFile file = dataFileService.find(fileId);
                if (file != null) {
                    validFileCount++;
                    if (isAccessAuthorized(file, apiToken)) {
                        logger.fine("adding datafile (id=" + file.getId() + ") to the download list of the ZippedDownloadInstance.");
                        if (donotwriteGBResponse != true && file.isReleased()) {
                            GuestbookResponse gbr = guestbookResponseService.initAPIGuestbookResponse(file.getOwner(), file, session, apiTokenUser);
                            guestbookResponseService.save(gbr);
                            MakeDataCountEntry entry = new MakeDataCountEntry(uriInfo, headers, dvRequestService, file);
                            mdcLogService.logEntry(entry);
                        }

                        if (zipServiceKey == null) {
                            zipServiceKey = fileDownloadService.generateServiceKey();
                        }
                        if (timestamp == null) {
                            timestamp = new Timestamp(new Date().getTime());
                        }

                        fileDownloadService.addFileToCustomZipJob(zipServiceKey, file, timestamp, true);
                        downloadAuthCount++;
                    }
                }
            }
        }

        if (validIdCount == 0) {
            throw new BadRequestException();
        }
        
        if (validFileCount == 0) {
            // no supplied id translated into an existing DataFile
            throw new NotFoundException();
        }
        
        if (downloadAuthCount == 0) {
            // none of the DataFiles were authorized for download
            throw new ForbiddenException();
        }
        
        URI redirectUri = null;
        try {
            redirectUri = new URI(customZipServiceUrl + "?" + zipServiceKey);
        } catch (URISyntaxException use) {
            throw new BadRequestException(); 
        }
        return redirectUri;
    }   
}
