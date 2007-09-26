/*
 * Copyright (c) 2007 UNINETT FAS
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package no.feide.client.lasso;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPConnection;
import javax.xml.soap.SOAPConnectionFactory;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;

import com.entrouvert.lasso.Login;
import com.entrouvert.lasso.Logout;
import com.entrouvert.lasso.Saml2NameID;
import com.entrouvert.lasso.Samlp2AuthnRequest;
import com.entrouvert.lasso.Samlp2Response;
import com.entrouvert.lasso.Server;
import com.entrouvert.lasso.lasso;
import com.entrouvert.lasso.lassoConstants;

/**
 * A simple test "web application", using the Lasso client API. Does not really
 * do anything, except show login success and handle logout requests.
 */
public class TestServlet
extends HttpServlet {

    /**
     * Serial version UID, default value.
     */
    private static final long serialVersionUID = 1L;

    /**
     * Used for debug logging. Will write to the debug log
     * <code>feideTestServlet</code>.
     */
    private Debug debug = new Debug();

    private Config config;

    /**
     * The Lasso server.
     */
    private Server lassoServer = null;


    /**
     * This method initializes the servlet. Initialization consists of loading the configuration
     * and initializing the lassoServer object with meta data.
     */
    @Override
    public void init() throws ServletException {
        this.config = new Config(this.getServletContext());

        // Initialize the lassoServer object with the meta data of the service provider.
        this.lassoServer = new Server(config.getSPMetadataPath(), config.getSPPrivateKeyPath(),
                null, null);

        // Load meta data for the IdP.
        this.lassoServer.addProvider(lassoConstants.PROVIDER_ROLE_IDP,
                config.getIdPMetadataPath(), config.getIdpPublicKeyPath(), null);
        System.out.println((new File(".").getAbsolutePath()));
    }

    /**
     * This method handles the parts of the request to the assertion consumer which is common between
     * the GET request and the POST request.
     *
     * @param response the response we send to the client.
     * @param loginAttempt the lasso Login object, with information about the user.
     * @throws ServletException
     * @throws IOException
     */
    private void handleLoginResponse(HttpServletResponse response,
            Login loginAttempt) throws ServletException, IOException {

        int rc;
        // More Lasso preparations.
        rc = loginAttempt.acceptSso();
        if(rc != 0) {
            throw new ServletException("Failed to accept SSO. Lasso error code: " + rc);
        }

        Samlp2Response r = (Samlp2Response)loginAttempt.getResponse();

        Saml2NameID nameId = (Saml2NameID)loginAttempt.getNameIdentifier();
        this.debug.message("Got name id: " + nameId.getContent());

        Map<String, Attribute> attributes = AttributeExtractor.extractAttributes(this.config, r);
        // We're logged in.
        onLogin(response, attributes);
    }

    /**
     * Handles GET requests to the assertion consumer.
     *
     * @param request the request we received.
     * @param response the response we send back.
     * @throws ServletException
     * @throws IOException
     */
    private void doLoginGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {

        if (debug.messageEnabled())
            debug.message("doLoginGet(HttpServletRequest, HttpServletResponse)");

        // Do we have a SAML artifact?
        String samlArtifact = request.getParameter("SAMLart");
        if (samlArtifact == null) {
            throw new ServletException("No SAMLart request parameter to the assertion consumer.");
        }

        // Initialize.
        Login loginAttempt = new Login(lassoServer);
        loginAttempt.initRequest(samlArtifact, lassoConstants.HTTP_METHOD_REDIRECT);
        loginAttempt.buildRequestMsg();
        
        String samlResponse = this.doSoapRequest(loginAttempt.getMsgUrl(), loginAttempt.getMsgBody());
        // Let Lasso check the reply.

        loginAttempt.processResponseMsg(samlResponse);

        this.handleLoginResponse(response, loginAttempt);

        debug.message("doLoginGet done");

    }

    /**
     * Handles requests to the logout handler.
     *
     * @param request the servlet request
     * @param response the servlet response we will write the response to.
     * @throws ServletException
     * @throws IOException
     */
    private void doLogoutGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {

        if(debug.messageEnabled()) {
            debug.message("doLogoutGet(HttpServletRequest, HttpServletResponse)");
        }

        Logout logout = new Logout(this.lassoServer);

        int rc;

        // Parse and process the logout message.
        rc = logout.processRequestMsg(request.getQueryString());
        if(rc != 0 && rc != lasso.DS_ERROR_SIGNATURE_NOT_FOUND) {
            throw new ServletException("Error processing logout request message. Lasso error: " + rc);
        }

        // Log the user out.
        this.onLogout(request);

        // Create a response to the IdP.
        rc = logout.buildResponseMsg();
        if(rc != 0) {
            throw new ServletException("Error creating logout response message. Lasso error: " + rc);
        }

        // Redirect back to the IdP.
        response.sendRedirect(logout.getMsgUrl());

        if(debug.messageEnabled()) {
            debug.message("doLogoutGet done");
        }
    }

    /**
     * Handles POST requests to the assertion consumer URL.
     *
     * @param request the servlet request.
     * @param response the servlet response.
     * @throws ServletException
     * @throws IOException
     */
    private void doLoginPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String samlResponse = request.getParameter("SAMLResponse");
        try {
            samlResponse = new String(Base64.decode(samlResponse), "UTF-8");
        } catch(UnsupportedEncodingException e) {
            throw new ServletException("UTF-8 encoding is unsupported.");
        }

        Login loginAttempt = new Login(this.lassoServer);
        loginAttempt.processAuthnResponseMsg(samlResponse);

        this.handleLoginResponse(response, loginAttempt);
    }

    /**
     * Will receive HTTP GET requests and handle login and logout. Throws a ServletException
     * if it receives an unknown URL.
     *
     * @see javax.servlet.http.HttpServlet#doGet(HttpServletRequest,
     *      HttpServletResponse)
     */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {

        if (debug.messageEnabled())
            debug.message("doGet(HttpServletRequest, HttpServletResponse)");

        // DEBUG: Dump information from the request.
        debug.message("HTTP request URL was " + request.getRequestURL());
        debug.message("HTTP request query was " + request.getQueryString());

        if("/login".equals(request.getPathInfo())) {
            this.initLogin(response);
        } else if("/logout".equals(request.getPathInfo())) {
            this.doLogoutGet(request, response);
        } else if("/assertion".equals(request.getPathInfo())) {
            this.doLoginGet(request, response);
        } else {
            throw new ServletException("GET request to unknown url. Path info: "
                    + request.getPathInfo());
        }

        debug.message("doGet done");

    }

    /**
     * This method handles POST requests to this servlet. The only place we want POST data is in the
     * assertion consumer. All other URLs will result in an ServletException
     */
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {

        if (debug.messageEnabled()) {
            debug.message("doPost(HttpServletRequest, HttpServletResponse)");
        }

        if("/assertion".equals(request.getPathInfo())) {
            this.doLoginPost(request, response);
        } else {
            throw new ServletException("POST to non-post url. Path info: " + request.getPathInfo());
        }

        debug.message("doPost done");
    }

    /**
     * Creates a Lasso login attempt based on meta data and redirects to the IdP
     * with an authentication request.
     *
     * @param request the servlet request
     * @param response the servlet response
     * @throws IOException if we fail to redirect the user
     */
    private void initLogin(HttpServletResponse response) throws IOException {

        if (debug.messageEnabled())
            debug.message("login(HttpServletRequest, HttpServletResponse)");

        // Create a Lasso login attempt.
        Login loginAttempt = new Login(lassoServer);
        loginAttempt.initAuthnRequest(lassoServer.getProviderIds().getItem(0), lassoConstants.HTTP_METHOD_REDIRECT);

        
        Samlp2AuthnRequest authnRequest = (Samlp2AuthnRequest)loginAttempt.getRequest();

        // Select the HTTP-Redirect binding for the login request.
        authnRequest.setProtocolBinding(lassoConstants.SAML2_METADATA_BINDING_ARTIFACT);

        if (debug.messageEnabled()) {
            debug.message("login: Authentication request: " + authnRequest.dump());
        }

        // Build the request message.
        loginAttempt.buildAuthnRequestMsg();

        // Get the URL we should redirect to.
        String gotoURL = loginAttempt.getMsgUrl();

        // Redirect with authentication request.
        if (debug.messageEnabled()) {
            debug.message("login: Redirecting to: " + gotoURL);
        }

        response.sendRedirect(gotoURL);
    }

    /**
     * This method is called on a successful login.
     *
     * @param response the servlet response we should write to.
     * @param attributes a Map containing the attributes we have received for the user.
     * @throws IOException if we fail to write the output.
     */
    private void onLogin(HttpServletResponse response, Map<String, Attribute> attributes)
    throws IOException {

        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = response.getWriter();
        out.println("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\" \"http://www.w3.org/TR/html4/strict.dtd\">");
        out.println("<html<head><title>Feide Lasso client API Test Servlet</title></head>");
        out.println("<body><h1>OK</h1>");
        out.println("<table><tr><th>Name</th><th>Values</th></tr>");
        for(Map.Entry<String, Attribute> a : attributes.entrySet()) {
            StringBuilder sb = new StringBuilder();
            for(String value : a.getValue()) {
                if(sb.length() != 0) {
                    sb.append("<br>");
                }
                sb.append(value);
            }
            out.println("<tr><td>"+a.getKey() + "</td><td>" + sb.toString() + "</td></tr>");
        }
        out.println("</table>");
        out.println("<p><a href=\"" + config.getProperty("no.feide.test.url.logout") + "\">Logout</a></p>");
        out.close();
    }

    /**
     * Logs the user out of this web application.
     *
     * @param request the servlet request.
     */
    private void onLogout(HttpServletRequest request) {
        // Nothing is done, since we don't implement sessions.
    }

    /**
     * Helper function to do a SOAP request to the specified URL with the specified message.
     *
     * @param url URL to send the request to.
     * @param message message to send.
     * @return answer to the message we sent.
     * @throws ServletException if the SOAP call failed.
     * @throws IOException if there was an IO error while executing the request, or if we had problems with the character set conversion.
     */
    private String doSoapRequest(String url, String message) throws ServletException, IOException {
        if(this.debug.messageEnabled()) {
            this.debug.message("doSoapRequest(\"" + url + "\", \"" + message + "\");");
        }

        // Set the content-type of the soap request to "text/xml".
        MimeHeaders soapRequestHeaders = new MimeHeaders();
        soapRequestHeaders.addHeader("Content-Type", "text/xml");

        // Create an InputStream for the request message.
        ByteArrayInputStream soapRequestBuffer = new ByteArrayInputStream(message.getBytes("UTF-8"));

        try {
            SOAPMessage soapRequest = MessageFactory.newInstance().createMessage(
                    soapRequestHeaders, soapRequestBuffer);
            SOAPConnection soapConnection = SOAPConnectionFactory.newInstance().createConnection();
            
            // Make the SOAP call.
            final SOAPMessage soapResponse = soapConnection.call(soapRequest, url);

            // Get the reply.
            ByteArrayOutputStream soapReplyBuffer = new ByteArrayOutputStream();
            soapResponse.writeTo(soapReplyBuffer);
            String reply = soapReplyBuffer.toString("UTF-8");

            if(this.debug.messageEnabled()) {
                this.debug.message("doSoapRequest got response:\n******\n" + reply + "\n******");
            }

            return reply;
        } catch(SOAPException e) {
            throw new ServletException("Error executing SOAP request against " + url, e);
        } 
    }

}
