package org.exoplatform.salesforce.integ.rest;

import com.force.api.ForceApi;
import com.force.api.QueryResult;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.exoplatform.commons.utils.MimeTypeResolver;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.salesforce.domain.PostActivitiesEntity;
import org.exoplatform.salesforce.integ.component.activity.UISalesforceActivity;
import org.exoplatform.salesforce.integ.component.activity.UISalesforceActivityBuilder;
import org.exoplatform.salesforce.integ.connector.entity.AggregateResult;
import org.exoplatform.salesforce.integ.connector.entity.ContentDocumentLink;
import org.exoplatform.salesforce.integ.connector.entity.ContentVersion;
import org.exoplatform.salesforce.integ.connector.entity.Opportunity;
import org.exoplatform.salesforce.integ.connector.servlet.OAuthServlet;
import org.exoplatform.salesforce.integ.connector.storage.api.ConfigurationInfoStorage;
import org.exoplatform.salesforce.integ.util.Utils;
import org.exoplatform.salesforce.service.PostActivitiesService;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.activity.model.ExoSocialActivityImpl;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.SpaceUtils;
import org.exoplatform.social.core.space.impl.DefaultSpaceApplicationHandler;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.plugin.doc.UIDocActivity;
import org.exoplatform.social.service.rest.RestChecker;
import org.exoplatform.social.service.rest.Util;
import org.exoplatform.social.webui.activity.UIDefaultActivity;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.json.JSONObject;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.*;

@Path("/salesforce")

public class OppRestService implements ResourceContainer { 
	    private static final Log LOG = ExoLogger.getLogger(OppRestService.class);
	    OrganizationService orgService = (OrganizationService) PortalContainer.getInstance().getComponentInstanceOfType(OrganizationService.class);
	    RepositoryService repositoryService = (RepositoryService) PortalContainer.getInstance().getComponentInstanceOfType(RepositoryService.class);
		//private DateTime CloseDate;
	    private static final String portalContainerName = "portal";
	    private static final String[] SUPPORTED_FORMATS = new String[]{"json"};


	    @SuppressWarnings("deprecation")
	@GET
	@Path("create/{oppID}")
	public Response createOpp(
			@Context HttpServletRequest request,
			@PathParam("oppID") String oppID) throws Exception {

		String oppName = null;
		String ammount = null;
		String description = null;
		String isClosed = null;
		String stageName = null;
		String closeDate = null;
		String permId=null;
	    	Identity sourceIdentity = Util.getAuthenticatedUserIdentity(portalContainerName);
	    	 SpaceService spaceService = Util.getSpaceService(portalContainerName);
			IdentityManager identityManager = Util.getIdentityManager(portalContainerName);
	    	 Cookie[] cookies = request.getCookies();
	            String accesstoken=null;
	            String instance_url=null;
	    	 ActivityManager activityManager = (ActivityManager) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(ActivityManager.class);
	    	 ExoSocialActivity activity = new ExoSocialActivityImpl();
            if (sourceIdentity == null)
				return Response.status(Response.Status.UNAUTHORIZED).build();
			for (int i = 0; i < cookies.length; i++) {
				Cookie cookie1 = cookies[i];
				if (cookie1.getName().equals("tk_ck_")) {

					accesstoken = cookie1.getValue();
				}

				if (cookie1.getName().equals("inst_ck_")) {

					instance_url = cookie1.getValue();
				}
			}
			
			if(accesstoken!=null&&instance_url!=null){
				
				ForceApi api = OAuthServlet.initApiFromCookies(accesstoken, instance_url);
				Opportunity opp = null;
				try {
					opp = api.getSObject("Opportunity", oppID).as(Opportunity.class);
				} catch (Exception e) {
					return Response.serverError().status(404).build();
				}
				oppName = opp.getName();
				if(spaceService.getSpaceByPrettyName(SpaceUtils.cleanString(oppName)) != null) {
					return Response.seeOther(URI.create(Util.getBaseUrl() + "/portal")).build();
				}
				 String qq="SELECT COUNT(Id) FROM OpportunityFeed where ParentId="+ "\'"+oppID+"\'";
					QueryResult<AggregateResult> totalFeed=api.query(qq, AggregateResult.class);
					//count the total feed at create deal room time on the opp 
					String TotalOppFeed=totalFeed.getRecords().get(0).getexpr0();
					//permId is the permanent id of the opportunity will be used to check update 
				    permId=opp.getId();
					ammount = (opp.getAmount()!=null) ?opp.getAmount().toString():"Not defined";
					description = (opp.getDescription()!=null)? opp.getDescription():"Not defined";
					isClosed = (opp.getIsClosed()!=null)? opp.getIsClosed().toString():"Not defined";
					closeDate= (opp.getCloseDate()!=null)? opp.getCloseDate().toString():"Not defined";
					if(closeDate!=null){
					DateTimeFormatter dateFormat = DateTimeFormat
							.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
					SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
					Date d = simpleDateFormat.parse(closeDate);
					closeDate = simpleDateFormat.format(d);
					}
					else
					{
					closeDate ="Not defined";
					}
					stageName = (opp.getStageName()!=null)?opp.getStageName().value().toString():"Not defined";
					
					
				
		

       	 String owner = sourceIdentity.getRemoteId();
            Space project_ = new Space();
            project_.setDisplayName(oppName);
            project_.setPrettyName(oppName);
            project_.setRegistration(Space.OPEN);
            if((description).equals("Not defined"))
			project_.setDescription(oppName);
		    else
			project_.setDescription(description);
            project_.setType(DefaultSpaceApplicationHandler.NAME);
            project_.setVisibility(Space.PUBLIC);
            project_.setRegistration(Space.VALIDATION);
            project_.setPriority(Space.INTERMEDIATE_PRIORITY);
            Space s= spaceService.createSpace(project_, owner);
            createspaceFolder(project_,spaceService);

			spaceService.addMember(s,"salesforce");

            if (s != null) {
            	 activity.setUserId(sourceIdentity.getId());
                Identity spaceIdentity = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, s.getPrettyName(), false);
                Profile oppProfile = spaceIdentity.getProfile();
                oppProfile.setProperty("oppID", permId);
                oppProfile.setProperty("opportunityName", oppName);
                oppProfile.setProperty("description", description);
                oppProfile.setProperty("CloseDate", closeDate);
                oppProfile.setProperty("ammount", ammount);
                oppProfile.setProperty("stageName", stageName);
                //store the total feed of the opportunity as property of space profile
                //any change on total means need update in chatter that need to be push to eXo
                oppProfile.setProperty("nbOppFeed", TotalOppFeed);

				String oppUrl = instance_url + "/" + oppID;
				String oppLink = "<a href=\"" + oppUrl + "\">" + oppName + "</a>";
				activity.setTitle(oppLink);
				activity.setType(UISalesforceActivity.ACTIVITY_TYPE);
				Map<String, String> templateParams = new HashMap<String, String>();
				templateParams.put(UISalesforceActivityBuilder.DESCRIPTION_PARAM,description);
				templateParams.put(UISalesforceActivityBuilder.STAGE_PARAM,stageName);
				templateParams.put(UISalesforceActivityBuilder.CLOSEDATE_PARAM,closeDate);
				templateParams.put(UISalesforceActivityBuilder.AMOUNT_PARAM,ammount);
				activity.setTemplateParams(templateParams);
                activity.setBody("The opportunity: " +oppName +" descp: "+description+" has a stage :stageName" );
                activityManager.saveActivityNoReturn(spaceIdentity, activity);

				oppProfile.setProperty("firstsalesforceactivity",activity.getId());
				Util.getIdentityManager(portalContainerName).saveProfile(oppProfile);

            } 
		}
       
