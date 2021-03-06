package org.exoplatform.salesforce.integ.connector.servlet;

import com.force.api.ApiConfig;
import com.force.api.ApiSession;
import com.force.api.ApiVersion;
import com.force.api.ForceApi;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.PostMethod;
import org.exoplatform.salesforce.integ.connector.entity.UserConfig;
import org.exoplatform.salesforce.integ.rest.UserService;
import org.exoplatform.salesforce.integ.util.RequestKeysConstants;
import org.exoplatform.salesforce.integ.util.ResourcePath;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;

public class OAuthServlet extends HttpServlet {
	public class OauthToken {

	}

	private static final long serialVersionUID = 1L;

	public static final String ACCESS_TOKEN = "ACCESS_TOKEN";
	private static final Log LOG = ExoLogger.getLogger(OAuthServlet.class);
	// public static final String OPP_ID = "oppID";
	// public static final String INSTANCE_URL = "INSTANCE_URL";

	private String clientId = null;
	private String clientSecret = null;
	private String redirectUri = null;
	private String authUrl = null;
	private String tokenUrl = null;
	private String tempOppID = null;
	private String refreshtoken = null;
	public static String tk_url;
	public static String tk_tok;
	public static String environment;
	private String initialURI =null;
	

	public void init() throws ServletException {
		try {
			// it's better to use filter in next step for all auth request
			clientId=System.getProperty("oauth.salesforce.clientId");
			if (System.getProperty("oauth.salesforce.clientId")==null) {
				clientId = "3MVG9Rd3qC6oMalVaRGdPD6BFFD89SgIXKOVxc2nwIPmdYDkFPuXBLWpPTz2D685IIG.DFVYEwYEdIqo9B827";
				System.setProperty("oauth.salesforce.clientId", clientId);

			}
			
			clientSecret =System.getProperty("oauth.salesforce.clientSecret") ;
			if (System.getProperty("oauth.salesforce.clientSecret") == null) {
				clientSecret = "3281403007789330224";
				System.setProperty("oauth.salesforce.clientSecret",clientSecret);
			}
			
			redirectUri=System.getProperty("oauth.salesforce.redirectUri");
			if (System.getProperty("oauth.salesforce.redirectUri") == null) {
				redirectUri = "https://plfent-4.3.x-pkgpriv-salesforce-integration-snapshot.acceptance5.exoplatform.org/salesforce-extension/oauth/_callback";
				System.setProperty("oauth.salesforce.redirectUri", redirectUri);

			}
			

			environment = RequestKeysConstants.SF_PROD;

			authUrl = environment + ResourcePath.AUTHORIZE.getPath()
					+ "?response_type=code&client_id=" + clientId
					+ "&redirect_uri="
					+ URLEncoder.encode(redirectUri, "UTF-8");
			tokenUrl = environment + ResourcePath.TOKEN.getPath();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			throw new ServletException(e);
		}
	}

	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
		Cookie[] cookies = request.getCookies();
		clientId=System.getProperty("oauth.salesforce.clientId");
		redirectUri=System.getProperty("oauth.salesforce.redirectUri");
		clientSecret =System.getProperty("oauth.salesforce.clientSecret") ;
		
		LOG.info("Begin OAuth");
		tempOppID = (tempOppID == null) ? request.getParameter("oppID")
				: tempOppID;
		initialURI = (initialURI == null) ? request.getParameter("initialURI")
				: initialURI;
		
		String accessToken = (String) request.getSession().getAttribute(
				ACCESS_TOKEN);
		String instanceUrl = (String) request.getSession().getAttribute(
				RequestKeysConstants.INSTANCE_URL);
		// request.getSession().setAttribute(RequestKeysConstants.OPPORTUNITY_ID,
		// request.getParameter("oppID"));