            return Response.seeOther(URI.create(Util.getBaseUrl() + "/portal")).build();
	    }

		private void createspaceFolder(Space s, SpaceService spaceService) {
			 try {
				Session session = repositoryService.getCurrentRepository().getSystemSession("collaboration");
				Node rootNode = session.getRootNode();
				Node groupFolder = rootNode.getNode("Groups" + s.getGroupId() + "/Documents");
				Node salesForceFolder = groupFolder.addNode("salesForceDocuments", "nt:folder");
				salesForceFolder.getSession().save();
			} catch (RepositoryException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}

		@GET
		@Path("addupdatecomment/{oppName}")
		public Response addUpdateComment(
				@Context HttpServletRequest request,
				@PathParam("oppName") String oppName,
				@QueryParam("newName") String newName,
				@QueryParam("oldstageName") String oldstageName,
				@QueryParam("newstageName") String newstageName,
				@QueryParam("oldamount") String oldamount,
				@QueryParam("newamount") String newamount,
				@QueryParam("oldclosedate") String oldclosedate,
				@QueryParam("newclosedate") String newclosedate,
				@QueryParam("olddescription") String olddeschhhription,
				@QueryParam("newdescription") String newdescription) throws Exception {
			MediaType mediaType = RestChecker.checkSupportedFormat("json", SUPPORTED_FORMATS);
			SpaceService spaceService = (SpaceService) PortalContainer.getInstance().getComponentInstanceOfType(SpaceService.class);
			Space space = spaceService.getSpaceByDisplayName(oppName);
			if(space == null) {
				return Response.ok("Opportunity Not Found", mediaType).build();
			}
			if(oppName != null && newName != null && !oppName.equals(newName)) {
				createcomment(space.getPrettyName(), "Name", oppName, newName);
				//TODO Rename Space after renaming Opportunity
				/*
				UserNode renamedNode = renamePageNode(newName, space);
				spaceService.renameSpace(space, newName);
				// rename group label
				OrganizationService organizationService = (OrganizationService) ExoContainerContext.getCurrentContainer().
						getComponentInstanceOfType(OrganizationService.class);
				GroupHandler groupHandler = organizationService.getGroupHandler();
				Group group = groupHandler.findGroupById(space.getGroupId());
				group.setLabel(space.getDisplayName());
				groupHandler.saveGroup(group, true);
				PortalRequestContext prContext = org.exoplatform.portal.webui.util.Util.getPortalRequestContext();
				prContext.createURL(NodeURL.TYPE).setNode(renamedNode);
				*/
			}
			if(oldstageName != null && newstageName != null && !oldstageName.equals(newstageName)) {
				createcomment(space.getPrettyName(), "Stage Name", oldstageName, newstageName);
			}
			if(oldamount != null && newamount != null && !oldamount.equals(newamount)) {
				createcomment(space.getPrettyName(), "Amount", oldamount, newamount);
			}
			if(oldclosedate != null && newclosedate != null && !oldclosedate.equals(newclosedate)) {
				createcomment(space.getPrettyName(), "Close Date", oldclosedate, newclosedate);
			}
			return Response.ok("Comment Added", mediaType).build();
		}


		@GET
		@Path("chatterpost/{oppID}")
		public Response chatterNewPost(
				@Context HttpServletRequest request,
				@PathParam("oppID") String oppID,
				@QueryParam("oppName") String oppName,
				@QueryParam("poster") String poster,
				@QueryParam("postType") String postType,
				@QueryParam("postId") String postId,
				@QueryParam("textPost") String textPost,
				@QueryParam("mentionned") String mentionned,
				@QueryParam("contentPost") String contentPost,
				@QueryParam("contentPostText") String contentPostText,
				@QueryParam("postedlink") String postedlink) throws Exception {
			MediaType mediaType = RestChecker.checkSupportedFormat("json", SUPPORTED_FORMATS);
			String[] supportedType = new String[] {"TextPost","LinkPost"};
			oppName=URLDecoder.decode(oppName, "UTF-8");
			poster=URLDecoder.decode(poster, "UTF-8");
			textPost =(textPost!=null)? URLDecoder.decode(textPost, "UTF-8"):null;
			contentPost=(contentPost!=null)? URLDecoder.decode(contentPost, "UTF-8"):null;
			mentionned=(mentionned!=null)? URLDecoder.decode(mentionned, "UTF-8"):null;
			SpaceService spaceService = (SpaceService) PortalContainer.getInstance().getComponentInstanceOfType(SpaceService.class);
			ActivityManager activityManager = (ActivityManager) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(ActivityManager.class);
			IdentityManager identityManager = Util.getIdentityManager(portalContainerName);
			Space space = spaceService.getSpaceByDisplayName(URLDecoder.decode(oppName, "UTF-8"));
			if (space == null) {
				return Response.status(Response.Status.NOT_FOUND).entity("Opportunity not found").build();
			}
			
			if (postType==null||!Arrays.asList(supportedType).contains(postType)) {
				return Response.status(Response.Status.OK).entity("").build();
			}

			Identity spaceIdentity = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), false);
			Profile oppProfile = spaceIdentity.getProfile();
			Identity salesforceIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "salesforce", false);
			ExoSocialActivity activity = new ExoSocialActivityImpl();
			// activity.setTitle(URLDecoder.decode(oppName, "UTF-8"));
			 activity.setUserId(salesforceIdentity.getId());
             //activity.setType("");

			String activitybody="<a href=\"#\">"+poster+"</a>";
			if(postType.equals("TextPost")){
				if(mentionned!=null){
					String[] mentionnedList = mentionned.split(",");
					for(int i=0 ; i<mentionnedList.length;i++){
						textPost =StringUtils.replace(textPost, "@"+mentionnedList[i], "<a href=\"#\">"+"@"+mentionnedList[i]+"</a>",1);
						//StringUtils.replace(text, searchString, replacement, max)e
						//StringUtils.replaceChars(str, searchChar, replaceChar)
						System.out.println(mentionnedList[i]);
						
					}
				
				//textPost=StringUtils.substringAfter("@"+mentionned, textpost);
				//textPost= StringUtils.substringAfter(textPost, "@"+mentionned);
				//activitybody+=textPost;
				
				
				}
				activitybody+=" posted new message: "+textPost;
			}
			/*else if(postType.equals("ContentPost")){
				
				activitybody+=" posted new file : "+"<a href=\"#\">"+contentPost+"</a>" +" " +contentPostText;
			}
			*/
			else if(postType.equals("LinkPost")){
				
				activitybody+=" posted new link : "+postedlink;
			}
			
			activity.setTitle(activitybody);
             activityManager.saveActivityNoReturn(spaceIdentity, activity);
           
			try {
				PostActivitiesService postActivitiesService = (PostActivitiesService) PortalContainer.getInstance().getComponentInstanceOfType(PostActivitiesService.class);
				postActivitiesService.createEntity(new PostActivitiesEntity(activity.getId(), postId));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				LOG.error(e.getMessage(), e);
				 return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("An internal error has occured").build();
			}
             return Response.ok("post created", mediaType).build();

		}
		
		@GET
		@Path("chattercomments/{oppID}")
		public Response chatterNewComment(
				@Context HttpServletRequest request,
				@PathParam("oppID") String oppID,
				@QueryParam("oppName") String oppName,
				@QueryParam("postID") String postID,
				@QueryParam("poster") String poster,
				@QueryParam("commentPost") String commentPost,
				@QueryParam("mentionned") String mentionned) throws Exception {
			MediaType mediaType = RestChecker.checkSupportedFormat("json", SUPPORTED_FORMATS);
			oppName=URLDecoder.decode(oppName, "UTF-8");
			poster=URLDecoder.decode(poster, "UTF-8");
			commentPost =(commentPost!=null)? URLDecoder.decode(commentPost, "UTF-8"):null;
			mentionned=(mentionned!=null)? URLDecoder.decode(mentionned, "UTF-8"):null;
			SpaceService spaceService = (SpaceService) PortalContainer.getInstance().getComponentInstanceOfType(SpaceService.class);
			ActivityManager activityManager = (ActivityManager) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(ActivityManager.class);
			IdentityManager identityManager = Util.getIdentityManager(portalContainerName);
			Space space = spaceService.getSpaceByDisplayName(URLDecoder.decode(oppName, "UTF-8"));
			if (space == null) {
				return Response.status(Response.Status.NOT_FOUND).entity("Opportunity not found").build();
			}
			
         
			Identity spaceIdentity = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), false);
			Profile oppProfile = spaceIdentity.getProfile();
			Identity salesforceIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "salesforce", false);
			PostActivitiesService postActivitiesService = (PostActivitiesService) PortalContainer.getInstance().getComponentInstanceOfType(PostActivitiesService.class);
			PostActivitiesEntity chatterPost=postActivitiesService.findEntityByPostId(postID);
			if(chatterPost==null){
				return Response.status(Response.Status.NOT_FOUND).entity("no related post found").build();
			}
			//chatterPost.getPostActivityId()
			
			ExoSocialActivity activityPost = activityManager.getActivity(chatterPost.getActivityId());
			if(activityPost==null){
				return Response.status(Response.Status.NOT_FOUND).entity("no related post found").build();
			}
			// activity.setUserId(salesforceIdentity.getId());
             //activity.setType("");

			String activitybody="<a href=\"#\">"+poster+"</a>";
			//if(postType.equals("TextPost")){
				if(mentionned!=null){
					String[] mentionnedList = mentionned.split(",");
					for(int i=0 ; i<mentionnedList.length;i++){
						commentPost =StringUtils.replace(commentPost, "@"+mentionnedList[i], "<a href=\"#\">"+"@"+mentionnedList[i]+"</a>",1);
						//StringUtils.replace(text, searchString, replacement, max)e
						//StringUtils.replaceChars(str, searchChar, replaceChar)
						System.out.println(mentionnedList[i]);
						
					}
				
				//textPost=StringUtils.substringAfter("@"+mentionned, textpost);
				//textPost= StringUtils.substringAfter(textPost, "@"+mentionned);
				//activitybody+=textPost;
				
				
				}
				activitybody+=" posted new comment: "+commentPost;
			//}
			
				ExoSocialActivity newcomment = new ExoSocialActivityImpl();
				newcomment.setType(UIDefaultActivity.ACTIVITY_TYPE);
				newcomment.setUserId(salesforceIdentity.getId());
				newcomment.setTitle(activitybody);
				newcomment.setBody(activitybody);
				activityManager.saveComment(activityPost,newcomment);
             return Response.ok("comment created", mediaType).build();

		}

	private void createcomment(String spaceName, String fieldName, String oldValue, String newValue) {
		ActivityManager activityManager = (ActivityManager) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(ActivityManager.class);
		IdentityManager identityManager = Util.getIdentityManager(portalContainerName);
		Identity spaceIdentity = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, spaceName, false);
		Profile oppProfile = spaceIdentity.getProfile();
		Identity salesforceIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "salesforce", false);
		ExoSocialActivity firstactivity = activityManager.getActivity(oppProfile.getProperty("firstsalesforceactivity").toString());
		Map<String, String> templateParams = firstactivity.getTemplateParams();
		boolean changed = true;
		switch (fieldName) {
			case "Name" :
				firstactivity.setTitle(newValue);
				break;
			case "Description" :
				if(templateParams.containsKey(UISalesforceActivityBuilder.DESCRIPTION_PARAM)) {
					templateParams.put(UISalesforceActivityBuilder.DESCRIPTION_PARAM, newValue);
					oppProfile.setProperty("description", newValue);
				}
				break;
			case "Stage Name" :
				if(templateParams.containsKey(UISalesforceActivityBuilder.STAGE_PARAM)) {
					templateParams.put(UISalesforceActivityBuilder.STAGE_PARAM, newValue);
					oppProfile.setProperty("stageName", newValue);
				}
				break;
			case "Amount" :
				if(templateParams.containsKey(UISalesforceActivityBuilder.AMOUNT_PARAM)) {
					templateParams.put(UISalesforceActivityBuilder.AMOUNT_PARAM, newValue);
					oppProfile.setProperty("ammount", newValue);
				}
				break;
			case "Close Date" :
				if(templateParams.containsKey(UISalesforceActivityBuilder.CLOSEDATE_PARAM)) {
					templateParams.put(UISalesforceActivityBuilder.CLOSEDATE_PARAM, newValue);
					oppProfile.setProperty("CloseDate", newValue);
				}
				break;
			default:
				changed = false;
				break;
		}

		try {
			if(changed) {
				firstactivity.setTemplateParams(templateParams);
				activityManager.updateActivity(firstactivity);
				identityManager.updateProfile(oppProfile);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		ExoSocialActivity newcomment = new ExoSocialActivityImpl();
		newcomment.setType(UIDefaultActivity.ACTIVITY_TYPE);
		newcomment.setUserId(salesforceIdentity.getId());
		newcomment.setTitle(fieldName+" changed from "+oldValue+" to "+newValue);
		newcomment.setBody(fieldName+" changed from "+oldValue+" to "+newValue);
		activityManager.saveComment(firstactivity,newcomment);
	}

	    @POST
	    @Path("config")
	    @Consumes({MediaType.APPLICATION_JSON})
	    public Response createConfig(@Context HttpServletRequest request,@Context HttpServletResponse response,@Context UriInfo uriInfo,
	                                 Map<String,String> conf) throws Exception {

	        Identity sourceIdentity = Util.getAuthenticatedUserIdentity(portalContainerName);
	        MediaType mediaType = RestChecker.checkSupportedFormat("json", SUPPORTED_FORMATS);
	        ConfigurationInfoStorage configurationInfoStorage = (ConfigurationInfoStorage) PortalContainer.getInstance().getComponentInstanceOfType(ConfigurationInfoStorage.class);
	        try {
	            if(sourceIdentity == null) {
	                return Response.status(Response.Status.UNAUTHORIZED).build();
	            }
	            String clientId=conf.get("clientId");
	            String clientSecret =conf.get("clientSecret");
	            String redirectUri=conf.get("redirectUri");
	            
	            
	            System.out.println(clientId);
	            System.out.println(clientSecret);
	            System.out.println(redirectUri);
	            configurationInfoStorage.saveConfigurationInfo(clientId, clientSecret, redirectUri);
	            System.setProperty("oauth.salesforce.clientId", clientId);
	            System.setProperty("oauth.salesforce.clientSecret",clientSecret);
	            System.setProperty("oauth.salesforce.redirectUri", redirectUri);

	            JSONObject jsonGlobal = new JSONObject();
	            jsonGlobal.put("message"," conf saved");
	            return Response.ok(jsonGlobal.toString(), mediaType).build();
	        } catch (Exception e) {
	            e.printStackTrace();
	            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("An internal error has occured").build();
	        }
	    }
	    
	    @GET
	    @Path("getconfig")
	    @Consumes({MediaType.APPLICATION_JSON})
	    public Response getConfig(@Context HttpServletRequest request,@Context HttpServletResponse response,@Context UriInfo uriInfo) throws Exception {
	        Identity sourceIdentity = Util.getAuthenticatedUserIdentity(portalContainerName);
	        MediaType mediaType = RestChecker.checkSupportedFormat("json", SUPPORTED_FORMATS);
	        ConfigurationInfoStorage configurationInfoStorage = (ConfigurationInfoStorage) PortalContainer.getInstance().getComponentInstanceOfType(ConfigurationInfoStorage.class);
	        try {
	            IdentityManager identityManager=Util.getIdentityManager(portalContainerName);
	            if(sourceIdentity == null) {
	                return Response.status(Response.Status.UNAUTHORIZED).build();
	            }
	            
	            JSONObject json = new JSONObject();
	           

	            
	            String clientId= System.getProperty("oauth.salesforce.clientId");
	            String clientSecret =System.getProperty("oauth.salesforce.clientSecret");
	            String redirectUri =System.getProperty("oauth.salesforce.redirectUri");
	            json.put("clientId",clientId);
	            json.put("clientSecret",clientSecret);
	            json.put("redirectUri",redirectUri);

	           
	            return Response.ok(json.toString(), MediaType.APPLICATION_JSON).build();
	        } catch (Exception e) {
	            e.printStackTrace();
	            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("An internal error has occured").build();
	        }
	    }
	    
	    /**
	     * get document contents of specific opportunity From SF  and store them into exo space drive
	     */  
	    
	    @GET
	    @Path("get/contentdocuments/{oppName}")
	    public Response createOpp(@Context HttpServletRequest request,
	    		 @PathParam("oppName")String oppName,
	    		 @QueryParam("workspaceName") String workspaceName,
	    		 @QueryParam("nodepath") String nodepath) throws Exception {
	    	Identity sourceIdentity = Util.getAuthenticatedUserIdentity(portalContainerName);
	    	 SpaceService spaceService = Util.getSpaceService(portalContainerName);
	    	 IdentityManager identityManager = Util.getIdentityManager(portalContainerName);
	    	 boolean firstCall=false;
	    	 Cookie[] cookies = request.getCookies();
	            String accesstoken=null;
	            String instance_url=null;
	    	 ActivityManager activityManager = (ActivityManager) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(ActivityManager.class);
	    	 ExoSocialActivity activity = new ExoSocialActivityImpl();
            if (sourceIdentity == null)
				return Response.status(Response.Status.UNAUTHORIZED).build();

			for (int i = 0; i < cookies.length; i++) {
				Cookie cookie1 = cookies[i];
				if (cookie1.getName().equals("tk_ck_")) {

					accesstoken = cookie1.getValue();
				}

				if (cookie1.getName().equals("inst_ck_")) {

					instance_url = cookie1.getValue();
				}
			}

			if(accesstoken!=null&&instance_url!=null){
			Space opp=spaceService.getSpaceByPrettyName(oppName);
			if(opp!=null){
				List<String> oppDocID = new ArrayList<String>();
				ForceApi api = OAuthServlet.initApiFromCookies(accesstoken, instance_url);
				//SELECT Id, ContentDocumentId FROM ContentDocumentLink WHERE LinkedEntityId = '00624000003onYq'
				 Identity spaceIdentity = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, opp.getPrettyName(), false);
				 String oppID =spaceIdentity.getProfile().getProperty("oppID").toString();
				//try to get the opportunity from the stored id
				
			
				String qq="SELECT Id, ContentDocumentId  FROM ContentDocumentLink where LinkedEntityId="+ "\'"+oppID+"\' LIMIT 100";
				QueryResult<ContentDocumentLink> queryDocID=api.query(qq, ContentDocumentLink.class);
				if(queryDocID.getTotalSize()>0){
					List<ContentDocumentLink> contentsLink = queryDocID.getRecords();
					Iterator<ContentDocumentLink> contentsLinkIt = contentsLink.iterator();
					while (contentsLinkIt.hasNext()) {
						String id =contentsLinkIt.next().getContentDocumentId();
						oppDocID.add(id);
						LOG.info("content id--->:"+id);
					}
					 nodepath = StringUtils.substringAfter(nodepath, "/");
					for (int i = 0; i < oppDocID.size(); i++) {
						String contentid=oppDocID.get(i);
						String qq2="SELECT  PathOnClient, FileType, VersionData  FROM ContentVersion where ContentDocumentId="+ "\'"+contentid+"\' LIMIT 1";
						
						QueryResult<ContentVersion> qdoc=api.query(qq2, ContentVersion.class);
						if(qdoc.getTotalSize()>0){
							LOG.info(qdoc.getRecords().get(0).getVersionDataUrl());
							HttpClient	httpclient1= new HttpClient();
							String VD=qdoc.getRecords().get(0).getVersionDataUrl();
							String path=qdoc.getRecords().get(0).getPathOnClient();
							GetMethod get = new GetMethod(instance_url+ VD);
							get.setRequestHeader("Authorization", "OAuth " + accesstoken);
							httpclient1.executeMethod(get);
							if (get.getStatusCode() == HttpStatus.SC_OK) {
                                 // download resource of the opportunity to local 
								// will be stored in eXo JCR(should create specific drive or folder for SF content) in next commits
 								 byte[] bodyByte =	get.getResponseBody();
 								SessionProvider sessionProvider = SessionProvider.createSystemProvider();
 								String workspace = (workspaceName!=null) ? workspaceName : "collaboration"; 				
 								Session session = sessionProvider.getSession(workspace,
 					                    repositoryService.getCurrentRepository());
 								
 								try {
									
									///Groups/spaces/united_oil_office_portable_generators/Documents/united_oil_office_portable_generators/xxxx.png
									

										Node rootNode = session.getRootNode().getNode(nodepath);
									 // Node file = rootNode.addNode("file", "nt:file");
									 if (!rootNode.hasNode(oppName)) {
									    rootNode.addNode(oppName, "nt:folder");
									   rootNode.save();
									}

									//check if the doc already imported from SLF to same path
									Node oppNode = rootNode.getNode(oppName);
									Node oppIDs = null;
									if(!oppNode.hasNode("oppIDs"))
									{
										oppIDs =oppNode.addNode("oppIDs","nt:folder");
									
										oppIDs.canAddMixin("exo:hiddenable");
										oppIDs.addMixin("exo:hiddenable");
										oppNode.save();
										
										
									}
									oppIDs =oppNode.getNode("oppIDs");
									
										if(oppIDs.getNodes().getSize()==0){
											oppIDs.addNode(contentid,"nt:folder");
											oppIDs.save();
											firstCall=true;
										}
										
										//dirty quick check , will be replaced
										
										 NodeIterator nodeIter = oppIDs.getNodes() ;
										 while(nodeIter.hasNext()) {
											 Node id = nodeIter.nextNode() ;
											 if(!id.getName().equals(contentid)||firstCall){
													String baseName = FilenameUtils.getBaseName(path);
													MimeTypeResolver resolver = new MimeTypeResolver();
													String type = resolver.getMimeType(path);

													Node fileNode = null;
													 if(!oppNode.hasNode(path)){
													fileNode = oppNode.addNode(path, "nt:file");
													
													Node jcrContent = fileNode.addNode("jcr:content", "nt:resource");
													jcrContent.setProperty("jcr:data",new ByteArrayInputStream(bodyByte));
													jcrContent.setProperty("jcr:lastModified",Calendar.getInstance());
													jcrContent.setProperty("jcr:encoding", "UTF-8");
													jcrContent.setProperty("jcr:mimeType", type);
													 }
													 if(!firstCall&&!oppIDs.hasNode(contentid))
													 {
													    oppIDs.addNode(contentid,"nt:folder");
													    oppIDs.save();
													 }
													 firstCall=false;
													oppNode.save();
													session.save();
												 }
												 
											 }
										
									

							         
	
									session.save();
								} catch (Exception e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}

							}

						}
					}

				}

			}
				

		/*
                activity.setTitle("");
   
                activity.setType("Salesforce_Activity");
                activity.setBody("The opportunity" );
                activityManager.saveActivityNoReturn( activity);
                */
            } 

       
           // portal/g/:spaces:united_oil_office_portable_generators/united_oil_office_portable_generators
            return Response.seeOther(URI.create(Util.getBaseUrl() + "/"+Utils.getSpaceUrl(oppName)+"/documents")).build();
           // return Response.ok("Created").build();
	    }
	    
	    
	    @POST
	    @Path("/chatterattachments/{oppID}")
	    @Consumes(MediaType.MULTIPART_FORM_DATA)
	    @Produces(MediaType.TEXT_PLAIN)
	    public Response uploadImageOrFile(@Context HttpServletRequest request,@Context HttpServletResponse response,
				@PathParam("oppID") String oppID,
				@QueryParam("oppName") String oppName,
				@QueryParam("poster") String poster,
				@QueryParam("postId") String postId,
				@QueryParam("textPost") String textPost,
				@QueryParam("mentionned") String mentionned,
				@QueryParam("contentPost") String contentPost,
				@QueryParam("contentID") String contentID,
				@QueryParam("contentPostText") String contentPostText) throws RepositoryException, IOException{
            	String REPOSITORY = "repository";
	    	String WORKSPACE = "collaboration";
	    	oppName=URLDecoder.decode(oppName, "UTF-8");
			poster=URLDecoder.decode(poster, "UTF-8");
			textPost =(textPost!=null)? URLDecoder.decode(textPost, "UTF-8"):null;
			contentPost=(contentPost!=null)? URLDecoder.decode(contentPost, "UTF-8"):null;
			mentionned=(mentionned!=null)? URLDecoder.decode(mentionned, "UTF-8"):null;
			SpaceService spaceService = (SpaceService) PortalContainer.getInstance().getComponentInstanceOfType(SpaceService.class);
			ActivityManager activityManager = (ActivityManager) ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(ActivityManager.class);
			IdentityManager identityManager = Util.getIdentityManager(portalContainerName);
			Space space = spaceService.getSpaceByDisplayName(URLDecoder.decode(oppName, "UTF-8"));
			if (space == null) {
				return Response.status(Response.Status.NOT_FOUND).entity("Opportunity not found").build();
			}
			
			Session session = repositoryService.getCurrentRepository().getSystemSession("collaboration");
			Node rootNode = session.getRootNode();
			Node sfFolder =rootNode.getNode("Groups"+space.getGroupId()+"/Documents/salesForceDocuments");
			String baseName = FilenameUtils.getBaseName(contentPost);
			MimeTypeResolver resolver = new MimeTypeResolver();
			String minetype = resolver.getMimeType(contentPost);
			//use contentID as unique name and title as real name as same content could be attached 
			Node fileNode = null;
			if(!sfFolder.hasNode(contentID)){
				fileNode=  sfFolder.addNode(contentID, "nt:file");
			fileNode.setProperty("exo:title", contentPost);
			Node jcrContent = fileNode.addNode("jcr:content", "nt:resource");
			String data = Utils.getBody(request);
			byte[] encodeBase64 = Base64.decodeBase64(data);
			jcrContent.setProperty("jcr:data",new ByteArrayInputStream(encodeBase64));
			jcrContent.setProperty("jcr:lastModified",Calendar.getInstance());
			jcrContent.setProperty("jcr:encoding", "UTF-8");
			jcrContent.setProperty("jcr:mimeType", minetype);
			sfFolder.save();
			}
			else
				fileNode=sfFolder.getNode(contentID);
			

			Identity spaceIdentity = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), false);
			Identity salesforceIdentity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "salesforce", false);
			ExoSocialActivity activity = new ExoSocialActivityImpl();

			
			activity.setType(UIDocActivity.ACTIVITY_TYPE);
			Map<String, String> templateParams = new HashMap<String, String>();
			if(mentionned!=null){
				String[] mentionnedList = mentionned.split(",");
				for(int i=0 ; i<mentionnedList.length;i++){
					contentPostText =StringUtils.replace(contentPostText, "@"+mentionnedList[i], "<a href=\"#\">"+"@"+mentionnedList[i]+"</a>",1);
					
				}
			}
			
			
			templateParams.put(UIDocActivity.WORKSPACE,WORKSPACE);
			templateParams.put(UIDocActivity.REPOSITORY,REPOSITORY);
			templateParams.put(UIDocActivity.MESSAGE, contentPostText);      
			//templateParams.put(UIDocActivity.DOCLINK,"/portal/rest/jcr/repository/collaboration/Users/t___/th___/tho___/thomas/Private/Documents/samir.pdf, DOCNAME=samir.pdf, DOCPATH=/Users/t___/th___/tho___/thomas/Private/Documents/samir.pdf");
			templateParams.put(UIDocActivity.DOCNAME,contentPost);
			templateParams.put(UIDocActivity.DOCUMENT_TITLE,contentPost);
			templateParams.put(UIDocActivity.IS_SYMLINK,"false");
			templateParams.put(UIDocActivity.MIME_TYPE, minetype);
			templateParams.put(UIDocActivity.DOCPATH,fileNode.getPath());
			activity.setTemplateParams(templateParams);
           
			
			
			activity.setUserId(salesforceIdentity.getId());
			String activitybody="";
			activitybody+=" posted new file : "+"<a href=\"#\">"+contentPost+"</a>" +" " +contentPostText;			
			activity.setTitle(activitybody);
			activity.setBody(activitybody);
            activityManager.saveActivityNoReturn(spaceIdentity, activity);
			PostActivitiesService postActivitiesService = (PostActivitiesService) PortalContainer.getInstance().getComponentInstanceOfType(PostActivitiesService.class);
			postActivitiesService.createEntity(new PostActivitiesEntity(activity.getId(), postId));

		return Response.status(200).build();
	}

	    
	    
	    // this rest service will be used to get update from SF chatter, auto send ajax update periodically  
	    @POST
	    @Path("update")
	    @Consumes({MediaType.APPLICATION_JSON})
	    public Response update(@Context HttpServletRequest request,@Context HttpServletResponse response,@Context UriInfo uriInfo,
	                                 Map<String,String> space) throws Exception {
		

		Identity sourceIdentity = Util
				.getAuthenticatedUserIdentity(portalContainerName);
		MediaType mediaType = RestChecker.checkSupportedFormat("json",
				SUPPORTED_FORMATS);

		try {
			if (sourceIdentity == null) {
				return Response.status(Response.Status.UNAUTHORIZED).build();
			}
			  String oppid=space.get("oppid");
			
			Cookie[] cookies = request.getCookies();
			request.getRequestURI();
			String accesstoken=null;
			String instance_url=null;
						for (int i = 0; i < cookies.length; i++) {
				Cookie cookie1 = cookies[i];
				if (cookie1.getName().equals("tk_ck_")) {

					accesstoken = cookie1.getValue();
				}

				if (cookie1.getName().equals("inst_ck_")) {

					instance_url = cookie1.getValue();
				}

			}
			if(accesstoken!=null&&accesstoken!=null){
			ForceApi api = OAuthServlet.initApiFromCookies(accesstoken, instance_url);
			//SELECT COUNT(Id) FROM OpportunityFeed where parentId="oppid"
			String qq="SELECT COUNT(Id) FROM OpportunityFeed where ParentId="+ "\'"+oppid+"\'";
			
			//SELECT COUNT(Id) FROM OpportunityFeed
			QueryResult<AggregateResult> totalFeed=api.query(qq, AggregateResult.class);
			
			
			int nbfeed=Integer.parseInt(totalFeed.getRecords().get(0).getexpr0());
			// check if nb feed was changed nbfeed >oppProfile.gettProperty("nbOppFeed"); means new update
			//so got the update , push to exo and store the new nbfeed to the space profile 
			}
			JSONObject jsonGlobal = new JSONObject();
			jsonGlobal.put("message", " update");
			return Response.ok(jsonGlobal.toString(), mediaType).build();
		} catch (Exception e) {
			e.printStackTrace();
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("An internal error has occured").build();
		}
	}
	    
	    

	    
	    
	    
	    
	    
}