		if (accessToken == null || instanceUrl == "") {

			// request.getSession().setAttribute(RequestKeysConstants.OPPORTUNITY_ID,
			// request.getParameter("oppID"));

			/* get the request query oppID=00624000003M6ac */
			request.getQueryString();
			request.getParameter("oppID");

			if (request.getRequestURI().endsWith("oauth")) {
				// we need to send the user to authorize
				request.getSession().setAttribute(
						RequestKeysConstants.OPPORTUNITY_ID, tempOppID);

				response.sendRedirect(authUrl + "&id=" + tempOppID);
				return;
			} else {
				LOG.info("Auth successful - got callback");
				// request.getSession().setAttribute(RequestKeysConstants.OPPORTUNITY_ID,tempOppID);

				String code = request.getParameter("code");

				HttpClient httpclient = new HttpClient();

				PostMethod post = new PostMethod(tokenUrl);

				request.getSession().setAttribute(
						RequestKeysConstants.OPPORTUNITY_ID, tempOppID);

				post.addParameter(RequestKeysConstants.CODE_KEY, code);
				post.addParameter(RequestKeysConstants.GRANT_TYPE_KEY,
						RequestKeysConstants.AUTHORIZATION_CODE);
				post.addParameter(RequestKeysConstants.CLIENT_ID_KEY, clientId);
				post.addParameter(RequestKeysConstants.CLIENT_SECRET_KEY,
						clientSecret);
				post.addParameter(RequestKeysConstants.REDIRECT_URI_KEY,
						redirectUri);
				// post.addParameter(RequestKeysConstants.REDIRECT_URI_KEY,
				// redirectUri);

				try {
					httpclient.executeMethod(post);

					try {

						InputStream rstream = post.getResponseBodyAsStream();
						BufferedReader reader = new BufferedReader(
								new InputStreamReader(rstream, "UTF-8"));
						String json = reader.readLine();
						JSONTokener tokener = new JSONTokener(json);
						JSONObject authResponse = new JSONObject(tokener);

						accessToken = authResponse
								.getString(RequestKeysConstants.ACCESS_TOKEN);

						instanceUrl = authResponse
								.getString(RequestKeysConstants.INSTANCE_URL);
						initApi(request, accessToken, instanceUrl);
					} catch (JSONException e) {
						e.printStackTrace();
						throw new ServletException(e);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				} catch (HttpException e) {
					e.printStackTrace();
					throw new ServletException(e);
				} finally {
					post.releaseConnection();
				}
			}

			// QueryResult<Account> iniResult =



			request.getSession().setAttribute(ACCESS_TOKEN, accessToken);
			// request.getSession().setAttribute(RequestKeysConstants.OPPORTUNITY_ID,
			// request.getParameter("oppID"));

			request.getSession().setAttribute(
					RequestKeysConstants.INSTANCE_URL, instanceUrl);
		}
		boolean ist = false, tk = false;
		for (int i = 0; i < cookies.length; i++) {
			Cookie cookie1 = cookies[i];
			if (cookie1.getName().equals("tk_ck_")) {

				tk = true;
			}

			if (cookie1.getName().equals("inst_ck_")) {

				ist = true;
			}

		}
		if (!tk) {
			Cookie tk_cookie = new Cookie("tk_ck_", accessToken);
			tk_cookie.setMaxAge(60 * 60); // 1 hour
			tk_cookie.setPath("/");
			response.addCookie(tk_cookie);
		}
		if (!ist) {
			Cookie inst_cookie = new Cookie("inst_ck_",
					instanceUrl);
			inst_cookie.setMaxAge(60 * 60); // 1 hour
			inst_cookie.setPath("/");
			response.addCookie(inst_cookie);
		}
		request.getSession().setAttribute(RequestKeysConstants.OPPORTUNITY_ID,
				request.getParameter("oppID"));

		
		if(initialURI!=null){
			String tempInitialURI=initialURI;
			initialURI=null; //re-inti
			response.sendRedirect(tempInitialURI+"?status=oauth");
			
		}
		if(tempOppID!=null)
		{
			String s = tempOppID;
			tempOppID = null;
		response.sendRedirect("/salesforce-extension/opp" + "?id=" + s);
		}
		//else
			//response.sendRedirect("/portal");
			
	}

	public static ForceApi initApi(HttpServletRequest request,
			String accessToken, String instanceUrl) throws Exception {
		ApiVersion apiVersion = ApiVersion.DEFAULT_VERSION;
		ApiConfig c = new ApiConfig()
		.setClientId(System.getProperty("oauth.salesforce.clientId"))
		.setClientSecret(System.getProperty("oauth.salesforce.clientSecret"))
		.setRedirectURI(System.getProperty("oauth.salesforce.redirectUri"))
		.setLoginEndpoint(RequestKeysConstants.SF_PROD)
		.setApiVersion(apiVersion);

		ApiSession s = new ApiSession(accessToken, instanceUrl);
		ForceApi api = new ForceApi(c, s);
		UserService.userMap.put(api.getIdentity().getUserId(), new UserConfig(
				accessToken, instanceUrl.toString()));
		return api;
	}

	public static ForceApi initApiFromCookies(String accessToken,
			String instanceUrl) throws Exception {
		ApiVersion apiVersion = ApiVersion.DEFAULT_VERSION;
		ApiConfig c = new ApiConfig()
		.setClientId(System.getProperty("oauth.salesforce.clientId"))
		.setClientSecret(System.getProperty("oauth.salesforce.clientSecret"))
		.setRedirectURI(System.getProperty("oauth.salesforce.redirectUri"))
		.setLoginEndpoint(RequestKeysConstants.SF_PROD)
		.setApiVersion(apiVersion);
		
		ApiSession s = new ApiSession(accessToken, instanceUrl);
		ForceApi api = new ForceApi(c, s);
		return api;
	}

}
